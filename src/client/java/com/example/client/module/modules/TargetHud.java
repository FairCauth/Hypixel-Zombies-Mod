package com.example.client.module.modules;

import com.darkmagician6.eventapi.EventTarget;
import com.example.client.events.RenderEvent;
import com.example.client.events.TickEvent;
import com.example.client.language.Language;
import com.example.client.language.GuiText;
import com.example.client.module.AbstractModule;
import com.example.client.module.annotation.ModuleInfo;
import com.example.client.setting.annotation.SettingInfo;
import com.example.client.setting.attribute.SettingAttribute;
import com.example.client.setting.settings.ModeSetting;
import com.example.client.setting.settings.NumberSetting;
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

import java.util.List;

@ModuleInfo(name = "module.target_hud", enable = true)
public class TargetHud extends AbstractModule {
    @SettingInfo(name = "setting.distance")
    private final NumberSetting distance = new NumberSetting(35, 1, 50, "#");
    @SettingInfo(name = "setting.x")
    private final NumberSetting posX = new NumberSetting(0.02, 0, 1, "#.00");
    @SettingInfo(name = "setting.y")
    private final NumberSetting posY = new NumberSetting(0.72, 0, 1, "#.00");
    @SettingInfo(name = "setting.hud_type")
    private final ModeSetting hudType = new ModeSetting("style1", List.of("style1", "style2"),
            new SettingAttribute<>(posX, "style2"),
            new SettingAttribute<>(posY, "style2"));

    public TargetHud() {
        registerSetting(distance, hudType);
    }
    private int raycastTimer = 0;
    private LivingEntity target = null;
    @EventTarget
    public void onTick(TickEvent event) {
        if (++raycastTimer < 3) {
            return;
        }
        raycastTimer = 0;
//        System.out.println(ServerTracker.serverPlayer.xRot() + " " + ServerTracker.serverPlayer.yRot());
        target = PlayerUtils.raycastTarget(ServerTracker.serverPlayer, distance.getValue().doubleValue(), TargetHud::isValidTarget);
    }

    @EventTarget
    public void onRender(RenderEvent event) {
        if (target == null || !target.isAlive() || mc.player == null)
            return;

        GuiGraphicsExtractor graphics = event.getGuiGraphicsExtractor();
        if (hudType.is("style1")) {
            renderClassic(graphics, event);
            return;
        }

        renderDamageEngine(graphics);
    }

    private void renderClassic(GuiGraphicsExtractor graphics, RenderEvent event) {
        float partialTicks = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        WorldToScreen.ScreenPos pos = WorldToScreen.projectEntity(target, partialTicks);
        if (pos == null)
            return;

        int width = 150;
        int height = 45;
        int x = (int) pos.x() - width / 2;
        int y = (int) pos.y() - height;


        String name = target.getName().getString();
        float health = Math.max(0.0F, target.getHealth());
        float maxHealth = Math.max(1.0F, target.getMaxHealth());
        float percent = Math.max(0.0F, Math.min(1.0F, health / maxHealth));

        int armor = target.getArmorValue();
        float armorPercent = Math.max(0.0F, Math.min(1.0F, armor / 20.0F));

        double armorToughness = 0.0D;

        try {
            armorToughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        } catch (Exception ignored) {
        }

        double distance = mc.player.distanceTo(target);
    BlurRenderer.draw(graphics, x, y, width, height,10);
    GuiGraphicsUtils.drawBackground(graphics, x, y, width, height);
    drawText(graphics, x, y, name, health, maxHealth, armor, armorToughness, distance,
                BadHeadshot.isBadHeadshotEntity(target));

    GuiGraphicsUtils.drawHealthBar(graphics, x + 8, y + 32, width - 16, 8, percent);
//        GuiGraphicsUtils.drawArmorBar(event.getGuiGraphicsExtractor(), x + 8, y + 50, width - 16, 8, armorPercent);
    }

    private void renderDamageEngine(GuiGraphicsExtractor graphics) {
    int width = 190;
    int height = 58;
    int x = Math.clamp((int) (graphics.guiWidth() * posX.getValue().doubleValue()), 0,
        Math.max(0, graphics.guiWidth() - width));
    int y = Math.clamp((int) (graphics.guiHeight() * posY.getValue().doubleValue()), 0,
        Math.max(0, graphics.guiHeight() - height));
    float health = Math.max(0.0F, target.getHealth());
    float maxHealth = Math.max(1.0F, target.getMaxHealth());
    float percent = Math.max(0.0F, Math.min(1.0F, health / maxHealth));
    double targetDistance = mc.player.distanceTo(target);

    BlurRenderer.draw(graphics, x, y, width, height, 10);
    GuiGraphicsUtils.drawBackground(graphics, x, y, width, height);
    String name = target.getName().getString();
    String hpText = String.format("%.1f / %.1f HP", health, maxHealth);
    String distanceText = String.format("%.1f m", targetDistance);
    int nameColor = BadHeadshot.isBadHeadshotEntity(target) ? 0xFFFF5555 : 0xFFFFFFFF;
    graphics.text(mc.font, name, x + 8, y + 6, nameColor, true);
    graphics.text(mc.font, hpText, x + 8, y + 18, 0xFFFF5555, true);
    graphics.text(mc.font, distanceText, x + width - mc.font.width(distanceText) - 8, y + 6, 0xFFAAAAAA, true);
    GuiGraphicsUtils.drawHealthBar(graphics, x + 8, y + 34, width - 16, 10, percent);
    }


    private static void drawText(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            String name,
            float health,
            float maxHealth,
            int armor,
            double armorToughness,
            double distance,
            boolean badHeadshot
    ) {
        String hpText = String.format("%.1f / %.1f HP", health, maxHealth);
        String armorText = armorToughness > 0.0D
                ? String.format("DEF: %d  T: %.1f", armor, armorToughness)
                : "DEF: " + armor;
        String distanceText = String.format("%.1f m", distance);

        int nameColor = badHeadshot ? 0xFFFF5555 : 0xFFFFFFFF;
        graphics.text(mc.font, name, x + 8, y + 7, nameColor, true);
        graphics.text(mc.font, hpText, x + 8, y + 18, 0xFFFF5555, true);
        if (badHeadshot) {
            graphics.text(mc.font, GuiText.text("hud.bad"), x + 105, y + 7, 0xFFFF5555, true);
        }
        graphics.text(mc.font, distanceText, x + 105, y + 18, 0xFFAAAAAA, true);
//        graphics.text(mc.font, armorText, x + 8, y + 42, 0xFF55AAFF, true);
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
