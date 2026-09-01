package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.events.RenderEvent;
import com.example.client.events.SkiaEvent;
import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.BooleanSetting;
import com.example.client.setting.settings.NumberSetting;
import com.example.client.skia.CanvasStack;
import com.example.client.skia.fbo.GameFramebuffer;
import com.example.client.skia.font.SkiaFont;
import com.example.client.skia.font.SkiaFonts;
import com.example.client.skia.render.RenderUtils;
import com.example.client.tracker.TeammateInfo;
import com.example.client.utils.PlayerUtils;
import com.example.client.utils.render.GuiGraphicsUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import net.minecraft.world.entity.player.Player;

import java.awt.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @see com.example.client.mixin.MinecraftMixin
 */

@ModuleInfo(name = {
        @Text(label = "Teammates Glow", language = Language.English),
        @Text(label = "队友高亮显示", language = Language.Chinese)
}, enable = true)
public class TeammatesGlow extends AbstractModule {
    private static final long HP_TRAIL_HOLD_MS = 500L;
    private static final float HP_TRAIL_RETURN_PER_SECOND = 0.65F;

    /**
     * Skia HUD 视觉调试；完成布局后改为 false。
     */
    private static final boolean SKIA_DEBUG_TEAMMATES = false;
    private static final TeammateInfo[] SKIA_DEBUG_DATA = createSkiaDebugData();

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

    /**
     * 每个队友的血条动画状态（按名字）。
     */
    private static final Map<String, HpAnim> HP_ANIMS = new HashMap<>();

    private static final class HpAnim {
        float ghost;  // 残影值（掉血后缓慢回落到当前血量）
        float lastTarget;
        long lastMs;
        long holdUntil;
        boolean init;
    }
    private final AtomicInteger totalHeight = new AtomicInteger();
    private final AtomicInteger maxWidth = new AtomicInteger();
    @EventTarget
    public void onRenderSkia(SkiaEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (!SKIA_DEBUG_TEAMMATES && onlyGame.getValue() && !PlayerUtils.isInHypZombies()) return;
        if (!info.getValue()) return;

//        TeammateInfo[] trackedTeammates = TeammateInfo.teammates;
//        installSkiaDebugData();
        try {
            SkiaFont skiaFont = SkiaFonts.getDefaultFont(8);

            int maxNameWidth = 0;
            Set<String> currentNames = new HashSet<>();
            for (TeammateInfo ti : TeammateInfo.teammates) {
                currentNames.add(ti.getName());
                String line = ti.getName() + " " + formatGold(ti.getGold()) + " (Blocking)";
                int nameWidth = Math.round(skiaFont.getWidth(line));

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
            GameFramebuffer gameFramebuffer = event.getCustomLayer();
            int height = 28;

            CanvasStack canvasStack = event.getCanvasStack();
            TeammateInfo[] framebufferTeammates = TeammateInfo.teammates.clone();


            int finalMaxNameWidth = maxNameWidth;
            gameFramebuffer.setRenderer((graphics, deltaTracker) -> {
                int tWidth = 0;
                int tempHeight = 0;

                int tempY = (int) (screenHeight * yPercent);
                for (TeammateInfo ti : framebufferTeammates) {

                    Player player = ti.getRenderEntity();
                    int hpReserve = Math.round(skiaFont.getWidth("9999/9999"));
                    int fastReviveReserve = Math.round(skiaFont.getWidth("⚡5.0s")) + 4;
                    int boxWidth = finalMaxNameWidth + hpReserve + fastReviveReserve + 15;
                    if (boxWidth > tWidth) {
                        tWidth = boxWidth;
                    }
                    if (player != null) {
//                         System.out.println(player.getName());
                        GuiGraphicsUtils.drawPlayerHead(graphics, player, x + 3, tempY + 3, 22);
                    }
                    tempHeight += height;
                    tempY += height;
                }
                maxWidth.set(tWidth);
                totalHeight.set(tempHeight);
            });

//            System.out.println(totalHeight.get());
            //bg
            RenderUtils.drawShadow(canvasStack, x,y , maxWidth.get(), totalHeight.get(),5, Color.BLACK.getRGB());
            RenderUtils.drawBlur(canvasStack, x, y, maxWidth.get(), totalHeight.get(), 5, 15);
            RenderUtils.drawRect(canvasStack, x, y, maxWidth.get(), totalHeight.get(), 5, 0xB81A1E24);

            gameFramebuffer.render(canvasStack);
            for (TeammateInfo ti : TeammateInfo.teammates) {
                Player player = ti.getRenderEntity();

                boolean blocking = PlayerUtils.isPlayerBlockingHyp(player);
                boolean down = ti.isDown();
                boolean terminal = ti.isTerminalState();
                boolean fastReviveActive = !terminal && ti.isFastReviveActive();
                String fastReviveText = fastReviveActive
                        ? "⚡" + String.format(Locale.ROOT, "%.1f", ti.getFastReviveSecondsLeft()) + "s"
                        : "";

                String name = ti.getName()
                        + ChatFormatting.GOLD + " " + formatGold(ti.getGold())
                        + ChatFormatting.YELLOW + (blocking? " (Blocking)" : "");

                int hpReserve = Math.round(skiaFont.getWidth("9999/9999"));
                int fastReviveReserve = Math.round(skiaFont.getWidth("⚡5.0s")) + 15;
                int boxWidth = maxNameWidth + hpReserve + fastReviveReserve + 4;

                if (player != null) {

                    float health = Math.max(0.0F, player.getHealth());
                    float maxHealth = Math.max(1.0F, player.getMaxHealth());
                    float percent = Math.clamp(health / maxHealth, 0.0F, 1.0F);

                    skiaFont.drawShadowString(canvasStack, name, x + 28, y + 3, Color.WHITE.getRGB(), true);
                    String hp = (int) Math.ceil(health) + "/" + (int) Math.ceil(maxHealth);
                    int hpColor = percent > 0.5f ? 0xFF66FF66 : (percent > 0.25f ? 0xFFFFD633 : 0xFFFF5555);
                    skiaFont.drawShadowString(canvasStack, hp, x + boxWidth - skiaFont.getWidth(hp) - 6, y + 3, hpColor, true);

                    //health bar
                    float healthBarX = x + 28F;
                    float healthBarY = y + 16F;
                    float healthBarWidth = boxWidth - 28F - 6F;
                    float trailPercent = updateHpTrail(
                            percent,
                            HP_ANIMS.computeIfAbsent(ti.getName(), ignored -> new HpAnim())
                    );
                    RenderUtils.drawRect(canvasStack, healthBarX, healthBarY, healthBarWidth, 3F, 4F,
                            new Color(24, 24, 24, 180).getRGB());
                    float currentWidth = healthBarWidth * percent;
                    float trailWidth = healthBarWidth * trailPercent;
                    if (trailWidth - currentWidth > 0.25F) {
                        RenderUtils.drawRect(canvasStack, healthBarX + currentWidth, healthBarY,
                                trailWidth - currentWidth, 3F, 0F, 0xD9FFFFFF);
                    }
                    RenderUtils.drawRect(canvasStack, healthBarX, healthBarY, currentWidth, 3F, 4F,
                            GuiGraphicsUtils.getHealthColor(percent));

                    //armor bar
                    int armor = player.getArmorValue();
                    float armorPercent = Math.clamp(armor / 20.0F, 0.0F, 1.0F);
                    RenderUtils.drawRect(canvasStack, x + 28, y + 22, boxWidth - 28 - 6, 3, 4, new Color(24, 24, 24, 130).getRGB());
                    RenderUtils.drawRect(canvasStack, x + 28, y + 22, (boxWidth - 28 - 6) * armorPercent, 3, 4, GuiGraphicsUtils.getArmorColor(armorPercent));


                }
                if (terminal) {
                    String terminalText = ti.getStatusText().isBlank()
                            ? "DEAD"
                            : ti.getStatusText().toUpperCase(Locale.ROOT);
                    Color terminalColor = new Color(255, 85, 85);
                    float statusWidth = skiaFont.getWidth(terminalText);
                    RenderUtils.drawRect(canvasStack, x, y, boxWidth, height, 0, 0xAA111111);
//                   graphics.fill(x, y, x + boxWidth, y + height, 0xAA111111);
                    skiaFont.drawShadowString(canvasStack, terminalText, (x + boxWidth / 2F - statusWidth / 2F), y + 7, terminalColor.getRGB(), true);

                } else if (down) {
                    boolean reviving = ti.isBeingRevived();
                    String str = reviving
                            ? "REVIVING " + String.format("%.1f", ti.getReviveSeconds()) + "s"
                            : "REVIVE";

                    float strW = skiaFont.getWidth(str);
//                   graphics.fill(x, y, x + boxWidth, y + height, 0xAA111111);
                    RenderUtils.drawRect(canvasStack, x, y, boxWidth, height, 0, 0xAA111111);
                    skiaFont.drawShadowString(canvasStack, str, (x + boxWidth / 2f - (strW / 2f)), y + 7, reviving ? Color.cyan.getRGB() : Color.GREEN.getRGB(), true);
                }

                if (fastReviveActive) {
                    int hpReferenceWidth = (int) skiaFont.getWidth("20/20");
                    int timerRight = x + boxWidth - 12 - hpReferenceWidth - 4;
                    int timerX = (int) (timerRight - skiaFont.getWidth(fastReviveText));
                    skiaFont.drawShadowString(canvasStack, fastReviveText, timerX, y + 3,
                            new Color(255, 255, 85).getRGB(), true);
                }
//                skiaFont.drawString(canvasStack, player);
//                System.out.println("11111");

                y += height;
            }

            // 队友离开面板后移除其动画状态，避免名字复用时沿用旧残影。
            HP_ANIMS.keySet().retainAll(currentNames);

        } catch (Exception e) {
            e.printStackTrace();

        } finally {
            // 调试数据只服务 Skia 路径，不覆盖 TeammateTracker 的正式公开快照。
//            TeammateInfo.teammates = trackedTeammates;
        }

    }

    private static TeammateInfo[] createSkiaDebugData() {
        TeammateInfo alive = new TeammateInfo("DebugAlive");
        alive.setGold(12_345L);

        TeammateInfo down = new TeammateInfo("DebugDown");
        down.setGold(800L);
        down.applyScoreboardState(TeammateInfo.PlayerState.DOWN, "REVIVE");

        TeammateInfo reviving = new TeammateInfo("DebugReviving");
        reviving.setGold(6_666L);
        reviving.applyScoreboardState(TeammateInfo.PlayerState.DOWN, "REVIVING");
        reviving.setBeingRevived(true);
        reviving.setReviveSeconds(3.5D);

        TeammateInfo terminal = new TeammateInfo("DebugDead");
        terminal.setGold(0L);
        terminal.applyScoreboardState(TeammateInfo.PlayerState.TERMINAL, "DEAD");

        TeammateInfo fastRevive = new TeammateInfo("DebugFastRevive");
        fastRevive.setGold(99_999L);
        fastRevive.startFastRevive(5_000L);

        return new TeammateInfo[]{alive, down, reviving, terminal, fastRevive};
    }

    /**
     * 把所有典型状态注入公开快照，供正在开发的 Skia 面板直接遍历。
     */
    private static void installSkiaDebugData() {
        long now = System.currentTimeMillis();

        // 复用本地玩家实体，使头像、血量、护甲和 hurt overlay 都有实际数据可画。
        for (TeammateInfo info : SKIA_DEBUG_DATA) {
            info.setRenderEntity(mc.player);
        }

        // 救援倒计时循环变化，方便观察数字宽度和刷新效果。
        double reviveSeconds = Math.max(0.1D, (4_000L - now % 4_000L) / 1_000.0D);
        SKIA_DEBUG_DATA[2].setReviveSeconds(reviveSeconds);

        // 快速复活进度走完后重新开始，持续测试整行进度动画。
        if (!SKIA_DEBUG_DATA[4].isFastReviveActive()) {
            SKIA_DEBUG_DATA[4].startFastRevive(5_000L);
        }

        TeammateInfo.teammates = SKIA_DEBUG_DATA;
    }

    public TeammatesGlow() {
        registerSetting(onlyGame, info, posX, posY);
    }

//    @EventTarget
    public void onRender(RenderEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (onlyGame.getValue() && !PlayerUtils.isInHypZombies()) return;
        if (!info.getValue()) return;

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
            if (fastReviveActive) {
                // FR 冷却以黄色半透明底色覆盖整行；宽度随剩余冷却时间缩短。
                int progressWidth = Math.round(boxWidth * ti.getFastReviveProgress());
                if (progressWidth > 0) {
                    graphics.fill(x, y, x + progressWidth, y + height,
                            new Color(255, 215, 0, 58).getRGB());
                    // 底部再给一条更明亮的边，进度结束位置更容易一眼看出来。
                    graphics.fill(x, y + height - 2, x + progressWidth, y + height,
                            new Color(255, 225, 70, 185).getRGB());
                }
            }

            if (player != null) {
                GuiGraphicsUtils.drawPlayerHead(graphics, player, x + 4, y + 4, 20);
                if (player.hurtTime != 0) {
                    graphics.fill(x + 4, y + 4, x + 4 + 20, y + 4 + 20, new Color(255, 0, 0, 150).getRGB());

                }
                if (!down && player.isShiftKeyDown()) {
                    int bx = x + 4 + 20 - 9;   // 头像右下角
                    int by = y + 4 + 20 - 9;
                    graphics.fill(bx - 1, by - 1, bx + 10, by + 10, 0xFF0A0A0A); // 深色描边
                    graphics.fill(bx, by, bx + 9, by + 9, 0xFF1D9E75); // 青色底
                    graphics.fill(bx + 1, by + 2, bx + 8, by + 3, 0xFFFFFFFF); // ▼ 顶
                    graphics.fill(bx + 2, by + 3, bx + 7, by + 4, 0xFFFFFFFF);
                    graphics.fill(bx + 3, by + 4, bx + 6, by + 5, 0xFFFFFFFF);
                    graphics.fill(bx + 4, by + 5, bx + 5, by + 6, 0xFFFFFFFF); // 尖
                }
            }

            graphics.text(mc.font, name, x + 28, y + 4, 0xFFFFFFFF, true);

            if (player != null) {

                float health = Math.max(0.0F, player.getHealth());
                float maxHealth = Math.max(1.0F, player.getMaxHealth());
                float percent = Math.clamp(health / maxHealth, 0.0F, 1.0F);

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
                float armorPercent = Math.clamp(armor / 20.0F, 0.0F, 1.0F);
                GuiGraphicsUtils.drawArmorBar(graphics, x + 27, y + 14 + 6, boxWidth - 30, 4, armorPercent);
            }

            if (terminal) {
                String terminalText = ti.getStatusText().isBlank()
                        ? "DEAD"
                        : ti.getStatusText().toUpperCase(Locale.ROOT);
                Color terminalColor = new Color(255, 85, 85);
                int statusWidth = mc.font.width(terminalText);
                graphics.fill(x, y, x + boxWidth, y + height, 0xAA111111);
                graphics.text(mc.font, terminalText,
                        (int) (x + boxWidth / 2F - statusWidth / 2F), y + 10,
                        terminalColor.getRGB(), true);
            } else if (down) {
                boolean reviving = ti.isBeingRevived();
                String str = reviving
                        ? "REVIVING " + String.format("%.1f", ti.getReviveSeconds()) + "s"
                        : "REVIVE";
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
        float ghost = updateHpTrail(target, a);

        int curW = Math.round(w * Math.clamp(target, 0F, 1F));
        int ghostW = Math.round(w * ghost);
        if (ghostW > curW) {
            g.fill(x + curW, y, x + ghostW, y + h, 0xCCFFFFFF);
        }
    }

    private static float updateHpTrail(float target, HpAnim a) {
        target = Math.clamp(target, 0F, 1F);
        long now = System.currentTimeMillis();
        float dt = a.init ? Math.min(0.1F, (now - a.lastMs) / 1000F) : 0F;
        a.lastMs = now;
        if (!a.init) {
            a.ghost = target;
            a.lastTarget = target;
            a.holdUntil = now;
            a.init = true;
            return target;
        }

        if (target < a.lastTarget) {
            a.ghost = Math.max(a.ghost, a.lastTarget);
            a.holdUntil = now + HP_TRAIL_HOLD_MS;
        } else if (target > a.lastTarget) {
            // 回血时不显示反向残影。
            a.ghost = target;
            a.holdUntil = now;
        }

        if (now > a.holdUntil) {
            a.ghost = Math.max(target, a.ghost - HP_TRAIL_RETURN_PER_SECOND * dt);
        }

        a.ghost = Math.max(target, Math.clamp(a.ghost, 0F, 1F));
        a.lastTarget = target;
        return a.ghost;
    }

    private static String formatGold(long gold) {
        return String.format("%,d", gold);
    }


}
