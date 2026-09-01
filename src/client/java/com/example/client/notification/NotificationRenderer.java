package com.example.client.notification;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.events.SkiaEvent;
import com.example.client.skia.CanvasStack;
import com.example.client.skia.font.SkiaFont;
import com.example.client.skia.font.SkiaFonts;
import com.example.client.skia.render.RenderUtils;
import com.example.client.utils.IMinecraft;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NotificationRenderer implements IMinecraft {
    private static final long ENTER_MS = 220L;
    private static final long EXIT_MS = 280L;
    private static final float CARD_WIDTH = 210F;
    private static final float CARD_HEIGHT = 42F;
    private static final float CARD_GAP = 5F;
    private static final float MARGIN = 8F;
    private static final Color CARD_BACKGROUND = new Color(26, 30, 36, 178);

    private final Map<Long, Float> animatedY = new HashMap<>();
    private long lastFrameMs;

    @EventTarget
    public void onRenderSkia(SkiaEvent event) {
        long now = System.currentTimeMillis();
        float dt = lastFrameMs == 0L ? 0F : Math.min(0.1F, (now - lastFrameMs) / 1_000F);
        lastFrameMs = now;

        drawNotifications(event.getCanvasStack(), now, dt);
        drawAlert(event.getCanvasStack(), NotificationManager.activeAlert(now), now);
    }

    private void drawNotifications(CanvasStack stack, long now, float dt) {
        List<NotificationMessage> notifications = NotificationManager.notifications(now);
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        float width = Math.clamp(screenWidth - MARGIN * 2F, 120F, CARD_WIDTH);
        Set<Long> visibleIds = new HashSet<>();

        SkiaFont titleFont = SkiaFonts.getBoldFont(8);
        SkiaFont messageFont = SkiaFonts.getDefaultFont(6);
        SkiaFont tagFont = SkiaFonts.getBoldFont(5);

        for (int i = 0; i < notifications.size(); i++) {
            NotificationMessage notification = notifications.get(i);
            visibleIds.add(notification.id());

            float targetY = MARGIN + i * (CARD_HEIGHT + CARD_GAP);
            float y = animatedY.computeIfAbsent(notification.id(), ignored -> targetY - 8F);
            float follow = dt <= 0F ? 1F : Math.min(1F, dt * 14F);
            y += (targetY - y) * follow;
            animatedY.put(notification.id(), y);

            float opacity = opacity(notification, now);
            if (opacity <= 0.01F) continue;
            float enter = easeOutCubic(Math.clamp((now - notification.startedAt()) / (float) ENTER_MS, 0F, 1F));
            long remaining = notification.durationMs() - (now - notification.startedAt());
            float exit = remaining < EXIT_MS ? 1F - Math.clamp(remaining / (float) EXIT_MS, 0F, 1F) : 0F;
            float x = screenWidth - MARGIN - width + (1F - enter + exit) * 28F;

            int accent = withOpacity(notification.type().color(), opacity);
            int background = withOpacity(CARD_BACKGROUND.getRGB(), opacity);
            int border = withOpacity(0x703D4650, opacity);
            int text = withOpacity(0xFFF2F5F7, opacity);
            int muted = withOpacity(0xFF9AA7B2, opacity);

            RenderUtils.drawShadow(stack, x, y, width, CARD_HEIGHT, 6F,
                    withOpacity(0x70000000, opacity), 7F, 0F, 2F);
            RenderUtils.drawBlur(stack, x, y, width, CARD_HEIGHT, 6F, 15f);
            RenderUtils.drawRect(stack, x, y, width, CARD_HEIGHT, 6F, background);
//            RenderUtils.drawRect(stack, x + 1F, y + 1F, width - 2F, 1F, 1F, border);
            RenderUtils.drawRect(stack, x , y, 3F, CARD_HEIGHT, 2F, accent);

            String tag = notification.type().label();
            String title = ellipsize(titleFont, notification.title(), width - 56F);
            String message = ellipsize(messageFont, notification.message(), width - 18F);
            titleFont.drawShadowString(stack, title, x + 10F, y + 7F, text, true);
            tagFont.drawShadowString(stack, tag,
                    x + width - 8F - tagFont.getWidth(tag), y + 8F, accent, true);
            messageFont.drawShadowString(stack, message, x + 10F, y + 22F, muted, true);

            float life = Math.clamp(remaining / (float) notification.durationMs(), 0F, 1F);
            RenderUtils.drawRect(stack, x + 6F, y + CARD_HEIGHT - 3F,
                    (width - 12F) * life, 1F, 0.5F, accent);
        }

        animatedY.keySet().retainAll(visibleIds);
    }

    private void drawAlert(CanvasStack stack, NotificationMessage alert, long now) {
        if (alert == null) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        float width = Math.clamp(screenWidth - 24F, 180F, 340F);
        float height = 62F;
        float x = (screenWidth - width) * 0.5F;
        float enter = easeOutCubic(Math.clamp((now - alert.startedAt()) / (float) ENTER_MS, 0F, 1F));
        long remaining = alert.durationMs() - (now - alert.startedAt());
        float opacity = opacity(alert, now);
        if (opacity <= 0.01F) return;
        float y = (screenHeight - height) * 0.5F + (1F - enter) * 12F;
        float pulse = 0.84F + 0.16F * (float) Math.sin((now - alert.startedAt()) / 115D);

        int accent = withOpacity(alert.type().color(), opacity * pulse);
        int background = withOpacity(0xEE15191F, opacity);
        int text = withOpacity(0xFFFFFFFF, opacity);
        int muted = withOpacity(0xFFD4DCE2, opacity);

        RenderUtils.drawShadow(stack, x, y, width, height, 8F,
                withOpacity(alert.type().color(), opacity * 0.38F), 14F, 0F, 0F);
        RenderUtils.drawRect(stack, x, y, width, height, 8F, background);
        RenderUtils.drawRect(stack, x, y, width, 2F, 1F, accent);
        RenderUtils.drawRect(stack, x, y + height - 2F, width, 2F, 1F, accent);
        RenderUtils.drawRect(stack, x + 7F, y + 9F, 3F, height - 18F, 1.5F, accent);
        RenderUtils.drawRect(stack, x + width - 10F, y + 9F, 3F, height - 18F, 1.5F, accent);

        SkiaFont labelFont = SkiaFonts.getBoldFont(6);
        SkiaFont titleFont = SkiaFonts.getBoldFont(11);
        SkiaFont messageFont = SkiaFonts.getDefaultFont(7);
        String label = alert.type().label();
        String title = ellipsize(titleFont, alert.title(), width - 40F);
        String message = ellipsize(messageFont, alert.message(), width - 40F);

        labelFont.drawShadowString(stack, label,
                x + (width - labelFont.getWidth(label)) * 0.5F, y + 7F, accent, true);
        titleFont.drawShadowString(stack, title,
                x + (width - titleFont.getWidth(title)) * 0.5F, y + 22F, text, true);
        messageFont.drawShadowString(stack, message,
                x + (width - messageFont.getWidth(message)) * 0.5F, y + 42F, muted, true);

        float life = Math.clamp(remaining / (float) alert.durationMs(), 0F, 1F);
        RenderUtils.drawRect(stack, x + 12F, y + height - 5F,
                (width - 24F) * life, 1F, 0.5F, accent);
    }

    private static float opacity(NotificationMessage notification, long now) {
        long age = now - notification.startedAt();
        long remaining = notification.durationMs() - age;
        float enter = Math.clamp(age / (float) ENTER_MS, 0F, 1F);
        float exit = Math.clamp(remaining / (float) EXIT_MS, 0F, 1F);
        return Math.min(enter, exit);
    }

    private static float easeOutCubic(float value) {
        float inverse = 1F - value;
        return 1F - inverse * inverse * inverse;
    }

    private static int withOpacity(int color, float opacity) {
        int alpha = color >>> 24;
        int resultAlpha = Math.clamp(Math.round(alpha * Math.clamp(opacity, 0F, 1F)), 0, 255);
        return (color & 0x00FFFFFF) | resultAlpha << 24;
    }

    private static String ellipsize(SkiaFont font, String text, float maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0F) return "";
        if (font.getWidth(text) <= maxWidth) return text;

        String suffix = "...";
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (font.getWidth(text.substring(0, mid) + suffix) <= maxWidth) low = mid;
            else high = mid - 1;
        }
        return text.substring(0, low) + suffix;
    }
}
