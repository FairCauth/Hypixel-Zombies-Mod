package com.example.client.utils;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class WorldToScreen implements IMinecraft {

    public static ScreenPos project(Vec3 worldPos, float partialTicks) {
        LocalPlayer player = mc.player;

        if (player == null || mc.level == null || mc.getWindow() == null) {
            return null;
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        Vec3 cameraPos = getCameraPos(player, partialTicks);

        float yaw = lerpRot(partialTicks, player.yRotO, player.getYRot());
        float pitch = lerp(partialTicks, player.xRotO, player.getXRot());

        Vec3 relative = worldPos.subtract(cameraPos);

        Vec3 forward = getLookVector(pitch, yaw).normalize();

        // 关键修正：屏幕右方向
        Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = forward.cross(worldUp).normalize();

        // 屏幕上方向
        Vec3 up = right.cross(forward).normalize();

        double cameraX = relative.dot(right);
        double cameraY = relative.dot(up);
        double cameraZ = relative.dot(forward);

        // 在相机背后，不绘制
        if (cameraZ <= 0.05D) {
            return null;
        }

        double fov = mc.options.fov().get();
        double fovRad = Math.toRadians(fov);

        double scale = screenHeight / (2.0D * Math.tan(fovRad / 2.0D));

        double screenX = screenWidth / 2.0D + cameraX * scale / cameraZ;
        double screenY = screenHeight / 2.0D - cameraY * scale / cameraZ;

        if (screenX < -200 || screenX > screenWidth + 200 || screenY < -200 || screenY > screenHeight + 200) {
            return null;
        }

        return new ScreenPos((float) screenX, (float) screenY, (float) cameraZ);
    }

    public static ScreenPos projectEntity(Entity entity, float partialTicks) {
        if (entity == null) {
            return null;
        }

        double x = lerp(partialTicks, entity.xOld, entity.getX());
        double y = lerp(partialTicks, entity.yOld, entity.getY()) + entity.getBbHeight() + 0.25D;
        double z = lerp(partialTicks, entity.zOld, entity.getZ());

        return project(new Vec3(x, y, z), partialTicks);
    }

    private static Vec3 getCameraPos(LocalPlayer player, float partialTicks) {
        double x = lerp(partialTicks, player.xOld, player.getX());
        double y = lerp(partialTicks, player.yOld, player.getY()) + player.getEyeHeight();
        double z = lerp(partialTicks, player.zOld, player.getZ());

        return new Vec3(x, y, z);
    }

    private static Vec3 getLookVector(float pitch, float yaw) {
        double pitchRad = Math.toRadians(pitch);
        double yawRad = Math.toRadians(yaw);

        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);

        return new Vec3(x, y, z);
    }

    private static float lerp(float delta, float oldValue, float newValue) {
        return oldValue + (newValue - oldValue) * delta;
    }

    private static double lerp(float delta, double oldValue, double newValue) {
        return oldValue + (newValue - oldValue) * delta;
    }

    private static float lerpRot(float delta, float oldYaw, float newYaw) {
        float diff = newYaw - oldYaw;

        while (diff < -180.0F) {
            diff += 360.0F;
        }

        while (diff >= 180.0F) {
            diff -= 360.0F;
        }

        return oldYaw + diff * delta;
    }

    public record ScreenPos(float x, float y, float depth) {
    }
}
