package com.example.client.mixin;

import com.example.client.tracker.TeammateInfo;
import com.example.client.tracker.TeammateTracker;
import com.example.client.language.GuiText;
import com.example.client.utils.PlayerUtils;
import com.example.client.utils.ScoreboardUtils;
import com.example.client.utils.ZombiesMap;
import com.example.client.utils.ZombiesUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
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
                if (!zombiesmod$isDeadEnd() || !(text instanceof Component component)) {
            return width;
        }

                TeammateInfo teammate = TeammateTracker.get(zombiesmod$playerName(component));
                if (teammate == null) {
                        return width;
                }

                Player player = teammate.getRenderEntity();
                Component decoration = zombiesmod$decoration(teammate, player, component);
                return decoration == null ? width : width + font.width(decoration) + zombiesmod$itemWidth(player);
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
                if (!zombiesmod$isDeadEnd()) {
            graphics.text(font, text, x, y, color, shadow);
            return;
        }

        String cleanText = zombiesmod$playerName(text);
                TeammateInfo teammate = TeammateTracker.get(cleanText);
                if (teammate == null) {
            graphics.text(font, text, x, y, color, shadow);
            return;
        }

                String statusText = zombiesmod$statusText(teammate);
                Player player = teammate.getRenderEntity();
                if (statusText != null) {
                        ChatFormatting statusColor = teammate.getPlayerState() == TeammateInfo.PlayerState.DOWN
                                        ? ChatFormatting.YELLOW : ChatFormatting.RED;
                        Component replacement = zombiesmod$itemDecoration(player)
                                        .append(Component.literal(statusText).withStyle(statusColor))
                                        .append(text.copy())
                                        .append(zombiesmod$killsDecoration(text));
                        zombiesmod$drawItem(graphics, player, x, y);
                        graphics.text(font, replacement, x + zombiesmod$itemWidth(player), y, color, shadow);
                        return;
                }

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
        String shiftText = player.isShiftKeyDown()
                ? " (" + GuiText.textString("hud.shift") + ")" : "";
        ChatFormatting healthColor = percent > 0.5F
                ? ChatFormatting.GREEN : percent > 0.25F ? ChatFormatting.YELLOW : ChatFormatting.RED;
        Component replacement = zombiesmod$itemDecoration(player)
                .append(Component.literal(healthText).withStyle(healthColor))
                .append(text.copy())
                .append(zombiesmod$killsDecoration(text))
                .append(Component.literal(blockingText).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(shiftText).withStyle(ChatFormatting.AQUA));

        zombiesmod$drawItem(graphics, player, x, y);
        graphics.text(font, replacement, x + zombiesmod$itemWidth(player), y, color, shadow);
        }

        private static boolean zombiesmod$isDeadEnd() {
                return PlayerUtils.isInHypZombies() && ZombiesUtils.getMap() == ZombiesMap.DEAD_END;
        }

        private static Component zombiesmod$decoration(
                        TeammateInfo teammate, Player player, Component original) {
                String statusText = zombiesmod$statusText(teammate);
                if (statusText != null) {
                        ChatFormatting statusColor = teammate.getPlayerState() == TeammateInfo.PlayerState.DOWN
                                        ? ChatFormatting.YELLOW : ChatFormatting.RED;
                        return zombiesmod$itemDecoration(player)
                                        .append(Component.literal(statusText).withStyle(statusColor))
                                        .append(zombiesmod$killsDecoration(original));
                }
                if (player == null) return null;

                float health = Math.max(0.0F, player.getHealth());
                float maxHealth = Math.max(1.0F, player.getMaxHealth());
                float percent = Math.max(0.0F, Math.min(1.0F, health / maxHealth));
                ChatFormatting healthColor = percent > 0.5F
                                ? ChatFormatting.GREEN : percent > 0.25F ? ChatFormatting.YELLOW : ChatFormatting.RED;
                return zombiesmod$itemDecoration(player)
                                .append(Component.literal(zombiesmod$healthText(player)).withStyle(healthColor))
                                .append(zombiesmod$killsDecoration(original))
                                .append(Component.literal(PlayerUtils.isPlayerBlockingHyp(player)
                                        ? " (" + GuiText.textString("hud.blocking") + ")" : "")
                                        .withStyle(ChatFormatting.YELLOW))
                                .append(Component.literal(player.isShiftKeyDown()
                                        ? " (" + GuiText.textString("hud.shift") + ")" : "")
                                        .withStyle(ChatFormatting.AQUA));
        }

        private static MutableComponent zombiesmod$itemDecoration(Player player) {
                return Component.empty();
        }

        private static int zombiesmod$itemWidth(Player player) {
                return player != null && !player.getMainHandItem().isEmpty() ? 18 : 0;
        }

        private static void zombiesmod$drawItem(
                        GuiGraphicsExtractor graphics, Player player, int x, int y) {
                if (player != null && !player.getMainHandItem().isEmpty()) {
                        graphics.item(player.getMainHandItem(), x, y - 4);
                }
        }

        private static Component zombiesmod$killsDecoration(Component original) {
                String cleanText = zombiesmod$playerName(original);
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.getConnection() == null) return Component.empty();
                for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
                        if (!PlayerUtils.cleanName(info.getProfile().name()).equalsIgnoreCase(cleanText)) continue;
                        String tabKills = zombiesmod$tabScore(minecraft, info);
                        if (tabKills != null) {
                                return Component.literal("| " + tabKills).withStyle(ChatFormatting.RED);
                        }
                        String kills = zombiesmod$tabScore(minecraft, info);
                        if (kills != null) return Component.literal(" | " + kills).withStyle(ChatFormatting.RED);
                }
                return Component.empty();
        }

        private static String zombiesmod$tabScore(Minecraft minecraft, PlayerInfo info) {
                if (minecraft.level == null) return null;
                var scoreboard = minecraft.level.getScoreboard();
                Objective objective = scoreboard.getDisplayObjective(DisplaySlot.LIST);
                String playerName = info.getProfile().name();
                if (objective != null) {
                        var profileScore = scoreboard.getPlayerScoreInfo(
                                        ScoreHolder.fromGameProfile(info.getProfile()), objective);
                        if (profileScore != null) return String.valueOf(profileScore.value());
                }
                return zombiesmod$scoreForObjective(scoreboard, objective, playerName);
        }

        private static String zombiesmod$playerName(Component text) {
                String cleanText = PlayerUtils.cleanName(text.getString());
                int colon = cleanText.indexOf(':');
                if (colon < 0) colon = cleanText.indexOf('：');
                return colon < 0 ? cleanText : cleanText.substring(0, colon).trim();
        }

        private static String zombiesmod$scoreForObjective(
                        net.minecraft.world.scores.Scoreboard scoreboard,
                        Objective objective,
                        String playerName) {
                if (objective == null) return null;
                var direct = scoreboard.getPlayerScoreInfo(
                                ScoreHolder.forNameOnly(playerName), objective);
                if (direct != null) return String.valueOf(direct.value());
                for (var entry : scoreboard.listPlayerScores(objective)) {
                        if (entry.owner().equalsIgnoreCase(playerName)
                                        || entry.ownerName().getString().equalsIgnoreCase(playerName)) {
                                return String.valueOf(entry.value());
                        }
                }
                return null;
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
                                ? "(" + GuiText.textString("hud.blocking") + ")" : "")
                                + (player.isShiftKeyDown()
                                ? " (" + GuiText.textString("hud.shift") + ")" : "");
        }

        private static String zombiesmod$healthText(Player player) {
                float health = Math.max(0.0F, player.getHealth());
                float maxHealth = Math.max(1.0F, player.getMaxHealth());
                return (int) Math.ceil(health) + "/" + (int) Math.ceil(maxHealth) + " ";
    }
}