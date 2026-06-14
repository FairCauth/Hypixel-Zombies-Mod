package com.example.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Hypixel Zombies「Alien Arcadium (AA)」出怪表（只含主要怪物 + 守点位置1）。
 *
 * <p>用法：</p>
 * <pre>
 *   String monsters = ZombiesSpawnTable.getMonsters(round); // 主要怪物
 *   String location = ZombiesSpawnTable.getLocation(round); // 守点位置1
 * </pre>
 *
 * <p>回合映射：</p>
 * <ul>
 *   <li>10~60：表里明确列出的回合直接命中；未列出的回合（11、12、27…）返回 null。</li>
 *   <li>≥61：按个位数循环 —— 个位 1~8 → x1~x8，个位 0 → x0；</li>
 *   <li>个位 9：69/79 与 89/99 每 20 回合交替
 *       （此规则按表格推断，若不符改 {@link #patternKey(int)}）。</li>
 * </ul>
 */
public final class ZombiesSpawnTable {

    /** 某回合的出怪信息（守点位置1）。 */
    public record SpawnInfo(String roundLabel, String monsters, String location) {}

    private static final Map<Integer, SpawnInfo> EXPLICIT = new HashMap<>();
    private static final Map<String, SpawnInfo> PATTERN = new HashMap<>();

    private ZombiesSpawnTable() {}

    // ====================== 对外方法 ======================

    /** 获取该回合的出怪信息；无数据返回 null。 */
    public static SpawnInfo get(int round) {
        SpawnInfo exact = EXPLICIT.get(round);
        if (exact != null) return exact;
        if (round >= 61) return PATTERN.get(patternKey(round));
        return null;
    }

    /** 该回合的主要怪物；无数据返回 null。 */
    public static String getMonsters(int round) {
        SpawnInfo info = get(round);
        return info == null ? null : info.monsters();
    }

    /** 该回合的守点位置1；无数据返回 null。 */
    public static String getLocation(int round) {
        SpawnInfo info = get(round);
        return info == null ? null : info.location();
    }

    /** 是否有该回合的数据。 */
    public static boolean has(int round) {
        return get(round) != null;
    }

    /** ≥61 回合时，把回合号映射到循环表的 key。 */
    private static String patternKey(int round) {
        int digit = round % 10;
        if (digit == 9) {
            return ((round - 69) / 20) % 2 == 0 ? "9A" : "9B"; // 69/79 与 89/99 交替
        }
        return "x" + digit; // x0 ~ x8
    }

    // ====================== 数据 ======================

    private static void put(int round, String monsters, String location) {
        EXPLICIT.put(round, new SpawnInfo(String.valueOf(round), monsters, location));
    }

    private static void putPattern(String key, String label, String monsters, String location) {
        PATTERN.put(key, new SpawnInfo(label, monsters, location));
    }

    static {
        // ---- 明确回合 10 ~ 60 ----
        put(10, "少量史莱姆", "箱子");
        put(15, "1金巨人(0-0-1)", "箱子");
        put(18, "较多史莱姆", "箱子墙角");
        put(20, "2金巨人(0-1-1)", "箱子");
        put(22, "2铁巨人(0-1-1)", "箱子");
        put(23, "较多史莱姆", "箱子墙角");
        put(24, "3铁巨人(1-1-1)", "箱子");
        put(25, "巨型史莱姆(奖励)", "箱子");
        put(26, "大量史莱姆", "perk机器");
        put(28, "蠕虫+彩虹僵尸+少量恶魂", "箱子");
        put(29, "较多史莱姆", "perk机器");
        put(30, "4钻石巨人(1-1-2)", "箱子");
        put(31, "较多史莱姆", "perk机器");
        put(33, "较少史莱姆", "perk机器");
        put(34, "较多史莱姆", "perk机器");
        put(35, "巨型史莱姆(奖励)", "Mid");
        put(36, "3钻石巨人(0-1-2) 大量远程", "箱子");
        put(37, "3钻石巨人(0-1-2)", "箱子");
        put(38, "3钻石巨人(0-1-2)", "箱子");
        put(39, "3钻石巨人(0-1-2) 大量史莱姆", "箱子");
        put(40, "4钻石巨人(0-2-2) 1长者", "箱子");
        put(41, "4钻石巨人(0-2-2)", "箱子");
        put(42, "6钻石巨人(2-2-2)", "箱子");
        put(43, "6钻石巨人(0-2-2-2) 大量史莱姆", "箱子");
        put(44, "9钻石巨人(3-3-3) 太空喷射者", "箱子");
        put(45, "3钻石巨人(0-3-0) 2长者 小丑 太空喷射者", "终极机器");
        put(46, "铁傀儡 1长者", "箱子");
        put(47, "3钻石巨人(0-0-3) 大量史莱姆", "箱子");
        put(48, "苦力怕 1长者", "箱子");
        put(49, "太空喷射者 小丑", "Alt");
        put(50, "4钻石巨人(0-2-2) 斧头帮", "终极机器");
        put(51, "4钻石巨人 (0-2-2) 骷颅", "终极机器前的门");
        put(52, "4钻石巨人(0-2-2) 超大量史莱姆", "箱子");
        put(53, "4钻石巨人(0-2-2) 小丑", "终极机器");
        put(54, "4彩虹巨人(0-2-2) 2长者 小丑 铁傀儡", "终极机器");
        put(55, "5彩虹巨人(1-1-1-1-1) 2长者 小丑 太空射手 岩浆怪", "终极机器");
        put(56, "铁傀儡 巨型史莱姆(奖励)", "Mid");
        put(57, "铁傀儡 巨型史莱姆(奖励)", "Mid");
        put(58, "6彩虹巨人(0-3-3) 5长者 小丑", "终极机器");
        put(59, "13长者", "卡长者");
        put(60, "2长者 Mini 太空喷射者", "终极机器");

        // ---- 循环回合（≥61，按个位数） ----
        putPattern("x1", "x1", "狼 史莱姆", "终极机器");
        putPattern("x2", "x2", "小丑", "Alt");
        putPattern("x3", "x3", "苦力怕 狼", "终极机器");
        putPattern("x4", "x4", "烈焰人 worm 长者", "终极机器");
        putPattern("x5", "x5", "烈焰人 火焰僵尸 3彩虹巨人(0-0-1-1-1) 史莱姆", "终极机器");
        putPattern("x6", "x6", "史莱姆 苦力怕 小僵尸", "Ent");
        putPattern("x7", "x7", "史莱姆 火焰僵尸 长者", "终极机器");
        putPattern("x8", "x8", "worm 小僵尸 2长者", "终极机器");
        putPattern("x0", "x0", "小丑 铁傀儡 大量彩虹巨人 大量长者", "终极机器");
        putPattern("9A", "69/79", "骷颅 小丑 mini太空射手 2长者", "终极机器");
        putPattern("9B", "89/99", "小丑 mini太空射手 守卫者 2长者", "终极机器");
    }
}
