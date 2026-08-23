package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.events.RenderEvent;
import com.example.client.language.Language;
import com.example.client.language.GuiText;
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
import net.minecraft.network.chat.Component;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @see com.example.client.mixin.MinecraftMixin
 */

@ModuleInfo(name = "module.teammates_glow", enable = true)
public class TeammatesGlow extends AbstractModule {
    @SettingInfo(name = "setting.only_in_zombies")
    public static final BooleanSetting onlyGame = new BooleanSetting(true);
    @SettingInfo(name = "setting.info")
    public static final BooleanSetting info = new BooleanSetting(true);
    @SettingInfo(name = "setting.x")
    public static final NumberSetting posX = new NumberSetting(0.1, 0, 1, "#.00");
    @SettingInfo(name = "setting.y")
    public static final NumberSetting posY = new NumberSetting(0.1, 0, 1, "#.00");

    /** 每个队友的血条动画状态（按名字）。 */
    private static final Map<String, HpAnim> HP_ANIMS = new HashMap<>();

    private static final class HpAnim {
        float ghost;  // 残影值（掉血后缓慢回落到当前血量）
        long lastMs;
        boolean init;
    }

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

        Set<String> currentNames = new HashSet<>();
        for (TeammateInfo ti : TeammateInfo.teammates) {
            currentNames.add(ti.getName());
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

            boolean blocking = player != null && PlayerUtils.isPlayerBlockingHyp(player);
            boolean down = ti.isDown();
            boolean terminal = ti.isTerminalState();
            boolean fastReviveActive = !terminal && ti.isFastReviveActive();
            String fastReviveText = fastReviveActive
                    ? "⚡" + String.format(Locale.ROOT, "%.1f", ti.getFastReviveSecondsLeft()) + "s"
                    : "";

            String name = ti.getName()
                    + ChatFormatting.GOLD + " " + formatGold(ti.getGold())
                    + ChatFormatting.YELLOW + (blocking ? " (Blocking)" : "");

            int hpReserve = mc.font.width("9999/9999");
            int fastReviveReserve = mc.font.width("⚡5.0s") + 4;
            int boxWidth = maxNameWidth + 32 + hpReserve + fastReviveReserve;
            GuiGraphicsUtils.drawBackground(graphics, x, y, boxWidth, height);

            if (player != null) {
                GuiGraphicsUtils.drawPlayerHead(graphics, player, x + 4, y + 4, 20);
                if (player.hurtTime != 0) {
                    graphics.fill(x + 4, y + 4, x + 4 + 20, y + 4 + 20, new Color(255,0,0,150).getRGB());

                }
                if (!down && player.isShiftKeyDown()) {
                    int bx = x + 4 + 20 - 9;   // 头像右下角
                    int by = y + 4 + 20 - 9;
                    graphics.fill(bx - 1, by - 1, bx + 10, by + 10, 0xFF0A0A0A); // 深色描边
                    graphics.fill(bx,     by,     bx + 9,  by + 9,  0xFF1D9E75); // 青色底
                    graphics.fill(bx + 1, by + 2, bx + 8,  by + 3,  0xFFFFFFFF); // ▼ 顶
                    graphics.fill(bx + 2, by + 3, bx + 7,  by + 4,  0xFFFFFFFF);
                    graphics.fill(bx + 3, by + 4, bx + 6,  by + 5,  0xFFFFFFFF);
                    graphics.fill(bx + 4, by + 5, bx + 5,  by + 6,  0xFFFFFFFF); // 尖
                }
            }

            graphics.text(mc.font, name, x + 28, y + 4, 0xFFFFFFFF, true);

            if (player != null) {

                float health = Math.max(0.0F, player.getHealth());
                float maxHealth = Math.max(1.0F, player.getMaxHealth());
                float percent = Math.max(0.0F, Math.min(1.0F, health / maxHealth));

                // 当前/最大生命值，右对齐在名字行；按血量比例上色
                String hp = (int) Math.ceil(health) + "/" + (int) Math.ceil(maxHealth);
                int hpColor = percent > 0.5f ? 0xFF66FF66 : (percent > 0.25f ? 0xFFFFD633 : 0xFFFF5555);
                graphics.text(mc.font, hp, x + boxWidth - mc.font.width(hp) - 6, y + 4, hpColor, true);

                // 第一层：原样血条（不变、无动画）
                GuiGraphicsUtils.drawHealthBar(graphics, x + 27, y + 14, boxWidth - 30, 4, percent);
                // 第二层：单独渲染掉血拖尾
                HpAnim anim = HP_ANIMS.computeIfAbsent(ti.getName(), k -> new HpAnim());
                drawHpTrail(graphics, x + 27, y + 14 + 1, boxWidth - 30, 2, percent, anim);

                int armor = player.getArmorValue();
                float armorPercent = Math.max(0.0F, Math.min(1.0F, armor / 20.0F));
                GuiGraphicsUtils.drawArmorBar(graphics, x + 27, y + 14 + 6, boxWidth - 30, 4, armorPercent);
            }

            if (terminal) {
                String terminalText = ti.getStatusText().isBlank()
                        ? GuiText.textString("hud.dead")
                        : ti.getStatusText().toUpperCase(Locale.ROOT);
                Color terminalColor = new Color(255, 85, 85);
                int statusWidth = mc.font.width(terminalText);
                graphics.fill(x, y, x + boxWidth, y + height, 0xAA111111);
                graphics.text(mc.font, terminalText,
                        (int) (x + boxWidth / 2F - statusWidth / 2F), y + 10,
                        terminalColor.getRGB(), true);
            } else if(down) {
                boolean reviving = ti.isBeingRevived();
                String str = reviving
                        ? GuiText.textString("hud.reviving", String.format("%.1f", ti.getReviveSeconds()))
                        : GuiText.textString("hud.revive");
                Component status = Component.literal(str)
                        .withStyle(reviving ? ChatFormatting.AQUA : ChatFormatting.GREEN);
                int strW = mc.font.width(status);
                graphics.fill(x, y, x + boxWidth, y + height, 0xAA111111);
                graphics.text(mc.font, status, (int) (x + boxWidth / 2f - (strW / 2f)),
                        y + 10, Color.WHITE.getRGB(), true);
            }

            // 固定在右侧血量预留区左边；倒地时也保持同一位置，终止状态不显示。
            if (fastReviveActive) {
                int hpReferenceWidth = mc.font.width("20/20");
                int timerRight = x + boxWidth - 6 - hpReferenceWidth - 4;
                int timerX = timerRight - mc.font.width(fastReviveText);
                graphics.text(mc.font, fastReviveText, timerX, y + 4,
                        new Color(255, 255, 85).getRGB(), true);
            }

            y += height;
        }

        // 清理已不在面板里的队友的动画状态，防止泄漏/复用串味
        HP_ANIMS.keySet().retainAll(currentNames);
    }

    /**
     * 第二层：只画掉血拖尾。主血条（第一层）保持当前真实血量 target，
     * 拖尾从 target 处向右延伸到 ghost（上一次较高的血量），随时间回落到 target；回血时直接跟上。
     * 不画背景/主条，避免覆盖第一层。
     */
    private static void drawHpTrail(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                    float target, HpAnim a) {
        long now = System.currentTimeMillis();
        float dt = a.init ? Math.min(0.1f, (now - a.lastMs) / 1000f) : 0f; // 限制 dt，防卡顿大跳
        a.lastMs = now;
        if (!a.init) { a.ghost = target; a.init = true; }

        if (target < a.ghost) {
            a.ghost -= dt * 0.9f;                 // 掉血：拖尾每秒回落 0.9
            if (a.ghost < target) a.ghost = target;
        } else {
            a.ghost = target;                     // 回血：直接跟上
        }

        int curW = Math.round(w * Math.max(0f, Math.min(1f, target)));
        int ghostW = Math.round(w * Math.max(0f, Math.min(1f, a.ghost)));
        if (ghostW > curW) {
            g.fill(x + curW, y, x + ghostW, y + h, 0xCCFFFFFF); // 刚掉的血：白色拖尾（半透明叠在第一层空区上）
        }
    }

    private static String formatGold(long gold) {
        return String.format("%,d", gold);
    }


}
