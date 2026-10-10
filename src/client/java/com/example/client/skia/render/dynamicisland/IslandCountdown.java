package com.example.client.skia.render.dynamicisland;

/** The real wave clock selects digits; render time only animates their handoff. */
final class IslandCountdown {
    int digit;
    int outgoing;
    double age = 1;
    private int round = -1;
    private String map = "";

    void update(IslandLiveData.Wave wave) {
        if (round != wave.round() || !map.equals(wave.map())) {
            digit = outgoing = 0;
            age = 1;
            round = wave.round();
            map = wave.map();
        }
        int next = wave.countdownFraction() > 0 && wave.nextSeconds() > 0 && wave.nextSeconds() <= 3
                ? (int) Math.ceil(wave.nextSeconds()) : 0;
        if (next == digit) return;
        // Repeated tracker updates must not restart the animation each frame.
        outgoing = digit;
        digit = next;
        age = 0;
    }

    void advance(double seconds) {
        age = Math.min(1, age + seconds);
        if (age >= .18) outgoing = 0;
    }

    float incoming() { return smooth(age / .22); }
    float leaving() { return smooth(age / .18); }
    float scale() {
        // A small damped landing, settled before the next second arrives.
        return age >= .34 ? 1 : (float) (1 + .16 * Math.exp(-14 * age) * Math.cos(18 * age));
    }

    private static float smooth(double value) {
        float t = (float) Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
