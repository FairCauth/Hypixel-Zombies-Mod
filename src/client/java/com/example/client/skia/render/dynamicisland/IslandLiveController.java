package com.example.client.skia.render.dynamicisland;

/** Real activities share the existing independent task queue and docking animations. */
public final class IslandLiveController {
    public static final String MUSIC = "qq-music", WAVE = "zombies-wave", REVIVE = "revive-aura";
    private static final long MUSIC_IDLE_NANOS = 30_000_000_000L;
    private final DynamicIsland island;
    private final java.util.function.LongSupplier clock;
    private boolean musicPaused;
    private long musicPausedAt;
    private IslandLiveData.Wave countdownWave;
    private double countdownExitRemaining;
    private java.util.Set<String> previousRescues = java.util.Set.of();
    private String autoFocus;
    private double autoRemaining;
    private boolean peekHeld;
    private boolean peekSecond;

    public IslandLiveController(DynamicIsland island) {
        this(island, System::nanoTime);
    }

    /** A monotonic clock keeps the idle timeout independent of FPS and render pauses. */
    public IslandLiveController(DynamicIsland island, java.util.function.LongSupplier clock) {
        this.island = island;
        this.clock = java.util.Objects.requireNonNull(clock);
        island.endTask("demo-main");
        island.endTask("demo-secondary");
    }

    public void update(IslandLiveData.Music music, IslandLiveData.Wave wave, IslandLiveData.Revive revive,
                       double dt, boolean glances) {
        boolean showMusic = musicVisible(music);
        wave = visibleCountdown(wave, dt);
        boolean rescuing = revive != null && !revive.rescues().isEmpty();
        autoRemaining = Math.max(0, autoRemaining - dt);
        if (autoFocus != null && (autoRemaining == 0 || !glances)) {
            if (autoFocus.equals(island.expandedTaskId())) island.setExpanded(false);
            autoFocus = null;
        }
        var rescueNames = rescuing ? revive.rescues().stream().map(IslandLiveData.Rescue::name)
                .collect(java.util.stream.Collectors.toSet()) : java.util.Set.<String>of();
        boolean newRescue = rescueNames.stream().anyMatch(name -> !previousRescues.contains(name));
        previousRescues = rescueNames;
        // A new rescue gets an automatic announcement; a held key remains under manual control.
        boolean announceRescue = newRescue && glances && !peekHeld;
        if (announceRescue) { autoFocus = REVIVE; autoRemaining = 3.2; }
        if (!rescuing) {
            island.endTask(REVIVE);
            if (REVIVE.equals(autoFocus)) { autoFocus = null; autoRemaining = 0; }
        } else island.updateLiveTask(REVIVE, revive, REVIVE.equals(autoFocus) ? 100 : 45);
        if (!showMusic) island.endTask(MUSIC); else island.updateLiveTask(MUSIC, music, wave != null || rescuing ? 50 : 10);
        if (wave == null) island.endTask(WAVE); else island.updateLiveTask(WAVE, wave, 40);
        island.setConcurrentWave(rescuing && showMusic ? wave : null);
        if (announceRescue) island.expandTask(REVIVE);
        // Wave countdowns stay compact; users can still open their details manually.
        if (peekHeld) expandPeekTask();
    }

    private IslandLiveData.Wave visibleCountdown(IslandLiveData.Wave wave, double dt) {
        if (wave != null && wave.nextSeconds() > 0 && wave.nextSeconds() <= 3 && wave.countdownFraction() > 0) {
            countdownWave = wave;
            countdownExitRemaining = .24;
            return wave;
        }
        if (wave != null && countdownWave != null && wave.round() == countdownWave.round()
                && wave.map().equals(countdownWave.map())) {
            countdownExitRemaining = Math.max(0, countdownExitRemaining - Math.max(0, dt));
            if (countdownExitRemaining > 0) {
                // Finish the old digit without flashing the next interval's full, empty ring.
                var previous = countdownWave;
                return new IslandLiveData.Wave(previous.round(), previous.map(), previous.current(),
                        previous.schedule(), previous.elapsed(), 0, previous.boss(), previous.bossName());
            }
        }
        countdownWave = null;
        countdownExitRemaining = 0;
        return null;
    }

    private boolean musicVisible(IslandLiveData.Music music) {
        if (music == null || music.playing()) {
            musicPaused = false;
            return music != null;
        }
        long now = clock.getAsLong();
        if (!musicPaused) {
            musicPaused = true;
            musicPausedAt = now;
        }
        return now - musicPausedAt < MUSIC_IDLE_NANOS;
    }

    public void toggle(boolean second) {
        if (peekHeld) return;
        autoFocus = null;
        var visible = island.visibleTaskIds();
        if (visible.isEmpty()) return;
        String id = visible.get(second && visible.size() > 1 ? 1 : 0);
        if (id.equals(island.expandedTaskId())) island.setExpanded(false); else island.expandTask(id);
    }

    /** Physical key state is sampled every frame, so lost release events cannot latch the island open. */
    public void setPeekHeld(boolean held, boolean second) {
        if (held != peekHeld) {
            autoFocus = null;
            autoRemaining = 0;
            if (!held) island.setExpanded(false);
        }
        peekHeld = held;
        peekSecond = second;
        if (held) expandPeekTask();
    }

    private void expandPeekTask() {
        var visible = island.visibleTaskIds();
        if (!visible.isEmpty()) island.expandTask(visible.get(peekSecond && visible.size() > 1 ? 1 : 0));
    }
}
