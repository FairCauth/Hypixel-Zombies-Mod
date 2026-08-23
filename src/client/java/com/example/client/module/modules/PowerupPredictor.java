package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.data.PowerupPredictor.Type;          // 直接导入嵌套枚举，避开与本模块同名的冲突
import com.example.client.events.RenderEvent;
import com.example.client.language.Language;
import com.example.client.language.GuiText;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.NumberSetting;
import com.example.client.tracker.ServerTracker;
import com.example.client.utils.PlayerUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;

@ModuleInfo(name = "module.powerup_predictor", enable = true)
public class PowerupPredictor extends AbstractModule {


    @SettingInfo(name = "setting.x")
    public static final NumberSetting posX = new NumberSetting(0.01, 0, 1, "#.00");

    @SettingInfo(name = "setting.y")
    public static final NumberSetting posY = new NumberSetting(0.45, 0, 1, "#.00");

    /** 每个道具往后列几个掉落回合 */
        @SettingInfo(name = "setting.count")
    public static final NumberSetting count = new NumberSetting(3, 1, 8, "#");

    public PowerupPredictor() {
        registerSetting(posX, posY, count);
    }

    @EventTarget
    public void onRender(RenderEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (!PlayerUtils.isInHypZombies()) return;

        var pred = ServerTracker.powerup.getPredictor();
        int cur = ServerTracker.currentRound;

        GuiGraphicsExtractor g = event.getGuiGraphicsExtractor();
        int x = (int) (mc.getWindow().getGuiScaledWidth() * posX.getValue().doubleValue());
        int y = (int) (mc.getWindow().getGuiScaledHeight() * posY.getValue().doubleValue());
        int lineHeight = 11;
        int show = count.getValue().intValue();

        g.text(mc.font, GuiText.text("hud.powerups"), x, y, 0xFFFFFFFF, true);
        y += lineHeight + 2;

        for (Type t : Type.values()) {
            String rounds;
            if (!pred.isLocked(t)) {
                rounds = "?";
            } else {
                StringBuilder sb = new StringBuilder();
                if (pred.isPowerupRound(t, cur)) sb.append(GuiText.textString("hud.now")).append(' ');
                int r = cur;
                for (int i = 0; i < show; i++) {
                    int n = pred.nextRound(t, r);
                    if (n < 0) break;
                    sb.append(GuiText.textString("hud.round")).append(n).append(' ');
                    r = n;
                }
                rounds = sb.toString().trim();
                if (rounds.isEmpty()) rounds = "-"; // 已锁定但后面没有了
            }

            g.text(mc.font, label(t), x, y, color(t), true);          // 道具名（按色）
            g.text(mc.font, rounds, x + 54, y, 0xFFFFFFFF, true);     // 掉落回合
            y += lineHeight;
        }
    }

    private static String label(Type t) {
        return switch (t) {
            case INSTA -> GuiText.textString("hud.powerup.insta");
            case MAX -> GuiText.textString("hud.powerup.max");
            case SS -> GuiText.textString("hud.powerup.ss");
        };
    }

    private static int color(Type t) {
        return switch (t) {
            case INSTA -> 0xFFFF5555; // 红
            case MAX -> 0xFF5555FF;   // 蓝
            case SS -> 0xFFAA00AA;    // 紫
        };
    }
}
