package com.example.client.utils;

import com.example.client.tracker.TeammateInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.scores.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
            long gold,
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
            long gold = 0L;
            TeammateInfo.PlayerState state = TeammateInfo.PlayerState.ALIVE;
            String statusText = "";
            // 用第一个冒号分隔（玩家名不含冒号）。左边永远是真名；
            // 右边是数字 = 金币（活着），是状态文字（已死亡/等待救援/复活中…）= 倒地，金币记 0。
            int colon = text.indexOf(':');
            if (colon > 0) {
                String left = text.substring(0, colon).trim();
                String right = text.substring(colon + 1).replace(",", "").trim();
                name = left;
                if (!right.isEmpty() && right.chars().allMatch(Character::isDigit)) {
                    gold = Long.parseLong(right);
                } else {
                    statusText = right;
                    state = isTerminalStatusText(right) || hasTerminalRedColor(component)
                            ? TeammateInfo.PlayerState.TERMINAL
                            : TeammateInfo.PlayerState.DOWN;
                }
            }
            name = name.trim();
            if (!name.isEmpty()) out.add(new ScorePlayer(name, gold, state, statusText));
        }
        return out;
    }

    /**
     * QUIT/DEAD 是服务端发送的稳定状态值，优先按文字判断；颜色只作为其他语言或
     * 服务器样式下的兜底。只检查冒号右侧，避免玩家名或前缀中的单词造成误判。
     */
    private static boolean isTerminalStatusText(String statusText) {
        String normalized = statusText == null
                ? ""
                : statusText.trim().toUpperCase(Locale.ROOT);
        return normalized.equals("QUIT")
                || normalized.equals("DEAD")
                || normalized.matches(".*\\b(?:QUIT|DEAD)\\b.*");
    }

    /**
     * QUIT/DEAD 的判据只取冒号后的状态文字颜色。不能检查整行：玩家名、
     * rank 或其他前缀也可能带红色，而 REVIVE 状态本身仍是白色。
     *
     * <p>服务器会把一行文本拆成多个 Component，因此按扁平化后的文字段
     * 依次累计字符位置；只要状态区域中存在红色系文字，即视为终止状态。</p>
     */
    private static boolean hasTerminalRedColor(Component component) {
        int colon = component.getString().indexOf(':');
        if (colon < 0) return false;

        int characterOffset = 0;
        for (Component part : component.toFlatList()) {
            String partText = part.getString();
            int partEnd = characterOffset + partText.length();
            if (partEnd > colon + 1 && !partText.isBlank()
                    && isTerminalRed(part.getStyle().getColor())) {
                return true;
            }
            characterOffset = partEnd;
        }
        return false;
    }

    private static boolean isTerminalRed(TextColor color) {
        if (color == null) return false;

        int value = color.getValue();
        int red = (value >> 16) & 255;
        int green = (value >> 8) & 255;
        int blue = value & 255;

        // Hypixel 会随资源包/计分板样式使用不同的红色值，不能只匹配原版
        // ChatFormatting.RED 与 DARK_RED 两个固定值。
        return red >= 128 && red >= green * 1.35F && red >= blue * 1.35F;
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
