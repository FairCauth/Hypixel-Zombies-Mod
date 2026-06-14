package com.example.client.utils;

import net.minecraft.network.chat.Component;
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
    public record ScorePlayer(String name, long gold) {}
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
        List<List<String>> groups = getSidebarGroups();
        if (groups.size() <= PLAYER_GROUP_INDEX) return out;

        for (String raw : groups.get(PLAYER_GROUP_INDEX)) {
            String text = cleanScoreboardText(raw);
            if (text.isEmpty()) continue;


            String name = text;
            long gold = 0L;
            int colon = text.lastIndexOf(':');
            if (colon > 0) {
                String left = text.substring(0, colon).trim();
                String right = text.substring(colon + 1).replace(",", "").trim();
                if (!right.isEmpty() && right.chars().allMatch(Character::isDigit)) {
                    name = left;
                    gold = Long.parseLong(right);
                }
            }
            name = name.trim();
            if (!name.isEmpty()) out.add(new ScorePlayer(name, gold));
        }
        return out;
    }


    public static List<String> getSidebarLinesRaw() {
        List<String> result = new ArrayList<>();
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
            result.add(getLineComponent(scoreboard, entry).getString());
        }
        return result;
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
