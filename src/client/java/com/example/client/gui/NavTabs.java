package com.example.client.gui;

import com.example.client.config.ZombiesConfig;
import com.example.client.language.GuiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * MC 统计界面那种"标签页"样式的顶部导航栏（自绘，非按钮）。四个屏幕共用：
 *   0 Features(模块) · 1 Guns Config · 2 Name Protect · 3 Stats Query
 *
 * 用法：屏幕在 extractRenderState 里调 NavTabs.draw(...)，在 mouseClicked 里调 NavTabs.hit(...) → open(...)。
 */
public final class NavTabs {
    private NavTabs() {}

    private static final String[] LABEL_KEYS = {"gui.features", "gui.guns_config", "gui.name_protect", "gui.stats_query"};

    public static final int Y = 22;
    public static final int H = 20;

    private static int[] bounds(int screenW) {
        int total = Math.min(screenW - 40, 540);
        int x0 = (screenW - total) / 2;
        return new int[]{x0, total};
    }

    public static void draw(GuiGraphicsExtractor g, Font font, int screenW, int active) {
        int[] b = bounds(screenW);
        int x0 = b[0], total = b[1];
        int n = LABEL_KEYS.length;
        int tabW = total / n;
        int yBot = Y + H;

        for (int i = 0; i < n; i++) {
            int x = x0 + i * tabW;
            int xe = (i == n - 1) ? x0 + total : x + tabW;
            boolean act = i == active;

            g.fill(x, Y, xe, yBot, act ? 0xFF34343C : 0xFF18181C);

            int bd = act ? 0xFFDDDDDD : 0xFF4A4A52;
            g.fill(x, Y, xe, Y + 1, bd);          // 上
            g.fill(x, Y, x + 1, yBot, bd);         // 左
            g.fill(xe - 1, Y, xe, yBot, bd);       // 右
            if (!act) g.fill(x, yBot - 1, xe, yBot, bd); // 未选中：下边封口

            int col = act ? 0xFFFFFFFF : 0xFFB0B0B0;
            Component text = GuiText.text(LABEL_KEYS[i]);
            g.text(font, text, x + (xe - x) / 2 - font.width(text) / 2, Y + 6, col, true);
        }

        // 内容顶部分隔线，在选中标签处断开 → 选中页和内容"连体"
        int actX = x0 + active * tabW;
        int actXe = (active == n - 1) ? x0 + total : actX + tabW;
        g.fill(x0, yBot - 1, actX + 1, yBot, 0xFFDDDDDD);
        g.fill(actXe - 1, yBot - 1, x0 + total, yBot, 0xFFDDDDDD);
    }

    /** 命中哪个标签；-1 = 没点到标签。 */
    public static int hit(int screenW, double mouseX, double mouseY) {
        if (mouseY < Y || mouseY > Y + H) return -1;
        int[] b = bounds(screenW);
        int x0 = b[0], total = b[1];
        if (mouseX < x0 || mouseX > x0 + total) return -1;
        int n = LABEL_KEYS.length;
        int tabW = total / n;
        int i = (int) ((mouseX - x0) / tabW);
        return Math.min(i, n - 1);
    }

    /** 切到某个标签对应的屏幕（保持同一 parent）。 */
    public static void open(int index, Screen parent) {
        ZombiesConfig.save();
        Minecraft mc = Minecraft.getInstance();
        switch (index) {
            case 0 -> {
                ZombiesConfigScreen s = ZombiesConfigScreen.instance;
                if (s == null) s = new ZombiesConfigScreen(parent);
                mc.gui.setScreen(s);
            }
            case 1 -> {
                if (AutoSwitchWeaponScreen.instance == null) {
                    AutoSwitchWeaponScreen.instance = new AutoSwitchWeaponScreen(parent);
                }
                mc.gui.setScreen(AutoSwitchWeaponScreen.instance);
            }
            case 2 -> mc.gui.setScreen(new NameProtectScreen(parent));
            case 3 -> mc.gui.setScreen(new StatsQueryScreen(parent));
        }
    }
}
