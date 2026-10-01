package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.ZombiesModClient;
import com.example.client.events.TickEvent;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.BooleanSetting;
import com.example.client.utils.ChatUtils;
import com.example.client.utils.PlayerUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.util.Locale;

@ModuleInfo(name = "module.old_one_detect", enable = true)
public class OldOneDetector extends AbstractModule {

    @SettingInfo(name = "setting.only_in_zombies")
    public static final BooleanSetting onlyGame = new BooleanSetting(true);

    @SettingInfo(name = "setting.send_to_chat")
    public static final BooleanSetting sendToChat = new BooleanSetting(false);

    private int lastTargetId = -1;
    private String lastNearestPlayerName = null;
    private double lastDistance = Double.NaN;
    private long lastSentAt = 0L;

    public OldOneDetector() {
        registerSetting(onlyGame, sendToChat);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.level == null || mc.player == null || !isAllowed()) {
            return;
        }

        LivingEntity target = findOldOne();
        if (target == null) {
            resetTrackedState();
            return;
        }

        Player nearestPlayer = findNearestPlayer(target);
        if (nearestPlayer == null) {
            return;
        }

        double distance = target.distanceTo(nearestPlayer);
        String playerName = nearestPlayer.getName() == null ? "Unknown" : nearestPlayer.getName().getString();
        boolean changed = target.getId() != lastTargetId
                || !playerName.equals(lastNearestPlayerName)
                || Double.isNaN(lastDistance)
                || Math.abs(distance - lastDistance) > 0.01D;

        long now = System.currentTimeMillis();
        if (!changed || now - lastSentAt < 1000L) {
            return;
        }

        Component message = Component.literal("[")
                .append(Component.literal("The Old One").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal("] ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Closest player: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(playerName).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(", distance: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format(Locale.ROOT, "%.2f", distance) + " m").withStyle(ChatFormatting.YELLOW));

        if (!Boolean.TRUE.equals(sendToChat.getValue())) {
            ChatUtils.print(message);
        }

        if (Boolean.TRUE.equals(sendToChat.getValue()) && mc.player != null && mc.player.connection != null) {
            String rawChat = "[The Old One] Closest player: " + playerName + ", distance: " + String.format(Locale.ROOT, "%.2f", distance) + " m";
            mc.player.connection.sendChat(rawChat);
        }

        lastTargetId = target.getId();
        lastNearestPlayerName = playerName;
        lastDistance = distance;
        lastSentAt = now;
    }

    private void resetTrackedState() {
        lastTargetId = -1;
        lastNearestPlayerName = null;
        lastDistance = Double.NaN;
        lastSentAt = 0L;
    }

    public static boolean matchesOldOneName(String rawName) {
        if (rawName == null) {
            return false;
        }

        String cleaned = rawName
                .replaceAll("(?i)§[0-9A-FK-ORX]", "")
                .trim();

        if (cleaned.isEmpty()) {
            return false;
        }

        String normalized = cleaned.toLowerCase(Locale.ROOT);
        return cleaned.contains("僵尸") || normalized.contains("zombie");
    }

    public static boolean matchesOldOneHealth(float maxHealth) {
        return maxHealth == 250.0F || maxHealth == 650.0F;
    }

    public static boolean matchesOldOne(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }

        if (living.isRemoved() || !living.isAlive()) {
            return false;
        }

        boolean hasDiamondSword = living.getMainHandItem().is(Items.DIAMOND_SWORD)
                || living.getOffhandItem().is(Items.DIAMOND_SWORD);
        if (!hasDiamondSword) {
            return false;
        }

        String name = living.getName() == null ? "" : living.getName().getString();
        String displayName = living.getDisplayName() == null ? "" : living.getDisplayName().getString();
        boolean nameMatches = matchesOldOneName(name) || matchesOldOneName(displayName);
        return nameMatches && matchesOldOneHealth(living.getMaxHealth());
    }

    private LivingEntity findOldOne() {
        if (mc.level == null) {
            return null;
        }

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (matchesOldOne(entity)) {
                return (LivingEntity) entity;
            }
        }
        return null;
    }

    private Player findNearestPlayer(LivingEntity target) {
        if (mc.level == null || target == null) {
            return null;
        }

        Player nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : mc.level.players()) {
            if (player == null || player.isRemoved() || !player.isAlive()) {
                continue;
            }
            double distance = target.distanceTo(player);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    private boolean isAllowed() {
        if (ZombiesModClient.moduleManager == null) {
            return false;
        }

        AbstractModule module = ZombiesModClient.moduleManager.getModule("module.old_one_detect");
        if (module == null || !module.isEnable()) {
            return false;
        }

        return !onlyGame.getValue() || PlayerUtils.isInHypZombies();
    }
}
