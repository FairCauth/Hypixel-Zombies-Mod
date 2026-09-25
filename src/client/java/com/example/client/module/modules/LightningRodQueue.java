package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.events.EntityLoadEvent;
import com.example.client.events.PacketEvent;
import com.example.client.events.SkiaEvent;
import com.example.client.events.TickEvent;
import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.NumberSetting;
import com.example.client.skia.CanvasStack;
import com.example.client.skia.font.SkiaFont;
import com.example.client.skia.font.SkiaFonts;
import com.example.client.skia.render.LiquidGlassUi;
import com.example.client.skia.render.RenderUtils;
import com.example.client.utils.PlayerUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.world.entity.EntityType;

import java.util.Arrays;

@ModuleInfo(name = {
        @Text(label = "Lightning Rod Queue", language = Language.English),
        @Text(label = "LRod 队列", language = Language.Chinese)
}, enable = true)
public class LightningRodQueue extends AbstractModule {
    private static final int SLOT_COUNT = 4;
    private static final long COOLDOWN_MS = 20_000L;
    private static final long OUTSIDE_RESET_GRACE_MS = 3_000L;

    private static final float PANEL_WIDTH = 170F;
    private static final float PANEL_HEIGHT = 50F;
    private static final float PANEL_RADIUS = 13F;
    private static final float PANEL_PADDING = 7.5F;
    private static final float SLOT_GAP = 3.5F;
    private static final float SLOT_WIDTH =
            (PANEL_WIDTH - PANEL_PADDING * 2F - SLOT_GAP * (SLOT_COUNT - 1)) / SLOT_COUNT;
    private static final float SLOT_HEIGHT = 27F;

    private static final int READY_COLOR = 0xFF72F5A5;
    private static final int COOLDOWN_COLOR = 0xFF69BFFF;
    private static final int PARTIAL_COLOR = 0xFFFFCC73;
    private static final int PRIMARY_TEXT_COLOR = 0xFFF6FAFD;
    private static final int MUTED_TEXT_COLOR = 0xFFB8C3CC;
    private static final int SLOT_TRACK_COLOR = 0x3DFFFFFF;

    @SettingInfo(name = {
            @Text(label = "X", language = Language.English),
            @Text(label = "X", language = Language.Chinese)
    })
    public static final NumberSetting posX = new NumberSetting(0.50, 0, 1, "#.00");

    @SettingInfo(name = {
            @Text(label = "Y", language = Language.English),
            @Text(label = "Y", language = Language.Chinese)
    })
    public static final NumberSetting posY = new NumberSetting(0.10, 0, 1, "#.00");

    private final long[] cooldownEndMs = new long[SLOT_COUNT];
    private long lastZombiesSeenMs;

    public LightningRodQueue() {
        registerSetting(posX, posY);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        Packet<?> packet = event.getPacket();

        if (packet instanceof ClientboundLoginPacket) {
            mc.execute(this::resetQueue);
            return;
        }

    }

    @EventTarget
    public void onEntityLoad(EntityLoadEvent event) {
        if (event.getEntity().getType() != EntityType.LIGHTNING_BOLT) return;

        // Fabric 的实体加载事件在客户端线程触发，单人和多人使用同一检测路径。
        recordLightningStrike();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        long now = System.currentTimeMillis();
        if (isTrackingEnvironment()) {
            lastZombiesSeenMs = now;
            return;
        }

        if (lastZombiesSeenMs != 0L && now - lastZombiesSeenMs >= OUTSIDE_RESET_GRACE_MS) {
            resetQueue();
        }
    }

    @EventTarget
    public void onRenderSkia(SkiaEvent event) {
        if (mc.player == null || mc.level == null || !isTrackingEnvironment()) return;

        long now = System.currentTimeMillis();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        float x = Math.round(screenWidth * posX.getValue().doubleValue()) - PANEL_WIDTH * 0.5F;
        float y = Math.round(screenHeight * posY.getValue().doubleValue());

        drawPanel(event.getCanvasStack(), x, y, now);
    }

    private void recordLightningStrike() {
        if (mc.player == null || mc.level == null || !isTrackingEnvironment()) return;

        long now = System.currentTimeMillis();
        lastZombiesSeenMs = now;
        for (int slot = 0; slot < cooldownEndMs.length; slot++) {
            if (cooldownEndMs[slot] <= now) {
                cooldownEndMs[slot] = now + COOLDOWN_MS;
                return;
            }
        }
    }

    private void drawPanel(CanvasStack canvasStack, float x, float y, long now) {
        SkiaFont titleFont = SkiaFonts.getBoldFont(8);
        SkiaFont summaryFont = SkiaFonts.getBoldFont(7);
        SkiaFont slotFont = SkiaFonts.getBoldFont(6);
        SkiaFont indexFont = SkiaFonts.getDefaultFont(5);

        int readyCount = 0;
        for (long cooldownEnd : cooldownEndMs) {
            if (cooldownEnd <= now) readyCount++;
        }

        int summaryColor = readyCount == SLOT_COUNT
                ? READY_COLOR
                : readyCount == 0 ? COOLDOWN_COLOR : PARTIAL_COLOR;

        LiquidGlassUi.drawPanel(canvasStack, x, y, PANEL_WIDTH, PANEL_HEIGHT, PANEL_RADIUS);

        // 小型发光状态点代替原来的高饱和竖线。
        LiquidGlassUi.drawStatusLight(
                canvasStack,
                x + PANEL_PADDING, y + 5F,
                3F, 6F,
                summaryColor
        );
        titleFont.drawString(
                canvasStack,
                "LR QUEUE",
                x + PANEL_PADDING + 7F,
                y + 3.7F,
                PRIMARY_TEXT_COLOR
        );

        String readyText = readyCount + "/" + SLOT_COUNT + " READY";
        float pillWidth = summaryFont.getWidth(readyText) + 12F;
        float pillX = x + PANEL_WIDTH - PANEL_PADDING - pillWidth;
        LiquidGlassUi.drawPill(canvasStack, pillX, y + 3F, pillWidth, 10F, summaryColor);
        LiquidGlassUi.drawStatusLight(
                canvasStack,
                pillX + 3.5F, y + 6.5F,
                3F, 3F,
                summaryColor
        );
        summaryFont.drawString(
                canvasStack,
                readyText,
                pillX + 8F,
                y + 4F,
                summaryColor
        );

        float slotY = y + 16.5F;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            float slotX = x + PANEL_PADDING + slot * (SLOT_WIDTH + SLOT_GAP);
            drawSlot(canvasStack, slotFont, indexFont, slotX, slotY, slot, now);
        }
    }

    private void drawSlot(
            CanvasStack canvasStack,
            SkiaFont slotFont,
            SkiaFont indexFont,
            float x,
            float y,
            int slot,
            long now
    ) {
        long remainingMs = Math.max(0L, cooldownEndMs[slot] - now);
        boolean coolingDown = remainingMs > 0L;
        int stateColor = coolingDown ? COOLDOWN_COLOR : READY_COLOR;

        String status;
        float progress;
        if (coolingDown) {
            status = (remainingMs + 999L) / 1_000L + "s";
            progress = Math.clamp(remainingMs / (float) COOLDOWN_MS, 0F, 1F);
        } else {
            status = "READY";
            progress = 1F;
        }

        // 每个槽位是一块悬浮在主玻璃上的轻磨砂层，不再使用粗霓虹描边。
        LiquidGlassUi.drawSurface(
                canvasStack,
                x, y, SLOT_WIDTH, SLOT_HEIGHT, 6F,
                stateColor,
                coolingDown ? 0x11 : 0x18,
                coolingDown ? 0x58 : 0x70
        );

        String index = "#" + (slot + 1);
        indexFont.drawString(canvasStack, index, x + 4F, y + 2F, MUTED_TEXT_COLOR);

        LiquidGlassUi.drawStatusLight(
                canvasStack,
                x + SLOT_WIDTH - 6.5F, y + 3.5F,
                2.5F, 2.5F,
                stateColor
        );

        slotFont.drawString(
                canvasStack,
                status,
                x + (SLOT_WIDTH - slotFont.getWidth(status)) * 0.5F,
                y + 11F,
                coolingDown ? PRIMARY_TEXT_COLOR : stateColor
        );

        RenderUtils.drawRect(
                canvasStack, x + 4F, y + SLOT_HEIGHT - 4F,
                SLOT_WIDTH - 8F, 1.5F, 0.75F, SLOT_TRACK_COLOR
        );
        RenderUtils.drawRect(
                canvasStack, x + 4F, y + SLOT_HEIGHT - 4F,
                (SLOT_WIDTH - 8F) * progress, 1.5F, 0.75F, stateColor
        );
    }

    private void resetQueue() {
        Arrays.fill(cooldownEndMs, 0L);
        lastZombiesSeenMs = 0L;
    }

    /** 正式环境仅 Zombies；集成服务器用于单人指令测试闪电检测与 UI。 */
    private boolean isTrackingEnvironment() {
        return PlayerUtils.isInHypZombies() || mc.hasSingleplayerServer();
    }

    @Override
    protected void onDisable() {
        resetQueue();
    }
}
