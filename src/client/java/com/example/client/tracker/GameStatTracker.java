package com.example.client.tracker;

import com.example.client.utils.IMinecraft;
import net.minecraft.network.chat.Component;

import java.util.*;

public class GameStatTracker implements IMinecraft {

    private static final EnumMap<GameStat, Long> EXPIRE_TIME = new EnumMap<>(GameStat.class);

    public static void activate(GameStat stat) {
        long expireAt = System.currentTimeMillis() + stat.getDurationMs();
        EXPIRE_TIME.put(stat, expireAt);
        updateCurrentStat();
        debug(stat.name() + " activated for " + (stat.getDurationMs() / 1000) + "s");
    }
    public static boolean isActive(GameStat stat) {
        if (stat == null) {
            return false;
        }

        cleanup();

        Long expireAt = EXPIRE_TIME.get(stat);

        return expireAt != null && expireAt > System.currentTimeMillis();
    }

    public static long getRemainingMs(GameStat stat) {
        if (stat == null) {
            return 0L;
        }

        cleanup();

        Long expireAt = EXPIRE_TIME.get(stat);

        if (expireAt == null) {
            return 0L;
        }

        return Math.max(0L, expireAt - System.currentTimeMillis());
    }

    public static int getRemainingSeconds(GameStat stat) {
        return (int) Math.ceil(getRemainingMs(stat) / 1000.0D);
    }

    public static void onTick() {
        cleanup();
    }

    public static void clear() {
        EXPIRE_TIME.clear();
        updateCurrentStat();
    }

    private static void cleanup() {
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<GameStat, Long>> iterator = EXPIRE_TIME.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<GameStat, Long> entry = iterator.next();

            if (entry.getValue() <= now) {
                iterator.remove();
                debug(entry.getKey().name() + " expired");
            }
        }

        updateCurrentStat();
    }

    private static void updateCurrentStat() {
        List<GameStat> activeStats = new ArrayList<>();

        long now = System.currentTimeMillis();

        for (Map.Entry<GameStat, Long> entry : EXPIRE_TIME.entrySet()) {
            if (entry.getValue() > now) {
                activeStats.add(entry.getKey());
            }
        }

        GameStat.currentStat = activeStats.toArray(new GameStat[0]);
    }

    private static void debug(String text) {
        if (mc.player == null) {
            return;
        }

        mc.gui.getChat().addClientSystemMessage(
                Component.literal("[GameStat] " + text)
        );
    }
}
