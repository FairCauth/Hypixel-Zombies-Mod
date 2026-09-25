package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.events.SkiaEvent;
import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.skia.render.LiquidGlassUi;
import com.example.client.skia.render.RenderUtils;

/**
 * 测试用：开启后在屏幕中央画一块液态玻璃矩形。
 *
 * 直接在 SkiaEvent 中绘制，坐标使用 GUI 缩放后的逻辑像素。
 */
@ModuleInfo(name = {
        @Text(label = "Liquid Glass Test", language = Language.English),
        @Text(label = "液态玻璃测试", language = Language.Chinese)
}, enable = false)
public class LiquidGlassTest extends AbstractModule {

    @EventTarget
    public void onRenderSkia(SkiaEvent event) {
        if (mc.player == null || mc.level == null) return;

        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();
        float width = screenWidth * 0.35F;
        float height = screenHeight * 0.30F;
        float x = (screenWidth - width) * 0.5F;
        float y = (screenHeight - height) * 0.5F;


//        RenderUtils.drawBlur(event.getCanvasStack(), 10,10, 100,100,5,15);

        LiquidGlassUi.drawPanel(
                event.getCanvasStack(),
                x, y, width, height,
                Math.min(26F, height * 0.22F)
        );
    }

}
