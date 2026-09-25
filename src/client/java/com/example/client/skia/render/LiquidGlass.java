package com.example.client.skia.render;

import com.example.client.skia.CanvasStack;
import com.example.client.skia.Skia;
import io.github.humbleui.skija.BlendMode;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.RuntimeEffect;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

/** Skia RuntimeEffect implementation of the old newrender liquid-glass shader. */
public final class LiquidGlass {
    private static final int UNIFORM_BYTES = 80;

    private static final String SKSL = """
            uniform shader blurredScreen;
            uniform shader sharpScreen;
            uniform float4 rect;
            uniform float4 tint;
            uniform float4 optics;
            uniform float4 screenInfo;
            uniform float4 style;

            float randomValue(float2 p) {
                return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
            }

            float refractionCurve(float x) {
                return 1.0 - 2.3 * pow(5.2 * 2.718281828, -6.9 * x - 0.7);
            }

            float roundedRectDistance(float2 point, float2 halfSize, float radius) {
                float2 q = abs(point) - halfSize + float2(radius, radius);
                return min(max(q.x, q.y), 0.0)
                        + length(max(q, float2(0.0, 0.0))) - radius;
            }

            half3 sharpDispersedSample(float2 sampleCoord, float2 chroma) {
                return half3(
                        sharpScreen.eval(sampleCoord + chroma).r,
                        sharpScreen.eval(sampleCoord).g,
                        sharpScreen.eval(sampleCoord - chroma).b
                );
            }

            half4 main(float2 coord) {
                float2 localUv = (coord - rect.xy) / rect.zw;
                float2 center = rect.xy + rect.zw * 0.5;
                float2 halfSize = rect.zw * 0.5;
                float cornerRadius = clamp(optics.x, 0.0, min(halfSize.x, halfSize.y));
                float pixelDistance = roundedRectDistance(coord - center, halfSize, cornerRadius);
                float coverage = 1.0 - smoothstep(-0.75, 0.75, pixelDistance);
                if (coverage <= 0.0) {
                    return half4(0.0);
                }

                float minHalfSize = max(min(halfSize.x, halfSize.y), 1.0);
                float distanceInside = max(-pixelDistance, 0.0) / minHalfSize;

                // 平滑的凸透镜缩放。弯折比例设有上限，避免局部法线位移把树干、
                // 方块边缘拉成重复条带，同时仍在较宽区域内保留明显放大。
                float lensBand = 1.0 - smoothstep(0.0, 0.42, distanceInside);
                float lensProfile = lensBand * lensBand * (3.0 - 2.0 * lensBand);
                float bendStrength = clamp(max(optics.w, 0.0) * 0.05, 0.0, 0.18);
                // 整块玻璃保留约 3% 的轻微放大，中心也会与原背景产生区别；
                // 边缘再叠加更强的透镜弯折。
                float bodyScale = 0.970 - bendStrength * lensProfile;
                float2 offset = coord - center;
                float2 sampleCoord = (center + offset * bodyScale) * screenInfo.zw;

                float2 direction = normalize(offset + float2(0.00001));
                float2 chroma = direction * lensBand * style.x * screenInfo.xy;
                half3 blurredColor = blurredScreen.eval(sampleCoord).rgb;
                half3 refractedColor = sharpDispersedSample(sampleCoord, chroma);
                // 苹果式液态玻璃中心仍能辨认背景；越靠近厚边缘，越偏向清晰且
                // 被折射的原始画面，而不是整块都成为高斯毛玻璃。
                float sharpMix = 0.34 + lensBand * 0.36;
                half3 color = mix(blurredColor, refractedColor, half(sharpMix));

                half luminance = dot(color, half3(0.2126, 0.7152, 0.0722));
                color = mix(half3(luminance), color, half(1.06));

                float noise = (randomValue(coord * screenInfo.zw * 0.001) - 0.5) * optics.z;
                color += half3(noise);
                color = mix(color, half3(tint.rgb), half(clamp(tint.a, 0.0, 1.0)));

                float2 glowCoord = localUv * 2.0 - 1.0;
                float directionalGlow = sin(atan(glowCoord.y, glowCoord.x) - 0.5);
                float rimBand = 1.0 - smoothstep(0.0, 0.22, distanceInside);
                float litSide = 0.5 + 0.5 * directionalGlow;
                float rimLight = litSide * style.y * rimBand;
                float rimShade = (1.0 - litSide) * style.y * rimBand;
                color *= half(1.015 + rimLight * 0.30 - rimShade * 0.20);
                color += half3(rimLight * 0.26);

                // 外圈之后再形成一条较淡的内高光，表现玻璃边缘的厚度。
                float innerRim = smoothstep(0.025, 0.07, distanceInside)
                        * (1.0 - smoothstep(0.07, 0.19, distanceInside));
                color += half3(innerRim * litSide * style.y * 0.10);

                // 将彩色光限制在较窄的玻璃边缘；真实 RGB 采样偏移负责色散，
                // 这里的光谱色只强化轮廓，不给面板内部叠加彩色滤镜。
                float spectralBand = 1.0 - smoothstep(0.0, 0.09, distanceInside);
                float spectralAngle = atan(glowCoord.y, glowCoord.x) - 0.35;
                half3 spectralColor = half3(
                        0.5 + 0.5 * cos(spectralAngle),
                        0.5 + 0.5 * cos(spectralAngle + 2.094395),
                        0.5 + 0.5 * cos(spectralAngle + 4.188790)
                );
                float spectralStrength = spectralBand * clamp(style.x * 55.0, 0.0, 0.14);
                color += (spectralColor - half3(0.35)) * half(spectralStrength);

                float alpha = clamp(style.z, 0.0, 1.0) * coverage;
                return half4(color * half(alpha), half(alpha));
            }
            """;

    private static RuntimeEffect effect;
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
            RuntimeEffect runtimeEffect = getEffect();
            int guiWidth = Math.max(1, com.example.client.utils.IMinecraft.mc.getWindow().getGuiScaledWidth());
            int guiHeight = Math.max(1, com.example.client.utils.IMinecraft.mc.getWindow().getGuiScaledHeight());
            float scaleX = snapshot.getWidth() / (float) guiWidth;
            float scaleY = snapshot.getHeight() / (float) guiHeight;
            float framebufferScale = (scaleX + scaleY) * 0.5F;
            Image background = blurRadius > 0F
                    ? getBlurredBackground(snapshot, blurRadius * framebufferScale / 2F)
                    : snapshot;

            byte[] uniformBytes = new byte[UNIFORM_BYTES];
            ByteBuffer uniforms = ByteBuffer.wrap(uniformBytes).order(ByteOrder.nativeOrder());
            put4(uniforms, x, y, width, height);
            put4(uniforms, tintR, tintG, tintB, tintStrength);
            put4(uniforms, cornerRadius, blurRadius, noise, refraction);
            put4(uniforms, snapshot.getWidth(), snapshot.getHeight(), scaleX, scaleY);
            put4(uniforms, dispersion, glow, alpha, 0F);

            try (Data data = Data.makeFromBytes(uniformBytes);
                 Shader blurredShader = background.makeShader(
                         FilterTileMode.CLAMP,
                         FilterTileMode.CLAMP,
                         SamplingMode.LINEAR,
                         null
                 );
                 Shader sharpShader = snapshot.makeShader(
                         FilterTileMode.CLAMP,
                         FilterTileMode.CLAMP,
                         SamplingMode.LINEAR,
                         null
                 );
                 Shader glassShader = runtimeEffect.makeShader(
                         data,
                         new Shader[]{blurredShader, sharpShader},
                         null
                 );
                 Paint glassPaint = new Paint()) {
                glassPaint.setMode(PaintMode.FILL);
                glassPaint.setAntiAlias(true);
                glassPaint.setBlendMode(BlendMode.SRC_OVER);
                glassPaint.setShader(glassShader);
                stack.canvas().drawRect(Rect.makeXYWH(x, y, width, height), glassPaint);
            }
        } catch (Throwable throwable) {
            failed = true;
            System.err.println("[Skia LiquidGlass] Failed to initialize or render; disabling effect");
            throwable.printStackTrace();
        }
    }

    private static RuntimeEffect getEffect() {
        if (effect == null) {
            effect = RuntimeEffect.makeForShader(SKSL);
        }
        return effect;
    }

    private static Image getBlurredBackground(Image source, float sigma) {
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

    private static void put4(ByteBuffer buffer, float x, float y, float z, float w) {
        buffer.putFloat(x).putFloat(y).putFloat(z).putFloat(w);
    }
}
