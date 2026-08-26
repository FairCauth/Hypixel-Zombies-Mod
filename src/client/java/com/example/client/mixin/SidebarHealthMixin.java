package com.example.client.mixin;

import com.example.client.tracker.TeammateInfo;
import com.example.client.tracker.TeammateTracker;
import com.example.client.language.GuiText;
import com.example.client.utils.PlayerUtils;
import com.example.client.utils.ScoreboardUtils;
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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(Hud.class)
public class SidebarHealthMixin {
        private static final int zombiesmod$sidebarWidth = 128;
        private static final int zombiesmod$sidebarPadding = 3;

        @Inject(
                        method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V",
                        at = @At("HEAD"),
                        cancellable = true
        )
        private void zombiesmod$renderFixedSidebar(
                        GuiGraphicsExtractor graphics,
                        Objective objective,
                        CallbackInfo ci
        ) {
                if (!zombiesmod$isZombiesMode()) return;

                Font font = Minecraft.getInstance().font;
                List<SidebarEntry> entries = zombiesmod$sidebarEntries();
                List<Component> renderedLines = new ArrayList<>();
                for (SidebarEntry entry : entries) {
                        renderedLines.add(entry.name());
                        if (entry.info() != null) renderedLines.add(entry.info());
                }

                Component title = objective.getDisplayName();
                int contentWidth = zombiesmod$sidebarWidth - zombiesmod$sidebarPadding * 2;

                int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
                int lineHeight = font.lineHeight;
                int totalHeight = lineHeight * (renderedLines.size() + 1);
                int left = screenWidth - zombiesmod$sidebarWidth;
                int top = screenHeight / 2 - totalHeight / 2;

                graphics.pose().pushMatrix();
                graphics.pose().translate(left, top);
                float titleScale = zombiesmod$lineScale(font, title, 0, contentWidth);
                int titleX = (zombiesmod$sidebarWidth - Math.round(font.width(title) * titleScale)) / 2;
                zombiesmod$drawScaledText(graphics, font, title, titleX, 0, titleScale);

                int y = lineHeight;
                for (SidebarEntry entry : entries) {
                        float nameScale = zombiesmod$lineScale(font, entry.name(), 0, contentWidth);
                        zombiesmod$drawScaledText(graphics, font, entry.name(), zombiesmod$sidebarPadding, y, nameScale);
                        y += lineHeight;
                        if (entry.info() != null) {
                                int itemWidth = zombiesmod$itemWidth(entry.player());
                                float infoScale = zombiesmod$lineScale(font, entry.info(), itemWidth, contentWidth);
                                graphics.pose().pushMatrix();
                                graphics.pose().translate(zombiesmod$sidebarPadding, y);
                                graphics.pose().scale(infoScale, infoScale);
                                zombiesmod$drawItem(graphics, entry.player(), 0, 0);
                                graphics.text(font, entry.info(), itemWidth, 0, 0xFFFFFFFF, true);
                                graphics.pose().popMatrix();
                                y += lineHeight;
                        }
                }
                graphics.pose().popMatrix();
                ci.cancel();
        }

        private static List<SidebarEntry> zombiesmod$sidebarEntries() {
                List<SidebarEntry> entries = new ArrayList<>();
                for (ScoreboardUtils.ScoreboardLine line : ScoreboardUtils.getSidebarLines()) {
                        String playerName = zombiesmod$playerName(line.component());
                        TeammateInfo teammate = TeammateTracker.get(playerName);
                        if (teammate == null) {
                                Component kills = zombiesmod$killsDecoration(line.component());
                                entries.add(new SidebarEntry(line.component(),
                                                kills.getString().isEmpty() ? null : kills, null));
                                continue;
                        }

                        Player player = teammate.getRenderEntity();
                        Component nameLine = teammate.getStatusText().isBlank()
                                        ? Component.literal(playerName).append(zombiesmod$goldDecoration(teammate))
                                        : line.component();
                        entries.add(new SidebarEntry(
                                        nameLine,
                                        zombiesmod$playerInfo(teammate, player, line.component(), teammate.getName()),
                                        player
                        ));
                }
                return entries;
        }

        private static Component zombiesmod$playerInfo(
                        TeammateInfo teammate, Player player, Component original) {
                return zombiesmod$playerInfo(teammate, player, original, null);
        }

        private static Component zombiesmod$playerInfo(
                        TeammateInfo teammate, Player player, Component original, String playerName) {
                String statusText = zombiesmod$statusText(teammate);
                if (statusText != null) {
                        return zombiesmod$killsDecoration(original, playerName);
                }
                if (player == null) {
                        return Component.empty()
                                        .append(zombiesmod$killsDecoration(original, playerName));
                }

                float health = Math.max(0.0F, player.getHealth());
                float maxHealth = Math.max(1.0F, player.getMaxHealth());
                float percent = Math.max(0.0F, Math.min(1.0F, health / maxHealth));
                ChatFormatting healthColor = percent > 0.5F
                                ? ChatFormatting.GREEN : percent > 0.25F ? ChatFormatting.YELLOW : ChatFormatting.RED;
                String blockingText = PlayerUtils.isPlayerBlockingHyp(player)
                                ? " (" + GuiText.textString("hud.blocking") + ")" : "";
                String shiftText = player.isShiftKeyDown()
                                ? " (" + GuiText.textString("hud.shift") + ")" : "";
                return Component.literal(zombiesmod$healthText(player)).withStyle(healthColor)
                                .append(zombiesmod$killsDecoration(original, playerName))
                                .append(Component.literal(blockingText).withStyle(ChatFormatting.YELLOW))
                                .append(Component.literal(shiftText).withStyle(ChatFormatting.AQUA));
        }

        private static Component zombiesmod$goldDecoration(TeammateInfo teammate) {
                return Component.literal(": " + String.format("%,d", teammate.getGold()))
                                .withStyle(ChatFormatting.GOLD);
        }

        private static float zombiesmod$lineScale(Font font, Component line, int extraWidth, int contentWidth) {
                int width = font.width(line) + extraWidth;
                return width > contentWidth ? (float) contentWidth / width : 1.0F;
        }

        private static void zombiesmod$drawScaledText(
                        GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, float scale) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(x, y);
                graphics.pose().scale(scale, scale);
                graphics.text(font, text, 0, 0, 0xFFFFFFFF, true);
                graphics.pose().popMatrix();
        }

        private record SidebarEntry(Component name, Component info, Player player) {
        }

    @Redirect(
            method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I"
            )
    )
    private int zombiesmod$includeHealthWidth(Font font, FormattedText text) {
        int width = font.width(text);
                if (!zombiesmod$isZombiesMode() || !(text instanceof Component component)) {
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
                if (!zombiesmod$isZombiesMode()) {
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

        private static boolean zombiesmod$isZombiesMode() {
                return PlayerUtils.isInHypZombies();
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
                return zombiesmod$killsDecoration(original, null);
        }

        private static Component zombiesmod$killsDecoration(Component original, String knownPlayerName) {
                String cleanText = knownPlayerName == null
                                ? PlayerUtils.cleanName(original.getString()) : knownPlayerName;
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.getConnection() == null) return Component.empty();
                PlayerInfo matched = null;
                int matchedLength = -1;
                for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
                        String tabName = PlayerUtils.cleanName(info.getProfile().name());
                        if (zombiesmod$containsPlayerName(cleanText, tabName)
                                        && tabName.length() > matchedLength) {
                                matched = info;
                                matchedLength = tabName.length();
                        }
                }
                if (matched != null) {
                        String kills = zombiesmod$tabScore(minecraft, matched);
                        if (kills != null) return Component.literal(" | " + kills).withStyle(ChatFormatting.RED);
                }
                return Component.empty();
        }

        private static boolean zombiesmod$containsPlayerName(String text, String playerName) {
                return text.equalsIgnoreCase(playerName)
                                || text.contains(playerName);
        }

        private static boolean zombiesmod$matchesPlayerName(String first, String second) {
                return first.equalsIgnoreCase(second)
                                || first.contains(second)
                                || second.contains(first);
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

                        var nameScore = scoreboard.getPlayerScoreInfo(
                                        ScoreHolder.forNameOnly(playerName), objective);
                        if (nameScore != null) return String.valueOf(nameScore.value());

                        for (var entry : scoreboard.listPlayerScores(objective)) {
                                String owner = PlayerUtils.cleanName(entry.owner());
                                String ownerDisplay = PlayerUtils.cleanName(entry.ownerName().getString());
                                if (zombiesmod$matchesPlayerName(owner, playerName)
                                                || zombiesmod$matchesPlayerName(ownerDisplay, playerName)) {
                                        return String.valueOf(entry.value());
                                }
                        }
                }
                return null;
        }

        private static String zombiesmod$playerName(Component text) {
                String cleanText = PlayerUtils.cleanName(text.getString());
                int colon = cleanText.indexOf(':');
                if (colon < 0) colon = cleanText.indexOf('：');
                return colon < 0 ? cleanText : cleanText.substring(0, colon).trim();
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