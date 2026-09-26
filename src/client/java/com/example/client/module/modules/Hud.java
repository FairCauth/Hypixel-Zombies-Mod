package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.events.RenderEvent;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.ModeSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.List;

@ModuleInfo(name = "module.hud", enable = true)
public class Hud extends AbstractModule {
    @SettingInfo(name = "setting.preshoot_preset")
    public static final ModeSetting preShootPreset =
            new ModeSetting("zoomed_crosshair", List.of("zoomed_crosshair", "off"));

    private static final int RANGE = 2;
    private static final double STEP = 1.0D;
    private static final int PIXELS_PER_DEGREE = 32;

    public Hud() {
        registerSetting(preShootPreset);
    }

    @EventTarget
    public void onRender(RenderEvent event) {
        if (mc.player == null || mc.level == null || !preShootPreset.is("zoomed_crosshair")) return;

        GuiGraphicsExtractor graphics = event.getGuiGraphicsExtractor();
        float partialTicks = event.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float currentFov = mc.gameRenderer.mainCamera().getFov();
        int fovScaler = 30 - (int) currentFov;
        if (fovScaler < 0) return;

        int alpha = Math.min(255, (int) ((fovScaler / 30.0F) * 255.0F));
        int color = (alpha << 24) | 0xFFFFFF;
        int centerX = (graphics.guiWidth() - 1) / 2;
        int centerY = (graphics.guiHeight() - 1) / 2;
        float yaw = mc.player.getViewYRot(partialTicks);
        float pitch = mc.player.getViewXRot(partialTicks);

        graphics.fill(centerX, centerY - PIXELS_PER_DEGREE * RANGE,
            centerX + 1, centerY + PIXELS_PER_DEGREE * RANGE, color);
        graphics.fill(centerX - PIXELS_PER_DEGREE * RANGE, centerY,
            centerX + PIXELS_PER_DEGREE * RANGE, centerY + 1, color);
        drawVerticalTicks(graphics, centerX, centerY, pitch, color);
        drawHorizontalTicks(graphics, centerX, centerY, yaw, color);

        String pitchText = String.format("%.1f", pitch);
        String yawText = String.format("%.1f", Mth.wrapDegrees(yaw));
        graphics.text(mc.font, pitchText, centerX - (mc.font.width(pitchText) - 1) / 2,
            centerY + 70, color, true);
        graphics.text(mc.font, yawText, centerX + 70,
            centerY - mc.font.lineHeight / 2, color, true);

        int entityCount = mc.level.getEntities(mc.player,
            mc.player.getBoundingBox().inflate(64.0D), entity -> entity.isAlive()).size();
        String countText = String.valueOf(entityCount);
        graphics.text(mc.font, countText,
            centerX - mc.font.lineHeight / 2 - 70,
            centerY - mc.font.lineHeight / 2 - 70, color, true);
    }

    private static void drawHorizontalTicks(GuiGraphicsExtractor graphics, int centerX, int centerY,
                                            float value, int color) {
        double base = Math.floor(value / STEP) * STEP;
        for (double degree = base - RANGE + 1; degree <= base + RANGE; degree += STEP) {
            int x = centerX + (int) ((degree - value) * PIXELS_PER_DEGREE);
            int size = tickSize(degree);
            graphics.fill(x, centerY - size, x + 1, centerY + size + 1, color);
        }
    }

    private static void drawVerticalTicks(GuiGraphicsExtractor graphics, int centerX, int centerY,
                                          float value, int color) {
        double base = Math.floor(value / STEP) * STEP;
        for (double degree = base - RANGE + 1; degree <= base + RANGE; degree += STEP) {
            int y = centerY + (int) ((degree - value) * PIXELS_PER_DEGREE);
            int size = tickSize(degree);
            graphics.fill(centerX - size, y, centerX + size + 1, y + 1, color);
        }
    }

    private static int tickSize(double degree) {
        if (isMultiple(degree, 2.0D)) return 2;
        if (isMultiple(degree, 1.0D)) return 1;
        return 0;
    }

    private static boolean isMultiple(double value, double divisor) {
        return Math.abs(value / divisor - Math.round(value / divisor)) < 1.0E-6D;
    }
}
