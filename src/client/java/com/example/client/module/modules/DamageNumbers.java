package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.data.ZombiesGuns;
import com.example.client.events.ChatEvent;
import com.example.client.events.RenderEvent;
import com.example.client.events.TickEvent;
import com.example.client.language.Language;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.BooleanSetting;
import com.example.client.tracker.ServerTracker;
import com.example.client.utils.PlayerUtils;
import com.example.client.utils.ZombiesUtils;
import com.example.client.utils.render.GradientTextRenderer;
import com.example.client.utils.render.WorldToScreen;
import com.example.client.utils.record.HitResult;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 伤害飘字（类原神）：怪掉血时在它身上飘出掉血量，2D 投影到屏幕，带上浮+淡出+按伤害比例变色。
 * 伤害值 = 实体 getHealth() 的下降量（真实掉血），飘字位置 = 该实体头顶（世界坐标投影到屏幕）。
 */
@ModuleInfo(name = "module.damage_numbers", enable = false)
public class DamageNumbers extends AbstractModule {

    @SettingInfo(name = "setting.only_in_zombies")
    public static final BooleanSetting onlyGame = new BooleanSetting(true);

    @SettingInfo(name = "setting.show_damage")
    public static final BooleanSetting showDamage = new BooleanSetting(true);

    @SettingInfo(name = "setting.show_gold")
    public static final BooleanSetting showGold = new BooleanSetting(true);

    /** 自定义字体（assets/zombies-mod/font/damage.json → yuanshen.ttf）。 */
    private static final FontDescription FONT =
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("zombies-mod", "damage"));
    /** damage.json 中 TTF provider 的 size；不能使用默认字体固定的 lineHeight=9。 */
    static final int DAMAGE_FONT_HEIGHT = 16;


    /** 通用飘字：文字 + 颜色 + 初始放大，伤害和金币共用同一套动画。 */
    private static final class Num {
        final double x, y, z;     // 生成时的世界锚点
        final String text;        // 显示文字（"1,234" 或 "+50"）
        final Color topColor;     // 文字顶部颜色
        final Color bottomColor;  // 文字底部颜色；相同颜色表示不使用渐变
        final float startScale;   // 出现时放大倍数（暴击/非暴击不同）
        final long start;
        final float spread;       // 水平随机偏移（屏幕像素），避免连击重叠
        Num(double x, double y, double z, String text, Color topColor, Color bottomColor,
            float startScale, float spread) {
            this.x = x; this.y = y; this.z = z; this.text = text;
            this.topColor = topColor; this.bottomColor = bottomColor;
            this.startScale = startScale;
            this.start = System.currentTimeMillis(); this.spread = spread;
        }
    }

    private record GradientColors(Color top, Color bottom) { }

    private final Map<Integer, Float> lastHp = new HashMap<>();
    private final List<Num> nums = new ArrayList<>();

    public DamageNumbers() {
        registerSetting(onlyGame, showDamage, showGold);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (onlyGame.getValue() && !PlayerUtils.isInHypZombies()) {
            lastHp.clear();
            return;
        }

        // ===== 可热改参数 =====
        double trackRange = 64;   // 只统计离玩家多少格内的怪
        float minDrop = 0.01f;    // 掉血小于此忽略(去抖)
        float spreadPx = 18f;     // 连击左右随机散开像素(±)
        double headOffset = 1.2;  // 飘字锚点 = 头顶高度的倍数
        float dmgStartScale = 7.2f; // 伤害飘字出现时放大倍数
        // 伤害按掉血占最大血量比例变色：阈值 + 4 段颜色(少→多)
        float heatT1 = 0.05f, heatT2 = 0.15f, heatT3 = 0.30f;
        Color colorLow = new Color(255, 255, 255);
        Color colorMid = new Color(255, 240, 102);
        Color colorHigh = new Color(255, 170, 51);
        Color colorMax = new Color(255, 68, 68);
        // =====================

        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player) continue;
            if (e instanceof Player) continue;          // 玩家/假人不算
            if (e instanceof ArmorStand) continue;      // 名牌/holo 不算
            if (!(e instanceof LivingEntity le)) continue;
            if (le.distanceToSqr(mc.player) > trackRange * trackRange) continue;

            int id = e.getId();
            seen.add(id);

            float hp = le.getHealth();
            Float prev = lastHp.get(id);
            lastHp.put(id, hp);

            if (prev != null && hp < prev - minDrop && hp > 0.0f) {
                float drop = prev - hp;
                float maxHp = Math.max(1.0f, le.getMaxHealth());
                float heat = Math.max(0f, Math.min(1f, drop / maxHp));
                Color damageColor = heat < heatT1 ? colorLow
                        : heat < heatT2 ? colorMid
                        : heat < heatT3 ? colorHigh
                        : colorMax;
                double ax = le.getX();
                double ay = le.getY() + le.getBbHeight() * headOffset; // 头顶偏上
                double az = le.getZ();
                // 伤害飘字（可关）
                if (showDamage.getValue()) {
                    float spread = (float) ((Math.random() * 2 - 1) * spreadPx);
                    nums.add(new Num(ax, ay, az, String.format("%,d", Math.round(drop)),
                            damageColor, damageColor, dmgStartScale, spread));
                }
            }
        }
        lastHp.keySet().retainAll(seen);
    }

    @EventTarget
    public void onRender(RenderEvent event) {
        if (mc.player == null || mc.level == null || nums.isEmpty()) return;

        // ===== 可热改参数 =====
        long lifetime = 1200L;    // 飘字总时长(ms)
        float endScale = 1f;    // 收缩到的倍数（startScale 改成每个飘字自带）
        float scalePhase = 0.18f; // 前百分之多少时间用于收缩(0~1)
        double riseBlocks = 0.20; // 整个生命周期往上飘几格
        float fadeStart = 0.70f;  // 从百分之多少处开始渐隐(0~1)
        // =====================

        GuiGraphicsExtractor g = event.getGuiGraphicsExtractor();
        float partial = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        long now = System.currentTimeMillis();

        Iterator<Num> it = nums.iterator();
        while (it.hasNext()) {
            Num n = it.next();
            float t = (now - n.start) / (float) lifetime;
            if (t >= 1f) { it.remove(); continue; }

            // 小幅上移
            double rise = t * riseBlocks;
            WorldToScreen.ScreenPos pos = WorldToScreen.project(new Vec3(n.x, n.y + rise, n.z), partial);
            if (pos == null) continue; // 相机后方/太偏
            float sx = pos.x() + n.spread;
            float sy = pos.y();

            // 缩放：前 scalePhase 段从 n.startScale 缓出收到 endScale，之后保持
            float scale;
            if (t < scalePhase) {
                float k = t / scalePhase;
                float ease = 1f - (1f - k) * (1f - k); // easeOut
                scale = n.startScale - (n.startScale - endScale) * ease;
            } else {
                scale = endScale;
            }

            // 淡出：fadeStart 之后渐隐
            float alpha = t < fadeStart ? 1f : 1f - (t - fadeStart) / (1f - fadeStart);
            int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
            // 自定义字体的飘字（伤害或金币）
            Component comp = Component.literal(n.text)
                    .withStyle(st -> st.withFont(FONT));
            int w = mc.font.width(comp);
            int lh = DAMAGE_FONT_HEIGHT;

            // 以 (sx,sy) 为中心缩放，并按文字高度绘制上下渐变
            drawGradientText(g, comp, sx, sy, w, lh, scale, n.topColor, n.bottomColor, a);
        }
    }

    /** 金币飘字：聊天的 "+N" 飘在准星指向的怪身上。 */
    @EventTarget
    public void onChat(ChatEvent event) {
        if (!showGold.getValue()) return;
        if (mc.player == null || mc.level == null) return;
        if (onlyGame.getValue() && !PlayerUtils.isInHypZombies()) return;

        // ===== 可热改参数 =====
        double raycastDist = 64;        // 准星射线最远找几格内的怪
        double headOffset = 1.2;        // 锚点 = 怪头顶高度倍数
        double goldYOffset = 0.35;      // 再往上一点（在伤害字上方）
        float goldSpreadPx = 18f;       // 屏幕水平随机偏移（像素，±）
        double goldRandXZ = 0.9;        // 世界坐标水平随机半径（格，±）→ 在怪四周随机散开
        double goldRandY = 0.75;        // 世界坐标垂直随机（格，±）
        Color fallbackCritColor = new Color(255, 215, 0); // 无法识别武器时仍使用金色
        Color normalColor = new Color(255, 255, 255);
        float critStartScale = 8.5f;    // 暴击出现放大倍数
        float normalStartScale = 2.0f;  // 非暴击出现放大倍数
        // =====================

        String msg = ZombiesUtils.cleanChat(event.getComponent().getString());
        if (msg.contains(":") || !msg.startsWith("+")) return;

        // 聊天的 "+N" 本身就是实际到手的金额（双倍 buff 时已是翻倍后的）→ 直接显示
        int gold = ZombiesUtils.getGoldFromChat(msg);
        if (gold <= 0) return;

        // 准星指向的怪
        LivingEntity target = ServerTracker.shootTarget;//PlayerUtils.raycastTarget(ServerTracker.serverPlayer, raycastDist, TargetHud::isValidTarget);
        if (target == null) return;

        HitResult hit = event.getHitResult();
        boolean crit = hit != null ? hit.critical() : ZombiesUtils.isCritical(msg);
        GradientColors colors;
        if (hit != null && crit) {
            colors = criticalGradient(hit.gun(), hit.ultimateLevel());
        } else if (hit != null) {
            Color gunColor = colorForGun(hit.gun());
            colors = new GradientColors(gunColor, gunColor);
        } else {
            Color color = crit ? fallbackCritColor : normalColor;
            colors = new GradientColors(color, color);
        }
        float startScale = crit ? critStartScale : normalStartScale;

        double gx = target.getX() + (Math.random() * 2 - 1) * goldRandXZ;
        double gy = target.getY() + target.getBbHeight() * headOffset + goldYOffset
                + (Math.random() * 2 - 1) * goldRandY;
        double gz = target.getZ() + (Math.random() * 2 - 1) * goldRandXZ;
        float spread = (float) ((Math.random() * 2 - 1) * goldSpreadPx);
        nums.add(new Num(gx, gy, gz, "+" + String.format("%,d", gold),
                colors.top(), colors.bottom(), startScale, spread));
    }

    /** 未强化时为纯武器色；强化后由顶部武器原色渐变到底部白色。 */
    private static GradientColors criticalGradient(ZombiesGuns gun, int ultimateLevel) {
        Color gunColor = colorForGun(gun);
        Color bottomColor = ultimateLevel > 0
                ? new Color(255, 255, 255)
                : gunColor;
        return new GradientColors(gunColor, bottomColor);
    }

    private static Color colorForGun(ZombiesGuns gun) {
        return switch (gun) {
            case Pistol -> new Color(224, 224, 224);
            case Rifle -> new Color(124, 255, 107);
            case Rainbow_Rifle -> new Color(255, 235, 70);
            case Shotgun -> new Color(255, 100, 100);
            case Rocket_Launcher -> new Color(255, 77, 77);
            case Sniper -> new Color(85, 255, 255);
            case Flamethrower -> new Color(255, 106, 0);
            case Blow_Dart -> new Color(173, 255, 47);
            case Zombie_Soaker -> new Color(77, 166, 255);
            case Zombie_Zapper -> new Color(125, 210, 255);
            case Double_Barrel_Shotgun -> new Color(198, 134, 66);
            case Elder_Gun -> new Color(155, 89, 182);
            case Gold_Digger -> new Color(255, 205, 90);
        };
    }

    static void drawGradientText(GuiGraphicsExtractor graphics, Component text,
                                 float centerX, float centerY, int textWidth, int lineHeight,
                                 float scale, Color top, Color bottom, int alpha) {
        if (top.equals(bottom)) {
            drawText(graphics, text, centerX, centerY, textWidth, lineHeight, scale,
                    withAlpha(top, alpha), true);
            return;
        }

        int shadowAlpha = Math.round(alpha * 0.35F);
        drawText(graphics, text, centerX + scale, centerY + scale, textWidth, lineHeight, scale,
                new Color(0, 0, 0, shadowAlpha).getRGB(), false);

        float topY = centerY - lineHeight * scale / 2F;
        float bottomY = topY + lineHeight * scale;
        try (GradientTextRenderer.Scope ignored = GradientTextRenderer.push(top, bottom, topY, bottomY)) {
            drawText(graphics, text, centerX, centerY, textWidth, lineHeight, scale,
                    withAlpha(top, alpha), false);
        }
    }

    private static void drawText(GuiGraphicsExtractor graphics, Component text,
                                 float centerX, float centerY, int textWidth, int lineHeight,
                                 float scale, int color, boolean shadow) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().scale(scale, scale);
        graphics.text(mc.font, text, -textWidth / 2, -lineHeight / 2, color, shadow);
        graphics.pose().popMatrix();
    }

    private static int withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha).getRGB();
    }

    private static Color mix(Color first, Color second, float amount) {
        float t = Math.clamp(amount, 0F, 1F);
        return new Color(
                Math.round(first.getRed() + (second.getRed() - first.getRed()) * t),
                Math.round(first.getGreen() + (second.getGreen() - first.getGreen()) * t),
                Math.round(first.getBlue() + (second.getBlue() - first.getBlue()) * t)
        );
    }

}
