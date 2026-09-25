package com.example.client.skia.render;

import com.example.client.skia.CanvasStack;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.RRect;

/** Shared visual primitives for compact liquid-glass HUDs. */
public final class LiquidGlassUi {
    private static final float EDGE_DISPERSION = 0.0014F;
    private static final float BACKGROUND_BLUR = 8F;
    private static final float EDGE_REFRACTION = 3.35F;

    private LiquidGlassUi() {
    }

    public static void drawPanel(
            CanvasStack stack,
            float x, float y, float width, float height, float radius
    ) {
        if (stack == null || width <= 0F || height <= 0F) return;

        RenderUtils.drawShadow(
                stack, x, y, width, height, radius,
                0x3D030A12, 10F, 0F, 3F
        );
        LiquidGlass.draw(
                stack,
                x, y, width, height,
                radius,
                BACKGROUND_BLUR,
                0.002F,
                EDGE_REFRACTION,
                EDGE_DISPERSION,
                1F, 1F, 1F, 0.065F,
                0.12F,
                0.94F
        );
        // 玻璃本体的轻微明暗层，让中心不仅是“空的背景截图”。
        drawVerticalGradient(
                stack,
                x + 1F, y + 1F,
                width - 2F, height - 2F,
                Math.max(0F, radius - 1F),
                0x14FFFFFF,
                0x0C0A121B
        );
        // 只保留一层极细轮廓；玻璃厚边由 shader 内部的折射与高光表达。
        drawRoundedStroke(
                stack,
                x + 0.6F, y + 0.6F,
                width - 1.2F, height - 1.2F,
                Math.max(0F, radius - 0.6F),
                0x42FFFFFF,
                0.5F
        );
    }

    public static void drawSurface(
            CanvasStack stack,
            float x, float y, float width, float height, float radius,
            int accentColor, int tintAlpha, int strokeAlpha
    ) {
        if (stack == null || width <= 0F || height <= 0F) return;

        drawVerticalGradient(
                stack, x, y, width, height, radius,
                0x32FFFFFF,
                0x24101820
        );
        RenderUtils.drawRect(
                stack,
                x + 0.7F, y + 0.7F,
                width - 1.4F, height - 1.4F,
                Math.max(0F, radius - 0.7F),
                withAlpha(accentColor, tintAlpha)
        );
        drawRoundedStroke(
                stack,
                x + 0.5F, y + 0.5F,
                width - 1F, height - 1F,
                Math.max(0F, radius - 0.5F),
                withAlpha(accentColor, strokeAlpha),
                0.65F
        );
        RenderUtils.drawRect(
                stack,
                x + Math.min(5F, width * 0.2F), y + 0.65F,
                Math.max(0F, width - Math.min(10F, width * 0.4F)), 0.6F,
                0.3F,
                0x48FFFFFF
        );
    }

    public static void drawPill(
            CanvasStack stack,
            float x, float y, float width, float height,
            int color
    ) {
        float radius = height * 0.5F;
        RenderUtils.drawRect(stack, x, y, width, height, radius, withAlpha(color, 0x22));
        drawRoundedStroke(
                stack,
                x + 0.4F, y + 0.4F,
                width - 0.8F, height - 0.8F,
                Math.max(0F, radius - 0.4F),
                withAlpha(color, 0x72),
                0.55F
        );
    }

    public static void drawStatusLight(
            CanvasStack stack,
            float x, float y, float width, float height,
            int color
    ) {
        float radius = Math.min(width, height) * 0.5F;
        RenderUtils.drawShadow(
                stack, x, y, width, height, radius,
                withAlpha(color, 0x68), 2.5F, 0F, 0F
        );
        RenderUtils.drawRect(stack, x, y, width, height, radius, color);
    }

    public static void drawVerticalGradient(
            CanvasStack stack,
            float x, float y, float width, float height, float radius,
            int topColor, int bottomColor
    ) {
        if (stack == null || width <= 0F || height <= 0F) return;

        try (Shader shader = Shader.makeLinearGradient(
                x, y, x, y + height,
                new int[]{topColor, bottomColor}
        ); Paint paint = new Paint()) {
            paint.setAntiAlias(true);
            paint.setMode(PaintMode.FILL);
            paint.setShader(shader);
            stack.canvas().drawRRect(RRect.makeXYWH(x, y, width, height, radius), paint);
        }
    }

    public static void drawRoundedStroke(
            CanvasStack stack,
            float x, float y, float width, float height, float radius,
            int color, float strokeWidth
    ) {
        if (stack == null || width <= 0F || height <= 0F || (color >>> 24) == 0) return;

        try (Paint paint = new Paint()) {
            paint.setAntiAlias(true);
            paint.setMode(PaintMode.STROKE);
            paint.setStrokeWidth(strokeWidth);
            paint.setColor(color);
            stack.canvas().drawRRect(RRect.makeXYWH(x, y, width, height, radius), paint);
        }
    }

    public static int withAlpha(int color, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }
}
