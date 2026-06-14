package com.example.client.utils;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class PlayerUtils implements IMinecraft {
    public static UUID findUUIDByCurrentName(String rawName) {
        if (mc.level == null) {
            return null;
        }

        String target = cleanName(rawName);

        if (target.isEmpty()) {
            return null;
        }

        for (Player player : mc.level.players()) {
            String currentName = cleanName(player.getName().getString());
            String profileName = getProfileName(player);

            if (target.equalsIgnoreCase(currentName)) {
                return player.getUUID();
            }

            if (target.equalsIgnoreCase(profileName)) {
                return player.getUUID();
            }

            if (target.endsWith(currentName) || target.endsWith(profileName)) {
                return player.getUUID();
            }
        }

        return null;
    }

    public static String getProfileName(Player player) {
        try {
            return cleanName(player.getGameProfile().name());
        } catch (Throwable ignored) {
            return "";
        }
    }
    public static Player getPlayerByName(String name) {
        if (mc.level == null || name == null) {
            return null;
        }

        String targetName = cleanName(name);

        if (targetName.isEmpty()) {
            return null;
        }

        for (Player player : mc.level.players()) {
            String playerName = cleanName(player.getName().getString());

            if (playerName.equalsIgnoreCase(targetName)) {
                return player;
            }
        }

        return null;
    }
    public static String cleanName(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replaceAll("(?i)§[0-9A-FK-ORX]", "")
                .trim();
    }
    public static boolean isInHypZombies() {
        if (mc.player == null) return false;
        MobEffectInstance effect = mc.player.getEffect(MobEffects.MINING_FATIGUE);
        if (effect == null)
            return false;
        return effect.getAmplifier() >= 4;
    }
    public static boolean isPlayerBlockingHyp(Player player) {
        if (player == null) {
            return false;
        }

        if (player.isBlocking()) {
            return true;
        }

        if (!player.isUsingItem()) {
            return false;
        }

        ItemStack useItem = player.getUseItem();

        if (isSword(useItem)) {
            return true;
        }

        ItemStack mainHand = player.getMainHandItem();

        return isSword(mainHand);
    }
    private static boolean isSword(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ItemTags.SWORDS);
    }
}
