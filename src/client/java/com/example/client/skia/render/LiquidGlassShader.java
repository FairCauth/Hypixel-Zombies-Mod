package com.example.client.skia.render;

import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Shared liquid-glass optics, independent of Minecraft and canvas transforms. */
public final class LiquidGlassShader implements AutoCloseable {
    /** One edge material for both regular HUD panels and the animated island. */
    public static final float DEFAULT_EDGE_DISPERSION = 0.0016F;
    private static final int UNIFORM_BYTES = 128;

    private static final String SKSL = """
            uniform shader blurredScreen;
            uniform shader sharpScreen;
            uniform float4 rect;
            uniform float4 tint;
            uniform float4 optics;
            uniform float4 screenInfo;
            uniform float4 style;
            uniform float4 sampleOrigin;
            uniform float4 satelliteRect;
            uniform float4 unionInfo;

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

            float shellDistance(float2 coord) {
                float a = roundedRectDistance(coord - rect.xy - rect.zw * 0.5,
                        rect.zw * 0.5, min(optics.x, min(rect.z, rect.w) * 0.5));
                if (unionInfo.z < 0.5) return a;
                float b = roundedRectDistance(coord - satelliteRect.xy - satelliteRect.zw * 0.5,
                        satelliteRect.zw * 0.5, unionInfo.x);
                if (unionInfo.y <= 0.0) return min(a, b);
                float k = max(unionInfo.y, 0.001);
                float contact = max(k - abs(a - b), 0.0) / k;
                return min(a, b) - contact * contact * k * 0.25;
            }

            half3 sharpDispersedSample(float2 sampleCoord, float2 chroma) {
                return half3(
                        sharpScreen.eval(sampleCoord + chroma).r,
                        sharpScreen.eval(sampleCoord).g,
                        sharpScreen.eval(sampleCoord - chroma).b
                );
            }

            half4 finishIsland(half3 color, float alpha, float2 coord, float4 opticalRect,
                    float pixelDistance, float coverage) {
                if (unionInfo.w < 0.5) return half4(color * half(alpha * coverage), half(alpha * coverage));
                // One glaze and one outer rim follow the distance field throughout docking.
                float2 gradient = opticalRect.zw * float2(0.25, 1.0);
                float t = clamp(dot(coord - opticalRect.xy, gradient) / dot(gradient, gradient), 0.0, 1.0);
                half glazeAlpha = half(t < 0.5 ? mix(34.0, 4.0, t * 2.0)
                        : mix(4.0, 22.0, (t - 0.5) * 2.0)) / 255.0;
                half3 glazeColor = mix(half3(1.0), half3(5.0, 15.0, 33.0) / 255.0,
                        half(max(t - 0.5, 0.0) * 2.0));
                half4 result = half4(color * half(alpha), half(alpha));
                result = half4(glazeColor * glazeAlpha, glazeAlpha) + result * (1.0 - glazeAlpha);
                float rimT = clamp(dot(coord - opticalRect.xy, opticalRect.zw)
                        / dot(opticalRect.zw, opticalRect.zw), 0.0, 1.0);
                float rimStrength = (rimT < 0.5 ? mix(96.0, 20.0, rimT * 2.0)
                        : mix(20.0, 64.0, (rimT - 0.5) * 2.0)) / 255.0;
                float band = 1.0 - smoothstep(0.175, 0.925, abs(pixelDistance + 0.5));
                half rimAlpha = half(band * rimStrength);
                result = half4(half3(rimAlpha), rimAlpha) + result * (1.0 - rimAlpha);
                return result * half(coverage);
            }

            half4 main(float2 coord) {
                float4 opticalRect = rect;
                float contact = 0.0;
                if (unionInfo.z > 0.5) {
                    float a = roundedRectDistance(coord - rect.xy - rect.zw * 0.5,
                            rect.zw * 0.5, min(optics.x, min(rect.z, rect.w) * 0.5));
                    float b = roundedRectDistance(coord - satelliteRect.xy - satelliteRect.zw * 0.5,
                            satelliteRect.zw * 0.5, unionInfo.x);
                    float k = max(unionInfo.y, 0.001);
                    float exposure = smoothstep(0.0, max(satelliteRect.w, 1.0),
                            satelliteRect.x + satelliteRect.z - rect.x - rect.z);
                    contact = max(k - abs(a - b), 0.0) / k * exposure;
                    // Preserve each lobe's optics away from the neck; blend them only at contact.
                    float satelliteWeight = (1.0 - smoothstep(-k, k, b - a)) * exposure;
                    opticalRect = mix(rect, satelliteRect, satelliteWeight);
                }
                float2 localUv = (coord - opticalRect.xy) / opticalRect.zw;
                float2 center = opticalRect.xy + opticalRect.zw * 0.5;
                float2 halfSize = opticalRect.zw * 0.5;
                float pixelDistance = shellDistance(coord);
                float coverage = 1.0 - smoothstep(-0.75, 0.75, pixelDistance);
                if (coverage <= 0.0) {
                    return half4(0.0);
                }

                // The joined silhouette also supplies its fallback and shadow mask.
                if (style.w > 1.5) {
                    half alpha = half(style.z * coverage);
                    return half4(half3(tint.rgb) * alpha, alpha);
                }
                if (style.w > 0.5) {
                    return finishIsland(half3(18.0, 32.0, 52.0) / 255.0, 40.0 / 255.0,
                            coord, opticalRect, pixelDistance, coverage);
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
                float2 sampleCoord = (center + offset * bodyScale) * screenInfo.zw + sampleOrigin.xy;

                float2 direction = normalize(offset + float2(0.00001));
                if (unionInfo.z > 0.5) {
                    // Follow the neck's surface normal, avoiding internal rims and optical seams.
                    float2 surfaceNormal = normalize(float2(
                            shellDistance(coord + float2(0.5, 0.0)) - shellDistance(coord - float2(0.5, 0.0)),
                            shellDistance(coord + float2(0.0, 0.5)) - shellDistance(coord - float2(0.0, 0.5))) + float2(0.00001));
                    float2 neckSample = (coord - surfaceNormal * (0.6 + bendStrength * minHalfSize * lensProfile))
                            * screenInfo.zw + sampleOrigin.xy;
                    direction = normalize(mix(direction, surfaceNormal, contact) + float2(0.00001));
                    sampleCoord = mix(sampleCoord, neckSample, contact);
                }
                // 色散距离使用局部逻辑像素，再映射到快照；窗口分辨率变化
                // 不应让同一块玻璃的 RGB 分离突然加倍。
                float chromaPixels = clamp(style.x * 900.0, 0.0, 2.4);
                float2 chroma = direction * lensBand * chromaPixels * screenInfo.zw;
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
                float spectralWidth = clamp(minHalfSize * 0.14, 2.4, 3.2);
                float spectralBand = 1.0 - smoothstep(0.0, spectralWidth, max(-pixelDistance, 0.0));
                float spectralAngle = atan(glowCoord.y, glowCoord.x) - 0.35;
                half3 spectralColor = half3(
                        0.5 + 0.5 * cos(spectralAngle),
                        0.5 + 0.5 * cos(spectralAngle + 2.094395),
                        0.5 + 0.5 * cos(spectralAngle + 4.188790)
                );
                // 光谱稍微去饱和，并随受光方向减弱，保留轻微彩色反光，
                // 避免整圈等强度的彩虹描边。RGB 背景采样仍负责真实色散。
                half spectralLuminance = dot(spectralColor, half3(0.2126, 0.7152, 0.0722));
                spectralColor = mix(half3(spectralLuminance), spectralColor, half(0.82));
                float spectralStrength = spectralBand * clamp(style.x * 200.0, 0.0, 0.34)
                        * (0.65 + 0.35 * litSide);
                color = mix(color, spectralColor, half(spectralStrength));

                return finishIsland(color, clamp(style.z, 0.0, 1.0), coord, opticalRect, pixelDistance, coverage);
            }
            """;

    private RuntimeEffect effect;

    /** Source coordinates = local coordinates * sourceScale + sourceOffset. Images are borrowed. */
    public void draw(
            Canvas canvas, Image sharp, Image blurred,
            float x, float y, float width, float height,
            float radius, float blurRadius, float noise, float refraction, float dispersion,
            float tintR, float tintG, float tintB, float tintStrength, float glow, float alpha,
            float sourceScaleX, float sourceScaleY, float sourceOffsetX, float sourceOffsetY
    ) {
        drawShape(canvas, sharp, blurred, x, y, width, height, radius, blurRadius, noise, refraction, dispersion,
                tintR, tintG, tintB, tintStrength, glow, alpha,
                sourceScaleX, sourceScaleY, sourceOffsetX, sourceOffsetY, null, 0, null, false);
    }

    /** One smooth distance field for two approaching shells; ordinary panels use the original shape. */
    public void drawJoined(
            Canvas canvas, Image sharp, Image blurred,
            float x, float y, float width, float height, float radius, Rect satellite, float blend,
            float sourceScaleX, float sourceScaleY, float sourceOffsetX, float sourceOffsetY, ImageFilter shadow
    ) {
        drawShape(canvas, sharp, blurred, x, y, width, height, radius, 8, 0.0015f, 2.8f, DEFAULT_EDGE_DISPERSION,
                shadow == null ? 0.055f : 0.02f, shadow == null ? 0.075f : 0.047f,
                shadow == null ? 0.11f : 0.094f, 0.09f, 0.26f, shadow == null ? 0.90f : 0.16f,
                sourceScaleX, sourceScaleY, sourceOffsetX, sourceOffsetY, satellite, blend, shadow, true);
    }

    private void drawShape(
            Canvas canvas, Image sharp, Image blurred,
            float x, float y, float width, float height,
            float radius, float blurRadius, float noise, float refraction, float dispersion,
            float tintR, float tintG, float tintB, float tintStrength, float glow, float alpha,
            float sourceScaleX, float sourceScaleY, float sourceOffsetX, float sourceOffsetY,
            Rect satellite, float blend, ImageFilter shadow, boolean islandSkin
    ) {
        if (width <= 0 || height <= 0 || alpha <= 0) return;
        if (effect == null) effect = RuntimeEffect.makeForShader(SKSL);
        ByteBuffer uniforms = ByteBuffer.allocate(UNIFORM_BYTES).order(ByteOrder.nativeOrder());
        put4(uniforms, x, y, width, height);
        put4(uniforms, tintR, tintG, tintB, tintStrength);
        put4(uniforms, radius, blurRadius, noise, refraction);
        put4(uniforms, sharp == null ? 1 : sharp.getWidth(), sharp == null ? 1 : sharp.getHeight(), sourceScaleX, sourceScaleY);
        put4(uniforms, dispersion, glow, alpha, shadow != null ? 2 : sharp == null ? 1 : 0);
        put4(uniforms, sourceOffsetX, sourceOffsetY, 0, 0);
        put4(uniforms, satellite == null ? 0 : satellite.getLeft(), satellite == null ? 0 : satellite.getTop(),
                satellite == null ? 0 : satellite.getWidth(), satellite == null ? 0 : satellite.getHeight());
        put4(uniforms, satellite == null ? 0 : Math.min(satellite.getWidth(), satellite.getHeight()) / 2,
                blend, satellite == null ? 0 : 1, islandSkin ? 1 : 0);
        try (Data data = Data.makeFromBytes(uniforms.array());
             Shader blurredShader = blurred == null ? Shader.makeColor(0) : blurred.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR, null);
             Shader sharpShader = sharp == null ? Shader.makeColor(0) : sharp.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR, null);
             Shader glassShader = effect.makeShader(data, new Shader[]{blurredShader, sharpShader}, null);
             Paint paint = new Paint().setAntiAlias(true).setShader(glassShader).setImageFilter(shadow)) {
            if (satellite == null) {
                float margin = islandSkin ? 1 : 0;
                canvas.drawRect(Rect.makeXYWH(x - margin, y - margin, width + margin * 2, height + margin * 2), paint);
            }
            else {
                float margin = blend * 0.25f + 1;
                canvas.drawRect(Rect.makeLTRB(Math.min(x, satellite.getLeft()) - margin,
                        Math.min(y, satellite.getTop()) - margin, Math.max(x + width, satellite.getRight()) + margin,
                        Math.max(y + height, satellite.getBottom()) + margin), paint);
            }
        }
    }

    private static void put4(ByteBuffer buffer, float x, float y, float z, float w) {
        buffer.putFloat(x).putFloat(y).putFloat(z).putFloat(w);
    }

    @Override
    public void close() {
        if (effect != null) { effect.close(); effect = null; }
    }
}
