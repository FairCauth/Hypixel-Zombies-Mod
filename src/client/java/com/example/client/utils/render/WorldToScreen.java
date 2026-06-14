package com.example.client.utils.render;

import com.example.client.utils.IMinecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class WorldToScreen implements IMinecraft {

    public static ScreenPos project(Vec3 worldPos, float partialTicks) {
        LocalPlayer player = mc.player;

        if (player == null || mc.level == null || worldPos == null) {
            return null;
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        Vec3 cameraPos = getCameraPos(player, partialTicks);

        float yaw = lerpRot(partialTicks, player.yRotO, player.getYRot());
        float pitch = lerp(partialTicks, player.xRotO, player.getXRot());

        Vec3 relative = worldPos.subtract(cameraPos);

        Vec3 forward = getLookVector(pitch, yaw).normalize();

        Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = forward.cross(worldUp).normalize();
        Vec3 up = right.cross(forward).normalize();

        double cameraX = relative.dot(right);
        double cameraY = relative.dot(up);
        double cameraZ = relative.dot(forward);

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

    public static ScreenBox projectEntityBox(Entity entity, float partialTicks) {
        if (entity == null) {
            return null;
        }

        double renderX = lerp(partialTicks, entity.xOld, entity.getX());
        double renderY = lerp(partialTicks, entity.yOld, entity.getY());
        double renderZ = lerp(partialTicks, entity.zOld, entity.getZ());

        double moveX = renderX - entity.getX();
        double moveY = renderY - entity.getY();
        double moveZ = renderZ - entity.getZ();

        AABB box = entity.getBoundingBox().move(moveX, moveY, moveZ);

        Vec3[] points = new Vec3[]{
                new Vec3(box.minX, box.minY, box.minZ),
                new Vec3(box.minX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.minZ),
                new Vec3(box.minX, box.maxY, box.maxZ),

                new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.maxZ)
        };

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        double minDepth = Double.MAX_VALUE;

        boolean projected = false;

        for (Vec3 point : points) {
            ScreenPos pos = project(point, partialTicks);

            if (pos == null) {
                continue;
            }

            minX = Math.min(minX, pos.x());
            minY = Math.min(minY, pos.y());
            maxX = Math.max(maxX, pos.x());
            maxY = Math.max(maxY, pos.y());

            minDepth = Math.min(minDepth, pos.depth());

            projected = true;
        }

        if (!projected) {
            return null;
        }

        if (maxX <= minX || maxY <= minY) {
            return null;
        }

        return new ScreenBox(
                (float) minX,
                (float) minY,
                (float) maxX,
                (float) maxY,
                (float) minDepth
        );
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

    public record ScreenBox(float minX, float minY, float maxX, float maxY, float depth) {

        public float width() {
            return maxX - minX;
        }

        public float height() {
            return maxY - minY;
        }

        public float centerX() {
            return (minX + maxX) / 2.0F;
        }

        public float centerY() {
            return (minY + maxY) / 2.0F;
        }
    }
}