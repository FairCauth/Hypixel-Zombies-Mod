package com.example.client.tracker;

import lombok.Getter;

@Getter
public enum GameStat {
    DOUBLE_GOLD(30_000L),
    SHOPPING_SPREE(20_000L),
    INSTA_KILL(10_000L);
    private final long durationMs;

    GameStat(long durationMs) {
        this.durationMs = durationMs;
    }

    public static GameStat[] currentStat = new GameStat[0];
}
