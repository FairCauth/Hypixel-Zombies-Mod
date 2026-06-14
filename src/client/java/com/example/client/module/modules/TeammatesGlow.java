package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.events.RenderEvent;
import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.BooleanSetting;
import com.example.client.setting.settings.NumberSetting;
import com.example.client.tracker.TeammateInfo;
import com.example.client.tracker.TeammateTracker;
import com.example.client.utils.PlayerUtils;
import com.example.client.utils.render.GuiGraphicsUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @see com.example.client.mixin.MinecraftMixin
 */

@ModuleInfo(name = {
        @Text(label = "Teammates Glow", language = Language.English),
        @Text(label = "队友高亮显示", language = Language.Chinese)
}, enable = true)
public class TeammatesGlow extends AbstractModule {
    @SettingInfo(name = {
            @Text(label = "Only In Zombies", language = Language.English),
            @Text(label = "仅在僵尸末日里", language = Language.Chinese)
    })
    public static final BooleanSetting onlyGame = new BooleanSetting(true);
    @SettingInfo(name = {
            @Text(label = "Info", language = Language.English),
            @Text(label = "队友信息", language = Language.Chinese)
    })
    public static final BooleanSetting info = new BooleanSetting(true);
    @SettingInfo(name = {
            @Text(label = "X", language = Language.English),
            @Text(label = "X", language = Language.Chinese)
    })
    public static final NumberSetting posX = new NumberSetting(0.1, 0, 1, "#.00");
    @SettingInfo(name = {
            @Text(label = "Y", language = Language.English),
            @Text(label = "Y", language = Language.Chinese)
    })
    public static final NumberSetting posY = new NumberSetting(0.1, 0, 1, "#.00");

    public TeammatesGlow() {
        registerSetting(onlyGame, info, posX, posY);
    }
    @EventTarget
    public void onRender(RenderEvent event) {
        if(mc.player == null || mc.level == null) return;
        if(onlyGame.getValue() && !PlayerUtils.isInHypZombies()) return;
        if(!info.getValue()) return;

        GuiGraphicsExtractor graphics = event.getGuiGraphicsExtractor();
        int maxNameWidth = 0;

        for (TeammateInfo ti : TeammateInfo.teammates) {
            String line = ti.getName() + " " + formatGold(ti.getGold()) + " (Blocking)";
            int nameWidth = mc.font.width(line);

            if (nameWidth > maxNameWidth) {
                maxNameWidth = nameWidth;
            }
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        double xPercent = posX.getValue().doubleValue();
        double yPercent = posY.getValue().doubleValue();

        int x = (int) (screenWidth * xPercent);
        int y = (int) (screenHeight * yPercent);

        int height = 28;

        for (TeammateInfo ti : TeammateInfo.teammates) {
            Player player = ti.getRenderEntity();

            boolean blocking = ti.isBlocking();
            boolean down = ti.isDown();

            String name = ti.getName()
                    + ChatFormatting.GOLD + " " + formatGold(ti.getGold())
                    + ChatFormatting.YELLOW + (blocking ? " (Blocking)" : "");

            int boxWidth = maxNameWidth + 42;
            GuiGraphicsUtils.drawBackground(graphics, x, y, boxWidth, height);

            if (player != null) {
                GuiGraphicsUtils.drawPlayerHead(graphics, player, x + 4, y + 4, 20);
            }

            graphics.text(mc.font, name, x + 28, y + 4, 0xFFFFFFFF, true);

            if (player != null) {
                float health = Math.max(0.0F, player.getHealth());
                float maxHealth = Math.max(1.0F, player.getMaxHealth());
                float percent = Math.max(0.0F, Math.min(1.0F, health / maxHealth));
                GuiGraphicsUtils.drawHealthBar(graphics, x + 27, y + 14, boxWidth - 30, 4, percent);

                int armor = player.getArmorValue();
                float armorPercent = Math.max(0.0F, Math.min(1.0F, armor / 20.0F));
                GuiGraphicsUtils.drawArmorBar(graphics, x + 27, y + 14 + 6, boxWidth - 30, 4, armorPercent);
            }

            //block
            if(down) {
                String str = "REVIVE";
                int strW = mc.font.width(str);
                graphics.fill(x, y, x + boxWidth, y + height, 0xAA111111);
                graphics.text(mc.font, "REVIVE", (int) (x + boxWidth / 2f - (strW / 2f)), y + 10, Color.GREEN.getRGB(), true);

            }

            y += height;
        }

    }

    private static String formatGold(long gold) {
        return String.format("%,d", gold);
    }


}
