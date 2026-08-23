package com.example.client.mixin;

import com.example.client.tracker.TeammateTracker;
import com.example.client.utils.PlayerUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Hud.class)
public class SidebarHealthMixin {
    @Redirect(
            method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I"
            )
    )
    private int zombiesmod$includeHealthWidth(Font font, FormattedText text) {
        int width = font.width(text);
        if (!PlayerUtils.isInHypZombies() || !(text instanceof Component component)) {
            return width;
        }

        Player player = TeammateTracker.getPlayer(component.getString().replaceAll("§.", "").trim());
        return player == null ? width : width + font.width(zombiesmod$nameDecoration(player));
    }

    @Redirect(
            method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"
            )
    )
    private void zombiesmod$appendHealth(
            GuiGraphicsExtractor graphics,
            Font font,
            Component text,
            int x,
            int y,
            int color,
            boolean shadow
    ) {
        if (!PlayerUtils.isInHypZombies()) {
            graphics.text(font, text, x, y, color, shadow);
            return;
        }

        String cleanText = text.getString().replaceAll("§.", "").trim();
        Player player = TeammateTracker.getPlayer(cleanText);
        if (player == null) {
            graphics.text(font, text, x, y, color, shadow);
            return;
        }

        float health = Math.max(0.0F, player.getHealth());
        float maxHealth = Math.max(1.0F, player.getMaxHealth());
        float percent = Math.max(0.0F, Math.min(1.0F, health / maxHealth));
        String healthText = zombiesmod$healthText(player);
        String blockingText = PlayerUtils.isPlayerBlockingHyp(player) ? " (Blocking)" : "";
        int healthColor = percent > 0.5F ? 0xFF66FF66 : percent > 0.25F ? 0xFFFFD633 : 0xFFFF5555;
        Component replacement = Component.literal(healthText)
                .withStyle(style -> style.withColor(healthColor))
                .append(text.copy())
                .append(Component.literal(blockingText).withStyle(style -> style.withColor(0xFFFFFF55)));

        graphics.text(font, replacement, x, y, color, shadow);
        }

        private static String zombiesmod$healthText(Player player) {
                float health = Math.max(0.0F, player.getHealth());
                float maxHealth = Math.max(1.0F, player.getMaxHealth());
                return (int) Math.ceil(health) + "/" + (int) Math.ceil(maxHealth) + " ";
        }

        private static String zombiesmod$nameDecoration(Player player) {
                return zombiesmod$healthText(player)
                                + (PlayerUtils.isPlayerBlockingHyp(player) ? "(Blocking)" : "");
    }
}