package com.example.client.skia.render;

import com.example.client.skia.CanvasStack;
import com.example.client.skia.Skia;
import io.github.humbleui.skija.BlendMode;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;

import java.util.HashMap;
import java.util.Map;

/** Skia RuntimeEffect implementation of the old newrender liquid-glass shader. */
public final class LiquidGlass {
    private static LiquidGlassShader shader;
    private static boolean failed;
    private static final Map<Integer, Surface> BLUR_SURFACES = new HashMap<>();
    private static final Map<Integer, Image> FRAME_BLURS = new HashMap<>();
    private static Image cachedFrameSource;
    private static int blurWidth = -1;
    private static int blurHeight = -1;

    private LiquidGlass() {
    }

    public static void draw(
            CanvasStack stack,
            float x, float y, float width, float height,
            float cornerRadius, float blurRadius, float noise, float refraction, float dispersion,
            float tintR, float tintG, float tintB, float tintStrength,
            float glow, float alpha
    ) {
        if (failed || stack == null || width <= 0F || height <= 0F || alpha <= 0F) return;

        Image snapshot = Skia.getGameImage();
        if (snapshot == null || snapshot.isClosed()) return;

        try {
            int guiWidth = Math.max(1, com.example.client.utils.IMinecraft.mc.getWindow().getGuiScaledWidth());
            int guiHeight = Math.max(1, com.example.client.utils.IMinecraft.mc.getWindow().getGuiScaledHeight());
            float scaleX = snapshot.getWidth() / (float) guiWidth;
            float scaleY = snapshot.getHeight() / (float) guiHeight;
            float framebufferScale = (scaleX + scaleY) * 0.5F;
            Image background = blurRadius > 0F
                    ? getBlurredBackground(snapshot, blurRadius * framebufferScale / 2F)
                    : snapshot;

            getShader().draw(stack.canvas(), snapshot, background,
                    x, y, width, height, cornerRadius, blurRadius, noise, refraction, dispersion,
                    tintR, tintG, tintB, tintStrength, glow, alpha, scaleX, scaleY, 0, 0);
        } catch (Throwable throwable) {
            failed = true;
            System.err.println("[Skia LiquidGlass] Failed to initialize or render; disabling effect");
            throwable.printStackTrace();
        }
    }

    private static LiquidGlassShader getShader() {
        if (shader == null) shader = new LiquidGlassShader();
        return shader;
    }

    /** Borrowed frame image; callers must not close it. Use only during the Skia render callback. */
    public static Image getBlurredBackground(Image source, float sigma) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width != blurWidth || height != blurHeight) {
            closeBlurResources();
            blurWidth = width;
            blurHeight = height;
        } else if (cachedFrameSource != source) {
            closeFrameBlurImages();
        }
        cachedFrameSource = source;

        int sigmaKey = Math.max(1, Math.round(sigma * 4F));
        Image cached = FRAME_BLURS.get(sigmaKey);
        if (cached != null && !cached.isClosed()) return cached;

        Surface blurSurface = BLUR_SURFACES.computeIfAbsent(sigmaKey, ignored ->
                Surface.makeRenderTarget(
                        Skia.getContext(),
                        false,
                        source.getImageInfo()
                )
        );

        float quantizedSigma = sigmaKey / 4F;
        blurSurface.getCanvas().clear(0x00000000);
        try (ImageFilter blur = ImageFilter.makeBlur(
                quantizedSigma,
                quantizedSigma,
                FilterTileMode.CLAMP
        ); Paint blurPaint = new Paint()) {
            blurPaint.setBlendMode(BlendMode.SRC);
            blurPaint.setImageFilter(blur);
            blurSurface.getCanvas().drawImage(source, 0F, 0F, blurPaint);
        }

        Image blurred = blurSurface.makeImageSnapshot();
        FRAME_BLURS.put(sigmaKey, blurred);
        return blurred;
    }

    private static void closeFrameBlurImages() {
        for (Image image : FRAME_BLURS.values()) {
            if (image != null && !image.isClosed()) image.close();
        }
        FRAME_BLURS.clear();
    }

    private static void closeBlurResources() {
        closeFrameBlurImages();
        for (Surface surface : BLUR_SURFACES.values()) {
            if (surface != null) surface.close();
        }
        BLUR_SURFACES.clear();
        cachedFrameSource = null;
    }

}
