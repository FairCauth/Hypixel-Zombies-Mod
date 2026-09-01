package com.example.client.utils;

import com.example.client.module.modules.TargetHud;
import com.example.client.tracker.MovePlayerRecord;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.function.Predicate;

public class PlayerUtils implements IMinecraft {
    public static LivingEntity raycastTarget(double distance, Predicate<? super Entity> selector){
        return raycastTarget(
                new MovePlayerRecord(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getXRot(), mc.player.getYRot())
                , distance, selector);
    }
    public static LivingEntity raycastTarget(MovePlayerRecord movePlayerRecord, double distance, Predicate<? super Entity> selector) {
        if(movePlayerRecord == null) {
            movePlayerRecord = new MovePlayerRecord(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getXRot(), mc.player.getYRot());
        }
        LocalPlayer player = mc.player;

        if (player == null || mc.level == null) {
            return null;
        }

        Vec3 start = new Vec3(
                movePlayerRecord.x(),
                player.getEyeY(),
                movePlayerRecord.z()
        );

        Vec3 look = getLookVector(movePlayerRecord.xRot(), movePlayerRecord.yRot()).normalize();
        Vec3 end = start.add(look.scale(distance));

        AABB searchBox = player.getBoundingBox()
                .expandTowards(look.scale(distance))
                .inflate(1.0D);

        Entity bestEntity = null;
        double bestDistanceSq = distance * distance;

        for (Entity entity : mc.level.getEntities(player, searchBox, selector)) {
            AABB box = entity.getBoundingBox().inflate(0.3D);

            var optionalHit = box.clip(start, end);

            if (optionalHit.isEmpty()) {
                continue;
            }

            double distanceSq = start.distanceToSqr(optionalHit.get());

            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                bestEntity = entity;
            }
        }

        if (bestEntity instanceof LivingEntity living) {
            return living;
        }

        return null;
    }
    private static Vec3 getLookVector(float xRot, float yRot) {
        double pitchRad = Math.toRadians(xRot);
        double yawRad = Math.toRadians(yRot);

        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);

        return new Vec3(x, y, z);
    }
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
        if(ZombiesUtils.getMap() == ZombiesMap.NULL) return false;
        MobEffectInstance effect = mc.player.getEffect(MobEffects.MINING_FATIGUE);
        MobEffectInstance effect2 = mc.player.getEffect(MobEffects.INVISIBILITY);
        if (effect == null)
             {
                 return effect2 != null;
             }
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
