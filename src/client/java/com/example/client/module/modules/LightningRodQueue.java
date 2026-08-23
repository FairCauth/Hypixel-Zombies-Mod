package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.events.EntityLoadEvent;
import com.example.client.events.PacketEvent;
import com.example.client.events.RenderEvent;
import com.example.client.events.TickEvent;
import com.example.client.language.Language;
import com.example.client.language.GuiText;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.NumberSetting;
import com.example.client.utils.PlayerUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.awt.Color;
import java.util.Arrays;

@ModuleInfo(name = "module.lightning_rod_queue", enable = true)
public class LightningRodQueue extends AbstractModule {
    private static final int SLOT_COUNT = 4;
    private static final long COOLDOWN_MS = 20_000L;
    private static final long OUTSIDE_RESET_GRACE_MS = 3_000L;

    private static final int TILE_WIDTH = 26;
    private static final int TILE_HEIGHT = 34;
    private static final int TILE_GAP = 3;
    private static final int PROGRESS_HEIGHT = 2;

    private static final Color BACKGROUND = new Color(13, 17, 23, 190);
    private static final Color READY_BORDER = new Color(70, 220, 120, 255);
    private static final Color COOLDOWN_BORDER = new Color(65, 165, 255, 255);
    private static final Color READY_TEXT = new Color(100, 255, 145, 255);
    private static final Color COOLDOWN_TEXT = new Color(235, 245, 255, 255);
    private static final Color SLOT_TEXT = new Color(175, 190, 205, 255);
    private static final Color COOLDOWN_OVERLAY = new Color(5, 8, 13, 155);
    private static final Color READY_PROGRESS = new Color(70, 220, 120, 255);
    private static final Color COOLDOWN_PROGRESS = new Color(55, 180, 255, 255);

    @SettingInfo(name = "setting.x")
    public static final NumberSetting posX = new NumberSetting(0.50, 0, 1, "#.00");

    @SettingInfo(name = "setting.y")
    public static final NumberSetting posY = new NumberSetting(0.10, 0, 1, "#.00");

    private final long[] cooldownEndMs = new long[SLOT_COUNT];
    private long lastZombiesSeenMs;
    private ItemStack lightningRodIcon;
    private ItemStack cooldownIcon;

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
        if (!(event.getEntity() instanceof LightningBolt)) return;

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
    public void onRender(RenderEvent event) {
        if (mc.player == null || mc.level == null || !isTrackingEnvironment()) return;

        long now = System.currentTimeMillis();
        int totalWidth = SLOT_COUNT * TILE_WIDTH + (SLOT_COUNT - 1) * TILE_GAP;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int startX = (int) Math.round(screenWidth * posX.getValue().doubleValue()) - totalWidth / 2;
        int y = (int) Math.round(screenHeight * posY.getValue().doubleValue());

        GuiGraphicsExtractor graphics = event.getGuiGraphicsExtractor();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            int x = startX + slot * (TILE_WIDTH + TILE_GAP);
            drawSlot(graphics, x, y, slot, now);
        }
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

    private void drawSlot(GuiGraphicsExtractor graphics, int x, int y, int slot, long now) {
        long remainingMs = Math.max(0L, cooldownEndMs[slot] - now);
        boolean coolingDown = remainingMs > 0L;
        Color border = coolingDown ? COOLDOWN_BORDER : READY_BORDER;

        graphics.fill(x, y, x + TILE_WIDTH, y + TILE_HEIGHT, BACKGROUND.getRGB());
        graphics.outline(x, y, TILE_WIDTH, TILE_HEIGHT, border.getRGB());
        graphics.item(getSlotIcon(coolingDown), x + (TILE_WIDTH - 16) / 2, y + 2);

        if (coolingDown) {
            graphics.fill(x + 4, y + 2, x + TILE_WIDTH - 4, y + 20, COOLDOWN_OVERLAY.getRGB());
        }

        String slotLabel = Integer.toString(slot + 1);
        graphics.text(mc.font, slotLabel, x + 2, y + 2, SLOT_TEXT.getRGB(), true);

        String status;
        Color statusColor;
        float progress;
        if (coolingDown) {
            status = Long.toString((remainingMs + 999L) / 1_000L);
            statusColor = COOLDOWN_TEXT;
            progress = Math.clamp(remainingMs / (float) COOLDOWN_MS, 0F, 1F);
        } else {
            status = GuiText.textString("hud.ready");
            statusColor = READY_TEXT;
            progress = 1F;
        }

        int textX = x + (TILE_WIDTH - mc.font.width(status)) / 2;
        graphics.text(mc.font, status, textX, y + 21, statusColor.getRGB(), true);

        int innerWidth = TILE_WIDTH - 2;
        int progressWidth = Math.round(innerWidth * progress);
        Color progressColor = coolingDown ? COOLDOWN_PROGRESS : READY_PROGRESS;
        graphics.fill(
                x + 1,
                y + TILE_HEIGHT - PROGRESS_HEIGHT - 1,
                x + 1 + progressWidth,
                y + TILE_HEIGHT - 1,
                progressColor.getRGB()
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

    /** ItemStack 依赖已绑定的物品组件，不能在客户端入口初始化阶段静态创建。 */
    private ItemStack getSlotIcon(boolean coolingDown) {
        if (coolingDown) {
            if (cooldownIcon == null) {
                cooldownIcon = new ItemStack(Items.DYE.gray());
            }
            return cooldownIcon;
        }

        if (lightningRodIcon == null) {
            lightningRodIcon = new ItemStack(Items.BLAZE_ROD);
        }
        return lightningRodIcon;
    }

    @Override
    protected void onDisable() {
        resetQueue();
    }
}
