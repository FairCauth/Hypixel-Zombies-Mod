package com.example.client.skia.render.dynamicisland;

import java.util.List;

/** Immutable data handed to Skia on the render thread. No game or OS access here. */
public sealed interface IslandLiveData {
    record Audio(float peak, boolean available, long sampledNanos) {}
    /** A real enhanced-LRC boundary, measured in Unicode code points rather than UTF-16 units. */
    record WordCue(double seconds, int textOffset) {}
    record Lyric(String title, String artist, String text, long sampledNanos,
                 double startSeconds, double endSeconds, List<WordCue> wordCues) {
        public Lyric {
            wordCues = wordCues == null ? List.of() : List.copyOf(wordCues);
            double previousTime = startSeconds;
            int previousOffset = 0, length = text.codePointCount(0, text.length());
            for (var cue : wordCues) {
                if (!Double.isFinite(cue.seconds()) || cue.seconds() < previousTime || cue.seconds() > endSeconds
                        || cue.textOffset() < previousOffset || cue.textOffset() > length) {
                    wordCues = List.of();
                    break;
                }
                previousTime = cue.seconds(); previousOffset = cue.textOffset();
            }
        }
        public Lyric(String title, String artist, String text, long sampledNanos, double startSeconds, double endSeconds) {
            this(title, artist, text, sampledNanos, startSeconds, endSeconds, List.of());
        }
        public Lyric(String title, String artist, String text, long sampledNanos) {
            this(title, artist, text, sampledNanos, Double.NaN, Double.NaN);
        }

        public float sungFraction(Music music) {
            return sungFraction(music, 0);
        }

        /** Advance affects only the sweep, never the playback clock or which line is selected. */
        public float sungFraction(Music music, double advanceSeconds) {
            double position = music.positionNow();
            if (music.duration() <= 0 || !Double.isFinite(position) || !Double.isFinite(startSeconds)
                    || !Double.isFinite(endSeconds) || endSeconds <= startSeconds) return -1;
            if (position < startSeconds) return 0;
            if (Double.isFinite(advanceSeconds)) position += Math.clamp(advanceSeconds, -2, 2);
            int length = text.codePointCount(0, text.length());
            if (!wordCues.isEmpty() && length > 0) {
                double previousTime = startSeconds;
                int previousOffset = 0;
                for (var cue : wordCues) {
                    if (position < cue.seconds()) {
                        double portion = cue.seconds() == previousTime ? 0
                                : Math.clamp((position - previousTime) / (cue.seconds() - previousTime), 0, 1);
                        return (float) ((previousOffset + (cue.textOffset() - previousOffset) * portion) / length);
                    }
                    previousTime = cue.seconds(); previousOffset = cue.textOffset();
                }
                if (previousOffset == length) return 1;
                double portion = endSeconds == previousTime ? 1
                        : Math.clamp((position - previousTime) / (endSeconds - previousTime), 0, 1);
                return (float) ((previousOffset + (length - previousOffset) * portion) / length);
            }
            // Ordinary LRC provides only line boundaries; its sweep remains an approximation.
            return (float) Math.clamp((position - startSeconds) / (endSeconds - startSeconds), 0, 1);
        }
    }
    record Palette(int start, int end) {
        public static final Palette DEFAULT = new Palette(0xFFB9D9E8, 0xFFE4BCD0);
    }
    /** A bounded correction keeps normal playback continuous while observations catch up. */
    record PlaybackClock(double rate, double correction, double settleSeconds) {
        public double elapsed(double seconds) {
            return rate * seconds + correction * Math.min(seconds / settleSeconds, 1);
        }
    }
    record Music(String title, String artist, String album, boolean playing, double position,
                 double duration, long sampledNanos, boolean timelineAdvances, String coverKey,
                 byte[] cover, boolean canToggle, boolean canPrevious, boolean canNext,
                 PlaybackClock clock) implements IslandLiveData {
        public Music(String title, String artist, String album, boolean playing, double position,
                     double duration, long sampledNanos, boolean timelineAdvances, String coverKey,
                     byte[] cover, boolean canToggle, boolean canPrevious, boolean canNext) {
            this(title, artist, album, playing, position, duration, sampledNanos, timelineAdvances,
                    coverKey, cover, canToggle, canPrevious, canNext, null);
        }
        public double positionNow() {
            return positionAt(System.nanoTime());
        }
        public double positionAt(long now) {
            double seconds = Math.max(0, now - sampledNanos) / 1e9;
            double value = position + (playing && timelineAdvances ? (clock == null ? seconds : clock.elapsed(seconds)) : 0);
            return duration > 0 ? Math.clamp(value, 0, duration) : Math.max(0, value);
        }
        /** Freeze one frame so lyric selection, highlight and progress cannot sample different times. */
        public Music atTime(long now) {
            return new Music(title, artist, album, playing, positionAt(now), duration, now, false,
                    coverKey, cover, canToggle, canPrevious, canNext);
        }
    }

    record Wave(int round, String map, int current, List<Integer> schedule, double elapsed,
                double nextSeconds, boolean boss, String bossName) implements IslandLiveData {
        public Wave { schedule = List.copyOf(schedule); }
        public int count() { return schedule.size(); }
        /** Fraction of the current spawn interval still remaining, including the first wave. */
        public float countdownFraction() {
            int next = current + 1;
            if (!Double.isFinite(nextSeconds) || nextSeconds < 0 || next < 0 || next >= count()) return 0;
            double start = current >= 0 ? schedule.get(current) : 0;
            double interval = schedule.get(next) - start;
            return interval <= 0 ? 0 : (float) Math.clamp(nextSeconds / interval, 0, 1);
        }
        public float progress() {
            if (current < 0 || schedule.isEmpty()) return 0;
            if (current >= count() - 1) return 1;
            return (float) Math.clamp((elapsed - schedule.get(current)) /
                    Math.max(0.001, schedule.get(current + 1) - schedule.get(current)), 0, 1);
        }
    }

    record Rescue(String name, boolean confirmed, double remaining, float progress) {}
    record Revive(List<Rescue> rescues) implements IslandLiveData {
        public Revive { rescues = List.copyOf(rescues); }
    }
}
