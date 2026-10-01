package com.example.client.tracker;

import com.example.ZombiesMod;
import com.example.client.data.PowerupPredictor;
import com.example.client.utils.IMinecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * 道具掉落检测 → 喂给 {@link PowerupPredictor} 锁模式。
 *
 * <p>流程：</p>
 * <ol>
 *   <li>开局先把现有盔甲架全排除（地图静态标签）。</li>
 *   <li>之后新出现、且名牌颜色命中道具色的盔甲架 → 进 pending，此刻记下掉落回合。</li>
 *   <li>确认它确实是道具（任一）：名牌闪白（自然过期）/ 收到激活声音（被捡起）。</li>
 *   <li>确认后用"掉落回合"喂预测器。没确认就消失的当误判丢弃。</li>
 * </ol>
 */
public final class PowerupDetector implements IMinecraft {

    private static final int WHITE = 0xFFFFFF;
    private static final int INSTA = 0xFF5555;
    private static final int MAX = 0x5555FF;
    private static final int SS = 0xAA00AA;

    private static final long DESPAWN_TIMEOUT_MS = 55000L;
    private static final long DROP_ANNOUNCE_COOLDOWN_MS = 2000L;
    private static final int PRIME_WARMUP_TICKS = 60;

    private final PowerupPredictor predictor = new PowerupPredictor();

    private boolean primed = false;
    private int primeWarmupTicks;
    private final Set<Integer> excluded = new HashSet<>(); // 初始/已判定为非道具的盔甲架
    private final Map<Integer, Pending> pending = new HashMap<>();
    private final Map<PowerupPredictor.Type, Long> lastAnnouncedDropMs = new HashMap<>();
    private Integer announcedDoubleGoldRound;
    private final Map<Integer, Integer> loggedColors = new HashMap<>();

    public PowerupPredictor getPredictor() {
        return predictor;
    }

    private static final class Pending {
        PowerupPredictor.Type type;
        int dropRound;
        long dropElapsedMs;
        boolean confirmed;
        long lastSeenMs;
    }

    /** 进入/离开一局时清空。 */
    public void reset() {
        primed = false;
        primeWarmupTicks = PRIME_WARMUP_TICKS;
        excluded.clear();
        pending.clear();
        lastAnnouncedDropMs.clear();
        announcedDoubleGoldRound = null;
        loggedColors.clear();
        predictor.reset();
    }

    /** 每 tick 调用（仅在 zombies 里）。 */
    public void tick() {
        if (mc.level == null || mc.player == null) return;
        long now = System.currentTimeMillis();

        Set<Integer> seen = new HashSet<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ArmorStand stand)) continue;
            Component name = stand.getCustomName();
            if (name == null) continue;

            int id = stand.getId();
            seen.add(id);
            logStandIfChanged(stand, name);

            if (!primed && primeWarmupTicks > 0) {
                excluded.add(id);
                continue;
            }

            // 首次扫描：现有盔甲架全排除（地图静态标签）
            if (!primed) {
                excluded.add(id);
                continue;
            }
            if (excluded.contains(id)) continue;

            int rgb = firstColor(name);
            Pending p = pending.get(id);

            if (p == null) {
                if (rgb == WHITE) continue;            // 正在闪白，等显原色再分类，别误排除
                PowerupPredictor.Type type = colorToType(rgb);
                if (isDoubleGold(name)) {
                    if (!Integer.valueOf(ServerTracker.currentRound)
                            .equals(announcedDoubleGoldRound)) {
                        announcedDoubleGoldRound = ServerTracker.currentRound;
                        GameStatTracker.announceDrop(GameStat.DOUBLE_GOLD);
                    }
                    excluded.add(id);
                    continue;
                }
                if (!isPowerupName(name, type)) {
                    excluded.add(id);
                    continue;
                }
                if (type == null) {
                    excluded.add(id);                  // 非道具色 → 永久排除该实体
                    continue;
                }
                p = new Pending();
                p.type = type;
                p.dropRound = ServerTracker.currentRound;
                p.dropElapsedMs = now - ServerTracker.roundTime;
                p.lastSeenMs = now;
                pending.put(id, p);
                long lastAnnouncedAt = lastAnnouncedDropMs.getOrDefault(type, 0L);
                if (now - lastAnnouncedAt >= DROP_ANNOUNCE_COOLDOWN_MS) {
                    lastAnnouncedDropMs.put(type, now);
                    GameStatTracker.announceDrop(type);
                }
            } else {
                p.lastSeenMs = now;
                if (rgb == WHITE) {                    // 闪白 = 真道具自然过期 → 确认
                    if (!p.confirmed) {
                        GameStatTracker.announceExpiring(p.type);
                    }
                    confirm(p);
                }
            }
        }

        if (primeWarmupTicks > 0) {
            primeWarmupTicks--;
            return;
        }
        primed = true;

        // 消失/超时的 pending：confirmed 的已喂过，没确认的当误判丢弃
        Iterator<Map.Entry<Integer, Pending>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Pending> en = it.next();
            Pending p = en.getValue();
            if (!seen.contains(en.getKey()) ) {
                excluded.add(en.getKey());
                it.remove();
            }
        }
    }

    /** 收到道具激活声音（被捡起）时调用，按类型确认对应 pending；没有就按当前回合兜底。 */
    public void onActivationSound(PowerupPredictor.Type type) {
        Pending best = null;
        for (Pending p : pending.values()) {
            if (p.type == type && !p.confirmed
                    && (best == null || p.dropElapsedMs > best.dropElapsedMs)) {
                best = p;
            }
        }
        if (best != null) {
            confirm(best);
        } else {
            // 没找到对应 pending（实体可能已移除）→ 用捡起回合兜底
            predictor.onDetect(type, ServerTracker.currentRound, System.currentTimeMillis() - ServerTracker.roundTime);
        }
    }

    private void logStandIfChanged(ArmorStand stand, Component name) {
        int color = firstColor(name);
        Integer previousColor = loggedColors.put(stand.getId(), color);
        if (previousColor != null && previousColor == color) return;

        ZombiesMod.LOGGER.info("[PowerupDetector] armorStand id={} tick={} pos={},{},{} color={} visible={} name='{}'",
            stand.getId(),
            stand.tickCount,
            String.format("%.2f", stand.getX()),
            String.format("%.2f", stand.getY()),
            String.format("%.2f", stand.getZ()),
            color == -1 ? "none" : String.format("#%06X", color),
            stand.isCustomNameVisible(),
            name.getString());
    }

    private void confirm(Pending p) {
        if (p.confirmed) return;
        p.confirmed = true;
        predictor.onDetect(p.type, p.dropRound, p.dropElapsedMs);
    }

    private static PowerupPredictor.Type colorToType(int rgb) {
        return switch (rgb) {
            case INSTA -> PowerupPredictor.Type.INSTA;
            case MAX -> PowerupPredictor.Type.MAX;
            case SS -> PowerupPredictor.Type.SS;
            default -> null;
        };
    }

    private static boolean isDoubleGold(Component name) {
        String text = name.getString();
        return text.contains("双倍金钱") || text.toLowerCase(java.util.Locale.ROOT).contains("double gold");
    }

    private static boolean isPowerupName(Component name, PowerupPredictor.Type type) {
        if (type == null) return false;
        String text = name.getString().toLowerCase(java.util.Locale.ROOT);
        return switch (type) {
            case INSTA -> text.contains("瞬间击杀") || text.contains("insta kill");
                case MAX -> text.contains("弹药满载") || text.contains("最大弹药")
                    || text.contains("max ammo") || text.contains("ammo refill");
            case SS -> text.contains("购物狂潮") || text.contains("shopping spree");
        };
    }

    /** 递归取第一个非空颜色（颜色常挂在子节点上）；没有返回 -1。 */
    private static int firstColor(Component c) {
        if (c.getStyle().getColor() != null) {
            return c.getStyle().getColor().getValue();
        }
        for (Component sib : c.getSiblings()) {
            int v = firstColor(sib);
            if (v != -1) return v;
        }
        return -1;
    }
}
