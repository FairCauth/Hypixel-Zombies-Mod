package com.example.client.utils;

import com.example.client.ZombiesModClient;
import com.example.client.module.modules.HideBlockingPlayer;
import com.example.client.module.modules.HideZombies;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public class HidePlayerHelper implements IMinecraft {
    public static boolean shouldFade(Player target) {
        HideBlockingPlayer hideBlockingPlayer = ((HideBlockingPlayer) ZombiesModClient.moduleManager.getModule("module.hide_blocking_player"));
        if(hideBlockingPlayer == null) return false;
        if(!hideBlockingPlayer.isEnable()) return false;

        LocalPlayer self = mc.player;

        if (self == null || mc.level == null) {
            return false;
        }

        if (target == self) {
            return false;
        }

        if (target.isInvisible()) {
            return false;
        }

        return withinFadeRange(target, HideBlockingPlayer.fadeRange.getValue().doubleValue());
    }

    private static boolean isHideEntitiesTarget(LivingEntity target) {
        return !(target instanceof Player)
                && !(target instanceof AbstractVillager)
                && !(target instanceof ArmorStand);
    }

    public static boolean shouldFade(LivingEntity target) {
        if (target instanceof Player player) {
            return shouldFade(player);
        }
        if (isHideEntitiesTarget(target)) {
            HideZombies hideZombies = (HideZombies) ZombiesModClient.moduleManager.getModule("module.hide_zombies");
            if (hideZombies == null || !hideZombies.isEnable()) {
                return false;
            }
            return withinFadeRange(target, HideZombies.fadeRange.getValue().doubleValue());
        }
        return false;
    }

    public static boolean shouldFullyHide(Entity target) {
        if (!(target instanceof LivingEntity livingEntity) || !shouldFade(livingEntity)) {
            return false;
        }

        if (livingEntity instanceof Player) {
            return HideBlockingPlayer.fullHide.getValue();
        }
        if (isHideEntitiesTarget(livingEntity)) {
            return HideZombies.fullHide.getValue();
        }
        return false;
    }

    public static boolean isFullHide(LivingEntity target) {
        if (target instanceof Player) {
            return HideBlockingPlayer.fullHide.getValue();
        }
        if (isHideEntitiesTarget(target)) {
            return HideZombies.fullHide.getValue();
        }
        return false;
    }

    public static int fadeAlpha(LivingEntity target) {
        double range;
        int minimumAlpha;
        if (target instanceof Player) {
            range = HideBlockingPlayer.fadeRange.getValue().doubleValue();
            minimumAlpha = HideBlockingPlayer.fullHide.getValue() ? 0 : 50;
        } else if (isHideEntitiesTarget(target)) {
            range = HideZombies.fadeRange.getValue().doubleValue();
            minimumAlpha = HideZombies.fullHide.getValue() ? 0 : 50;
        } else {
            return 255;
        }

        double distance = horizontalDistance(target);
        if (range <= 1.0) return distance <= 1.0 ? minimumAlpha : 255;
        double progress = Math.max(0.0, Math.min(1.0, (distance - 1.0) / (range - 1.0)));
        return (int) Math.round(minimumAlpha + (255 - minimumAlpha) * progress);
    }

    private static boolean overlapsSelf(LivingEntity target, double expand) {
        LocalPlayer self = mc.player;

        if (self == null || mc.level == null || target == self || target.isInvisible()) {
            return false;
        }

        AABB selfBox = self.getBoundingBox().inflate(expand, 0.1, expand);
        AABB targetBox = target.getBoundingBox();

        return selfBox.intersects(targetBox);
    }

    private static boolean withinFadeRange(LivingEntity target, double range) {
        return horizontalDistance(target) <= range;
    }

    private static double horizontalDistance(LivingEntity target) {
        LocalPlayer self = mc.player;
        if (self == null || mc.level == null || target == self || target.isInvisible()) return Double.POSITIVE_INFINITY;
        double dx = self.getX() - target.getX();
        double dz = self.getZ() - target.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static int alphaWhite(int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | 0xFFFFFF;
    }
}
