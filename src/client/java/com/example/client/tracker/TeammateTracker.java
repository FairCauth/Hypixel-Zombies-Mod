package com.example.client.tracker;

import com.example.client.utils.IMinecraft;
import com.example.client.utils.PlayerUtils;
import com.example.client.utils.ScoreboardUtils;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.regex.Pattern;

public class TeammateTracker implements IMinecraft {

    private static final Map<String, TeammateInfo> TEAMMATES = new LinkedHashMap<>();

    public static void syncTeammates() {
        if (mc.player == null || mc.level == null) {
            clear();
            return;
        }

        // 必须在计分板/TAB/实体三种判定全部更新前保存最终上一帧状态，
        // 这样不会把同一帧内的临时状态变化误判为“被救起”。
        Map<String, TeammateInfo.PlayerState> stateBeforeSync = new HashMap<>();
        for (TeammateInfo info : TEAMMATES.values()) {
            stateBeforeSync.put(info.getName(), info.getPlayerState());
        }

        List<ScoreboardUtils.ScorePlayer> scorePlayers = ScoreboardUtils.getZombiesPlayers();

        if (!scorePlayers.isEmpty()) {
            Set<String> current = new HashSet<>();

            for (ScoreboardUtils.ScorePlayer sp : scorePlayers) {
                String name = cleanName(sp.name());
                if (name.isEmpty()) continue;

                current.add(name);
                TeammateInfo info = TEAMMATES.computeIfAbsent(name, TeammateInfo::new);
                if (sp.gold() != null) {
                    info.setGold(sp.gold());
                }
                info.setPlayerState(sp.state());
                info.setStatusText(sp.statusText());
                info.setDown(sp.state() == TeammateInfo.PlayerState.DOWN);
                if (info.isTerminalState()) {
                    info.setBeingRevived(false);
                    info.setReviveSeconds(0);
                    info.clearFastRevive();
                }
            }

            // 只移除已经不在计分板上的人（真正离开了游戏）
            TEAMMATES.keySet().removeIf(k -> !current.contains(k));
        }
        // 计分板暂时读不到（切图/加载）时，保留旧名单，不清空

        // 2) 每帧把名单关联到世界实体，并判定倒地
        resolveEntities();

        // 最终状态由 down -> alive：确认救起，启动 5 秒快速复活窗口。
        for (TeammateInfo info : TEAMMATES.values()) {
            if (stateBeforeSync.get(info.getName()) == TeammateInfo.PlayerState.DOWN
                    && info.getPlayerState() == TeammateInfo.PlayerState.ALIVE) {
                info.startFastRevive(5_000L);
            }
        }

        // 3) 判定"正在被救"
        detectBeingRevived();

        updateSnapshot();
    }

    /** 倒地者头顶 holo 的计时格式（语言无关）：如 24.0s / 0.6s。 */
    private static final Pattern REVIVE_TIMER = Pattern.compile("\\d+(\\.\\d+)?s");

    /**
     * 正在被救判定（语言无关）：倒地者身体附近的 holo 盔甲架里，
     *  - 有"计时"holo（X.Xs）= 这是个救援 holo；
     *  - 且附近【没有】含 "SHIFT" 的提示行（等待救援时是"按住SHIFT…"，被救时变成"复活中…"不含 SHIFT）
     *  → 正在被救。需要先定位到倒地者身体（renderEntity）。
     */
    /** 一个救援 holo 簇（同一 xz 上方那一摞盔甲架）。 */
    private static final class Cluster {
        double x, z;
        String timerText;   // 计时行 X.Xs（有 = 这是个救援簇）
        boolean hasShift;   // 含 "按住SHIFT…" = 等待救援；否则 = 正在被救
    }

    // holo 就在身体正上方同一 xz；皮肤匹配给出精确身体坐标，所以收紧这两个半径：
    // 只合并"同一摞"的 holo、并就近配给身体。两人精确坐标差一点点就能分开（只有完全同坐标才无解）。
    private static final double CLUSTER_XZ = 0.6;   // 同簇(同一摞)的水平合并半径
    private static final double MATCH_MAX = 1.5;    // 身体坐标 ↔ 簇 的最大匹配水平距离

    private static long skinDbgMs = 0;

    private static void detectBeingRevived() {
        // 调试：对比"倒地队友活着时记录的皮肤"和"世界里假人实体的皮肤"，看能否按皮肤对应
//        if (System.currentTimeMillis() - skinDbgMs > 1000) {
//            skinDbgMs = System.currentTimeMillis();
//            for (TeammateInfo t : TEAMMATES.values()) {
//                if (t.isDown()) System.out.println("[SKIN-T] " + t.getName() + " skin=" + t.getSkin());
//            }
//            for (Player p : mc.level.players()) {
//                System.out.println("[SKIN-W] disp='" + cleanName(p.getName().getString())
//                        + "' skin=" + skinOf(p) + " pos=(" + (int) p.getX() + "," + (int) p.getZ() + ")");
//            }
//            //■■■■■■■■■■■■■■■
//            // 看救援 holo 盔甲架是否"挂在"某个实体上(passenger/vehicle)——若是，完全重叠也能精确归属
//            for (Entity e : mc.level.entitiesForRendering()) {
//                if (!(e instanceof ArmorStand)) continue;
//                Component cn = e.getCustomName();
//                if (cn == null || cn.getString().trim().isEmpty()) continue;
//                Entity veh = e.getVehicle();
//                System.out.println("[HOLO] '" + cn.getString().trim() + "' vehicle="
//                        + (veh == null ? "null" : (veh.getType() + "#" + veh.getId()))
//                        + " id=" + e.getId());
//            }
//        }

        // 先全部清掉，匹配到的再设
        List<TeammateInfo> downed = new ArrayList<>();
        for (TeammateInfo info : TEAMMATES.values()) {
            info.setBeingRevived(false);
            info.setReviveSeconds(0);
            if (info.isDown() && (info.getRenderEntity() != null || info.isPosKnown())) {
                downed.add(info);
            }
        }
        if (downed.isEmpty()) return;

        // 1) 把世界里所有带名牌的盔甲架按 xz 聚成簇，提取计时/SHIFT
        List<Cluster> clusters = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ArmorStand)) continue;
            Component cn = e.getCustomName();
            if (cn == null) continue;
            String t = cn.getString().trim();
            if (t.isEmpty()) continue;

            Cluster c = null;
            for (Cluster ex : clusters) {
                double dx = ex.x - e.getX(), dz = ex.z - e.getZ();
                if (dx * dx + dz * dz <= CLUSTER_XZ * CLUSTER_XZ) { c = ex; break; }
            }
            if (c == null) { c = new Cluster(); c.x = e.getX(); c.z = e.getZ(); clusters.add(c); }

            if (t.toUpperCase().contains("SHIFT") || t.toUpperCase().contains("SNEAK")) c.hasShift = true;
            if (REVIVE_TIMER.matcher(t).matches()) c.timerText = t;
        }
        // 只保留"救援簇"（有计时行的）
        clusters.removeIf(c -> c.timerText == null);
        if (clusters.isEmpty()) return;

        // 2) 倒地队友 ↔ 救援簇 做一对一最近匹配（每个簇只用一次），拥挤时也能分对
        record Pair(TeammateInfo info, Cluster cluster, double dist2) {}
        List<Pair> pairs = new ArrayList<>();
        for (TeammateInfo info : downed) {
            double tx, tz;
            Player body = info.getRenderEntity();
            if (body != null) { tx = body.getX(); tz = body.getZ(); }
            else { tx = info.getLastX(); tz = info.getLastZ(); }

            for (Cluster c : clusters) {
                double dx = c.x - tx, dz = c.z - tz;
                double d2 = dx * dx + dz * dz;
                if (d2 <= MATCH_MAX * MATCH_MAX) pairs.add(new Pair(info, c, d2));
            }
        }
        pairs.sort(Comparator.comparingDouble(Pair::dist2));

        Set<TeammateInfo> usedInfo = new HashSet<>();
        Set<Cluster> usedCluster = new HashSet<>();
        for (Pair p : pairs) {
            if (usedInfo.contains(p.info()) || usedCluster.contains(p.cluster())) continue;
            usedInfo.add(p.info());
            usedCluster.add(p.cluster());

            boolean reviving = !p.cluster().hasShift; // 救援簇且不含 SHIFT = 正在被救
            p.info().setBeingRevived(reviving);
            if (reviving) {
                String tt = p.cluster().timerText;
                try {
                    p.info().setReviveSeconds(Double.parseDouble(tt.substring(0, tt.length() - 1)));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    /**
     * 判定倒地 + 关联渲染实体，全程不依赖聊天（语言无关）：
     *  - 倒地判定：在计分板名单里、但账号不在 TAB 列表里 = 倒地（玩家倒地会从 TAB 消失）
     *  - 渲染实体：活着用真玩家（在 TAB、gameProfile 名匹配），倒地用假人（名牌含队友名）
     */
    private static void resolveEntities() {
        for (TeammateInfo info : TEAMMATES.values()) {
            info.setRenderEntity(null);
        }

        // TAB 名单：真实账号的 UUID 和用户名
        Set<UUID> tabUuids = new HashSet<>();
        Set<String> tabNames = new HashSet<>();
        if (mc.getConnection() != null) {
            for (PlayerInfo pi : mc.getConnection().getOnlinePlayers()) {
                tabUuids.add(pi.getProfile().id());
                tabNames.add(cleanName(pi.getProfile().name()));
            }
        }

        // 1) 倒地兜底：计分板状态已在 syncTeammates 设过（主判据）。
        //    这里只用 TAB 补漏——不在 TAB 也算倒地（只升不降，避免 TAB 延迟把已判定的倒地清掉）。
        if (!tabNames.isEmpty()) {
            for (TeammateInfo info : TEAMMATES.values()) {
                if (info.getPlayerState() == TeammateInfo.PlayerState.ALIVE
                        && !tabNames.contains(info.getName())) {
                    info.setPlayerState(TeammateInfo.PlayerState.DOWN);
                    info.setDown(true);
                }
            }
        }

        // 2) 活着的真人(在 TAB) → 显示实体
        for (Player p : mc.level.players()) {
            if (tabUuids.contains(p.getUUID())) {
                TeammateInfo info = TEAMMATES.get(cleanName(p.getGameProfile().name()));
                if (info != null) {
                    info.setRenderEntity(p);
                }
            }
        }

        // 2.5) 倒地者身体：本地玩家=自己(mc.player)；远程=按【皮肤】匹配的假人。
        //      假人名字/UUID 是随机的，但皮肤是该玩家的真皮肤 → 位置无关，完全重叠也能对应。
        String selfName = cleanName(mc.player.getGameProfile().name());
        for (TeammateInfo info : TEAMMATES.values()) {
            if (!info.isDown() || info.getRenderEntity() != null) continue;

            if (info.getName().equals(selfName)) {
                info.setRenderEntity(mc.player); // 你自己倒地（自身皮肤客户端取不到，直接用 mc.player）
                continue;
            }

            String skin = info.getSkin();
            // 皮肤为 none(默认皮)或与其他队友重复 → 不靠皮肤(会认错)，留给 detectBeingRevived 的 lastPos 兜底
            if (skin == null || skin.equals("none") || !isSkinUnique(skin, info)) continue;
            for (Player p : mc.level.players()) {
                if (tabUuids.contains(p.getUUID())) continue;   // 活人跳过
                if (skin.equals(skinOf(p))) {                   // 假人皮肤 == 该队友活着时记录的真皮肤
                    info.setRenderEntity(p);
                    break;
                }
            }
        }

        // 3) 活着(有真人实体)时持续记录坐标+皮肤；倒地后实体没了，用它们定位/对应
        for (TeammateInfo info : TEAMMATES.values()) {
            Player e = info.getRenderEntity();
            if (e != null) {
                info.setLastPos(e.getX(), e.getY(), e.getZ());
                info.setSkin(skinOf(e));
            }
        }
    }

    /** 这个皮肤在队友名单里是否唯一（只有 self 这一个用它）。重复就不靠皮肤匹配，避免认错。 */
    private static boolean isSkinUnique(String skin, TeammateInfo self) {
        for (TeammateInfo other : TEAMMATES.values()) {
            if (other == self) continue;
            if (skin.equals(other.getSkin())) return false;
        }
        return true;
    }

    /** 取皮肤标识（textures 属性值的短哈希）；没有返回 "none"。 */
    static String skinOf(Player p) {
        try {
            for (com.mojang.authlib.properties.Property pr : p.getGameProfile().properties().get("textures")) {
                return Integer.toHexString(pr.value().hashCode());
            }
        } catch (Throwable ignored) {}
        return "none";
    }

    public static TeammateInfo get(String name) {
        return resolveByName(name);
    }

    public static Player getPlayer(String name) {
        TeammateInfo info = resolveByName(name);
        return info == null ? null : info.getRenderEntity();
    }

    private static TeammateInfo resolveByName(String rawName) {
        String target = cleanName(rawName);
        if (target.isEmpty()) return null;

        TeammateInfo exact = TEAMMATES.get(target);
        if (exact != null) return exact;

        for (TeammateInfo info : TEAMMATES.values()) {
            if (target.equalsIgnoreCase(info.getName())) return info;
            if (target.endsWith(info.getName())) return info;   // 带前缀
            if (target.contains(info.getName())) return info;
        }
        return null;
    }

    public static void clear() {
        TEAMMATES.clear();
        updateSnapshot();
    }

    private static void updateSnapshot() {
        TeammateInfo.teammates = TEAMMATES.values().toArray(new TeammateInfo[0]);
    }

    public static String cleanName(String text) {
        if (text == null) return "";
        return text.replaceAll("§.", "").trim();
    }
}
