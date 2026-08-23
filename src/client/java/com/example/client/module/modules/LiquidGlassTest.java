package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.ZombiesModClient;
import com.example.client.events.RenderEvent;
import com.example.client.language.Language;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.newrender.LiquidGlass;
import net.minecraft.client.Minecraft;

/**
 * 测试用：开启后在屏幕中央画一块液态玻璃矩形。
 *
 * 绘制不在 RenderEvent(extract 提取阶段)里做——那时机太早会被正式渲染覆盖。
 * 改由 GameRendererLiquidGlassMixin 在 GameRenderer.render(...) 的 TAIL（整帧画完、呈现前）调用
 * 本类的 renderIfEnabled()，此时主目标已是最终画面，立即模式画上去就能显示。
 *
 * 坐标是「帧缓冲物理像素」（和 LiquidGlass 内部 uScreenSize=main.width 一致），故用 window 物理宽高居中。
 */
@ModuleInfo(name = "module.liquid_glass_test", enable = false)
public class LiquidGlassTest extends AbstractModule {


    public static void renderIfEnabled() {
    //        AbstractModule m = ZombiesModClient.moduleManager == null ? null
    //                : ZombiesModClient.moduleManager.getModule("Liquid Glass Test");
    //        if (m == null || !m.isEnable()) return;
    //
    //        Minecraft mc = Minecraft.getInstance();
    //        if (mc.getWindow() == null) return;
    //
    //        int sw = mc.getWindow().getWidth();
    //        int sh = mc.getWindow().getHeight();
    //
    //        float w = sw * 0.35f;
    //        float h = sh * 0.30f;
    //        float x = (sw - w) / 2f;
    //        float y = (sh - h) / 2f;
    //        LiquidGlass.draw(
    //                x, y, w, h,
    //                8f,      // uPower 超椭圆指数（≈4 方圆，越大越方/越接近矩形）
    //                0.01f,   // uNoise grain 噪点
    //                2.5f,    // uRefractionPower 折射幂（越大边缘放大/挤压越强）
    //                0.004f,  // uChroma 色散（uv 单位，很小）
    //                1f, 1f, 1f, 0f,   // tint rgb + 强度（0=clear 不染色）
    //                0.12f,   // uGlow 描边光强
    //                1.0f     // alpha
    //        );
    }
}
