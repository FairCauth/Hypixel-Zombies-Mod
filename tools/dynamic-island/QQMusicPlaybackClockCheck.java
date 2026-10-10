package com.example.client.integration;

import com.example.client.skia.render.dynamicisland.IslandLiveData;

/** Deterministic production-clock fixtures; no QQ processes, network or playback controls. */
public final class QQMusicPlaybackClockCheck {
    private static final long START = 10_000_000_000L;
    private static final String TRACK = "Song\0Artist\0Album";
    private static final String FOOTER = "qq-playback-footer", MEDIA = "media-session";

    public static void main(String[] args) {
        checkWholeSeconds();
        checkNoisyPrecision();
        checkSeekAndOutliers();
        checkPauseAndRate();
        checkLatencyAndSourceChanges();
        checkFrameAndLyrics();
        System.out.println("Playback clock checks passed: quantization, 120-second continuity, jitter, stale samples, seek confirmation, pause/resume, playback rate, delayed covers, source switching, song change and shared lyric/frame time.");
    }

    private static void checkWholeSeconds() {
        var clock = new QQMusicPlaybackClock();
        IslandLiveData.Music music = null;
        double previous = -1;
        for (int frame = 0; frame < 7200; frame++) {
            double time = frame / 60.0;
            double actual = 42.17 + time;
            long now = now(time);
            // Footer observes at 2 Hz, with 0..90ms read/delivery jitter.
            if (frame % 30 == 0) {
                double age = .045 + .045 * Math.sin(frame * .23);
                double raw = Math.floor(actual - age);
                music = music(clock.update(sample(FOOTER, raw, true, 1, age, (long)raw + 1), now), true);
            }
            double position = music.positionAt(now);
            require(position >= previous, "whole-second packets never step backwards at frame " + frame);
            require(Math.abs(position - actual) <= .6, "quantized observations stay within their sub-second uncertainty");
            if (previous >= 0) require(position - previous >= .92 / 60 - 1e-8
                    && position - previous <= 1.08 / 60 + 1e-8, "no speed spikes between footer updates");
            previous = position;
        }
    }

    private static void checkNoisyPrecision() {
        var clock = new QQMusicPlaybackClock();
        IslandLiveData.Music music = null;
        double previous = -1;
        for (int frame = 0; frame < 1800; frame++) {
            double time = frame / 60.0;
            long now = now(time);
            if (frame % 30 == 0) {
                double noise = .12 * Math.sin(frame * .71);
                music = music(clock.update(sample(MEDIA, 20 + time + noise, true, 0, 0, frame + 1), now), true);
            }
            double position = music.positionAt(now);
            require(position >= previous, "precise provider jitter does not rewind");
            require(Math.abs(position - (20 + time)) < .2, "small precise errors remain bounded");
            previous = position;
        }
    }

    private static void checkSeekAndOutliers() {
        var clock = new QQMusicPlaybackClock();
        var first = clock.update(sample(MEDIA, 42, true, 0, 0, 100), now(0));
        var outlier = clock.update(sample(MEDIA, 12, true, 0, 0, 101), now(.5));
        near(music(outlier, true).positionAt(now(.5)), 42.5, "one bad reading retains original anchor");
        var normal = clock.update(sample(MEDIA, 43, true, 0, 0, 102), now(1));
        near(music(normal, true).positionAt(now(1)), 43, "normal reading cancels the outlier");
        clock.update(sample(MEDIA, 12, true, 0, 0, 103), now(1.5));
        var sought = clock.update(sample(MEDIA, 12.5, true, 0, 0, 104), now(2));
        near(music(sought, true).positionAt(now(2)), 12.5, "confirmed backward seek resets immediately");
        var stale = clock.update(sample(MEDIA, 43.5, true, 0, 0, 102), now(2.5));
        near(music(stale, true).positionAt(now(2.5)), 13, "old revision cannot undo a seek");
        clock.update(sample(MEDIA, 80, true, 0, 0, 105), now(3));
        var forward = clock.update(sample(MEDIA, 80.5, true, 0, 0, 106), now(3.5));
        near(music(forward, true).positionAt(now(3.5)), 80.5, "confirmed forward seek resets immediately");

        clock.reset();
        clock.update(sample(FOOTER, 50, true, 1, 0, 10), now(0));
        clock.update(sample(FOOTER, 49, true, 1, 0, 11), now(.5));
        var smallSeek = clock.update(sample(FOOTER, 49, true, 1, 0, 11), now(1));
        require(music(smallSeek, true).positionAt(now(1)) < 50, "small real backwards seek is not smoothed away");
        require(first.clock() != null, "production clock accompanies its immutable reading");
    }

    private static void checkPauseAndRate() {
        var clock = new QQMusicPlaybackClock();
        clock.update(sample(FOOTER, 42, true, 1, 0, 10), now(0));
        var paused = clock.update(sample(FOOTER, 43, false, 1, 0, 11), now(1.4));
        var held = music(paused, false);
        near(held.positionAt(now(100)), 43.9, "pause holds continuous fractional phase");
        var repeated = clock.update(sample(FOOTER, 43, false, 1, 0, 11), now(30));
        near(music(repeated, false).positionAt(now(30)), 43.9, "duplicate paused integer cannot change held position");
        var resumed = clock.update(sample(FOOTER, 43, true, 1, 0, 12), now(40));
        near(music(resumed, true).positionAt(now(40.5)), 44.4, "resume excludes paused wall time");
        clock.reset();
        var atZero = clock.update(sample(FOOTER, 0, false, 1, 0, 1), now(0));
        near(music(atZero, false).positionAt(now(10)), 0, "paused intro has no invented half second");
        var fast = clock.update(new QQMusicPlaybackClock.Sample(TRACK, MEDIA, 0, 300, true,
                true, 1.5, 0, 0, 1), now(11));
        near(music(fast, true).positionAt(now(12)), 1.5, "published playback rate controls interpolation");
    }

    private static void checkLatencyAndSourceChanges() {
        var clock = new QQMusicPlaybackClock();
        var delayed = clock.update(sample(MEDIA, 10, true, 0, 2.4, 1), now(0));
        near(music(delayed, true).positionAt(now(0)), 12.4, "cover read latency is included exactly once");
        var footer = clock.update(sample(FOOTER, 13, true, 1, 0, 1), now(1));
        near(music(footer, true).positionAt(now(1)), 13.4, "provider switch preserves phase");
        var system = clock.update(sample(MEDIA, 13.9, true, 0, 0, 2), now(1.5));
        near(music(system, true).positionAt(now(1.5)), 13.9, "returning to system timeline stays continuous");
        var oldSystem = clock.update(sample(MEDIA, 9, true, 0, 0, 1), now(2));
        near(music(oldSystem, true).positionAt(now(2)), 14.4, "stale source data does not reset interpolation");
        var other = clock.update(new QQMusicPlaybackClock.Sample("Other song", FOOTER, 0, 232, false,
                false, 1, 1, 0, 1), now(3));
        near(music(other, false).positionAt(now(3)), 0, "song change never inherits old position");
        var unknown = clock.update(new QQMusicPlaybackClock.Sample("Other song", "unknown", 0, 0, true,
                false, 1, 0, 0, 0), now(4));
        near(unknown.position(), 0, "missing source cannot invent a duration or anchor");
    }

    private static void checkFrameAndLyrics() {
        var clock = new QQMusicPlaybackClock();
        var lines = QQMusicLyrics.parse("[00:10]First\n[00:14]Second\n[00:18]");
        IslandLiveData.Music music = null;
        float previous = 0;
        String previousText = "";
        for (int frame = 0; frame < 240; frame++) {
            double time = frame / 60.0;
            long now = now(time);
            if (frame % 30 == 0) music = music(clock.update(sample(FOOTER, Math.floor(10 + time),
                    true, 1, 0, frame + 1), now), true);
            var frozen = music.atTime(now);
            var line = QQMusicLyrics.timedLineAt(lines, frozen);
            require(frozen.positionNow() == frozen.positionAt(now(100)), "one rendered frame has one timestamp");
            float fraction = line.sungFraction(frozen);
            if (previousText.equals(line.text())) require(fraction >= previous, "highlight does not reverse within a line");
            require(Math.abs(fraction - (frozen.positionNow() - line.startSeconds()) /
                    (line.endSeconds() - line.startSeconds())) < 1e-6, "progress and highlight use exactly the same time");
            previous = fraction; previousText = line.text();
        }
    }

    private static QQMusicPlaybackClock.Sample sample(String source, double position, boolean playing,
                                                       double precision, double age, long revision) {
        return new QQMusicPlaybackClock.Sample(TRACK, source, position, 300, playing, true, 1, precision, age, revision);
    }
    private static IslandLiveData.Music music(QQMusicPlaybackClock.Reading reading, boolean playing) {
        return new IslandLiveData.Music("Song", "Artist", "Album", playing, reading.position(), 300,
                reading.sampledNanos(), true, "", null, false, false, false, reading.clock());
    }
    private static long now(double seconds) { return START + Math.round(seconds * 1e9); }
    private static void near(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 1e-6, message + "; actual=" + actual + ", expected=" + expected);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
