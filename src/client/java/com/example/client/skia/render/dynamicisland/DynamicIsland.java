package com.example.client.skia.render.dynamicisland;

import io.github.humbleui.skija.*;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pure Skia renderer, shared by the in-game demo and the offline preview. */
public final class DynamicIsland implements AutoCloseable {
    private static final float OPEN_DURING_DOCKING = 0.40f;
    private static final int[] METER_OFFSETS = {10, 4, 0, 2, 7};
    public enum Activity {
        MUSIC("Music", 154, 350, 168, 0xFFFF638D),
        TIMER("Timer", 166, 350, 116, 0xFFFFAC37),
        CALL("Call", 182, 350, 108, 0xFF55D98A),
        WAVE("Wave", 192, 350, 152, 0xFF67DDFC),
        REVIVE("Revive", 186, 350, 194, 0xFF55E7AC);

        public final String label;
        final float compactWidth;
        final float expandedWidth;
        final float expandedHeight;
        final int accent;

        Activity(String label, float compactWidth, float expandedWidth, float expandedHeight, int accent) {
            this.label = label;
            this.compactWidth = compactWidth;
            this.expandedWidth = expandedWidth;
            this.expandedHeight = expandedHeight;
            this.accent = accent;
        }
    }

    private final IslandSpring width = new IslandSpring(154, 19, 0.76);
    private final IslandSpring height = new IslandSpring(36, 21, 0.73);
    private final IslandSpring reveal = new IslandSpring(0, 18, 0.92);
    private final IslandSpring split = new IslandSpring(0, 20, 0.84);
    private final IslandSpring docking = new IslandSpring(0, 17, 0.94);
    private final IslandSpring satelliteWidth = new IslandSpring(36, 20, 0.84);
    private final IslandTasks tasks = new IslandTasks();
    private final Paint fill = new Paint().setAntiAlias(true);
    private final Paint stroke = new Paint().setAntiAlias(true).setStroke(true)
            .setStrokeCap(PaintStrokeCap.ROUND).setStrokeJoin(PaintStrokeJoin.ROUND);
    private final Paint layer = new Paint().setAntiAlias(true);
    private final Typeface regular = Typeface.makeFromName("Segoe UI", FontStyle.NORMAL);
    private final Typeface bold = Typeface.makeFromName("Segoe UI", FontStyle.BOLD);
    private final Typeface chinese = Typeface.makeFromName("Microsoft YaHei", FontStyle.NORMAL);
    private final Typeface chineseBold = Typeface.makeFromName("Microsoft YaHei", FontStyle.BOLD);
    private Image liveCover;
    private String liveCoverKey;
    private byte[] liveCoverBytes;
    private IslandLiveData.Audio musicAudio;
    private IslandLiveData.Palette musicPalette = IslandLiveData.Palette.DEFAULT;
    private final float[] recentPeaks = new float[16];
    private final float[] musicBars = new float[5];
    private long lastAudioSample;
    private float peakEnvelope = .015f;
    private int musicColorStart = IslandLiveData.Palette.DEFAULT.start();
    private int musicColorEnd = IslandLiveData.Palette.DEFAULT.end();
    private java.util.function.Consumer<String> musicControl = ignored -> {};
    private boolean lyricsEnabled;
    private IslandLiveData.Lyric musicLyric;
    private IslandLiveData.Wave concurrentWave;
    private String caption = "", previousCaption = "", captionTrack = "";
    private float captionMix = 1;
    private float captionMeasuredWidth, captionProgress = -1, previousCaptionProgress = -1;
    private float captionOffset, previousCaptionOffset;
    private double captionStart = Double.NaN;
    private double captionAge;
    private final Paint captionPaint = new Paint();
    private final Paint captionFadePaint = new Paint().setBlendMode(BlendMode.DST_IN);
    private final Shader captionLeftFade = Shader.makeLinearGradient(38, 0, 46, 0,
            new int[]{0x00FFFFFF, 0xFFFFFFFF});
    private final Shader captionRightFade = Shader.makeLinearGradient(0, 0, 8, 0,
            new int[]{0xFFFFFFFF, 0x00FFFFFF});
    private final Paint countdownPaint = new Paint().setAntiAlias(true);
    private final Map<String, Font> fonts = new HashMap<>();
    // Quantized, bounded filters: no JNI filter allocation per animation frame.
    private final ImageFilter[] blurs = new ImageFilter[33];
    private final DynamicIslandGlass glass = new DynamicIslandGlass();
    private final ImageFilter textShadowBlur = ImageFilter.makeBlur(1, 1, FilterTileMode.DECAL);
    private final Paint textShadow = new Paint().setAntiAlias(true).setColor(0xB0000712).setImageFilter(textShadowBlur);
    private IslandTasks.Task primary;
    private IslandTasks.Task secondary;
    private IslandTasks.Task presented;
    private IslandTasks.Task outgoing;
    private IslandTasks.Task satellite;
    private IslandTasks.Task outgoingSatellite;
    private float activityMix = 1;
    private float satelliteMix = 1;
    private boolean expanded;
    private boolean merging;
    private String focusedId;
    private float lastX;
    private float lastY;
    private float lastSatelliteX;
    private float lastSatelliteY;
    private float lastSatelliteWidth;
    private float lastSatelliteHeight;
    private float lastSatelliteAlpha;
    private float bridgeBlend;
    private boolean closed;
    private boolean drawingContent;

    public DynamicIsland() {
        for (int i = 1; i < blurs.length; i++) {
            blurs[i] = ImageFilter.makeBlur(i * 0.25f, i * 0.25f, FilterTileMode.DECAL);
        }
        setActivity(Activity.MUSIC);
    }

    public void setExpanded(boolean expanded) {
        boolean requested = expanded && primary != null;
        if (requested && !this.expanded) merging = secondary != null && reveal.value() < 0.1f && docking.value() < OPEN_DURING_DOCKING;
        if (!requested) merging = false;
        this.expanded = requested;
        if (this.expanded && tasks.get(focusedId) == null) focusedId = primary.id;
        synchronize();
    }

    public void toggle() {
        setExpanded(!expanded);
    }

    public boolean isExpanded() {
        return expanded;
    }

    public Activity activity() {
        return primary == null ? Activity.MUSIC : primary.activity;
    }

    public void setActivity(Activity next) {
        startTask("demo-main", next, 0);
    }

    /** Repeated updates of the same id preserve playback, countdown and slot ordering. */
    public void startTask(String id, Activity activity, int priority) {
        tasks.start(id, activity, priority);
        synchronize();
    }

    public void endTask(String id) {
        tasks.end(id);
        synchronize();
    }

    public void updateLiveTask(String id, IslandLiveData data, int priority) {
        Activity activity = switch (data) {
            case IslandLiveData.Music ignored -> Activity.MUSIC;
            case IslandLiveData.Wave ignored -> Activity.WAVE;
            case IslandLiveData.Revive ignored -> Activity.REVIVE;
        };
        tasks.start(id, activity, priority);
        IslandTasks.Task task = tasks.get(id);
        task.live = data;
        if (data instanceof IslandLiveData.Wave wave) task.countdown.update(wave);
        if (data instanceof IslandLiveData.Music music) {
            task.playing = music.playing();
            task.musicTime = music.positionNow();
            if (!java.util.Objects.equals(liveCoverKey, music.coverKey()) || liveCoverBytes != music.cover()) {
                if (liveCover != null) { liveCover.close(); liveCover = null; }
                liveCoverKey = music.coverKey();
                liveCoverBytes = music.cover();
                if (music.cover() != null) try { liveCover = Image.makeFromEncoded(music.cover()); } catch (RuntimeException ignored) { }
            }
        }
        synchronize();
    }

    public void setMusicControl(java.util.function.Consumer<String> control) { musicControl = control; }

    public void setConcurrentWave(IslandLiveData.Wave wave) { concurrentWave = wave; }

    public void setMusicLyrics(boolean enabled, IslandLiveData.Lyric lyric) {
        setMusicLyrics(enabled, lyric, 0);
    }

    private double lyricHighlightAdvance;

    public void setMusicLyrics(boolean enabled, IslandLiveData.Lyric lyric, double advanceSeconds) {
        lyricsEnabled = enabled;
        musicLyric = enabled ? lyric : null;
        lyricHighlightAdvance = Double.isFinite(advanceSeconds) ? Math.clamp(advanceSeconds, -2, 2) : 0;
    }

    private float highlightProgress(IslandLiveData.Lyric lyric, IslandLiveData.Music music) {
        if (lyric == null) return -1;
        float fraction = lyric.sungFraction(music, lyricHighlightAdvance);
        if (fraction <= 0 || fraction >= 1 || lyric.wordCues().isEmpty()) return fraction;
        String text = lyric.text();
        int count = text.codePointCount(0, text.length());
        double position = fraction * count;
        int complete = Math.min((int) position, count - 1);
        int end = text.offsetByCodePoints(0, complete), next = text.offsetByCodePoints(end, 1);
        var font = textFont(text, 10, true);
        float before = font.measureTextWidth(text.substring(0, end));
        float after = font.measureTextWidth(text.substring(0, next));
        float width = font.measureTextWidth(text);
        return width <= 0 ? fraction : Math.clamp((before + (after - before) * (float) (position - complete)) / width, 0, 1);
    }

    private String musicCaption(IslandLiveData.Music music) {
        var lyric = currentLyric(music);
        return lyric == null ? music.title() : lyric.text();
    }

    private IslandLiveData.Lyric currentLyric(IslandLiveData.Music music) {
        return lyricsEnabled && musicLyric != null && musicLyric.title().equals(music.title())
                && musicLyric.artist().equals(music.artist()) && !musicLyric.text().isBlank()
                && System.nanoTime() - musicLyric.sampledNanos() < 3_000_000_000L ? musicLyric : null;
    }

    private void advanceCaption(double seconds) {
        for (var task : tasks.visible()) {
            if (!(task.live instanceof IslandLiveData.Music music)) continue;
            String track = music.title() + "\u0000" + music.artist();
            String next = musicCaption(music);
            var lyric = currentLyric(music);
            double start = lyric == null ? Double.NaN : lyric.startSeconds();
            float progress = highlightProgress(lyric, music);
            if (!track.equals(captionTrack)) {
                // A new song must never crossfade through the previous song's lyric.
                captionTrack = track; caption = next; previousCaption = ""; captionMix = 1; captionAge = 0;
                captionOffset = previousCaptionOffset = 0;
                captionMeasuredWidth = textFont(next, 10, true).measureTextWidth(next);
            } else if (!next.equals(caption)) {
                previousCaption = caption; caption = next; captionMix = 0; captionAge = 0;
                previousCaptionProgress = captionProgress;
                previousCaptionOffset = captionOffset;
                captionMeasuredWidth = textFont(next, 10, true).measureTextWidth(next);
            } else if (Double.compare(start, captionStart) != 0 || (progress >= 0 && progress < captionProgress - .1f)) {
                // Repeated identical lines and backwards seeks reset their own scrolling phase.
                captionAge = 0;
            }
            captionStart = start; captionProgress = progress;
            captionMix = Math.min(1, captionMix + (float) seconds / .22f);
            if (music.playing()) captionAge += seconds;
            return;
        }
        captionTrack = caption = previousCaption = ""; captionMix = 1; captionAge = 0;
        captionStart = Double.NaN; captionProgress = previousCaptionProgress = -1;
    }

    public void setMusicVisuals(IslandLiveData.Audio audio, IslandLiveData.Palette palette) {
        musicAudio = audio;
        musicPalette = palette == null ? IslandLiveData.Palette.DEFAULT : palette;
    }

    public List<String> visibleTaskIds() {
        return tasks.visible().stream().map(task -> task.id).toList();
    }

    public String expandedTaskId() { return expanded ? focusedId : null; }

    public record TaskSnapshot(String id, Activity activity, double musicSeconds, double timerSeconds,
                               boolean playing, boolean timerRunning) {}

    public TaskSnapshot taskSnapshot(String id) {
        IslandTasks.Task task = tasks.get(id);
        return task == null ? null : new TaskSnapshot(task.id, task.activity, task.musicTime,
                task.timerSeconds, task.playing, task.timerRunning);
    }

    public void expandTask(String id) {
        if ((primary == null || !primary.id.equals(id)) && (secondary == null || !secondary.id.equals(id))) return;
        focusedId = id;
        setExpanded(true);
    }

    public void expandSecondary() { if (secondary != null) expandTask(secondary.id); }

    public boolean hasSecondary() { return secondary != null; }

    private void synchronize() {
        List<IslandTasks.Task> visible = tasks.visible();
        primary = visible.isEmpty() ? null : visible.getFirst();
        secondary = visible.size() < 2 ? null : visible.get(1);
        if (expanded && (focusedId == null || visible.stream().noneMatch(task -> task.id.equals(focusedId)))) {
            // The expanded task ended or lost its slot: return to the remaining live tasks.
            expanded = false;
            merging = false;
            focusedId = null;
        }
        if (secondary == null) merging = false;
        IslandTasks.Task next = expanded && !merging ? tasks.get(focusedId) : primary;
        if (presented != next) {
            if (activityMix >= 0.5f || outgoing == null) outgoing = presented;
            presented = next;
            activityMix = 0;
        }
        if (secondary != null && satellite != secondary) {
            if (satelliteMix >= 0.5f || outgoingSatellite == null) outgoingSatellite = satellite;
            satellite = secondary;
            satelliteMix = 0;
        }
        updateTargets();
    }

    private void updateTargets() {
        boolean opening = expanded && !merging;
        float compactWidth = primary == null ? 96 : secondary == null ? primary.activity.compactWidth : 124;
        if (primary != null && primary.live instanceof IslandLiveData.Music music && currentLyric(music) != null
                && captionTrack.equals(music.title() + "\u0000" + music.artist())) {
            // Reserve cover, padding and meter. A second task keeps a smaller text budget.
            compactWidth = Math.clamp(captionMeasuredWidth + 86, secondary == null ? 138 : 124,
                    secondary == null ? 280 : 232);
        }
        width.target(opening ? presented.activity.expandedWidth : primary == null ? 96
                : compactWidth);
        height.target(opening ? presented.activity.expandedHeight : primary == null ? 28 : 36);
        reveal.target(opening ? 1 : 0);
        // On the way back, finish collapsing the details before releasing the detached lobe.
        docking.target(secondary != null && (expanded || reveal.value() > 0.10f) ? 1 : 0);
        split.target(secondary == null ? 0 : 1);
        if (secondary != null) satelliteWidth.target(secondary.activity == Activity.TIMER ? 60 : 36);
    }

    public void advance(double seconds) {
        if (closed || seconds <= 0) return;
        advanceMusicVisuals(seconds);
        advanceCaption(seconds);
        if (tasks.advance(seconds)) synchronize();
        double remaining = seconds;
        while (remaining > 0.000001) {
            double dt = Math.min(remaining, 1.0 / 120);
            updateTargets();
            docking.advance(dt);
            if (merging && docking.value() >= OPEN_DURING_DOCKING) {
                merging = false;
                synchronize();
            }
            width.advance(dt);
            height.advance(dt);
            reveal.advance(dt);
            split.advance(dt);
            satelliteWidth.advance(dt);
            activityMix = Math.min(1, activityMix + (float) dt / 0.32f);
            satelliteMix = Math.min(1, satelliteMix + (float) dt / 0.32f);
            remaining -= dt;
        }
        if (activityMix == 1) outgoing = null;
        if (satelliteMix == 1) outgoingSatellite = null;
        if (secondary == null && split.value() < 0.002f) { satellite = null; outgoingSatellite = null; }
    }

    public void draw(Canvas canvas, float centerX, float top) {
        draw(canvas, centerX, top, null);
    }

    void draw(Canvas canvas, float centerX, float top, DynamicIslandGlass.Background background) {
        if (primary == null) return;
        float w = width.value();
        float h = height.value();
        float p = clamp(reveal.value());
        lastX = centerX - w / 2;
        lastY = top;
        layoutSatellite(w);
        RRect shell = RRect.makeXYWH(lastX, top, w, h, lerp(18, 38, p));
        // Use the same material for detached, touching and fully docked shells.
        boolean joined = satellite != null && clamp(split.value()) > 0.002f;
        if (joined) glass.drawJoined(canvas, lastX, top, w, h, lerp(18, 38, p),
                Rect.makeXYWH(lastSatelliteX, lastSatelliteY, lastSatelliteWidth, lastSatelliteHeight), bridgeBlend, background);
        else glass.draw(canvas, lastX, top, w, h, lerp(18, 38, p), background);

        int saved = canvas.save();
        try {
            // Clip AFTER the content layer is filtered, keeping the shell perfectly sharp.
            canvas.clipRRect(shell, true);
            drawingContent = true;
            if (outgoing != null) presentation(canvas, outgoing, p, 1 - smooth(activityMix), w, h);
            if (presented != null) presentation(canvas, presented, p, smooth(activityMix), w, h);
        } finally {
            drawingContent = false;
            canvas.restoreToCount(saved);
        }
        drawSatellite(canvas, joined, background);
    }

    private void presentation(Canvas c, IslandTasks.Task state, float p, float mix, float w, float h) {
        if (mix <= 0.002f) return;
        float switchingBlur = outgoing == null ? 0 : (1 - mix) * 5;
        float compactAlpha = (1 - range(p, 0.04f, 0.42f)) * mix;
        if (expanded && !state.id.equals(focusedId)) compactAlpha *= 1 - range(clamp(docking.value()), 0.25f, 0.9f);
        if (compactAlpha > 0.002f) {
            final float contentAlpha = compactAlpha;
            contentLayer(c, w, h, contentAlpha, (1 - contentAlpha / mix) * 5 + switchingBlur,
                    1 - p * 0.14f, 1 - p * 0.25f, 0, () -> {
                        float minimal = clamp(split.value());
                        compactLayer(c, state, w, h, 1 - minimal, false);
                        compactLayer(c, state, w, h, minimal, true);
                    });
        }
        float expandedAlpha = range(p, 0.12f, 0.9f) * mix;
        if (expandedAlpha > 0.002f) {
            contentLayer(c, w, h, expandedAlpha, (1 - p) * 8 + switchingBlur,
                    lerp(0.82f, 1, p), lerp(0.68f, 1, p), (1 - p) * -13,
                    () -> expanded(c, state, w));
        }
    }

    private void contentLayer(Canvas c, float w, float h, float opacity, float blur,
                              float sx, float sy, float dy, Runnable draw) {
        int saved = c.save();
        try {
            int index = Math.min(32, Math.max(0, Math.round(blur * 4)));
            layer.setAlphaf(clamp(opacity)).setImageFilter(blurs[index]);
            c.saveLayer(Rect.makeXYWH(lastX - 24, lastY - 24, w + 48, h + 48), layer);
            c.translate(lastX + w / 2, lastY + 18 + dy);
            c.scale(sx, sy);
            c.translate(-w / 2, -18);
            draw.run();
        } finally {
            c.restoreToCount(saved);
            layer.setImageFilter(null);
        }
    }

    private void compactLayer(Canvas c, IslandTasks.Task task, float w, float h, float alpha, boolean minimal) {
        if (alpha < 0.002f) return;
        int saved = c.save();
        try (Paint opacity = new Paint().setAlphaf(clamp(alpha)).setImageFilter(blurs[Math.round((1 - alpha) * 12)])) {
            c.saveLayer(Rect.makeXYWH(-8, -8, w + 16, h + 16), opacity);
            if (minimal) minimal(c, task, w, true);
            else compact(c, task, w);
        } finally {
            c.restoreToCount(saved);
        }
    }

    private void layoutSatellite(float mainWidth) {
        float separated = clamp(split.value());
        float dock = clamp(docking.value());
        lastSatelliteAlpha = separated * (1 - range(dock, 0.72f, 0.99f));
        float size = separated;
        float tension = (float) Math.sin(dock * Math.PI) * 0.045f;
        float w = satelliteWidth.value() * size * (1 + tension);
        float h = 36 * size * (1 - tension * 0.45f);
        float right = lastX + mainWidth;
        float opening = clamp(reveal.value());
        float cx = lerp(right - w / 2 + (8 + w) * smooth(separated), right - w / 2 - 4 - opening * 20, smooth(dock));
        lastSatelliteX = cx - w / 2;
        lastSatelliteY = lastY + (36 - h) / 2 + opening * 20;
        lastSatelliteWidth = w;
        lastSatelliteHeight = h;
        float gap = lastSatelliteX - right;
        bridgeBlend = 14 * (1 - range(gap, 2, 8)) * (1 - range(dock, 0.65f, 0.95f));
    }

    public record MotionSnapshot(float width, float height, float reveal, float docking,
                                 float satelliteGap, float bridgeBlend, boolean merging) {}

    public MotionSnapshot motionSnapshot() {
        return new MotionSnapshot(width.value(), height.value(), clamp(reveal.value()), clamp(docking.value()),
                lastSatelliteX - lastX - width.value(), bridgeBlend, merging);
    }

    private void drawSatellite(Canvas c, boolean joined, DynamicIslandGlass.Background background) {
        float visibility = lastSatelliteAlpha;
        if (satellite == null || visibility <= 0.002f) return;
        float w = lastSatelliteWidth;
        float h = lastSatelliteHeight;
        float size = h / 36;
        int saved = c.save();
        try (Paint opacity = new Paint().setAlphaf(clamp(visibility))) {
            c.saveLayer(Rect.makeXYWH(lastSatelliteX - 24, lastSatelliteY - 24, w + 48, h + 48), opacity);
            if (!joined) glass.draw(c, lastSatelliteX, lastSatelliteY, w, h, h / 2, background);
            c.clipRRect(RRect.makeXYWH(lastSatelliteX, lastSatelliteY, w, h, h / 2), true);
            drawingContent = true;
            satelliteContent(c, outgoingSatellite, 1 - smooth(satelliteMix), w, h, size, visibility);
            satelliteContent(c, satellite, smooth(satelliteMix), w, h, size, visibility);
        } finally {
            drawingContent = false;
            c.restoreToCount(saved);
        }
    }

    private void satelliteContent(Canvas c, IslandTasks.Task task, float mix, float w, float h, float size, float visibility) {
        if (task == null || mix <= 0.002f) return;
        int saved = c.save();
        try (Paint opacity = new Paint().setAlphaf(clamp(mix)).setImageFilter(
                blurs[Math.min(32, Math.round(((1 - mix) * 4 + (1 - visibility) * 3) * 4))])) {
            c.saveLayer(Rect.makeXYWH(lastSatelliteX - 8, lastSatelliteY - 8, w + 16, h + 16), opacity);
            c.translate(lastSatelliteX + w / 2, lastSatelliteY + h / 2);
            c.scale(size, size);
            c.translate(-satelliteWidth.value() / 2, -18);
            minimal(c, task, satelliteWidth.value(), false);
        } finally {
            c.restoreToCount(saved);
        }
    }

    private void minimal(Canvas c, IslandTasks.Task task, float w, boolean attached) {
        if (attached && task.live != null) { compact(c, task, w); return; }
        float center = attached ? w - 22 : w / 2;
        if (task.live instanceof IslandLiveData.Wave wave) {
            waveCountdown(c, wave, task.countdown, center);
            return;
        }
        if (task.live instanceof IslandLiveData.Revive revive) {
            rescueProgress(c, revive, center);
            return;
        }
        switch (task.activity) {
            case MUSIC -> musicWaveform(c, task, center - 10, 18, 20);
            case TIMER -> {
                String time = clock(task.timerSeconds);
                float x = attached ? w - 12 - font(13, true).measureTextWidth(time)
                        : (w - font(13, true).measureTextWidth(time)) / 2;
                compactText(c, time, x, 13, true, task.activity.accent);
            }
            case CALL -> phone(c, center, 18, 7, task.activity.accent);
            case WAVE -> timerIcon(c, center, 18, 7, task.activity.accent);
            case REVIVE -> rescueIcon(c, center, 18, 7);
        }
    }

    private void waveCountdown(Canvas c, IslandLiveData.Wave wave, IslandCountdown countdown, float center) {
        float radius = 12.3f;
        float remaining = wave.countdownFraction();
        int amber = 0xFFFFB547;
        stroke.setStrokeWidth(1.8f).setColor(0x38FFB547);
        c.drawCircle(center, 18, radius, stroke);
        if (remaining > 0) {
            stroke.setColor(amber);
            if (remaining >= .9999f) c.drawCircle(center, 18, radius, stroke);
            else c.drawArc(center - radius, 18 - radius, center + radius, 18 + radius,
                    -90, 360 * remaining, false, stroke);
        }
        int saved = c.save();
        try {
            // Clip only the glyphs: the glass rim and continuously shrinking ring stay crisp.
            c.clipRRect(RRect.makeXYWH(center - 10.3f, 7.7f, 20.6f, 20.6f, 10.3f), true);
            float leaving = countdown.leaving();
            if (countdown.outgoing != 0)
                countdownDigit(c, countdown.outgoing, center, 1 - leaving,
                        1 - .22f * leaving, -2.2f * leaving, 2.5f * leaving);
            float incoming = countdown.incoming();
            if (countdown.digit != 0)
                countdownDigit(c, countdown.digit, center, incoming,
                        countdown.scale(), 2.2f * (1 - incoming), 2.25f * (1 - incoming));
        } finally { c.restoreToCount(saved); }
    }

    private void countdownDigit(Canvas c, int digit, float center, float alpha, float scale, float dy, float blur) {
        if (alpha <= .001f) return;
        countdownPaint.setAlphaf(alpha).setImageFilter(blurs[Math.clamp(Math.round(blur * 4), 0, 32)]);
        int saved = c.saveLayer(Rect.makeXYWH(center - 18, 0, 36, 36), countdownPaint);
        try {
            c.translate(center, 18 + dy);
            c.scale(scale, scale);
            c.translate(-center, -18);
            String second = Integer.toString(digit);
            Rect ink = textFont(second, 14, true).measureText(second);
            compactText(c, second, center - (ink.getLeft() + ink.getRight()) / 2, 14, true, 0xFFF5F5F7);
        } finally {
            c.restoreToCount(saved);
            countdownPaint.setImageFilter(null);
        }
    }

    private void rescueProgress(Canvas c, IslandLiveData.Revive revive, float center) {
        float radius = 12.3f;
        stroke.setStrokeWidth(1.8f).setColor(0x3855E7AC);
        c.drawCircle(center, 18, radius, stroke);
        if (!revive.rescues().isEmpty()) {
            // The most advanced confirmed rescue owns the ring; pending confirmation has no invented progress.
            var rescue = revive.rescues().stream().filter(IslandLiveData.Rescue::confirmed)
                    .max(java.util.Comparator.comparingDouble(IslandLiveData.Rescue::progress)).orElse(null);
            if (rescue != null) {
                float progress = Math.clamp(rescue.progress(), 0, 1);
                stroke.setColor(Activity.REVIVE.accent);
                if (progress >= .9999f) c.drawCircle(center, 18, radius, stroke);
                else if (progress > 0) c.drawArc(center - radius, 18 - radius, center + radius, 18 + radius,
                        -90, 360 * progress, false, stroke);
            }
        }
        rescueIcon(c, center, 18, 5);
    }

    private void compact(Canvas c, IslandTasks.Task task, float w) {
        if (task.live instanceof IslandLiveData.Wave wave) {
            timerIcon(c, 19, 18, 8, task.activity.accent);
            compactText(c, "R" + wave.round(), 35, 12, true, 0xFFF5F5F7);
            String next = wave.nextSeconds() < 0 ? "W" + wave.count() : (int) Math.ceil(wave.nextSeconds()) + "s";
            compactText(c, next, w - 12 - textFont(next, 13, true).measureTextWidth(next), 13, true, task.activity.accent);
            return;
        }
        if (task.live instanceof IslandLiveData.Revive revive) {
            rescueIcon(c, 19, 18, 8);
            if (!revive.rescues().isEmpty()) {
                IslandLiveData.Rescue first = revive.rescues().getFirst();
                compactText(c, "救援", 34, 11, true, 0xFFF5F5F7);
                String remaining = first.confirmed() ? String.format(java.util.Locale.ROOT, "%.1fs", first.remaining()) : "连接中";
                compactText(c, fitText(remaining, 13, true, 50), w - 58, 13, true, task.activity.accent);
            }
            return;
        }
        Activity state = task.activity;
        switch (state) {
            case MUSIC -> {
                artwork(c, task, 9, 7, 22);
                if (task.live instanceof IslandLiveData.Music music && (w > 140 || (lyricsEnabled && w > 104)))
                    drawMusicCaption(c, music, w - 78);
                else if (task.live instanceof IslandLiveData.Music && concurrentWave != null && w > 104)
                    compactText(c, fitText(waveSummary(concurrentWave, false), 9, true, w - 78), 38, 9, true, 0xFFFFB547);
                musicWaveform(c, task, w - 30, 18, 20);
            }
            case TIMER -> {
                timerIcon(c, 19, 18, 8, state.accent);
                compactText(c, clock(task.timerSeconds), w - 52, 14, true, state.accent);
            }
            case CALL -> {
                phone(c, 19, 18, 7, state.accent);
                waveform(c, w - 46, 18, 19, state.accent, true, task.elapsed);
                compactText(c, clock(24 + task.elapsed), w - 29, 10, true, state.accent);
            }
            case WAVE -> timerIcon(c, 19, 18, 8, state.accent);
            case REVIVE -> rescueIcon(c, 19, 18, 8);
        }
    }

    private void expanded(Canvas c, IslandTasks.Task state, float w) {
        switch (state.activity) {
            case MUSIC -> music(c, state, w);
            case TIMER -> timer(c, state, w);
            case CALL -> call(c, w);
            case WAVE -> { if (state.live instanceof IslandLiveData.Wave wave) wave(c, wave, w); }
            case REVIVE -> { if (state.live instanceof IslandLiveData.Revive revive) revive(c, revive, w); }
        }
    }

    private void music(Canvas c, IslandTasks.Task task, float w) {
        if (task.live instanceof IslandLiveData.Music music) { liveMusic(c, task, music, w); return; }
        artwork(c, 20, 20, 56);
        text(c, "Midnight City", 88, 42, 17, true, 0xFFF5F5F7);
        text(c, "M83 · Hurry Up, We're Dreaming", 88, 62, 11, false, 0xFF8E8E93);
        waveform(c, w - 36, 47, 22, Activity.MUSIC.accent, task.playing, task.elapsed);
        float progress = (float) task.musicTime / 214;
        rounded(c, 20, 92, w - 40, 3, 1.5f, 0xFF343437);
        rounded(c, 20, 92, (w - 40) * progress, 3, 1.5f, 0xFFEEEEF0);
        text(c, clock(task.musicTime), 20, 109, 9, false, 0xFF8E8E93);
        String remaining = "-" + clock(214 - task.musicTime);
        text(c, remaining, w - 20 - font(9, false).measureTextWidth(remaining), 109, 9, false, 0xFF8E8E93);
        transport(c, w / 2 - 62, 136, -1);
        if (task.playing) {
            rounded(c, w / 2 - 8, 125, 5, 21, 1.4f, 0xFFF5F5F7);
            rounded(c, w / 2 + 3, 125, 5, 21, 1.4f, 0xFFF5F5F7);
        } else triangle(c, w / 2 - 6, 125, 17, 21, 0xFFF5F5F7, false);
        transport(c, w / 2 + 62, 136, 1);
        for (int i = 0; i < 3; i++) {
            stroke.setColor(0xFF8E8E93).setStrokeWidth(1.3f);
            c.drawArc(w - 33 - i * 3, 130 - i * 3, w - 27 + i * 3, 136 + i * 3, 220, 100, false, stroke);
        }
        triangle(c, w - 34, 140, 8, 5, 0xFF8E8E93, false);
    }

    private void timer(Canvas c, IslandTasks.Task task, float w) {
        timerIcon(c, 33, 36, 11, Activity.TIMER.accent);
        text(c, "Timer", 54, 34, 13, true, 0xFFF5F5F7);
        text(c, task.timerRunning ? "Counting down" : "Paused", 54, 50, 10, false, 0xFF8E8E93);
        text(c, clock(task.timerSeconds), 21, 95, 37, false, Activity.TIMER.accent);
        circle(c, w - 89, 66, 22, 0xFF392B15);
        if (task.timerRunning) {
            rounded(c, w - 95, 58, 4, 16, 1, Activity.TIMER.accent);
            rounded(c, w - 87, 58, 4, 16, 1, Activity.TIMER.accent);
        } else triangle(c, w - 95, 58, 14, 16, Activity.TIMER.accent, false);
        circle(c, w - 36, 66, 22, 0xFF28282B);
        line(c, w - 41, 61, w - 31, 71, 2, 0xFFD1D1D6);
        line(c, w - 31, 61, w - 41, 71, 2, 0xFFD1D1D6);
    }

    private void liveMusic(Canvas c, IslandTasks.Task task, IslandLiveData.Music music, float w) {
        artwork(c, task, 20, 20, 56);
        fittedText(c, music.title(), 88, 40, 16, true, 0xFFF5F5F7, w - 135);
        fittedText(c, music.artist().isBlank() ? "QQ 音乐" : music.artist(), 88, 61, 11, false, 0xFFD5DFEA, w - 135);
        musicWaveform(c, task, w - 37, 47, 22);
        rounded(c, 20, 90, w - 40, 3, 1.5f, 0x60495B67);
        if (music.duration() > 0) {
            double position = music.positionNow();
            float progress = (float) Math.clamp(position / music.duration(), 0, 1);
            musicProgress(c, 20, 90, (w - 40) * progress, 3);
            circle(c, 20 + (w - 40) * progress, 91.5f, 2.4f, 0xFFF5F5F7);
            text(c, clock(position), 20, 108, 9, false, 0xFFD5DFEA);
            String remaining = "-" + clock(music.duration() - position);
            text(c, remaining, w - 20 - font(9, false).measureTextWidth(remaining), 108, 9, false, 0xFFD5DFEA);
        } else {
            // Unknown timeline stays visibly indeterminate; never invent a song duration or position.
            float segment = (w - 40) * .18f;
            float travel = music.playing() ? (float) (.5 - .5 * Math.cos(task.elapsed * 1.8)) : 0;
            musicProgress(c, 20 + (w - 40 - segment) * travel, 90, segment, 3);
            text(c, "--:--", 20, 108, 9, false, 0xFFD5DFEA);
            text(c, "--:--", w - 20 - font(9, false).measureTextWidth("--:--"), 108, 9, false, 0xFFD5DFEA);
        }
        // Controls are dimmed when the selected QQ session does not support that action.
        int saved = c.save();
        try (Paint opacity = new Paint().setAlphaf(music.canPrevious() ? 1 : .3f)) {
            c.saveLayer(null, opacity); transport(c, w / 2 - 62, 133, -1);
        } finally { c.restoreToCount(saved); }
        saved = c.save();
        try (Paint opacity = new Paint().setAlphaf(music.canNext() ? 1 : .3f)) {
            c.saveLayer(null, opacity); transport(c, w / 2 + 62, 133, 1);
        } finally { c.restoreToCount(saved); }
        int color = music.canToggle() ? 0xFFF5F5F7 : 0x60F5F5F7;
        if (music.playing()) {
            rounded(c, w / 2 - 8, 122, 5, 21, 1.4f, color);
            rounded(c, w / 2 + 3, 122, 5, 21, 1.4f, color);
        } else triangle(c, w / 2 - 6, 122, 17, 21, color, false);
        if (concurrentWave != null) fittedText(c, waveSummary(concurrentWave, true), 20, 162, 9, false, 0xFFFFB547, w - 40);
    }

    private void wave(Canvas c, IslandLiveData.Wave wave, float w) {
        int accent = wave.boss() ? 0xFFFF8D99 : Activity.WAVE.accent;
        timerIcon(c, 28, 30, 10, accent);
        text(c, "ROUND " + wave.round(), 48, 30, 14, true, 0xFFF5F5F7);
        fittedText(c, wave.map(), 48, 46, 10, false, 0xFFD5DFEA, 176);
        String count = Math.max(0, wave.current() + 1) + " / " + wave.count();
        text(c, count, w - 24 - font(14, true).measureTextWidth(count), 34, 14, true, accent);
        String label = wave.nextSeconds() < 0 ? "本回合全部波次已出" : "下一波抵达";
        text(c, label, 22, 72, 11, false, 0xFFD5DFEA);
        String next = wave.nextSeconds() < 0 ? "清场中" : String.format(java.util.Locale.ROOT, "%.1f s", wave.nextSeconds());
        text(c, next, 22, 103, 26, true, accent);
        String badge = wave.bossName().isBlank() ? wave.boss() ? "BOSS ROUND" : "WAVE " + Math.max(0, wave.current() + 1) : wave.bossName();
        fittedText(c, badge, 173, 84, 11, true, accent, w - 196);
        text(c, "回合用时 " + clock(wave.elapsed()), 173, 103, 10, false, 0xFFD5DFEA);
        if (wave.count() == 0) return;
        float gap = 5, segment = (w - 44 - gap * (wave.count() - 1)) / wave.count();
        for (int i = 0; i < wave.count(); i++) {
            float x = 22 + i * (segment + gap);
            rounded(c, x, 120, segment, 5, 2.5f, 0x304C6676);
            float progress = i < wave.current() ? 1 : i == wave.current() ? wave.progress() : 0;
            rounded(c, x, 120, segment * progress, 5, 2.5f, accent);
            centeredText(c, Integer.toString(i + 1), x + segment / 2, 139, 9, true,
                    i == wave.current() ? 0xFFFFFFFF : 0xFFB5C6D3);
        }
    }

    private void rescueIcon(Canvas c, float x, float y, float r) {
        rounded(c, x - r, y - 2, r * 2, 4, 1.5f, Activity.REVIVE.accent);
        rounded(c, x - 2, y - r, 4, r * 2, 1.5f, Activity.REVIVE.accent);
    }

    private void revive(Canvas c, IslandLiveData.Revive revive, float w) {
        rescueIcon(c, 28, 29, 10);
        text(c, "救援进行中", 48, 32, 15, true, 0xFFF5F5F7);
        text(c, "REVIVE AURA", 48, 47, 9, false, 0xFFD5DFEA);
        text(c, revive.rescues().size() + " ACTIVE", w - 88, 32, 10, true, Activity.REVIVE.accent);
        int count = Math.min(3, revive.rescues().size());
        for (int i = 0; i < count; i++) {
            IslandLiveData.Rescue rescue = revive.rescues().get(i);
            float y = 69 + 34 * i;
            circle(c, 27, y - 4, 4, rescue.confirmed() ? Activity.REVIVE.accent : 0xFFFFCA79);
            fittedText(c, rescue.name(), 39, y, 12, true, 0xFFF5F5F7, w - 133);
            String status = rescue.confirmed() ? String.format(java.util.Locale.ROOT, "%.1f s", rescue.remaining()) : "等待确认";
            fittedText(c, status, w - 86, y, 10, false, rescue.confirmed() ? Activity.REVIVE.accent : 0xFFFFCA79, 64);
            rounded(c, 39, y + 9, w - 61, 4, 2, 0x304C6676);
            rounded(c, 39, y + 9, (w - 61) * Math.clamp(rescue.progress(), 0, 1), 4, 2, Activity.REVIVE.accent);
        }
        if (revive.rescues().size() > 3) text(c, "+" + (revive.rescues().size() - 3) + " 位队友", 39, 163, 9, false, 0xFFD5DFEA);
        if (concurrentWave != null) {
            timerIcon(c, 28, 178, 6, 0xFFFFB547);
            fittedText(c, waveSummary(concurrentWave, true), 40, 181, 10, false, 0xFFFFB547, w - 62);
        }
    }

    private static String waveSummary(IslandLiveData.Wave wave, boolean detail) {
        String next = wave.nextSeconds() < 0 ? "清场" : (int) Math.ceil(wave.nextSeconds()) + "s";
        return detail ? "ROUND " + wave.round() + " · " + (wave.nextSeconds() < 0 ? "清场中" : "下一波 " + next)
                : "R" + wave.round() + " " + next;
    }

    private void artwork(Canvas c, IslandTasks.Task task, float x, float y, float size) {
        if (!(task.live instanceof IslandLiveData.Music)) { artwork(c, x, y, size); return; }
        int saved = c.save();
        try {
            c.clipRRect(RRect.makeXYWH(x, y, size, size, size * .18f), true);
            if (liveCover != null) {
                float srcSize = Math.min(liveCover.getWidth(), liveCover.getHeight());
                c.drawImageRect(liveCover, Rect.makeXYWH((liveCover.getWidth() - srcSize) / 2,
                        (liveCover.getHeight() - srcSize) / 2, srcSize, srcSize), Rect.makeXYWH(x, y, size, size));
            } else {
                rounded(c, x, y, size, size, size * .18f, 0x80405262);
                circle(c, x + size / 2, y + size / 2, size * .27f, 0xFF9DECD0);
                circle(c, x + size / 2, y + size / 2, size * .08f, 0xFF203C41);
            }
        } finally { c.restoreToCount(saved); }
    }

    private void call(Canvas c, float w) {
        circle(c, 41, 40, 22, 0xFF303C4E);
        text(c, "A", 31, 48, 22, true, 0xFFDCE7FA);
        text(c, "Alex", 76, 36, 17, true, 0xFFF5F5F7);
        text(c, "Incoming call", 76, 54, 11, false, 0xFF8E8E93);
        circle(c, w - 92, 42, 22, 0xFFFF453A);
        phone(c, w - 92, 42, 10, 0xFFFFFFFF);
        circle(c, w - 37, 42, 22, 0xFF30D158);
        phone(c, w - 37, 42, 10, 0xFFFFFFFF);
        text(c, "Decline", w - 111, 83, 10, false, 0xFF8E8E93);
        text(c, "Accept", w - 54, 83, 10, false, 0xFF8E8E93);
    }

    /** Shared drawing helpers keep demo UI typography consistent with the island. */
    public void text(Canvas c, String text, float x, float baseline, int size, boolean heavy, int color) {
        Font selectedFont = textFont(text, size, heavy);
        if (drawingContent) {
            c.drawString(text, x, baseline + 0.8f, selectedFont, textShadow);
            if (color == 0xFF8E8E93) color = 0xFFD5DFEA;
        }
        fill.setColor(color);
        c.drawString(text, x, baseline, selectedFont, fill);
    }

    public void centeredText(Canvas c, String text, float center, float baseline, int size, boolean heavy, int color) {
        text(c, text, center - textFont(text, size, heavy).measureTextWidth(text) / 2, baseline, size, heavy, color);
    }

    private Font textFont(String text, int size, boolean heavy) {
        if (text.codePoints().noneMatch(cp -> cp > 0x2E7F)) return font(size, heavy);
        return fonts.computeIfAbsent("zh:" + size + ":" + heavy,
                ignored -> new Font(heavy ? chineseBold : chinese, size).setSubpixel(true).setEdging(FontEdging.ANTI_ALIAS));
    }

    private void fittedText(Canvas c, String value, float x, float y, int size, boolean heavy, int color, float maxWidth) {
        text(c, fitText(value, size, heavy, maxWidth), x, y, size, heavy, color);
    }

    private String fitText(String value, int size, boolean heavy, float maxWidth) {
        Font f = textFont(value, size, heavy);
        if (f.measureTextWidth(value) > maxWidth) {
            int end = value.length();
            while (end > 0 && f.measureTextWidth(value.substring(0, end) + "…") > maxWidth) end = value.offsetByCodePoints(end, -1);
            value = value.substring(0, end) + "…";
        }
        return value;
    }

    private void compactText(Canvas c, String value, float x, int size, boolean heavy, int color) {
        // Center the visible glyphs with the cover and icons, including Chinese font fallback.
        Rect ink = textFont(value, size, heavy).measureText(value);
        text(c, value, x, 18 - (ink.getTop() + ink.getBottom()) / 2, size, heavy, color);
    }

    private void drawMusicCaption(Canvas c, IslandLiveData.Music music, float available) {
        String text = musicCaption(music);
        // During task crossfades use that task's own title, not the active track's lyric.
        boolean active = captionTrack.equals(music.title() + "\u0000" + music.artist()) && text.equals(caption);
        int saved = c.save();
        try {
            c.clipRect(Rect.makeXYWH(38, 3, available, 30));
            if (active && captionMix < 1 && !previousCaption.isEmpty())
                drawCaptionLayer(c, previousCaption, previousCaptionOffset, 1 - captionMix, -captionMix * 2, previousCaptionProgress, available);
            float overflow = Math.max(0, textFont(text, 10, true).measureTextWidth(text) - available);
            var lyric = currentLyric(music);
            float progress = highlightProgress(lyric, music);
            boolean scrolling = lyric != null && overflow > 0;
            float offset = 0;
            if (scrolling && progress >= 0) {
                // Keep the singing boundary in view, rather than ping-ponging away from it.
                offset = Math.clamp((overflow + available) * progress - available * .65f, 0, overflow);
            } else if (scrolling && active) {
                double travel = overflow / 20.0;
                double phase = captionAge % (2 * travel + 2.4);
                if (phase > 1.2 && phase < 1.2 + travel) offset = (float) ((phase - 1.2) * 20);
                else if (phase >= 1.2 + travel && phase < 2.4 + travel) offset = overflow;
                else if (phase >= 2.4 + travel) offset = (float) (overflow - (phase - 2.4 - travel) * 20);
            }
            if (active) captionOffset = offset;
            drawCaptionLayer(c, scrolling ? text : fitText(text, 10, true, available), offset,
                    active ? captionMix : 1, active ? (1 - captionMix) * 2 : 0, progress, available);
        } finally { c.restoreToCount(saved); }
    }

    private void drawCaptionLayer(Canvas c, String text, float offset, float alpha, float dy, float progress, float available) {
        if (alpha <= .001f) return;
        int saved = c.saveLayer(null, captionPaint.setAlphaf(alpha));
        try {
            c.translate(0, dy);
            float x = 38 - offset;
            compactText(c, text, x, 10, true, progress < 0 ? 0xFFF5F5F7 : 0xFF9CA6B3);
            if (progress > 0) {
                float sungWidth = textFont(text, 10, true).measureTextWidth(text) * progress;
                int highlight = c.save();
                c.clipRect(Rect.makeXYWH(x, 0, sungWidth, 36));
                compactText(c, text, x, 10, true, mixColor(musicColorStart, 0xFFFFFFFF, .72f));
                c.restoreToCount(highlight);
            }
            if (offset > .5f) {
                captionFadePaint.setShader(captionLeftFade);
                c.drawRect(Rect.makeXYWH(38, 0, 8, 36), captionFadePaint);
            }
            if (textFont(text, 10, true).measureTextWidth(text) - offset > available + .5f) {
                c.translate(38 + available - 8, 0);
                captionFadePaint.setShader(captionRightFade);
                c.drawRect(Rect.makeXYWH(0, 0, 8, 36), captionFadePaint);
            }
        }
        finally { c.restoreToCount(saved); }
    }

    private Font font(int size, boolean heavy) {
        return fonts.computeIfAbsent(size + ":" + heavy, ignored -> new Font(heavy ? bold : regular, size)
                .setSubpixel(true).setEdging(FontEdging.ANTI_ALIAS));
    }

    public void rounded(Canvas c, float x, float y, float w, float h, float r, int color) {
        if (w <= 0 || h <= 0) return;
        fill.setColor(color);
        c.drawRRect(RRect.makeXYWH(x, y, w, h, r), fill);
    }

    private void circle(Canvas c, float x, float y, float r, int color) {
        fill.setColor(color);
        c.drawCircle(x, y, r, fill);
    }

    private void line(Canvas c, float x1, float y1, float x2, float y2, float thickness, int color) {
        stroke.setColor(color).setStrokeWidth(thickness);
        c.drawLine(x1, y1, x2, y2, stroke);
    }

    private void waveform(Canvas c, float x, float y, float w, int color, boolean playing, double elapsed) {
        for (int i = 0; i < 5; i++) {
            float wave = playing ? (float) (0.5 + 0.5 * Math.sin(elapsed * 7.5 + i * 1.8)) : 0.1f;
            float h = 4 + wave * (i == 2 ? 17 : 12);
            rounded(c, x + i * w / 5, y - h / 2, 2.5f, h, 1.25f, color);
        }
    }

    private void advanceMusicVisuals(double seconds) {
        boolean fresh = musicAudio != null && musicAudio.available()
                && System.nanoTime() - musicAudio.sampledNanos() < 500_000_000L;
        if (fresh && musicAudio.sampledNanos() != lastAudioSample) {
            System.arraycopy(recentPeaks, 0, recentPeaks, 1, recentPeaks.length - 1);
            recentPeaks[0] = musicAudio.peak() < .0015f ? 0 : Math.clamp(musicAudio.peak(), 0, 1);
            lastAudioSample = musicAudio.sampledNanos();
        }
        float peak = fresh ? recentPeaks[0] : 0;
        peakEnvelope = Math.max(.015f, Math.max(peak, peakEnvelope * (float) Math.exp(-seconds / 3)));
        // A wider real sample history separates the columns. Expand loudness contrast
        // instead of compressing every sample close to full height; emphasize actual rises.
        for (int i = 0; i < 5; i++) {
            int offset = METER_OFFSETS[i];
            float level = Math.clamp((recentPeaks[offset] / peakEnvelope - .08f) / .92f, 0, 1);
            float rise = Math.max(0, recentPeaks[offset] - recentPeaks[offset + 2]) / peakEnvelope;
            float target = fresh ? Math.clamp((float) Math.pow(level, 1.65) + rise * .3f, 0, 1) : 0;
            float response = (float) (1 - Math.exp(-seconds / (target > musicBars[i] ? .022 : .085)));
            musicBars[i] += (target - musicBars[i]) * response;
        }
        float colorMix = (float) (1 - Math.exp(-seconds * 5));
        musicColorStart = mixColor(musicColorStart, musicPalette.start(), colorMix);
        musicColorEnd = mixColor(musicColorEnd, musicPalette.end(), colorMix);
    }

    private void musicWaveform(Canvas c, IslandTasks.Task task, float x, float y, float w) {
        if (task.live == null) {
            waveform(c, x, y, w, Activity.MUSIC.accent, task.playing, task.elapsed);
            return;
        }
        for (int i = 0; i < 5; i++) {
            float height = 2.5f + (task.playing ? musicBars[i] : 0) * (i == 2 ? 24 : 20);
            rounded(c, x + i * w / 5, y - height / 2, 2.5f, height, 1.25f,
                    mixColor(musicColorStart, musicColorEnd, i / 4f));
        }
    }

    private void musicProgress(Canvas c, float x, float y, float w, float h) {
        if (w <= 0) return;
        try (Shader gradient = Shader.makeLinearGradient(x, y, x + w, y,
                new int[]{musicColorStart, musicColorEnd})) {
            fill.setShader(gradient).setColor(0xFFFFFFFF);
            c.drawRRect(RRect.makeXYWH(x, y, w, h, h / 2), fill);
        } finally { fill.setShader(null); }
    }

    private static int mixColor(int from, int to, float amount) {
        int r = Math.round(lerp(from >> 16 & 255, to >> 16 & 255, amount));
        int g = Math.round(lerp(from >> 8 & 255, to >> 8 & 255, amount));
        int b = Math.round(lerp(from & 255, to & 255, amount));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private void artwork(Canvas c, float x, float y, float size) {
        int saved = c.save();
        try {
            c.clipRRect(RRect.makeXYWH(x, y, size, size, size * 0.18f), true);
            try (Shader gradient = Shader.makeLinearGradient(x, y, x + size, y + size,
                    new int[]{0xFF243A74, 0xFFCA597D, 0xFFF6BA8B})) {
                fill.setShader(gradient);
                c.drawRect(Rect.makeXYWH(x, y, size, size), fill);
                fill.setShader(null);
            }
            circle(c, x + size * 0.7f, y + size * 0.28f, size * 0.17f, 0xFFFFC8A8);
            try (Path mountains = new Path()) {
                mountains.moveTo(x, y + size * 0.75f).lineTo(x + size * 0.3f, y + size * 0.4f)
                        .lineTo(x + size * 0.52f, y + size * 0.68f).lineTo(x + size * 0.72f, y + size * 0.48f)
                        .lineTo(x + size, y + size * 0.8f).lineTo(x + size, y + size).lineTo(x, y + size).closePath();
                fill.setColor(0xFF142344);
                c.drawPath(mountains, fill);
            }
            if (size > 30) text(c, "M83", x + size * 0.13f, y + size * 0.89f, 11, true, 0xFFF7CEDD);
        } finally {
            fill.setShader(null);
            c.restoreToCount(saved);
        }
    }

    private void triangle(Canvas c, float x, float y, float w, float h, int color, boolean left) {
        try (Path path = new Path()) {
            path.moveTo(left ? x + w : x, y).lineTo(left ? x : x + w, y + h / 2)
                    .lineTo(left ? x + w : x, y + h).closePath();
            fill.setColor(color);
            c.drawPath(path, fill);
        }
    }

    private void transport(Canvas c, float x, float y, int direction) {
        boolean left = direction < 0;
        triangle(c, x - 11, y - 7, 11, 14, 0xFFF5F5F7, left);
        triangle(c, x - 1, y - 7, 11, 14, 0xFFF5F5F7, left);
    }

    private void timerIcon(Canvas c, float x, float y, float r, int color) {
        stroke.setColor(color).setStrokeWidth(1.8f);
        c.drawCircle(x, y + 1, r * 0.78f, stroke);
        line(c, x - r * 0.27f, y - r, x + r * 0.27f, y - r, 1.8f, color);
        line(c, x, y + 1, x, y - r * 0.38f, 1.8f, color);
        line(c, x, y + 1, x + r * 0.32f, y + r * 0.26f, 1.8f, color);
    }

    private void phone(Canvas c, float x, float y, float r, int color) {
        stroke.setColor(color).setStrokeWidth(r * 0.38f);
        try (Path path = new Path()) {
            path.moveTo(x - r * 0.6f, y - r * 0.65f).cubicTo(x - r, y + r * 0.05f,
                    x + r * 0.05f, y + r, x + r * 0.65f, y + r * 0.5f);
            c.drawPath(path, stroke);
        }
        line(c, x - r * 0.62f, y - r * 0.7f, x - r * 0.22f, y - r * 0.32f, r * 0.4f, color);
        line(c, x + r * 0.32f, y + r * 0.18f, x + r * 0.72f, y + r * 0.56f, r * 0.4f, color);
    }

    public boolean contains(float x, float y) {
        float w = width.value();
        float h = height.value();
        float radius = Math.min(Math.min(w, h) / 2, lerp(18, 38, clamp(reveal.value())));
        if (x < lastX || x > lastX + w || y < lastY || y > lastY + h) return false;
        float dx = Math.max(Math.abs(x - lastX - w / 2) - (w / 2 - radius), 0);
        float dy = Math.max(Math.abs(y - lastY - h / 2) - (h / 2 - radius), 0);
        return dx * dx + dy * dy <= radius * radius;
    }

    public boolean satelliteContains(float x, float y) {
        if (secondary == null || lastSatelliteAlpha < 0.85f) return false;
        float radius = lastSatelliteHeight / 2;
        float dx = Math.max(Math.abs(x - lastSatelliteX - lastSatelliteWidth / 2)
                - (lastSatelliteWidth / 2 - radius), 0);
        float dy = Math.abs(y - lastSatelliteY - radius);
        return dx * dx + dy * dy <= radius * radius;
    }

    /** Desktop equivalent of holding either minimal presentation to expand that task. */
    public void expandAt(float x, float y) {
        if (satelliteContains(x, y)) expandTask(secondary.id);
        else if (contains(x, y) && primary != null) expandTask(primary.id);
    }

    public void click(float x, float y) {
        if (satelliteContains(x, y)) { expandTask(secondary.id); return; }
        if (!contains(x, y)) return;
        float localX = x - lastX;
        float localY = y - lastY;
        float w = width.value();
        IslandTasks.Task task = presented;
        if (expanded && task != null && reveal.value() > 0.85f) {
            if (task.activity == Activity.MUSIC && localY > 117 && localY < 156) {
                if (task.live instanceof IslandLiveData.Music music) {
                    if (Math.abs(localX - w / 2) < 25 && music.canToggle()) musicControl.accept("toggle");
                    else if (Math.abs(localX - (w / 2 - 62)) < 25 && music.canPrevious()) musicControl.accept("previous");
                    else if (Math.abs(localX - (w / 2 + 62)) < 25 && music.canNext()) musicControl.accept("next");
                    return;
                }
                if (Math.abs(localX - w / 2) < 25) task.playing = !task.playing;
                else if (Math.abs(localX - (w / 2 - 62)) < 25) task.musicTime = Math.max(0, task.musicTime - 15);
                else if (Math.abs(localX - (w / 2 + 62)) < 25) task.musicTime = (task.musicTime + 15) % 214;
                return;
            }
            if (task.activity == Activity.TIMER && localY > 43 && localY < 89) {
                if (Math.abs(localX - (w - 89)) < 23) { task.timerRunning = !task.timerRunning; return; }
                if (Math.abs(localX - (w - 36)) < 23) { endTask(task.id); return; }
            }
            if (task.activity == Activity.CALL && localX > w - 116 && localY < 68) {
                endTask(task.id);
                return;
            }
        }
        toggle();
    }

    public static String clock(double seconds) {
        int total = Math.max(0, (int) seconds);
        return total / 60 + ":" + (total % 60 < 10 ? "0" : "") + total % 60;
    }

    private static float clamp(float value) { return Math.max(0, Math.min(1, value)); }
    private static float lerp(float a, float b, float p) { return a + (b - a) * p; }
    private static float smooth(float p) { p = clamp(p); return p * p * (3 - 2 * p); }
    private static float range(float p, float start, float end) { return smooth((p - start) / (end - start)); }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        layer.close();
        captionPaint.close();
        captionFadePaint.close();
        captionLeftFade.close();
        captionRightFade.close();
        countdownPaint.close();
        fill.close();
        stroke.close();
        glass.close();
        textShadow.close();
        textShadowBlur.close();
        for (ImageFilter blur : blurs) if (blur != null) blur.close();
        fonts.values().forEach(Font::close);
        if (liveCover != null) liveCover.close();
        regular.close();
        bold.close();
        chinese.close();
        chineseBold.close();
    }
}
