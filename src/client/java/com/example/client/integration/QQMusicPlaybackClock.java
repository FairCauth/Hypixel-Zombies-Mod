package com.example.client.integration;

import com.example.client.skia.render.dynamicisland.IslandLiveData.PlaybackClock;

/** Reader-thread clock: whole-second observations are intervals, not new playback anchors. */
final class QQMusicPlaybackClock {
    record Sample(String track, String source, double position, double duration, boolean playing,
                  boolean advances, double rate, double precision, double age, long revision) {}
    record Reading(double position, long sampledNanos, PlaybackClock clock) {}
    private String track, source;
    private double position, duration, lastRaw;
    private long sampled, revision;
    private boolean playing, advances;
    private PlaybackClock clock;
    private double pendingTarget = Double.NaN;
    private long pendingAt;
    private String pendingSource;

    void reset() { track = null; pendingTarget = Double.NaN; }

    Reading update(Sample next, long now) {
        double rate = Math.clamp(next.rate(), .1, 4);
        double precision = Math.clamp(next.precision(), 0, 1);
        double age = next.playing() && next.advances() ? Math.max(0, next.age()) * rate : 0;
        double low = Math.clamp(next.position() + age, 0, next.duration());
        double high = Math.min(next.duration(), low + precision);
        double target = next.playing() && next.advances() ? (low + high) / 2 : low;
        boolean same = next.track().equals(track) && Math.abs(next.duration() - duration) <= 2;
        if (!same || next.duration() <= 0) {
            reset();
            return anchor(next, now, target, rate, 0);
        }
        double expected = at(now);
        boolean sameSource = next.source().equals(source);
        boolean stateChanged = playing != next.playing() || advances != next.advances();
        // A delayed provider update must not reverse an already accepted newer sample.
        if (!stateChanged && sameSource && next.revision() > 0 && revision > 0 && next.revision() < revision)
            return new Reading(position, sampled, clock);

        double error = expected < low ? low - expected : expected > high ? high - expected : 0;
        boolean backwards = sameSource && next.position() < lastRaw - Math.max(.3, precision * .5);
        boolean jump = Math.abs(error) > Math.max(1.5, precision + .75) || backwards;
        if (jump && !stateChanged) {
            // Confirm a seek with a second observation; isolated stale/outlier packets never jump the HUD.
            double pendingNow = pendingTarget + (next.playing() && next.advances() ? (now - pendingAt) / 1e9 * rate : 0);
            if (next.source().equals(pendingSource) && now - pendingAt <= 2_000_000_000L
                    && Math.abs(target - pendingNow) <= Math.max(.35, precision)) {
                return anchor(next, now, target, rate, 0);
            }
            pendingTarget = target; pendingAt = now; pendingSource = next.source();
            // Keep the original anchor during confirmation, including when the source changes.
            return new Reading(position, sampled, clock);
        }
        pendingTarget = Double.NaN; pendingSource = null;
        if (stateChanged) {
            // Pausing a whole-second footer holds the continuous phase inside its valid interval.
            double held = jump ? target : Math.clamp(expected, low, high);
            return anchor(next, now, held, rate, 0);
        }
        if (!next.playing() || !next.advances()) return anchor(next, now, Math.clamp(expected, low, high), rate, 0);
        // Corrections stay within 8% of the published playback rate and never step backwards.
        double correction = Math.abs(error) < .04 ? 0 : error;
        return anchor(next, now, expected, rate, correction);
    }

    private double at(long now) {
        double seconds = Math.max(0, now - sampled) / 1e9;
        return Math.clamp(position + (playing && advances ? clock.elapsed(seconds) : 0), 0, duration);
    }
    private Reading anchor(Sample next, long now, double value, double rate, double correction) {
        track = next.track(); source = next.source(); duration = next.duration();
        playing = next.playing(); advances = next.advances(); lastRaw = next.position();
        revision = next.revision(); sampled = now; position = value;
        clock = new PlaybackClock(rate, correction, Math.max(2, Math.abs(correction) / (.08 * rate)));
        pendingTarget = Double.NaN; pendingSource = null;
        return new Reading(position, sampled, clock);
    }
}
