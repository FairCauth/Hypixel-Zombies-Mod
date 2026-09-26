package com.example.client.tracker;

import com.example.client.utils.IMinecraft;
import com.example.client.data.PowerupPredictor;
import com.example.client.language.GuiText;
import com.example.client.module.modules.Notification;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.*;

public class GameStatTracker implements IMinecraft {

    private static final EnumMap<GameStat, Long> EXPIRE_TIME = new EnumMap<>(GameStat.class);

    public static void activate(GameStat stat) {
        long expireAt = System.currentTimeMillis() + stat.getDurationMs();
        EXPIRE_TIME.put(stat, expireAt);
        updateCurrentStat();
        debug(GuiText.text("game_stat.activated", statName(stat), stat.getDurationMs() / 1000)
                .copy().withStyle(ChatFormatting.GREEN));
    }

    public static void announceDrop(PowerupPredictor.Type type) {
        debug(GuiText.text("game_stat.dropped", powerupName(type)).copy().withStyle(ChatFormatting.YELLOW));
    }

    public static void announceDrop(GameStat stat) {
        debug(GuiText.text("game_stat.dropped", statName(stat)).copy().withStyle(ChatFormatting.YELLOW));
    }

    public static void announceCollected(PowerupPredictor.Type type) {
        debug(GuiText.text("game_stat.collected", powerupName(type)).copy().withStyle(ChatFormatting.GREEN));
    }

    public static void announceExpiring(PowerupPredictor.Type type) {
        debug(GuiText.text("game_stat.expiring", powerupName(type)).copy().withStyle(ChatFormatting.GOLD));
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
        try {
            long now = System.currentTimeMillis();

            Iterator<Map.Entry<GameStat, Long>> iterator = EXPIRE_TIME.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<GameStat, Long> entry = iterator.next();

                if (entry.getValue() <= now) {
                    // EnumMap 的 Entry 在 iterator.remove() 后立即失效，必须先保存 key。
                    GameStat expiredStat = entry.getKey();
                    iterator.remove();
                    debug(GuiText.text("game_stat.expired", statName(expiredStat)).copy().withStyle(ChatFormatting.GRAY));
                }
            }

            updateCurrentStat();
        } catch (Throwable _) {

        }

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

    private static Component statName(GameStat stat) {
        return switch (stat) {
            case DOUBLE_GOLD -> GuiText.text("game_stat.double_gold").copy().withStyle(ChatFormatting.GOLD);
            case SHOPPING_SPREE -> GuiText.text("game_stat.shopping_spree").copy().withStyle(ChatFormatting.DARK_PURPLE);
            case INSTA_KILL -> GuiText.text("game_stat.insta_kill").copy().withStyle(ChatFormatting.RED);
        };
    }

    private static Component powerupName(PowerupPredictor.Type type) {
        return switch (type) {
            case INSTA -> GuiText.text("game_stat.insta_kill").copy().withStyle(ChatFormatting.RED);
            case MAX -> GuiText.text("game_stat.max_ammo").copy().withStyle(ChatFormatting.BLUE);
            case SS -> GuiText.text("game_stat.shopping_spree").copy().withStyle(ChatFormatting.DARK_PURPLE);
        };
    }

    private static void debug(Component text) {
        if (mc.player == null) {
            return;
        }

        Component message = GuiText.text("game_stat.prefix").copy().append(text);
        mc.gui.chatListener().handleSystemMessage(message, false);

        if (Boolean.TRUE.equals(Notification.gameStatChat.getValue())) {
            mc.player.connection.sendChat(text.getString());
        }
    }
}
