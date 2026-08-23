package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.events.RenderEvent;
import com.example.client.language.Language;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

import java.awt.Color;

@ModuleInfo(name = "module.hud", enable = true)
public class Hud extends AbstractModule {
    private static final FontDescription TEST_FONT =
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("zombies-mod", "damage"));
    @EventTarget
    public void onRender(RenderEvent event) {
        if (mc.player == null || mc.level == null || !mc.hasSingleplayerServer()) return;
//
//        GuiGraphicsExtractor graphics = event.getGuiGraphicsExtractor();
//        Component text = Component.literal("+12")
//                .withStyle(style -> style.withFont(TEST_FONT));
//
//        float centerX = 80F;
//        float centerY = 70F;
//        float scale = 1;
//        Color top = new Color(255, 100, 100);
//        Color bottom = new Color(255, 255, 255);
//
//        DamageNumbers.drawGradientText(
//                graphics, text, centerX, centerY,
//                mc.font.width(text), DamageNumbers.DAMAGE_FONT_HEIGHT,
//                scale, top, bottom, 255
//        );
    }
}
