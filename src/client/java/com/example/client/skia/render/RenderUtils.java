package com.example.client.skia.render;

import com.example.client.skia.CanvasStack;
import com.example.client.skia.Skia;
import com.example.client.skia.font.FilterCache;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

public class RenderUtils {
    private static final Paint paint = new Paint();
    private static final Paint blurPant = new Paint();
    private static final Paint shadowPant = new Paint();

    public static void drawAngularHudBackground(
            CanvasStack stack,
            float x,
            float y,
            float width,
            float height
    ) {
        drawAngularHudBackground(stack, x, y, width, height, 0xD91A2028);
    }

    public static void drawAngularHudBackground(
            CanvasStack stack,
            float x,
            float y,
            float width,
            float height,
            int color
    ) {
        if (stack == null || width <= 0 || height <= 0 || (color >>> 24) == 0) return;

        // 与 TargetHud 血条的 slant/height（4/7）保持相同斜率。
        float edgeSlope = 4F / 7F;

        // 原图左端是不对称三角形：尖点偏上，上边短，下边向右延伸得更长。
        float leftTopWidth = Math.min(8F, width * 0.055F);
        float leftPointY = y + height * 0.32F;
        float leftBottomWidth = Math.min(
                (y + height - leftPointY) * edgeSlope,
                width * 0.1F
        );
        float rightCut = Math.min(5F, height * 0.2F);
        float rightSlopeY = Math.min(6F, height * 0.25F);
        float rightSlopeWidth = Math.min(
                (height - rightSlopeY) * edgeSlope,
                width * 0.12F
        );

        try (Path background = new Path()
                .moveTo(x + leftTopWidth, y)
                .lineTo(x + width - rightSlopeWidth, y)
                .lineTo(x + width, y + height - rightSlopeY)
                .lineTo(x + width - rightCut, y + height)
                .lineTo(x + leftBottomWidth, y + height)
                .lineTo(x, leftPointY)
                .closePath()) {
            paint.reset();
            paint.setAntiAlias(true);
            paint.setMode(PaintMode.FILL);
            paint.setColor(color);
            stack.canvas().drawPath(background, paint);
        }
    }


    public static void drawParallelogramHealthBar(
            CanvasStack stack,
            float x,
            float y,
            float width,
            float height,
            float slant,
            float progress
    ) {
        drawParallelogramHealthBar(
                stack, x, y, width, height, slant, progress,
                0xFF34383D,
                0xFFF2EBEE
        );
    }

    public static void drawParallelogramHealthBar(
            CanvasStack stack,
            float x,
            float y,
            float width,
            float height,
            float slant,
            float progress,
            int trackColor,
            int healthColor
    ) {
        if (stack == null || width <= 0 || height <= 0) return;

        progress = Math.clamp(progress, 0F, 1F);
        slant = Math.clamp(slant, 0F, width * 0.45F);

        try (Path track = createParallelogram(x, y, width, height, slant)) {
            paint.reset();
            paint.setAntiAlias(true);
            paint.setMode(PaintMode.FILL);
            paint.setColor(trackColor);
            stack.canvas().drawPath(track, paint);

            float filledWidth = width * progress;
            if (filledWidth > 0F) {
                try (Path health = createParallelogram(
                        x, y, filledWidth, height,
                        Math.min(slant, filledWidth * 0.45F)
                )) {
                    paint.setColor(healthColor);
                    stack.canvas().drawPath(health, paint);
                }
            }
        }
    }

    /**
     * 绘制带伤害拖尾的平行四边形血条：trailProgress 先画红色残影，
     * healthProgress 再覆盖当前的白色血量。
     */
    public static void drawTrailingParallelogramHealthBar(
            CanvasStack stack,
            float x,
            float y,
            float width,
            float height,
            float slant,
            float healthProgress,
            float trailProgress
    ) {
        if (stack == null || width <= 0 || height <= 0) return;

        healthProgress = Math.clamp(healthProgress, 0F, 1F);
        trailProgress = Math.clamp(Math.max(trailProgress, healthProgress), 0F, 1F);
        slant = Math.clamp(slant, 0F, width * 0.45F);

        try (Path track = createParallelogram(x, y, width, height, slant)) {
            paint.reset();
            paint.setAntiAlias(true);
            paint.setMode(PaintMode.FILL);
            paint.setColor(0xFF34383D);
            stack.canvas().drawPath(track, paint);

            drawParallelogramTrailSegment(
                    stack, x, y, width, height, slant,
                    healthProgress, trailProgress, 0xFFE51B23
            );
            drawParallelogramProgress(
                    stack, x, y, width, height, slant,
                    healthProgress, 0xFFF2EBEE
            );
        }
    }

    /** 只画当前血量终点与拖尾终点之间的区域，避免红色垫在白条下产生抗锯齿红边。 */
    private static void drawParallelogramTrailSegment(
            CanvasStack stack,
            float x,
            float y,
            float width,
            float height,
            float slant,
            float healthProgress,
            float trailProgress,
            int color
    ) {
        float healthWidth = width * Math.clamp(healthProgress, 0F, 1F);
        float trailWidth = width * Math.clamp(trailProgress, 0F, 1F);

        // 小于半个 GUI 像素时直接吸附，避免只剩一根红色细线。
        if (trailWidth - healthWidth <= 0.5F) return;

        float healthSlant = Math.min(slant, healthWidth * 0.45F);
        float trailSlant = Math.min(slant, trailWidth * 0.45F);

        try (Path trail = new Path()
                .moveTo(x + healthWidth - healthSlant, y)
                .lineTo(x + trailWidth - trailSlant, y)
                .lineTo(x + trailWidth, y + height)
                .lineTo(x + healthWidth, y + height)
                .closePath()) {
            paint.setMode(PaintMode.FILL);
            paint.setColor(color);
            stack.canvas().drawPath(trail, paint);
        }
    }

    private static void drawParallelogramProgress(
            CanvasStack stack,
            float x,
            float y,
            float width,
            float height,
            float slant,
            float progress,
            int color
    ) {
        float filledWidth = width * Math.clamp(progress, 0F, 1F);
        if (filledWidth <= 0F) return;

        try (Path fill = createParallelogram(
                x, y, filledWidth, height,
                Math.min(slant, filledWidth * 0.45F)
        )) {
            paint.setMode(PaintMode.FILL);
            paint.setColor(color);
            stack.canvas().drawPath(fill, paint);
        }
    }

    /** 绘制独立分段的平行四边形条。 */
    public static void drawSegmentedParallelogramBar(
            CanvasStack stack,
            float x,
            float y,
            float width,
            float height,
            float slant,
            float progress,
            int segments,
            int trackColor,
            int fillColor
    ) {
        drawSegmentedParallelogramBar(
                stack, x, y, width, height, slant, progress,
                segments, segments, 1.5F,
                trackColor, fillColor, 0xFF686D72
        );
    }

    /**
     * width 表示 maxSegments 全部画满时的总宽度；segments 决定当前实际显示几格。
     */
    public static void drawSegmentedParallelogramBar(
            CanvasStack stack,
            float x,
            float y,
            float width,
            float height,
            float slant,
            float progress,
            int segments,
            int maxSegments,
            float gap,
            int trackColor,
            int fillColor,
            int outlineColor
    ) {
        if (stack == null || width <= 0 || height <= 0 || segments < 1 || maxSegments < segments) return;

        progress = Math.clamp(progress, 0F, 1F);
        gap = Math.max(0F, gap);
        float segmentSlant = Math.min(slant, width / maxSegments * 0.35F);

        float segmentWidth = (
                width - (gap - segmentSlant) * (maxSegments - 1)
        ) / maxSegments;
        if (segmentWidth <= 0F) return;

        float filledSegments = progress * segments;

        for (int i = 0; i < segments; i++) {
            float segmentX = x + i * (segmentWidth + gap - segmentSlant);

            try (Path track = createParallelogram(segmentX, y, segmentWidth, height, segmentSlant)) {
                paint.reset();
                paint.setAntiAlias(true);
                paint.setMode(PaintMode.FILL);
                paint.setColor(trackColor);
                stack.canvas().drawPath(track, paint);

                float segmentProgress = Math.clamp(filledSegments - i, 0F, 1F);
                float filledWidth = segmentWidth * segmentProgress;
                if (filledWidth > 0F) {
                    try (Path fill = createParallelogram(
                            segmentX, y, filledWidth, height,
                            Math.min(segmentSlant, filledWidth * 0.35F)
                    )) {
                        paint.setColor(fillColor);
                        stack.canvas().drawPath(fill, paint);
                    }
                }

                paint.setMode(PaintMode.STROKE);
                paint.setStrokeWidth(0.5F);
                paint.setColor(outlineColor);
                stack.canvas().drawPath(track, paint);
            }
        }
    }

    private static Path createParallelogram(
            float x,
            float y,
            float width,
            float height,
            float slant
    ) {
        return new Path()
                .moveTo(x, y)
                .lineTo(x + width - slant, y)
                .lineTo(x + width, y + height)
                .lineTo(x + slant, y + height)
                .closePath();
    }

    public static void drawRect(CanvasStack stack, float x, float y, float width, float height, float radius, int color) {
        if (width <= 0 || height <= 0 || (color >> 24 & 0xFF) == 0) return;

        paint.reset();
        paint.setColor(color);
        paint.setAntiAlias(radius > 0);

        if (radius > 0) {
            stack.canvas().drawRRect(RRect.makeXYWH(x, y, width, height, radius), paint);
        } else {
            stack.canvas().drawRect(Rect.makeXYWH(x, y, width, height), paint);
        }
    }


    public static void drawShadow(
            CanvasStack stack,
            float x,
            float y,
            float width,
            float height,
            float radius,
            int color
    ) {
        drawShadow(stack, x, y, width, height, radius, color, 6F, 0F, 2F);
    }


    public static void drawShadow(
            CanvasStack stack,
            float x,
            float y,
            float width,
            float height,
            float radius,
            int color,
            float blurRadius,
            float offsetX,
            float offsetY
    ) {
        if (stack == null || width <= 0 || height <= 0 || (color >>> 24) == 0) return;

        blurRadius = Math.max(0F, blurRadius);
        radius = Math.max(0F, radius);
        stack.push();
        shadowPant.reset();
        shadowPant.setMode(PaintMode.FILL);
        shadowPant.setColor(color);
        shadowPant.setAntiAlias(true);
        if (blurRadius > 0F) {
            shadowPant.setMaskFilter(FilterCache.getMaskBlur(blurRadius));
        }

        float shadowX = x + offsetX;
        float shadowY = y + offsetY;
        if (radius > 0F) {
            stack.canvas().drawRRect(
                    RRect.makeXYWH(shadowX, shadowY, width, height, radius),
                    shadowPant
            );
        } else {
            stack.canvas().drawRect(Rect.makeXYWH(shadowX, shadowY, width, height), shadowPant);
        }

        shadowPant.setMaskFilter(null);
        stack.pop();
    }

    public static void drawImage(CanvasStack stack,Image image, float x, float y, float width, float height, float radius) {
        paint.reset();

        stack.push();

        if (radius > 0) {
            paint.setAntiAlias(true);
            stack.canvas().clipRRect(RRect.makeXYWH(x, y, width, height, radius), true);
        } else {
            paint.setAntiAlias(false);
            stack.canvas().clipRect(Rect.makeXYWH(x, y, width, height), ClipMode.INTERSECT, false);
        }

        stack.canvas().resetMatrix();
        if (image != null) {
            stack.canvas().drawImage(image, 0, 0, paint);
        }else{
            System.out.println("image NULL");
        }

        stack.pop();
    }

    public static void drawBlur(CanvasStack stack, float x, float y, float width, float height, float radius, float blurRadius) {
        if (width <= 0 || height <= 0) return;
        blurPant.reset();
        try (ImageFilter blur = ImageFilter.makeBlur(blurRadius, blurRadius, FilterTileMode.CLAMP)) {
            blurPant.setAlpha(255);
            blurPant.setImageFilter(blur);

            stack.push();

            if (radius > 0) {
                blurPant.setAntiAlias(true);
                stack.canvas().clipRRect(RRect.makeXYWH(x, y, width, height, radius), true);
            } else {
                blurPant.setAntiAlias(false);
                stack.canvas().clipRect(Rect.makeXYWH(x, y, width, height), ClipMode.INTERSECT, false);
            }

            stack.canvas().resetMatrix();
            Image image = Skia.getGameImage();
            if (image != null) {
                stack.canvas().drawImage(image, 0, 0, blurPant);
            }else{
                System.out.println("image NULL");
            }

            stack.pop();
        } finally {
            blurPant.setImageFilter(null);
        }
    }



}
