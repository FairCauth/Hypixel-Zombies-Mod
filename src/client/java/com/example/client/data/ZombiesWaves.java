package com.example.client.data;

import com.example.client.utils.ZombiesMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 各地图出怪波次时间表。data[round-1] = 该回合每一波【相对回合开始的秒数】。
 * 数据来自 Seosean/ShowSpawnTime（LanguageUtils.ZombiesMap）。
 * 地图用项目里现有的 {@link ZombiesMap}（由 ZombiesUtils.getMap() 识别）。
 */
public final class ZombiesWaves {

    private static final int[][] DEAD_END = {
            {10,20},{10,20},{10,20,35},{10,20,35},{10,22,37},{10,22,44},{10,25,47},{10,25,50},{10,22,38},{10,24,45},
            {10,25,48},{10,25,50},{10,25,50},{10,25,45},{10,25,46},{10,24,47},{10,24,47},{10,24,47},{10,24,47},{10,24,49},
            {10,23,44},{10,23,45},{10,23,42},{10,23,43},{10,23,43},{10,23,36},{10,24,44},{10,24,42},{10,24,42},{10,24,45}
    };

    private static final int[][] BAD_BLOOD = {
            {10,22},{10,22},{10,22},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},
            {10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},
            {10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,24,38},{10,24,38},{10,22,34},{10,24,38},{10,22,34}
    };

    private static final int[][] THE_LAB = {
            {10,22},{10,22},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},
            {10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},
            {10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},
            {10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34},{10,22,34}
    };

    private static final int[][] PRISON = {
            {10,20},{10,20,30},{10,17,24,31},{10,17,24,31},{10,20,30},{10,20,30},{10,20,30},{10,25,40},{10,25,35},{10,25,45},
            {10,25,40},{10,25,37},{10,22,34},{10,25,37},{10,25,40},{10,22,37},{10,22,42},{10,25,45},{10,25,45},{10,25,40},
            {10,20,35,55,75},{10,25,40},{10,30,50},{10,30,50},{10,25,45},{10,30,50},{10,25,45},{10,30,50},{10,30,55},{10}
    };

    private static final int[][] ALIEN_ARCADIUM = {
            {10,13,16,19},{10,14,18,22},{10,13,16,19},{10,14,17,21,25,28},{10,14,18,22,26,30},{10,14,19,23,28,32},
            {10,15,19,23,27,31},{10,15,20,25,30,35},{10,14,19,23,28,32},{10,16,22,27,33,38},{10,16,21,27,32,38},
            {10,16,22,28,34,40},{10,16,22,28,34,40},{10,16,21,26,31,36},{10,17,24,31,38,46},{10,16,22,27,33,38},
            {10,14,19,23,28,32},{10,14,19,23,28,32},{10,14,18,22,26,30},{10,15,21,26,31,36},{10,14,19,23,28,32},
            {10,14,19,23,28,34},{10,14,18,22,26,30},{10,14,19,23,28,32},{10},{10,23,36},{10,22,34},{10,20,30},{10,24,38},
            {10,22,34},{10,22,34},{10,21,32},{10,22,34},{10,22,34},{10},{10,22,34},{10,20,31},{10,22,34},{10,22,34},
            {10,22,34,37,45},{10,21,32},{10,22,34},{10,13,22,25,34,37},{10,22,34},{10,22,34,35},{10,21,32,35},{10,20,30},
            {10,20,30,33},{10,21,32},{10,22,34,37},{10,20,30,33},{10,22,34,37},{10,22,34,37},{10,20,32,35,39},
            {10,16,22,28,34,40},{10,14,18},{10,14,18},{10,22,34,37,38},{10,14,18,22,26,30},{10,20,30,33},
            {10,14,18,22,26,30},{10,14,18,22,26,30},{10,14,18,22,26,30},{10,14,18,22,26,30},{10,14,18,22,26,30},
            {10,14,18,22,26,30},{10,14,18,22,26,30},{10,14,18,22,26,30},{10,14,18,22,26,30},{10,14,18,22,26,30},
            {10,14,18,22,26,30},{10,14,18,22,26,30},{10,14,18,22,26,30},{10,14,18,22,27,32},{10,14,18,22,27,32},
            {10,14,18,22,26,30},{10,14,18,22,26,30},{10,14,18,22,26,30},{10,14,18,22,26,30},{10,14,18,22,26,30},
            {10,14,18,22,26,30},{10,14,18,22,26,30},{10,14,18,22,26,30},{10,14,18,22,26,30},{10,14,18,22,26,30},
            {10,14,18,22,26,30},{10,14,18,22,26,30},{10,14,18,22,26,30},{10,14,18,22,26,30},{10,14,18,22,26,30},
            {10,14,18,22,26,30},{10,14,18,22,26,30},{10,14,18,22,26,30},{10,14,18,22,26,30},{10,14,18,22,26,30},
            {10,14,18,22,26,30},{10,14,18,22,26,30},{10,14,18,22,26,30},{10,14,18,22,26,30},{10,14,18,22,26,30},{5},{5},{5},{5},{5}
    };

    /** AA 逐波 boss 类型（数据来自 ShowSpawnTime GameUtils）。 */
    public enum WaveBoss { NONE, GIANT, OLD_ONE, BOTH }

    // round -> 该回合哪几波（1-based）出对应 boss
    private static final Map<Integer, List<Integer>> AA_OLD_ONE = new HashMap<>();
    private static final Map<Integer, List<Integer>> AA_GIANT = new HashMap<>();
    private static final Map<Integer, List<Integer>> AA_BOTH = new HashMap<>();

    static {
        AA_OLD_ONE.put(40, Arrays.asList(5));
        AA_OLD_ONE.put(45, Arrays.asList(3, 4));
        AA_OLD_ONE.put(46, Arrays.asList(4));
        AA_OLD_ONE.put(48, Arrays.asList(4));
        AA_OLD_ONE.put(54, Arrays.asList(5));
        AA_OLD_ONE.put(55, Arrays.asList(6));
        AA_OLD_ONE.put(58, Arrays.asList(5));
        AA_OLD_ONE.put(59, Arrays.asList(1, 2, 3, 4, 5, 6));
        AA_OLD_ONE.put(60, Arrays.asList(3, 4));
        AA_OLD_ONE.put(64, Arrays.asList(5, 6));
        AA_OLD_ONE.put(67, Arrays.asList(6));
        AA_OLD_ONE.put(68, Arrays.asList(5, 6));
        AA_OLD_ONE.put(69, Arrays.asList(5, 6));
        AA_OLD_ONE.put(70, Arrays.asList(2, 3));
        AA_OLD_ONE.put(74, Arrays.asList(4, 5, 6));
        AA_OLD_ONE.put(77, Arrays.asList(6));
        AA_OLD_ONE.put(78, Arrays.asList(5, 6));
        AA_OLD_ONE.put(79, Arrays.asList(5, 6));
        AA_OLD_ONE.put(80, Arrays.asList(2, 3));
        AA_OLD_ONE.put(84, Arrays.asList(4, 5, 6));
        AA_OLD_ONE.put(87, Arrays.asList(6));
        AA_OLD_ONE.put(88, Arrays.asList(5, 6));
        AA_OLD_ONE.put(89, Arrays.asList(5, 6));
        AA_OLD_ONE.put(90, Arrays.asList(2, 3));
        AA_OLD_ONE.put(94, Arrays.asList(4, 5, 6));
        AA_OLD_ONE.put(97, Arrays.asList(6));
        AA_OLD_ONE.put(98, Arrays.asList(5, 6));
        AA_OLD_ONE.put(99, Arrays.asList(5, 6));
        AA_OLD_ONE.put(100, Arrays.asList(2, 3));

        AA_GIANT.put(15, Arrays.asList(6));
        AA_GIANT.put(20, Arrays.asList(3, 5));
        AA_GIANT.put(22, Arrays.asList(4, 6));
        AA_GIANT.put(24, Arrays.asList(2, 4, 6));
        AA_GIANT.put(30, Arrays.asList(1, 2, 3));
        AA_GIANT.put(36, Arrays.asList(2, 3));
        AA_GIANT.put(37, Arrays.asList(2, 3));
        AA_GIANT.put(38, Arrays.asList(2, 3));
        AA_GIANT.put(39, Arrays.asList(2, 3));
        AA_GIANT.put(40, Arrays.asList(2, 3));
        AA_GIANT.put(41, Arrays.asList(2, 3));
        AA_GIANT.put(42, Arrays.asList(1, 2, 3));
        AA_GIANT.put(43, Arrays.asList(2, 4, 6));
        AA_GIANT.put(44, Arrays.asList(1, 2, 3));
        AA_GIANT.put(45, Arrays.asList(2));
        AA_GIANT.put(47, Arrays.asList(3));
        AA_GIANT.put(50, Arrays.asList(2, 4));
        AA_GIANT.put(51, Arrays.asList(2, 4));
        AA_GIANT.put(52, Arrays.asList(2, 4));
        AA_GIANT.put(53, Arrays.asList(2, 4));
        AA_GIANT.put(54, Arrays.asList(4));
        AA_GIANT.put(55, Arrays.asList(1, 2, 3, 4));
        AA_GIANT.put(58, Arrays.asList(4));
        AA_GIANT.put(65, Arrays.asList(4, 5, 6));
        AA_GIANT.put(75, Arrays.asList(4, 5, 6));
        AA_GIANT.put(85, Arrays.asList(4, 5, 6));
        AA_GIANT.put(95, Arrays.asList(4, 5, 6));

        AA_BOTH.put(54, Arrays.asList(2));
        AA_BOTH.put(55, Arrays.asList(5));
        AA_BOTH.put(58, Arrays.asList(2));
        AA_BOTH.put(70, Arrays.asList(4, 5, 6));
        AA_BOTH.put(80, Arrays.asList(4, 5, 6));
        AA_BOTH.put(90, Arrays.asList(4, 5, 6));
        AA_BOTH.put(100, Arrays.asList(4, 5, 6));
    }

    private ZombiesWaves() {}

    /** 该地图所有"有 boss 的回合"（数据同 ShowSpawnTime getBossRounds）。 */
    public static int[] bossRounds(ZombiesMap map) {
        if (map == null) return new int[0];
        return switch (map) {
            case ALIEN_ARCADIUM -> new int[]{25, 35, 56, 57, 101};
            case DEAD_END -> new int[]{5, 10, 15, 20, 25, 30};
            case BAD_BLOOD -> new int[]{10, 15, 20, 25, 30};
            case THE_LAB -> new int[]{5, 10, 15, 20, 25, 30, 35, 40};
            case PRISON -> new int[]{10, 20, 30};
            case NULL -> new int[0];
        };
    }

    /** 这个回合是不是 Boss 回合。 */
    public static boolean isBossRound(ZombiesMap map, int round) {
        for (int r : bossRounds(map)) {
            if (r == round) return true;
        }
        return false;
    }

    /** AA 专用：该回合第 wave 波（1-based）出什么 boss。非 AA / 无 boss 返回 NONE。 */
    public static WaveBoss aaWaveBoss(ZombiesMap map, int round, int wave) {
        if (map != ZombiesMap.ALIEN_ARCADIUM) return WaveBoss.NONE;
        if (contains(AA_GIANT, round, wave)) return WaveBoss.GIANT;
        if (contains(AA_OLD_ONE, round, wave)) return WaveBoss.OLD_ONE;
        if (contains(AA_BOTH, round, wave)) return WaveBoss.BOTH;
        return WaveBoss.NONE;
    }

    private static boolean contains(Map<Integer, List<Integer>> table, int round, int wave) {
        List<Integer> waves = table.get(round);
        return waves != null && waves.contains(wave);
    }

    private static int[][] timerOf(ZombiesMap map) {
        if (map == null) return null;
        return switch (map) {
            case DEAD_END -> DEAD_END;
            case BAD_BLOOD -> BAD_BLOOD;
            case THE_LAB -> THE_LAB;
            case PRISON -> PRISON;
            case ALIEN_ARCADIUM -> ALIEN_ARCADIUM;
            case NULL -> null;
        };
    }

    /** 该回合各波时间（秒）；地图未知/越界返回 null。 */
    public static int[] getWaves(ZombiesMap map, int round) {
        int[][] timer = timerOf(map);
        if (timer == null || round < 1 || round > timer.length) return null;
        return timer[round - 1];
    }

    /** 当前进行到第几波（0-based）；-1 表示第一波还没到。 */
    public static int currentWaveIndex(int[] waves, double elapsedSec) {
        int idx = -1;
        for (int i = 0; i < waves.length; i++) {
            if (elapsedSec >= waves[i]) idx = i;
            else break;
        }
        return idx;
    }

    /** 距下一波还有几秒；已是最后一波返回 -1。 */
    public static double secondsToNextWave(int[] waves, double elapsedSec) {
        for (int time : waves) {
            if (time > elapsedSec) return time - elapsedSec;
        }
        return -1;
    }
}
