package com.example.client.tracker;

import com.example.client.utils.IMinecraft;
import com.example.client.utils.PlayerUtils;
import com.example.client.utils.ScoreboardUtils;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;

import java.util.*;

public class TeammateTracker implements IMinecraft {

    /** 以用户名为主键，名单来自计分板侧边栏 */
    private static final Map<String, TeammateInfo> TEAMMATES = new LinkedHashMap<>();

    public static void syncTeammates() {
        if (mc.player == null || mc.level == null) {
            clear();
            return;
        }

        List<ScoreboardUtils.ScorePlayer> scorePlayers = ScoreboardUtils.getZombiesPlayers();

        if (!scorePlayers.isEmpty()) {
            Set<String> current = new HashSet<>();

            for (ScoreboardUtils.ScorePlayer sp : scorePlayers) {
                String name = cleanName(sp.name());
                if (name.isEmpty()) continue;

                current.add(name);
                TeammateInfo info = TEAMMATES.computeIfAbsent(name, TeammateInfo::new);
                info.setGold(sp.gold());
            }

            // 只移除已经不在计分板上的人（真正离开了游戏）
            TEAMMATES.keySet().removeIf(k -> !current.contains(k));
        }
        // 计分板暂时读不到（切图/加载）时，保留旧名单，不清空

        // 2) 每帧把名单关联到世界实体，并判定倒地
        resolveEntities();

        updateSnapshot();
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

        // 1) 倒地状态：名单里有、TAB 里没有 = 倒地（TAB 暂时为空时不误判）
        if (!tabNames.isEmpty()) {
            for (TeammateInfo info : TEAMMATES.values()) {
                info.setDown(!tabNames.contains(info.getName()));
            }
        }

        // 2) 关联渲染实体
        for (Player p : mc.level.players()) {
            if (tabUuids.contains(p.getUUID())) {
                // 活着的真玩家
                TeammateInfo info = TEAMMATES.get(cleanName(p.getGameProfile().name()));
                if (info != null) {
                    info.setRenderEntity(p);
                    info.setUuid(p.getUUID());
                    info.setBlocking(PlayerUtils.isPlayerBlockingHyp(p));
                }
            } else {
                // 不在 TAB：可能是倒地假人，名牌里包含某个倒地队友名
                String display = cleanName(p.getName().getString());
                if (display.isEmpty()) continue;

                for (TeammateInfo info : TEAMMATES.values()) {
                    if (info.isDown() && display.contains(info.getName())) {
                        info.setRenderEntity(p);
                        info.setBlocking(false);
                        break;
                    }
                }
            }
        }
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
