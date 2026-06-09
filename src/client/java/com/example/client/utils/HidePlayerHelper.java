package com.example.client.utils;

import com.example.client.ZombiesModClient;
import com.example.client.module.modules.AutoSwitchWeapon;
import com.example.client.module.modules.HideBlockingPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class HidePlayerHelper implements IMinecraft {
    public static boolean shouldFade(Player target) {
        HideBlockingPlayer hideBlockingPlayer = ((HideBlockingPlayer) ZombiesModClient.moduleManager.getModule("Hide Blocking Player"));
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

        double expand = HideBlockingPlayer.fadeOverlapExpand.getValue().doubleValue();

        AABB selfBox = self.getBoundingBox().inflate(expand, 0.1, expand);
        AABB targetBox = target.getBoundingBox();

        return selfBox.intersects(targetBox);
    }

    public static boolean shouldFade(int entityId) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null) {
            return false;
        }

        Entity entity = mc.level.getEntity(entityId);

        return entity instanceof Player player && shouldFade(player);
    }

    public static int alphaWhite(int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | 0xFFFFFF;
    }
}