package com.example.client.utils;

public class TimeUtils {
    public long lastMS = System.currentTimeMillis();

    public void reset() {
        lastMS = System.currentTimeMillis();
    }

    public boolean hasTimeElapsed(long time, boolean reset) {
        if (System.currentTimeMillis() - lastMS > time) {
            if (reset) reset();
            return true;
        }

        return false;
    }

    public boolean hasTimeElapsed(long time) {
        return System.currentTimeMillis() - lastMS > time;
    }

    public long getTime() {
        return System.currentTimeMillis() - lastMS;
    }

    public void setTime(long time) {
        lastMS = time;
    }

    public static long randomClickDelayNanos(final int minCPS, final int maxCPS) {
        int lowerCPS = Math.max(1, Math.min(minCPS, maxCPS));
        int upperCPS = Math.max(1, Math.max(minCPS, maxCPS));
        double shortestDelay = 1_000_000_000d / upperCPS;
        double longestDelay = 1_000_000_000d / lowerCPS;
        return Math.round(shortestDelay + Math.random() * (longestDelay - shortestDelay));
    }

    public void waitForAtLeast(long ms) {
        this.lastMS = Math.max(this.lastMS, System.currentTimeMillis() + ms);
    }
}
