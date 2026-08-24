package com.example.client.mixin;

import com.example.client.tracker.TeammateInfo;
import com.example.client.tracker.TeammateTracker;
import com.example.client.language.GuiText;
import com.example.client.utils.PlayerUtils;
import net.minecraft.ChatFormatting;
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

                TeammateInfo teammate = TeammateTracker.get(component.getString().replaceAll("§.", "").trim());
                if (teammate == null) {
                        return width;
                }

                Player player = teammate.getRenderEntity();
                String decoration = zombiesmod$statusText(teammate);
                if (decoration == null && player != null) {
                        decoration = zombiesmod$healthDecoration(player);
                }
                return decoration == null ? width : width + font.width(decoration);
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
                TeammateInfo teammate = TeammateTracker.get(cleanText);
                if (teammate == null) {
            graphics.text(font, text, x, y, color, shadow);
            return;
        }

                String statusText = zombiesmod$statusText(teammate);
                if (statusText != null) {
                        ChatFormatting statusColor = teammate.getPlayerState() == TeammateInfo.PlayerState.DOWN
                                        ? ChatFormatting.YELLOW : ChatFormatting.RED;
                        graphics.text(font, Component.literal(statusText).withStyle(statusColor)
                                        .append(text.copy()), x, y, color, shadow);
                        return;
                }

                Player player = teammate.getRenderEntity();
                if (player == null) {
                        graphics.text(font, text, x, y, color, shadow);
                        return;
                }

        float health = Math.max(0.0F, player.getHealth());
        float maxHealth = Math.max(1.0F, player.getMaxHealth());
        float percent = Math.max(0.0F, Math.min(1.0F, health / maxHealth));
        String healthText = zombiesmod$healthText(player);
        String blockingText = PlayerUtils.isPlayerBlockingHyp(player)
                ? " (" + GuiText.textString("hud.blocking") + ")" : "";
        ChatFormatting healthColor = percent > 0.5F
                ? ChatFormatting.GREEN : percent > 0.25F ? ChatFormatting.YELLOW : ChatFormatting.RED;
        Component replacement = Component.literal(healthText)
                .withStyle(healthColor)
                .append(text.copy())
                .append(Component.literal(blockingText).withStyle(ChatFormatting.YELLOW));

        graphics.text(font, replacement, x, y, color, shadow);
        }

        private static String zombiesmod$statusText(TeammateInfo teammate) {
                if (teammate.getPlayerState() == TeammateInfo.PlayerState.TERMINAL) {
                        String statusText = teammate.getStatusText().toUpperCase(java.util.Locale.ROOT);
                        return statusText.contains("QUIT") || statusText.contains("退出")
                                        ? GuiText.textString("hud.quit") : GuiText.textString("hud.dead");
                }
                if (teammate.getPlayerState() == TeammateInfo.PlayerState.DOWN) {
                        return GuiText.textString("hud.down");
                }
                return null;
        }

        private static String zombiesmod$healthDecoration(Player player) {
                return zombiesmod$healthText(player)
                                + (PlayerUtils.isPlayerBlockingHyp(player)
                                ? "(" + GuiText.textString("hud.blocking") + ")" : "");
        }

        private static String zombiesmod$healthText(Player player) {
                float health = Math.max(0.0F, player.getHealth());
                float maxHealth = Math.max(1.0F, player.getMaxHealth());
                return (int) Math.ceil(health) + "/" + (int) Math.ceil(maxHealth) + " ";
    }
}