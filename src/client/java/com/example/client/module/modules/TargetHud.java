package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.events.RenderEvent;
import com.example.client.events.TickEvent;
import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.NumberSetting;
import com.example.client.utils.WorldToScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(name = {
        @Text(label = "Target Hud", language = Language.English),
        @Text(label = "目标Hud显示", language = Language.Chinese)
}, enable = true)
public class TargetHud extends AbstractModule {
    @SettingInfo(name = {
            @Text(label = "Distance", language = Language.English),
            @Text(label = "距离", language = Language.Chinese)
    })
    private final NumberSetting distance = new NumberSetting(35, 1, 50, "#");

    public TargetHud() {
        registerSetting(distance);
    }
    private int raycastTimer = 0;
    private LivingEntity target = null;
    @EventTarget
    public void onTick(TickEvent event) {
        if (++raycastTimer < 3) {
            return;
        }
        raycastTimer = 0;
        target = raycastTarget(distance.getValue().doubleValue());
    }

    @EventTarget
    public void onRender(RenderEvent event) {
        if (target == null)
            return;

        float partialTicks = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        WorldToScreen.ScreenPos pos = WorldToScreen.projectEntity(target, partialTicks);
        if (pos == null)
            return;

        int width = 150;
        int height = 48;
        int x = (int) pos.x() - width / 2;
        int y = (int) pos.y() - height;


        String name = target.getName().getString();

        float health = Math.max(0.0F, target.getHealth());
        float maxHealth = Math.max(1.0F, target.getMaxHealth());
        float percent = Math.max(0.0F, Math.min(1.0F, health / maxHealth));

        double distance = mc.player.distanceTo(target);

        drawBackground(event.getGuiGraphicsExtractor(), x, y, width, height);
        drawText(event.getGuiGraphicsExtractor(), x, y, name, health, maxHealth, distance);
        drawHealthBar(event.getGuiGraphicsExtractor(), x + 8, y + 32, width - 16, 8, percent);
    }
    private static void drawBackground(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xAA111111);


        graphics.fill(x, y, x + width, y + 1, 0xFF444444);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF444444);
        graphics.fill(x, y, x + 1, y + height, 0xFF444444);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF444444);
    }

    private static void drawText(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            String name,
            float health,
            float maxHealth,
            double distance
    ) {
        String hpText = String.format("%.1f / %.1f HP", health, maxHealth);
        String distanceText = String.format("%.1f m", distance);

        graphics.text(mc.font, name, x + 8, y + 7, 0xFFFFFFFF, true);
        graphics.text(mc.font, hpText, x + 8, y + 18, 0xFFFF5555, true);
        graphics.text(mc.font, distanceText, x + 105, y + 18, 0xFFAAAAAA, true);
    }

    private static void drawHealthBar(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            float percent
    ) {
        int filled = (int) (width * percent);

        //血条背景
        graphics.fill(x, y, x + width, y + height, 0xFF333333);

        //血条颜色
        int color = getHealthColor(percent);
        graphics.fill(x, y, x + filled, y + height, color);

        //血条边框
        graphics.fill(x, y, x + width, y + 1, 0xFF000000);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF000000);
        graphics.fill(x, y, x + 1, y + height, 0xFF000000);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF000000);
    }

    private static int getHealthColor(float percent) {
        if (percent > 0.66F)
            return 0xFF55FF55; //绿
        if (percent > 0.33F)
            return 0xFFFFFF55; //黄
        return 0xFFFF5555; //红
    }
    public static LivingEntity raycastTarget(double distance) {
        LocalPlayer player = mc.player;

        if (player == null || mc.level == null) {
            return null;
        }

        Vec3 start = new Vec3(
                player.getX(),
                player.getEyeY(),
                player.getZ()
        );

        Vec3 look = getLookVector(player.getXRot(), player.getYRot()).normalize();
        Vec3 end = start.add(look.scale(distance));

        AABB searchBox = player.getBoundingBox()
                .expandTowards(look.scale(distance))
                .inflate(1.0D);

        Entity bestEntity = null;
        double bestDistanceSq = distance * distance;

        for (Entity entity : mc.level.getEntities(player, searchBox, TargetHud::isValidTarget)) {
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
    private static boolean isValidTarget(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }

        if (!living.isAlive()) {
            return false;
        }

        if (entity == mc.player) {
            return false;
        }

        if (entity instanceof Player) {
            return false;
        }

        if (entity instanceof ArmorStand) {
            return false;
        }
        String name = entity.getName().getString();

        if (name.equalsIgnoreCase("Farmer")) {
            return false;
        }
        return true;
    }

    private static Vec3 getLookVector(float xRot, float yRot) {
        double pitchRad = Math.toRadians(xRot);
        double yawRad = Math.toRadians(yRot);

        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);

        return new Vec3(x, y, z);
    }
}
