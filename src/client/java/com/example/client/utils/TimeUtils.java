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

    public static long randomClickDelay(final int minCPS, final int maxCPS) {
        return (long) ((Math.random() * (1000d / minCPS - 1000d / maxCPS + 1)) + 1000d / maxCPS);
    }

    public void waitForAtLeast(long ms) {
        this.lastMS = Math.max(this.lastMS, System.currentTimeMillis() + ms);
    }
}
