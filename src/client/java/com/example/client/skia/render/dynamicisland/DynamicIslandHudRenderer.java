package com.example.client.skia.render.dynamicisland;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;

/** Transparent, top-centered HUD. Coordinates use Minecraft's GUI logical pixels. */
public final class DynamicIslandHudRenderer implements AutoCloseable {
    private final DynamicIsland island = new DynamicIsland();
    private boolean autoplay;
    private double timeline;
    private int cue = -1;
    private DynamicIsland.Activity configuredMain;
    private DynamicIsland.Activity configuredSecond;
    private boolean configuredExpanded;
    private boolean configuredFocusSecond;
    private boolean configured;
    private static final double[] CUES = {0, 1, 3.8, 5, 7, 9.8, 11, 13.8, 15, 17, 18.5, 20.8, 22, 23.2};

    public DynamicIsland island() { return island; }

    public void setAutoplay(boolean autoplay) {
        if (this.autoplay != autoplay) { timeline = 0; cue = -1; }
        this.autoplay = autoplay;
    }

    public void configure(DynamicIsland.Activity activity, boolean expanded, boolean autoplay) {
        configure(activity, null, false, expanded, autoplay);
    }

    public void configure(DynamicIsland.Activity main, DynamicIsland.Activity second, boolean focusSecond,
                          boolean expanded, boolean autoplay) {
        boolean autoChanged = this.autoplay != autoplay;
        setAutoplay(autoplay);
        if (!autoplay) {
            if (!configured || autoChanged || configuredMain != main) island.startTask("demo-main", main, 1);
            if (!configured || autoChanged || configuredSecond != second) {
                if (second == null) island.endTask("demo-secondary");
                else island.startTask("demo-secondary", second, 0);
            }
            if (!configured || autoChanged || configuredExpanded != expanded || configuredFocusSecond != focusSecond) {
                if (expanded) island.expandTask(focusSecond && second != null ? "demo-secondary" : "demo-main");
                else island.setExpanded(false);
            }
        }
        configured = true;
        configuredMain = main;
        configuredSecond = second;
        configuredExpanded = expanded;
        configuredFocusSecond = focusSecond;
    }

    public void advance(double seconds) {
        if (seconds <= 0) return;
        if (!autoplay) { island.advance(seconds); return; }
        double remaining = seconds;
        while (remaining > 0.000001) {
            double dt = Math.min(remaining, 1.0 / 120);
            timeline += dt;
            if (timeline >= 24) { timeline -= 24; cue = -1; }
            int next = 0;
            for (int i = 1; i < CUES.length; i++) if (timeline >= CUES[i]) next = i;
            if (next != cue) {
                cue = next;
                switch (cue) {
                    case 0 -> {
                        island.endTask("demo-secondary");
                        island.setActivity(DynamicIsland.Activity.MUSIC);
                        island.setExpanded(false);
                    }
                    case 1, 6 -> island.expandTask("demo-main");
                    case 2, 5, 7 -> island.setExpanded(false);
                    case 3 -> island.startTask("demo-secondary", DynamicIsland.Activity.TIMER, 0);
                    case 4 -> island.expandSecondary();
                    case 8 -> island.endTask("demo-main");
                    case 9 -> island.startTask("demo-main", DynamicIsland.Activity.CALL, 1);
                    case 10 -> island.expandTask("demo-main");
                    case 11 -> island.setExpanded(false);
                    case 12 -> island.endTask("demo-main");
                    case 13 -> {
                        island.endTask("demo-secondary");
                        island.setActivity(DynamicIsland.Activity.MUSIC);
                    }
                }
            }
            island.advance(dt);
            remaining -= dt;
        }
    }

    public void draw(Canvas canvas, float screenWidth, float screenHeight, float requestedScale, float topMargin) {
        draw(canvas, screenWidth, screenHeight, requestedScale, topMargin, null, null, 1, 1);
    }

    /** Frame images are borrowed and used only for this draw, never retained or closed. */
    public void draw(Canvas canvas, float screenWidth, float screenHeight, float requestedScale, float topMargin,
                     Image sharpBackground, Image blurredBackground, float framebufferScaleX, float framebufferScaleY) {
        if (screenWidth <= 0 || screenHeight <= 0) return;
        float top = Math.max(0, Math.min(topMargin, screenHeight - 24));
        // Reserve enough space for the expanded presentation and spring overshoot.
        float scale = Math.min(Math.max(0.1f, requestedScale),
                Math.min((screenWidth - 16) / 370, (screenHeight - top - 8) / 188));
        if (scale <= 0) return;
        int saved = canvas.save();
        try {
            canvas.translate(screenWidth / 2, top);
            canvas.scale(scale, scale);
            DynamicIslandGlass.Background background = sharpBackground == null ? null : new DynamicIslandGlass.Background(
                    sharpBackground, blurredBackground == null ? sharpBackground : blurredBackground,
                    framebufferScaleX * scale, framebufferScaleY * scale,
                    screenWidth / 2 * framebufferScaleX, top * framebufferScaleY);
            island.draw(canvas, 0, 0, background);
        } finally {
            canvas.restoreToCount(saved);
        }
    }

    @Override
    public void close() {
        island.close();
    }
}
