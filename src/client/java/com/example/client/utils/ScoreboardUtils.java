package com.example.client.utils;

import com.example.client.tracker.TeammateInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.scores.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScoreboardUtils implements IMinecraft {
    public record ScoreboardLine(
            Component component,
            String text,
            String cleanText,
            int score
    ) {
    }
    public record ScorePlayer(
            String name,
            Long gold,
            TeammateInfo.PlayerState state,
            String statusText
    ) {}
    public static Component getSidebarTitle() {
        if (mc.level == null) {
            return Component.literal("");
        }

        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);

        if (objective == null) {
            return Component.literal("");
        }

        return objective.getDisplayName();
    }
    public static final int PLAYER_GROUP_INDEX = 2;

    public static List<ScorePlayer> getZombiesPlayers() {
        List<ScorePlayer> out = new ArrayList<>();
        List<List<Component>> groups = getSidebarComponentGroups();
        if (groups.size() <= PLAYER_GROUP_INDEX) return out;

        for (Component component : groups.get(PLAYER_GROUP_INDEX)) {
            String text = cleanScoreboardText(component.getString());
            if (text.isEmpty()) continue;


            String name = text;
            Long gold = null;
            TeammateInfo.PlayerState state = TeammateInfo.PlayerState.ALIVE;
            String statusText = "";
            // 用第一个冒号分隔（玩家名不含冒号）。左边永远是真名；
            // 右边是数字 = 金币（活着），是状态文字（已死亡/等待救援/复活中…）= 倒地。
            int colon = text.indexOf(':');
            if (colon > 0) {
                String left = text.substring(0, colon).trim();
                String right = text.substring(colon + 1).replace(",", "").trim();
                name = left;
                if (!right.isEmpty() && right.chars().allMatch(Character::isDigit)) {
                    gold = Long.parseLong(right);
                } else {
                    statusText = right;
                    String normalizedStatus = right.toUpperCase(java.util.Locale.ROOT);
                    boolean dead = normalizedStatus.contains("DEAD") || normalizedStatus.contains("死亡");
                    boolean quit = normalizedStatus.contains("QUIT") || normalizedStatus.contains("退出");
                    state = dead || quit || hasTerminalRedColor(component)
                            ? TeammateInfo.PlayerState.TERMINAL
                            : TeammateInfo.PlayerState.DOWN;
                }
            }
            name = name.trim();
            if (!name.isEmpty()) out.add(new ScorePlayer(name, gold, state, statusText));
        }
        return out;
    }

    /** 右侧状态位于计分板行末；只按最后一个显式文字颜色判断终止状态。 */
    private static boolean hasTerminalRedColor(Component component) {
        TextColor lastColor = null;
        for (Component part : component.toFlatList()) {
            if (!part.getString().isBlank() && part.getStyle().getColor() != null) {
                lastColor = part.getStyle().getColor();
            }
        }
        if (lastColor == null) return false;

        int value = lastColor.getValue();
        return value == TextColor.fromLegacyFormat(ChatFormatting.RED).getValue()
                || value == TextColor.fromLegacyFormat(ChatFormatting.DARK_RED).getValue();
    }


    public static List<String> getSidebarLinesRaw() {
        List<String> result = new ArrayList<>();

        for (Component component : getSidebarLineComponentsRaw()) {
            result.add(component.getString());
        }
        return result;
    }

    private static List<Component> getSidebarLineComponentsRaw() {
        List<Component> result = new ArrayList<>();
        if (mc.level == null) return result;

        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return result;

        List<PlayerScoreEntry> entries = scoreboard.listPlayerScores(objective)
                .stream()
                .filter(entry -> !entry.isHidden())
                .sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed())
                .limit(20)
                .toList();

        for (PlayerScoreEntry entry : entries) {
            result.add(getLineComponent(scoreboard, entry));
        }
        return result;
    }

    private static List<List<Component>> getSidebarComponentGroups() {
        List<List<Component>> groups = new ArrayList<>();
        List<Component> current = new ArrayList<>();

        for (Component component : getSidebarLineComponentsRaw()) {
            if (cleanScoreboardText(component.getString()).isEmpty()) {
                if (!current.isEmpty()) {
                    groups.add(current);
                    current = new ArrayList<>();
                }
            } else {
                current.add(component);
            }
        }
        if (!current.isEmpty()) groups.add(current);
        return groups;
    }

    public static List<List<String>> getSidebarGroups() {
        List<List<String>> groups = new ArrayList<>();
        List<String> current = new ArrayList<>();

        for (String raw : getSidebarLinesRaw()) {
            if (cleanScoreboardText(raw).isEmpty()) {
                if (!current.isEmpty()) {
                    groups.add(current);
                    current = new ArrayList<>();
                }
            } else {
                current.add(raw);
            }
        }
        if (!current.isEmpty()) groups.add(current);
        return groups;
    }

//    public static void dumpSidebar() {
//        List<String> lines = getSidebarLinesRaw();
//        for (int i = 0; i < lines.size(); i++) {
//            System.out.println("[SB " + i + "] '" + cleanScoreboardText(lines.get(i)) + "'");
//        }
//        List<List<String>> groups = getSidebarGroups();
//        for (int i = 0; i < groups.size(); i++) {
//            System.out.println("[GROUP " + i + "] "
//                    + groups.get(i).stream().map(ScoreboardUtils::cleanScoreboardText).toList());
//        }
//    }
    public static String getSidebarTitleString() {
        return cleanScoreboardText(getSidebarTitle().getString());
    }

    public static List<ScoreboardLine> getSidebarLines() {
        List<ScoreboardLine> result = new ArrayList<>();

        if (mc.level == null) {
            return result;
        }

        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);

        if (objective == null) {
            return result;
        }

        List<PlayerScoreEntry> entries = scoreboard.listPlayerScores(objective)
                .stream()
                .filter(entry -> !entry.isHidden())
                .sorted(
                        Comparator.comparingInt(PlayerScoreEntry::value)
                                .reversed()
                                .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER)
                )
                .limit(15)
                .toList();

        for (PlayerScoreEntry entry : entries) {
            Component lineComponent = getLineComponent(scoreboard, entry);
            String text = lineComponent.getString();
            String cleanText = cleanScoreboardText(text);

            if (cleanText.isEmpty()) {
                continue;
            }

            result.add(new ScoreboardLine(
                    lineComponent,
                    text,
                    cleanText,
                    entry.value()
            ));
        }

        return result;
    }

    private static Component getLineComponent(Scoreboard scoreboard, PlayerScoreEntry entry) {
        PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
        if (team != null) {
            return PlayerTeam.formatNameForTeam(team, Component.literal(entry.owner()));
        }

        return entry.ownerName();
    }

    public static String cleanScoreboardText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("§.", "")
                .trim();
    }
}
