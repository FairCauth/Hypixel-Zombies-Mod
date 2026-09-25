package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.events.SkiaEvent;
import com.example.client.events.TickEvent;
import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.BooleanSetting;
import com.example.client.setting.settings.NumberSetting;
import com.example.client.skia.CanvasStack;
import com.example.client.skia.font.SkiaFont;
import com.example.client.skia.font.SkiaFonts;
import com.example.client.skia.render.LiquidGlassUi;
import com.example.client.skia.render.RenderUtils;
import com.example.client.tracker.TeammateInfo;
import com.example.client.tracker.TeammateTracker;
import com.example.client.utils.PlayerUtils;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@ModuleInfo(name = {
        @Text(label = "Revive Aura", language = Language.English),
        @Text(label = "自动救人", language = Language.Chinese)
}, enable = false)
public class ReviveAura extends AbstractModule {
    private static final long INTERRUPT_GRACE_MS = 650L;
    private static final double FR_PRIORITY_SECONDS = 1.5D;

    private static final float HUD_WIDTH = 156F;
    private static final float HUD_RADIUS = 12F;
    private static final float HUD_PADDING = 7F;
    private static final float HEADER_HEIGHT = 17F;
    private static final float ROW_HEIGHT = 22F;
    private static final float ROW_GAP = 3F;

    private static final int ACTIVE_COLOR = 0xFF72F5A5;
    private static final int PENDING_COLOR = 0xFF69DDF5;
    private static final int WARNING_COLOR = 0xFFFFCE69;
    private static final int START_COLOR = 0xFFFF6B78;
    private static final int TEXT_COLOR = 0xFFF6FAFD;
    private static final int MUTED_TEXT_COLOR = 0xFFB9C5CE;
    private static final int TRACK_COLOR = 0x3AFFFFFF;

    @SettingInfo(name = {
            @Text(label = "Range", language = Language.English),
            @Text(label = "距离", language = Language.Chinese)
    })
    public static final NumberSetting range = new NumberSetting(4.5D, 1.0D, 10.0D, "#.0");

    @SettingInfo(name = {
            @Text(label = "Revive Interval", language = Language.English),
            @Text(label = "救人间隔", language = Language.Chinese)
    })
    public static final NumberSetting interval = new NumberSetting(200, 50, 1000, "#");

    @SettingInfo(name = {
            @Text(label = "Skip Being Revived", language = Language.English),
            @Text(label = "跳过正在救援", language = Language.Chinese)
    })
    public static final BooleanSetting skipBeingRevived = new BooleanSetting(true);

    @SettingInfo(name = {
            @Text(label = "Priority", language = Language.English),
            @Text(label = "FR 优先", language = Language.Chinese)
    })
    public static final BooleanSetting priority = new BooleanSetting(true);

    private long lastReviveMs;
    /** 只保存本模块实际发过救援包的目标，不包含队友发起的救援。 */
    private final Map<String, OwnedRevive> ownedRevives = new LinkedHashMap<>();

    public ReviveAura() {
        registerSetting(range, interval, skipBeingRevived, priority);
    }

    @Override
    protected void onEnable() {
        lastReviveMs = 0L;
        // AbstractModule 会在子类字段初始化前调用生命周期方法。
        if (ownedRevives != null) ownedRevives.clear();
    }

    @Override
    protected void onDisable() {
        lastReviveMs = 0L;
        if (ownedRevives != null) ownedRevives.clear();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null
                || mc.level == null
                || !PlayerUtils.isInHypZombies()) {
            ownedRevives.clear();
            return;
        }

        long now = System.currentTimeMillis();
        updateOwnedRevives(now);

        if (mc.screen != null || mc.getConnection() == null) return;
        if (now - lastReviveMs < interval.getValue().longValue()) {
            return;
        }

        ReviveCandidate target = findClosestDownedPlayer(range.getValue().doubleValue());
        if (target == null) return;

        sendRevivePacket(target.player());
        OwnedRevive revive = ownedRevives.get(target.info().getName());
        if (revive == null) {
            ownedRevives.put(
                    target.info().getName(),
                    new OwnedRevive(target.info().getName(), target.player().getId())
            );
        }
        lastReviveMs = now;
    }

    private ReviveCandidate findClosestDownedPlayer(double distance) {
        double closestDistanceSq = distance * distance;
        ReviveCandidate closest = null;
        double closestFrDistanceSq = distance * distance;
        ReviveCandidate closestFr = null;
        double closestPendingDistanceSq = distance * distance;
        ReviveCandidate closestPending = null;

        for (Player candidate : mc.level.players()) {
            if (candidate == mc.player || !EasyRevive.isDown(candidate)) continue;

            // 睡觉姿态和假人实体通常早于计分板/TAB 的倒地状态到达。
            TeammateInfo info = TeammateTracker.getReviveCandidate(candidate);
            if (info == null || info.isTerminalState()) continue;

            double distanceSq = mc.player.distanceToSqr(candidate);
            if (distanceSq > distance * distance) continue;

            OwnedRevive owned = ownedRevives.get(info.getName());
            if (owned != null) {
                // 已确认出现救援倒计时后停止发包；未确认则优先持续重试同一个目标。
                if (owned.confirmed || info.isBeingRevived()
                        || TeammateTracker.isBeingRevivedAt(candidate)) continue;
                if (distanceSq <= closestPendingDistanceSq) {
                    closestPendingDistanceSq = distanceSq;
                    closestPending = new ReviveCandidate(candidate, info);
                }
                continue;
            }

            // 默认不抢已经由其他玩家开始的救援；关闭选项后则允许继续交互。
            if (skipBeingRevived.getValue()
                    && (info.isBeingRevived() || TeammateTracker.isBeingRevivedAt(candidate))) continue;

            if (priority.getValue()
                    && info.isFastReviveActive()
                    && info.getFastReviveSecondsLeft() <= FR_PRIORITY_SECONDS) {
                if (distanceSq <= closestFrDistanceSq) {
                    closestFrDistanceSq = distanceSq;
                    closestFr = new ReviveCandidate(candidate, info);
                }
                continue;
            }

            if (distanceSq <= closestDistanceSq) {
                closestDistanceSq = distanceSq;
                closest = new ReviveCandidate(candidate, info);
            }
        }
        if (closestPending != null) return closestPending;
        return closestFr != null ? closestFr : closest;
    }

    /**
     * tracker 没有提供“救援者是谁”的协议字段，因此以本机成功发包作为归属起点，
     * 再用 tracker 的 beingRevived 状态确认开始，并在完成/中断后自动移除。
     */
    private void updateOwnedRevives(long now) {
        Iterator<OwnedRevive> iterator = ownedRevives.values().iterator();
        while (iterator.hasNext()) {
            OwnedRevive revive = iterator.next();
            TeammateInfo info = TeammateTracker.get(revive.name);

            if (info == null || info.isTerminalState()) {
                iterator.remove();
                continue;
            }

            // 首次点击时计分板可能仍是 ALIVE；只要同一个睡觉假人还在，就保留待确认
            // 状态并按间隔重试。否则每帧清除会让 HUD 闪烁，还会丢失重试优先级。
            if (!info.isDown()) {
                if (!revive.confirmed
                        && mc.level.getEntity(revive.entityId) instanceof Player player
                        && EasyRevive.isDown(player)) continue;
                iterator.remove();
                continue;
            }

            if (info.isBeingRevived()) {
                double remaining = Math.max(0D, info.getReviveSeconds());
                if (!revive.confirmed) {
                    revive.confirmed = true;
                    revive.totalSeconds = remaining > 0D
                            ? Math.max(0.5D, Math.ceil(remaining * 2D) / 2D)
                            : 4D;
                }
                revive.remainingSeconds = remaining;
                revive.totalSeconds = Math.max(revive.totalSeconds, remaining);
                revive.lastConfirmedMs = now;
                continue;
            }

            // 尚未出现倒计时可能只是服务端延迟：保留为待确认状态，扫描器会按间隔持续发包。
            if (revive.confirmed && now - revive.lastConfirmedMs > INTERRUPT_GRACE_MS) {
                iterator.remove();
            }
        }
    }

    @EventTarget
    public void onRenderSkia(SkiaEvent event) {
        if (mc.player == null
                || mc.level == null
                || mc.screen != null
                || !PlayerUtils.isInHypZombies()
                || ownedRevives.isEmpty()) {
            return;
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int count = ownedRevives.size();
        float height = HEADER_HEIGHT + HUD_PADDING
                + count * ROW_HEIGHT
                + Math.max(0, count - 1) * ROW_GAP;
        float x = Math.round((screenWidth - HUD_WIDTH) * 0.5F);
        // 放在准星下方，仍处于屏幕中央，但不会遮住正在瞄准的倒地玩家。
        float y = Math.clamp(
                screenHeight * 0.5F + 18F,
                2F,
                Math.max(2F, screenHeight - height - 2F)
        );

        drawHud(event.getCanvasStack(), x, y, height);
    }

    private void drawHud(CanvasStack stack, float x, float y, float height) {
        SkiaFont titleFont = SkiaFonts.getBoldFont(7);
        SkiaFont smallFont = SkiaFonts.getDefaultFont(5);
        SkiaFont nameFont = SkiaFonts.getBoldFont(6);

        LiquidGlassUi.drawPanel(stack, x, y, HUD_WIDTH, height, HUD_RADIUS);
        LiquidGlassUi.drawStatusLight(stack, x + HUD_PADDING, y + 5F, 3F, 7F, ACTIVE_COLOR);
        titleFont.drawString(stack, "MY REVIVES", x + HUD_PADDING + 7F, y + 3.8F, TEXT_COLOR);

        String countText = ownedRevives.size() + " ACTIVE";
        float pillWidth = smallFont.getWidth(countText) + 11F;
        float pillX = x + HUD_WIDTH - HUD_PADDING - pillWidth;
        LiquidGlassUi.drawPill(stack, pillX, y + 3F, pillWidth, 10F, ACTIVE_COLOR);
        smallFont.drawString(stack, countText, pillX + 5.5F, y + 5F, ACTIVE_COLOR);

        float rowY = y + HEADER_HEIGHT;
        long animationNow = System.nanoTime();
        for (OwnedRevive revive : ownedRevives.values()) {
            drawReviveRow(stack, nameFont, smallFont, x + HUD_PADDING, rowY, revive, animationNow);
            rowY += ROW_HEIGHT + ROW_GAP;
        }
    }

    private void drawReviveRow(
            CanvasStack stack,
            SkiaFont nameFont,
            SkiaFont smallFont,
            float x,
            float y,
            OwnedRevive revive,
            long animationNow
    ) {
        float width = HUD_WIDTH - HUD_PADDING * 2F;
        float progress = revive.animatedProgress(animationNow);
        int color = revive.confirmed ? progressColor(progress) : PENDING_COLOR;
        LiquidGlassUi.drawSurface(stack, x, y, width, ROW_HEIGHT, 6F, color, 0x10, 0x42);

        LiquidGlassUi.drawStatusLight(stack, x + 4F, y + 4.5F, 3F, 3F, color);
        String status = revive.confirmed
                ? String.format(Locale.ROOT, "%.1fs", revive.remainingSeconds)
                : "LINK";
        float statusWidth = smallFont.getWidth(status);
        float nameMaxWidth = width - 18F - statusWidth;
        String name = fitText(nameFont, revive.name, nameMaxWidth);

        nameFont.drawString(stack, name, x + 10F, y + 3F, TEXT_COLOR);
        smallFont.drawString(stack, status, x + width - 4F - statusWidth, y + 4.2F,
                revive.confirmed ? ACTIVE_COLOR : MUTED_TEXT_COLOR);

        float barX = x + 4F;
        float barY = y + 15.7F;
        float barWidth = width - 8F;
        RenderUtils.drawRect(stack, barX, barY, barWidth, 2.4F, 1.2F, TRACK_COLOR);
        if (progress > 0F) {
            RenderUtils.drawShadow(
                    stack, barX, barY, barWidth * progress, 2.4F, 1.2F,
                    LiquidGlassUi.withAlpha(color, 0x58), 2F, 0F, 0F
            );
            RenderUtils.drawRect(stack, barX, barY, barWidth * progress, 2.4F, 1.2F, color);
        }
    }

    private static int progressColor(float progress) {
        float value = Math.clamp(progress, 0F, 1F);
        if (value < 0.5F) {
            return mixColor(START_COLOR, WARNING_COLOR, value * 2F);
        }
        return mixColor(WARNING_COLOR, ACTIVE_COLOR, (value - 0.5F) * 2F);
    }

    private static int mixColor(int from, int to, float amount) {
        float t = Math.clamp(amount, 0F, 1F);
        int alpha = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int red = Math.round(((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * t);
        int green = Math.round(((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * t);
        int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static String fitText(SkiaFont font, String text, float maxWidth) {
        if (font.getWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && font.getWidth(text.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }

    private void sendRevivePacket(Player player) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 relativeHit = player.getBoundingBox().clip(eye, player.position())
                .map(hit -> hit.subtract(player.getX(), player.getY(), player.getZ()))
                .orElse(Vec3.ZERO);

        mc.getConnection().send(new ServerboundInteractPacket(
                player.getId(),
                InteractionHand.MAIN_HAND,
                relativeHit,
                false
        ));
    }

    private record ReviveCandidate(Player player, TeammateInfo info) {
    }

    private static final class OwnedRevive {
        private final String name;
        private final int entityId;
        private long lastConfirmedMs;
        private boolean confirmed;
        private double totalSeconds = 4D;
        private double remainingSeconds = 4D;
        private float displayedProgress = 0.04F;
        private long lastAnimationNanos;

        private OwnedRevive(String name, int entityId) {
            this.name = name;
            this.entityId = entityId;
        }

        private float progress() {
            if (!confirmed || totalSeconds <= 0D) return 0.04F;
            return Math.clamp((float) (1D - remainingSeconds / totalSeconds), 0F, 1F);
        }

        private float animatedProgress(long nowNanos) {
            float target = progress();
            if (lastAnimationNanos == 0L) {
                lastAnimationNanos = nowNanos;
                displayedProgress = target;
                return displayedProgress;
            }

            float deltaSeconds = Math.clamp((nowNanos - lastAnimationNanos) / 1_000_000_000F, 0F, 0.1F);
            lastAnimationNanos = nowNanos;
            float blend = 1F - (float) Math.exp(-12F * deltaSeconds);
            displayedProgress += (target - displayedProgress) * blend;
            if (Math.abs(target - displayedProgress) < 0.001F) displayedProgress = target;
            return Math.clamp(displayedProgress, 0F, 1F);
        }
    }
}
