package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.events.RenderEvent;
import com.example.client.events.SkiaEvent;
import com.example.client.events.TickEvent;
import com.example.client.language.Language;
import com.example.client.language.Text;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.settings.ModeSetting;
import com.example.client.setting.settings.NumberSetting;
import com.example.client.skia.CanvasStack;
import com.example.client.skia.render.RenderUtils;
import com.example.client.tracker.ServerTracker;
import com.example.client.utils.PlayerUtils;
import com.example.client.utils.render.BlurRenderer;
import com.example.client.utils.render.GuiGraphicsUtils;
import com.example.client.utils.render.WorldToScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.lang.reflect.Array;
import java.util.Arrays;

@ModuleInfo(name = {
        @Text(label = "Target Hud", language = Language.English),
        @Text(label = "目标Hud显示", language = Language.Chinese)
}, enable = true)
public class TargetHud extends AbstractModule {
    private static final long TARGET_LOST_DELAY_MS = 2_000L;
    private static final long HEALTH_TRAIL_HOLD_MS = 1_200L;
    private static final float HEALTH_TRAIL_RETURN_PER_SECOND = 0.45F;
    private static final Color NORMAL_BACKGROUND = new Color(17, 17, 17, 170);
    private static final Color NORMAL_BORDER = new Color(68, 68, 68, 255);
    private static final Color BAD_HEADSHOT_BACKGROUND = new Color(120, 16, 16, 190);
    private static final Color BAD_HEADSHOT_BORDER = new Color(255, 85, 85, 255);

    private enum ApexArmorTier {
        WHITE(2, 0xFFF2F2F2, 1F / 3F),
        BLUE(3, 0xFF4388F4, 3F / 7F),
        PURPLE(4, 0xFFB64CF2, 0.5F),
        GOLD(5, 0xFFFFB52E, 0.5F);

        final int segments;
        final int color;
        final float shieldShare;

        ApexArmorTier(int segments, int color, float shieldShare) {
            this.segments = segments;
            this.color = color;
            this.shieldShare = shieldShare;
        }
    }

    @SettingInfo(name = {
            @Text(label = "Distance", language = Language.English),
            @Text(label = "距离", language = Language.Chinese)
    })
    private final NumberSetting distance = new NumberSetting(35, 1, 50, "#");

    @SettingInfo(name = {
            @Text(label = "Mode", language = Language.English),
            @Text(label = "模式", language = Language.Chinese)
    })
    private final ModeSetting mode = new ModeSetting("Default", Arrays.asList("Default", "Apex"));

    public TargetHud() {
        registerSetting(distance, mode);
    }
    private int raycastTimer = 0;
    private LivingEntity target = null;
    private long lastTargetSeenAt;
    private int healthTrailTargetId = Integer.MIN_VALUE;
    private float lastHealthProgress;
    private float healthTrailProgress;
    private long healthTrailHoldUntil;
    private long healthTrailLastUpdate;
    @EventTarget
    public void onTick(TickEvent event) {
        if (++raycastTimer < 3) {
            return;
        }
        raycastTimer = 0;
//        System.out.println(ServerTracker.serverPlayer.xRot() + " " + ServerTracker.serverPlayer.yRot());
        LivingEntity raycastTarget = PlayerUtils.raycastTarget(
                ServerTracker.serverPlayer,
                distance.getValue().doubleValue(),
                TargetHud::isValidTarget
        );
        long now = System.currentTimeMillis();

        if (raycastTarget != null) {
            // 新目标无需等待，立即替换当前 HUD。
            target = raycastTarget;
            lastTargetSeenAt = now;
        } else if (target != null) {
            // 目标死亡/失效立即清除；只是准心移开则延迟消失。
            if (!target.isAlive() || now - lastTargetSeenAt >= TARGET_LOST_DELAY_MS) {
                target = null;
            }
        }
    }

    @EventTarget
    public void onRenderSkia(SkiaEvent event) {
        if (target == null || !mode.is("Apex")) return;

        float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        WorldToScreen.ScreenPos pos = WorldToScreen.projectEntity(target, partialTicks);
        if (pos == null) return;

        // ScreenPos 和当前 Skia Canvas 都使用 GUI 缩放后的坐标，直接使用即可。
        float width = 150F;
        float height = 18F;
        float x = pos.x() - width * 0.5F;
        float y = pos.y() - height - 6F;

        CanvasStack canvasStack = event.getCanvasStack();
        boolean badHeadshot = BadHeadshot.isBadHeadshotEntity(target);
        int backgroundColor = badHeadshot ? new Color(222,0,0,140).getRGB() : new Color(18,18,18,160).getRGB();

        RenderUtils.drawAngularHudBackground(
                canvasStack,
                x,
                y,
                width,
                height,
                backgroundColor
        );

        float health = Math.max(0F, target.getHealth());
        float maxHealth = Math.max(1F, target.getMaxHealth());
        ApexArmorTier armorTier = getApexArmorTier(maxHealth);

        // 总生命拆成护盾生命和基础生命：受伤时先减少上层，耗尽后再减少下层。
        float shieldCapacity = maxHealth * armorTier.shieldShare;
        float baseHealthCapacity = maxHealth - shieldCapacity;
        float shieldHealth = Math.clamp(health - baseHealthCapacity, 0F, shieldCapacity);
        float baseHealth = Math.clamp(health, 0F, baseHealthCapacity);
        float shieldProgress = shieldCapacity > 0F ? shieldHealth / shieldCapacity : 0F;
        float healthProgress = baseHealthCapacity > 0F ? baseHealth / baseHealthCapacity : 0F;
        float trailProgress = updateHealthTrail(target, healthProgress);

        RenderUtils.drawSegmentedParallelogramBar(
                canvasStack,
                x + 16F,
                y + 5F,
                width - 34F,
                3.5F,
                3F,
                shieldProgress,
                armorTier.segments,
                5,
                1F,
                0xFF3E4042,
                armorTier.color,
                0xFF565758
        );

        RenderUtils.drawTrailingParallelogramHealthBar(
                canvasStack,
                x + 18F,
                y + 10F,
                width - 32F,
                4.5F,
                4F,
                healthProgress,
                trailProgress
        );
    }

    private float updateHealthTrail(LivingEntity entity, float currentProgress) {
        long now = System.currentTimeMillis();

        if (healthTrailTargetId != entity.getId() || healthTrailLastUpdate == 0L) {
            healthTrailTargetId = entity.getId();
            lastHealthProgress = currentProgress;
            healthTrailProgress = currentProgress;
            healthTrailHoldUntil = now;
            healthTrailLastUpdate = now;
            return currentProgress;
        }

        float deltaSeconds = Math.min(0.1F, (now - healthTrailLastUpdate) / 1_000F);

        if (currentProgress < lastHealthProgress) {
            healthTrailProgress = Math.max(healthTrailProgress, lastHealthProgress);
            healthTrailHoldUntil = now + HEALTH_TRAIL_HOLD_MS;
        } else if (currentProgress > lastHealthProgress) {
            // 治疗时不保留红色残影。
            healthTrailProgress = currentProgress;
            healthTrailHoldUntil = now;
        }

        if (now > healthTrailHoldUntil) {
            healthTrailProgress = Math.max(
                    currentProgress,
                    healthTrailProgress - HEALTH_TRAIL_RETURN_PER_SECOND * deltaSeconds
            );
        }

        healthTrailProgress = Math.max(currentProgress, healthTrailProgress);
        lastHealthProgress = currentProgress;
        healthTrailLastUpdate = now;
        return healthTrailProgress;
    }


    private static ApexArmorTier getApexArmorTier(float maxHealth) {
        if (maxHealth < 65) return ApexArmorTier.WHITE;
        if (maxHealth < 400F) return ApexArmorTier.BLUE;
        if (maxHealth < 650) return ApexArmorTier.PURPLE;
        return ApexArmorTier.GOLD;
    }
    @EventTarget
    public void onRender(RenderEvent event) {
        if (target == null)
            return;
        if(!mode.is("Default")) return;
        float partialTicks = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        WorldToScreen.ScreenPos pos = WorldToScreen.projectEntity(target, partialTicks);
        if (pos == null)
            return;

        int width = 150;
        int height = 65;
        int x = (int) pos.x() - width / 2;
        int y = (int) pos.y() - height;


        String name = target.getName().getString();
        float health = Math.max(0.0F, target.getHealth());
        double attributeMaxHealth = getAttributeValue(target, Attributes.MAX_HEALTH);
        float maxHealth = Math.max(1.0F, (float) attributeMaxHealth);
        float percent = Math.clamp(health / maxHealth, 0.0F, 1.0F);

        double armor = getAttributeValue(target, Attributes.ARMOR);
        double armorToughness = getAttributeValue(target, Attributes.ARMOR_TOUGHNESS);
        double attackDamage = getAttributeValue(target, Attributes.ATTACK_DAMAGE);

        double distance = mc.player.distanceTo(target);
        boolean badHeadshot = BadHeadshot.isBadHeadshotEntity(target);
        BlurRenderer.draw(event.getGuiGraphicsExtractor(),  x, y, width, height,10);
        drawBackground(event.getGuiGraphicsExtractor(), x, y, width, height, badHeadshot);
        drawText(event.getGuiGraphicsExtractor(), x, y, name, health, maxHealth, armor, armorToughness,
                attackDamage, distance);

        GuiGraphicsUtils.drawHealthBar(event.getGuiGraphicsExtractor(), x + 8, y + 53, width - 16, 7, percent);
//        GuiGraphicsUtils.drawArmorBar(event.getGuiGraphicsExtractor(), x + 8, y + 50, width - 16, 8, armorPercent);
    }

    private static double getAttributeValue(LivingEntity entity,
                                            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        try {
            return entity.getAttributeValue(attribute);
        } catch (IllegalArgumentException ignored) {
            return 0.0D;
        }
    }

    public static void drawBackground(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean badHeadshot) {
        Color background = badHeadshot ? BAD_HEADSHOT_BACKGROUND : NORMAL_BACKGROUND;
        Color border = badHeadshot ? BAD_HEADSHOT_BORDER : NORMAL_BORDER;

        graphics.fill(x, y, x + width, y + height, background.getRGB());


        graphics.fill(x, y, x + width, y + 1, border.getRGB());
        graphics.fill(x, y + height - 1, x + width, y + height, border.getRGB());
        graphics.fill(x, y, x + 1, y + height, border.getRGB());
        graphics.fill(x + width - 1, y, x + width, y + height, border.getRGB());
    }

    private static void drawText(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            String name,
            float health,
            float maxHealth,
            double armor,
            double armorToughness,
            double attackDamage,
            double distance
    ) {
        String hpText = String.format("%.1f / %.1f HP", health, maxHealth);
        String defenseText = String.format("MAX %.1f  ARM %.1f", maxHealth, armor);
        String combatText = String.format("TOUGH %.1f  ATK %.1f", armorToughness, attackDamage);
        String distanceText = String.format("%.1f m", distance);

        int nameColor = 0xFFFFFFFF;
        graphics.text(mc.font, name, x + 8, y + 7, nameColor, true);
        graphics.text(mc.font, hpText, x + 8, y + 18, 0xFFFF5555, true);

        graphics.text(mc.font, distanceText, x + 105, y + 18, 0xFFAAAAAA, true);
        graphics.text(mc.font, defenseText, x + 8, y + 30, 0xFF55AAFF, true);
        graphics.text(mc.font, combatText, x + 8, y + 41, 0xFFFFCC55, true);
    }





    public static boolean isValidTarget(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }

        if (!living.isAlive()) {
            return false;
        }

        if (entity == mc.player) {
            return false;
        }

        if (entity instanceof Player) {
            return false;
        }

        if (entity instanceof ArmorStand) {
            return false;
        }
        String name = entity.getName().getString();

        if (name.equalsIgnoreCase("Farmer")) {
            return false;
        }
        return true;
    }


}
