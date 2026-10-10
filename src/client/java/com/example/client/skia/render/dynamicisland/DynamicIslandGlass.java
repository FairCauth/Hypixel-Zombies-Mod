package com.example.client.skia.render.dynamicisland;

import com.example.client.skia.render.LiquidGlassShader;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;

/** Glass shell follows the same animated geometry as its clipped content. */
final class DynamicIslandGlass implements AutoCloseable {
    /** Mapping from island-local pixels to the immutable framebuffer snapshot. */
    record Background(Image sharp, Image blurred, float scaleX, float scaleY, float offsetX, float offsetY) {}

    private final LiquidGlassShader optics = new LiquidGlassShader();
    private final ImageFilter shadow = ImageFilter.makeBlur(8, 8, FilterTileMode.DECAL);

    void drawJoined(Canvas canvas, float x, float y, float width, float height, float radius,
                    Rect satellite, float blend, Background background) {
        Image sharp = background == null ? null : background.sharp();
        Image blurred = background == null ? null : background.blurred();
        float sx = background == null ? 1 : background.scaleX();
        float sy = background == null ? 1 : background.scaleY();
        float ox = background == null ? 0 : background.offsetX();
        float oy = background == null ? 0 : background.offsetY();
        int saved = canvas.save();
        try {
            canvas.translate(0, 4);
            optics.drawJoined(canvas, sharp, blurred, x, y, width, height, radius, satellite, blend, sx, sy, ox, oy, shadow);
        } finally {
            canvas.restoreToCount(saved);
        }
        optics.drawJoined(canvas, sharp, blurred, x, y, width, height, radius, satellite, blend, sx, sy, ox, oy, null);
    }

    void draw(Canvas canvas, float x, float y, float width, float height, float radius, Background background) {
        drawJoined(canvas, x, y, width, height, radius, null, 0, background);
    }

    @Override
    public void close() {
        optics.close();
        shadow.close();
    }
}
