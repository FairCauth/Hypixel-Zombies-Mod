package com.example.client.utils.render;

import com.example.client.language.GuiText;
import com.example.client.module.modules.SidebarModification;
import com.example.client.tracker.LatencyTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

public final class SidebarLatencyRenderer {
    private SidebarLatencyRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics, Font font, int x, int y, int availableWidth) {
        if (!SidebarModification.isActive() || availableWidth <= 0) return;

        Component text = latencyText();
        int width = font.width(text);
        float scale = width > availableWidth ? (float) availableWidth / width : 1.0F;

        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, 0xFFFFFFFF, true);
        graphics.pose().popMatrix();
    }

    private static Component latencyText() {
        int latencyMs = LatencyTracker.getLatencyMs();
        Component value = latencyMs < 0
                ? Component.literal("--").withStyle(ChatFormatting.GRAY)
                : Component.literal(Integer.toString(latencyMs))
                        .withStyle(style -> style.withColor(pingColor(latencyMs) & 0xFFFFFF));
        return GuiText.text("hud.ping", value);
    }

    private static int pingColor(int latencyMs) {
        int clamped = Math.min(latencyMs, 500);
        return clamped < 250
                ? ARGB.srgbLerp((float) (clamped / 250.0D), 0xFF00FF00, 0xFFFFFF00)
                : ARGB.srgbLerp((float) ((clamped - 250) / 250.0D), 0xFFFFFF00, 0xFFFF0000);
    }
}
