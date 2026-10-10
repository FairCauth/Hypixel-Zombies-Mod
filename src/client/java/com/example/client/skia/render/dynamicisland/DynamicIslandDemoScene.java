package com.example.client.skia.render.dynamicisland;

import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;

/** Interactive motion study. All coordinates are mapped to a 900 x 560 design canvas. */
public final class DynamicIslandDemoScene implements AutoCloseable {
    public static final float DESIGN_WIDTH = 900;
    public static final float DESIGN_HEIGHT = 560;
    private final DynamicIslandHudRenderer renderer = new DynamicIslandHudRenderer();
    private final DynamicIsland island = renderer.island();
    private final Paint paint = new Paint().setAntiAlias(true);
    private final ImageFilter atmosphere = ImageFilter.makeBlur(54, 54, FilterTileMode.DECAL);
    private boolean autoplay = true;
    private boolean slow;
    private String pressedTask;
    private float pressX;
    private float pressY;
    private double pressTime;
    private boolean holdHandled;
    private float mouseX = -1;
    private float mouseY = -1;
    private float scale = 1;
    private float offsetX;
    private float offsetY;
    private boolean closed;

    public DynamicIsland island() { return island; }

    public void advance(double seconds) {
        if (closed || seconds <= 0) return;
        if (pressedTask != null && !holdHandled) {
            pressTime += seconds;
            if (pressTime >= 0.35 && Math.abs(mouseX - pressX) < 8 && Math.abs(mouseY - pressY) < 8) {
                island.expandTask(pressedTask);
                holdHandled = true;
            }
        }
        renderer.setAutoplay(autoplay);
        renderer.advance(seconds * (slow ? 0.35 : 1));
    }

    public void draw(Canvas c, int width, int height) {
        scale = Math.min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT);
        offsetX = (width - DESIGN_WIDTH * scale) / 2;
        offsetY = (height - DESIGN_HEIGHT * scale) / 2;
        c.clear(0xFFF4F3F8);
        int saved = c.save();
        try {
            c.translate(offsetX, offsetY);
            c.scale(scale, scale);
            backdrop(c);
            island.text(c, "Z / MOTION LAB", 48, 39, 11, true, 0xFF696474);
            island.rounded(c, 752, 21, 100, 25, 12.5f, 0xEFFFFFFF);
            island.centeredText(c, "SKIA RENDER", 802, 37, 9, true, 0xFF655E73);
            island.centeredText(c, "Dynamic Island", 450, 98, 36, true, 0xFF211D2A);
            island.centeredText(c, "A little space. A lot of motion.", 450, 125, 13, false, 0xFF817A8F);

            island.draw(c, 450, 182);

            island.centeredText(c, island.isExpanded() ? "EXPANDED" : island.hasSecondary() ? "TWO ACTIVITIES" : "COMPACT", 450, 379, 9, true, 0xFF736B84);
            island.centeredText(c, island.isExpanded() ? "Click outside to return to live activities" : "Click or hold either island to expand its task",
                    450, 400, 12, false, 0xFF857C94);
            for (int i = 0; i < 3; i++) {
                DynamicIsland.Activity activity = DynamicIsland.Activity.values()[i];
                button(c, 232 + i * 148, 429, 140, 36, activity.label, island.activity() == activity);
            }
            button(c, 287, 483, 158, 30, autoplay ? "Auto play  ·  ON" : "Auto play  ·  OFF", autoplay);
            button(c, 455, 483, 158, 30, slow ? "Speed  ·  0.35×" : "Speed  ·  1×", slow);
            button(c, 87, 483, 190, 30, island.hasSecondary() ? "Second activity  ·  ON" : "Second activity  ·  OFF", island.hasSecondary());
            button(c, 623, 483, 190, 30, "End focused activity", false);
            island.text(c, "Spring morph / Content blur / Continuous motion", 48, 539, 10, false, 0xFF96909E);
            String hint = "M  Second     Shift+Space  Expand second     A  Auto     S  Slow";
            island.text(c, hint, 490, 539, 9, false, 0xFF96909E);
        } finally {
            c.restoreToCount(saved);
        }
    }

    private void backdrop(Canvas c) {
        try (Shader shader = Shader.makeLinearGradient(0, 0, 900, 560,
                new int[]{0xFFF8F6FA, 0xFFEDECF7, 0xFFF8F3F3})) {
            paint.setShader(shader);
            c.drawRect(Rect.makeXYWH(0, 0, 900, 560), paint);
            paint.setShader(null);
        }
        // Soft wallpaper drawn by Skia, with no raster texture or external assets.
        paint.setImageFilter(atmosphere).setColor(0xA9C3B1ED);
        c.drawOval(Rect.makeXYWH(235, 224, 470, 172), paint);
        paint.setColor(0x90AED1F2);
        c.drawOval(Rect.makeXYWH(222, 185, 203, 150), paint);
        paint.setColor(0x91F1BAD1);
        c.drawOval(Rect.makeXYWH(516, 160, 160, 135), paint);
        paint.setImageFilter(null);
        // Delicate orbit lines give the island a stage without a panel around it.
        paint.setStroke(true).setStrokeWidth(0.8f).setColor(0x26FFFFFF);
        c.drawOval(Rect.makeXYWH(123, 158, 654, 226), paint);
        c.drawOval(Rect.makeXYWH(96, 143, 708, 256), paint);
        paint.setStroke(false);
    }

    private void button(Canvas c, float x, float y, float w, float h, String label, boolean active) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        island.rounded(c, x, y, w, h, h / 2, active ? 0xFF27222F : hover ? 0xFFFFFFFF : 0xBBFFFFFF);
        island.centeredText(c, label, x + w / 2, y + h / 2 + 4, 11, active, active ? 0xFFF7F5FB : 0xFF746B81);
    }

    public void pointer(double x, double y) {
        mouseX = (float) ((x - offsetX) / scale);
        mouseY = (float) ((y - offsetY) / scale);
    }

    public boolean click(double x, double y) {
        pointer(x, y);
        if (island.contains(mouseX, mouseY) || island.satelliteContains(mouseX, mouseY)) {
            autoplay = false;
            island.click(mouseX, mouseY);
            return true;
        }
        for (int i = 0; i < 3; i++) {
            if (inside(mouseX, mouseY, 232 + i * 148, 429, 140, 36)) {
                select(i);
                return true;
            }
        }
        if (inside(mouseX, mouseY, 287, 483, 158, 30)) { toggleAuto(); return true; }
        if (inside(mouseX, mouseY, 455, 483, 158, 30)) { slow = !slow; return true; }
        if (inside(mouseX, mouseY, 87, 483, 190, 30)) { toggleSecondary(); return true; }
        if (inside(mouseX, mouseY, 623, 483, 190, 30)) { finishFocused(); return true; }
        if (island.isExpanded()) { autoplay = false; island.setExpanded(false); return true; }
        return false;
    }

    public void select(int index) {
        stopAuto();
        island.startTask("demo-main", DynamicIsland.Activity.values()[index], 1);
    }

    public void toggleExpanded() {
        stopAuto();
        island.toggle();
    }

    public void toggleSecondary() {
        stopAuto();
        if (island.hasSecondary()) island.endTask("demo-secondary");
        else {
            // Recreate the missing slot after either task has ended, preserving the survivor.
            if (island.taskSnapshot("demo-main") == null) {
                island.startTask("demo-main", island.activity() == DynamicIsland.Activity.TIMER
                        ? DynamicIsland.Activity.MUSIC : DynamicIsland.Activity.TIMER, 1);
            }
            if (island.taskSnapshot("demo-secondary") == null) {
                island.startTask("demo-secondary", island.activity() == DynamicIsland.Activity.TIMER
                        ? DynamicIsland.Activity.MUSIC : DynamicIsland.Activity.TIMER, 0);
            }
        }
    }

    public void expandSecondary() { stopAuto(); island.expandSecondary(); }

    public void finishFocused() {
        stopAuto();
        String id = island.expandedTaskId();
        if (id == null && !island.visibleTaskIds().isEmpty()) id = island.visibleTaskIds().getFirst();
        if (id != null) island.endTask(id);
    }

    private void stopAuto() {
        if (autoplay) {
            autoplay = false;
            // Switch off the timeline without applying demo settings to live tasks.
            renderer.setAutoplay(false);
        }
    }

    public boolean pointerDown(double x, double y) {
        pointer(x, y);
        boolean satellite = island.satelliteContains(mouseX, mouseY);
        if (!satellite && !island.contains(mouseX, mouseY)) return false;
        stopAuto();
        if (island.visibleTaskIds().isEmpty()) return false;
        pressedTask = satellite ? island.visibleTaskIds().get(1)
                : island.isExpanded() ? island.expandedTaskId() : island.visibleTaskIds().getFirst();
        pressX = mouseX;
        pressY = mouseY;
        pressTime = 0;
        holdHandled = false;
        return true;
    }

    public boolean pointerUp(double x, double y) {
        if (pressedTask == null) return false;
        pointer(x, y);
        if (!holdHandled && Math.abs(mouseX - pressX) < 8 && Math.abs(mouseY - pressY) < 8) island.click(mouseX, mouseY);
        pressedTask = null;
        return true;
    }

    public void toggleAuto() {
        autoplay = !autoplay;
        renderer.setAutoplay(autoplay);
    }

    public void toggleSlow() { slow = !slow; }

    private static boolean inside(float x, float y, float bx, float by, float w, float h) {
        return x >= bx && x <= bx + w && y >= by && y <= by + h;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        renderer.close();
        paint.close();
        atmosphere.close();
    }
}
