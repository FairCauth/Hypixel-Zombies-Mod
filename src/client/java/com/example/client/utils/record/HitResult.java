package com.example.client.utils.record;

import com.example.client.ZombiesGuns;

public record HitResult(
        long shotId,
        ZombiesGuns gun,
        int ultimateLevel,
        int slot,
        int gold,
        boolean critical,
        double damage,
        long delayMs
) {
}