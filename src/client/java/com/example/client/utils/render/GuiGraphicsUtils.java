package com.example.client.utils.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

public class GuiGraphicsUtils {
    public static void drawBackground(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xAA111111);


        graphics.fill(x, y, x + width, y + 1, 0xFF444444);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF444444);
        graphics.fill(x, y, x + 1, y + height, 0xFF444444);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF444444);
    }
    public static void drawArmorBar(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            float percent
    ) {
        int filled = (int) (width * percent);

        graphics.fill(x, y, x + width, y + height, 0xFF333333);

        graphics.fill(x, y, x + filled, y + height, getArmorColor(percent));

        graphics.fill(x, y, x + width, y + 1, 0xFF000000);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF000000);
        graphics.fill(x, y, x + 1, y + height, 0xFF000000);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF000000);
    }
    public static void drawPlayerHead(GuiGraphicsExtractor graphics, Player player, int x, int y, int size) {
        if (graphics == null || player == null) {
            return;
        }

        if (!(player instanceof AbstractClientPlayer clientPlayer)) {
            return;
        }

        Identifier skin = clientPlayer.getSkin().body().texturePath();

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                skin,
                x,
                y,
                8.0F,
                8.0F,
                size,
                size,
                8,
                8,
                64,
                64,
                0xFFFFFFFF
        );

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                skin,
                x,
                y,
                40.0F,
                8.0F,
                size,
                size,
                8,
                8,
                64,
                64,
                0xFFFFFFFF
        );

    }
    public static int getArmorColor(float percent) {
        if (percent > 0.66F) {
            return 0xFF55AAFF;
        }

        if (percent > 0.33F) {
            return 0xFF55FFFF;
        }

        return 0xFFAAAAAA;
    }
    public static void drawHealthBar(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            float percent
    ) {
        int filled = (int) (width * percent);

        //血条背景
        graphics.fill(x, y, x + width, y + height, 0xFF333333);

        //血条颜色
        int color = getHealthColor(percent);
        graphics.fill(x, y, x + filled, y + height, color);

        //血条边框
        graphics.fill(x, y, x + width, y + 1, 0xFF000000);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF000000);
        graphics.fill(x, y, x + 1, y + height, 0xFF000000);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF000000);
    }
    private static int getHealthColor(float percent) {
        if (percent > 0.66F)
            return 0xFF55FF55; //绿
        if (percent > 0.33F)
            return 0xFFFFFF55; //黄
        return 0xFFFF5555; //红
    }
}
