import com.example.client.skia.render.dynamicisland.DynamicIsland;
import com.example.client.skia.render.dynamicisland.DynamicIslandDemoScene;
import com.example.client.skia.render.dynamicisland.DynamicIslandHudRenderer;
import io.github.humbleui.skija.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Exercises the actual two-task renderer, task lifecycle and independent controls. */
public final class DynamicIslandMultitaskPreview {
    public static void main(String[] args) throws Exception {
        Path output = Path.of("build/dynamic-island-preview/multitask");
        Files.createDirectories(output);
        verifyLifecycle();
        verifyInteraction(output);
        verifyConfiguration();
        verifyHold(output);
        if (args.length > 0) renderScreenshot(Path.of(args[0]), output);
        System.out.println("Multitask checks passed: stable priority slots, queued progress, independent playback/timers, secondary hit testing/expansion, split/merge interruptions, completion/promotion, same-kind tasks, repeated configuration, hold gesture, small windows, glass snapshot ownership.");
    }

    private static void verifyLifecycle() {
        try (DynamicIsland island = new DynamicIsland()) {
            island.startTask("timer", DynamicIsland.Activity.TIMER, 0);
            require(island.visibleTaskIds().equals(List.of("demo-main", "timer")), "two stable slots");
            island.advance(2);
            island.startTask("timer", DynamicIsland.Activity.TIMER, 0);
            require(close(island.taskSnapshot("timer").timerSeconds(), 298), "update preserves timer");
            island.startTask("call", DynamicIsland.Activity.CALL, 10);
            require(island.visibleTaskIds().equals(List.of("call", "demo-main")), "priority task takes a slot");
            island.advance(3);
            require(close(island.taskSnapshot("timer").timerSeconds(), 295), "queued timer still runs");
            island.expandTask("call");
            island.endTask("call");
            require(!island.isExpanded(), "ended expanded task collapses");
            require(island.visibleTaskIds().equals(List.of("demo-main", "timer")), "queued activity returns");
            island.endTask("demo-main");
            require(island.visibleTaskIds().equals(List.of("timer")), "survivor becomes compact primary");
            require(close(island.taskSnapshot("timer").timerSeconds(), 295), "promotion preserves progress");
            island.advance(296);
            require(island.visibleTaskIds().isEmpty(), "completed timer releases its slot");
            island.startTask("timer-a", DynamicIsland.Activity.TIMER, 0);
            island.advance(2);
            island.startTask("timer-b", DynamicIsland.Activity.TIMER, 0);
            island.advance(1);
            require(close(island.taskSnapshot("timer-a").timerSeconds(), 297)
                    && close(island.taskSnapshot("timer-b").timerSeconds(), 299), "same-kind tasks have independent clocks");
        }
    }

    private static void verifyInteraction(Path output) throws Exception {
        try (DynamicIsland island = new DynamicIsland(); Surface target = Surface.makeRasterN32Premul(900, 560)) {
            island.advance(1);
            island.startTask("timer", DynamicIsland.Activity.TIMER, 0);
            island.advance(0.08);
            draw(island, target);
            save(target, output.resolve("splitting.png"));
            island.advance(1);
            draw(island, target);
            save(target, output.resolve("dual-layout.png"));
            require(island.contains(450, 200), "attached primary is interactive");
            require(island.satelliteContains(550, 200), "detached timer is interactive");
            require(!island.contains(516, 200) && !island.satelliteContains(516, 200), "gap is not a hit target");
            island.click(550, 200);
            require("timer".equals(island.expandedTaskId()), "detached activity expands its own task");
            island.advance(1);
            draw(island, target);
            save(target, output.resolve("second-expanded-layout.png"));
            require(!island.satelliteContains(550, 200), "hidden satellite does not intercept clicks");
            island.click(536, 248); // Expanded timer's pause control.
            double countdown = island.taskSnapshot("timer").timerSeconds();
            double playback = island.taskSnapshot("demo-main").musicSeconds();
            island.advance(1);
            require(close(island.taskSnapshot("timer").timerSeconds(), countdown), "only selected timer pauses");
            require(close(island.taskSnapshot("demo-main").musicSeconds(), playback + 1), "hidden music keeps playing");
            island.setExpanded(false);
            island.advance(1);
            draw(island, target);
            require(island.satelliteContains(550, 200), "dual layout returns after collapse");
            island.expandSecondary();
            island.advance(1);
            draw(island, target);
            island.click(589, 248); // Close only the expanded timer.
            require(island.taskSnapshot("timer") == null && island.taskSnapshot("demo-main") != null,
                    "close control ends only its own activity");
            island.advance(1);
            draw(island, target);
            save(target, output.resolve("single-after-end.png"));
            for (int frame = 0; frame < 50; frame++) {
                if (frame % 7 == 0) island.startTask("timer", DynamicIsland.Activity.TIMER, 0);
                if (frame % 11 == 0) island.expandSecondary();
                if (frame % 13 == 0) island.endTask("timer");
                if (frame % 17 == 0) island.setExpanded(false);
                island.advance(1.0 / 60);
                draw(island, target);
                require(target.getCanvas().getSaveCount() == 1, "interrupted split/merge restores canvas state");
            }
        }
    }

    private static void verifyConfiguration() {
        try (DynamicIslandHudRenderer hud = new DynamicIslandHudRenderer()) {
            for (int frame = 0; frame < 60; frame++) {
                hud.configure(DynamicIsland.Activity.MUSIC, DynamicIsland.Activity.TIMER, false, false, false);
                hud.advance(1.0 / 60);
            }
            require(close(hud.island().taskSnapshot("demo-secondary").timerSeconds(), 299), "per-frame configuration preserves progress");
            hud.configure(DynamicIsland.Activity.MUSIC, DynamicIsland.Activity.TIMER, true, true, false);
            require("demo-secondary".equals(hud.island().expandedTaskId()), "HUD can expand second task");
            hud.island().endTask("demo-secondary");
            hud.configure(DynamicIsland.Activity.MUSIC, DynamicIsland.Activity.TIMER, true, true, false);
            require(hud.island().taskSnapshot("demo-secondary") == null, "unchanged config does not resurrect ended tasks");
            hud.configure(DynamicIsland.Activity.MUSIC, DynamicIsland.Activity.MUSIC, false, false, false);
            hud.island().endTask("demo-main");
            hud.configure(DynamicIsland.Activity.CALL, DynamicIsland.Activity.MUSIC, false, false, false);
            require(hud.island().visibleTaskIds().getFirst().equals("demo-main")
                    && hud.island().activity() == DynamicIsland.Activity.CALL, "configured main regains primary slot after ending");
            hud.configure(DynamicIsland.Activity.MUSIC, false, true);
            hud.advance(6);
            require(hud.island().hasSecondary(), "auto demo includes simultaneous tasks");
            hud.advance(2);
            require("demo-secondary".equals(hud.island().expandedTaskId()), "auto demo expands second task");
            hud.advance(8);
            require(hud.island().visibleTaskIds().equals(List.of("demo-secondary")), "auto demo ends main and promotes survivor");
        }
    }

    private static void verifyHold(Path output) throws Exception {
        try (DynamicIslandDemoScene scene = new DynamicIslandDemoScene(); Surface target = Surface.makeRasterN32Premul(900, 560)) {
            scene.select(0);
            scene.toggleSecondary();
            scene.advance(1);
            scene.draw(target.getCanvas(), 900, 560);
            require(scene.pointerDown(550, 200), "secondary press captured");
            scene.advance(0.36);
            require("demo-secondary".equals(scene.island().expandedTaskId()), "hold expands selected activity");
            require(scene.pointerUp(550, 200) && scene.island().isExpanded(), "release does not toggle held activity closed");
            scene.draw(target.getCanvas(), 900, 560);
            save(target, output.resolve("demo-hold.png"));
            scene.island().endTask("demo-main");
            double survivor = scene.island().taskSnapshot("demo-secondary").timerSeconds();
            scene.toggleSecondary();
            require(scene.island().hasSecondary(), "second button restores missing main slot");
            require(close(scene.island().taskSnapshot("demo-secondary").timerSeconds(), survivor), "restoring main preserves survivor");
            scene.island().endTask("demo-main");
            scene.select(2);
            require(scene.island().activity() == DynamicIsland.Activity.CALL, "selection remains primary after promotion");
            scene.island().endTask("demo-main");
            scene.island().endTask("demo-secondary");
            scene.toggleSecondary();
            require(scene.island().hasSecondary(), "second button can restart an empty demo");
        }
        try (DynamicIslandHudRenderer hud = new DynamicIslandHudRenderer(); Surface small = Surface.makeRasterN32Premul(150, 110)) {
            hud.configure(DynamicIsland.Activity.MUSIC, DynamicIsland.Activity.TIMER, false, false, false);
            hud.advance(1);
            small.getCanvas().clear(0);
            hud.draw(small.getCanvas(), 150, 110, 0.65f, 8);
            require(small.getCanvas().getSaveCount() == 1, "small dual layout restores state");
            save(small, output.resolve("small-dual.png"));
            hud.configure(DynamicIsland.Activity.MUSIC, DynamicIsland.Activity.TIMER, true, true, false);
            hud.advance(1);
            small.getCanvas().clear(0);
            hud.draw(small.getCanvas(), 150, 110, 0.65f, 8);
            save(small, output.resolve("small-second-expanded.png"));
        }
    }

    private static void renderScreenshot(Path path, Path output) throws Exception {
        BufferedImage source = ImageIO.read(path.toFile());
        BufferedImage game = source.getSubimage(0, 31, source.getWidth(), source.getHeight() - 31);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(game, "png", png);
        try (Image sharp = Image.makeFromEncoded(png.toByteArray());
             Surface target = Surface.makeRasterN32Premul(game.getWidth(), game.getHeight());
             Surface blurTarget = Surface.makeRasterN32Premul(game.getWidth(), game.getHeight());
             ImageFilter blur = ImageFilter.makeBlur(12, 12, FilterTileMode.CLAMP);
             Paint paint = new Paint().setImageFilter(blur);
             DynamicIslandHudRenderer hud = new DynamicIslandHudRenderer()) {
            blurTarget.getCanvas().drawImage(sharp, 0, 0, paint);
            try (Image blurred = blurTarget.makeImageSnapshot()) {
                hud.configure(DynamicIsland.Activity.MUSIC, DynamicIsland.Activity.TIMER, false, false, false);
                hud.advance(1);
                drawHud(hud, target, sharp, blurred);
                save(target, output.resolve("two-tasks.png"));
                hud.configure(DynamicIsland.Activity.MUSIC, DynamicIsland.Activity.TIMER, true, true, false);
                hud.advance(1);
                drawHud(hud, target, sharp, blurred);
                save(target, output.resolve("second-expanded.png"));
                hud.configure(DynamicIsland.Activity.MUSIC, DynamicIsland.Activity.TIMER, false, false, false);
                hud.advance(1);
                drawHud(hud, target, sharp, blurred);
                save(target, output.resolve("restored-dual.png"));
                hud.island().endTask("demo-main");
                hud.advance(1);
                drawHud(hud, target, sharp, blurred);
                save(target, output.resolve("task-ended.png"));
                require(!sharp.isClosed() && !blurred.isClosed(), "dual shells borrow immutable frame snapshots");
            }
        }
    }

    private static void drawHud(DynamicIslandHudRenderer hud, Surface target, Image sharp, Image blurred) {
        Canvas c = target.getCanvas();
        c.clear(0);
        c.drawImage(sharp, 0, 0);
        int saved = c.save();
        c.scale(3, 3);
        hud.draw(c, target.getWidth() / 3f, target.getHeight() / 3f, 0.65f, 8, sharp, blurred, 3, 3);
        c.restoreToCount(saved);
        require(c.getSaveCount() == 1, "dual glass canvas state");
    }

    private static void draw(DynamicIsland island, Surface target) {
        target.getCanvas().clear(0xFF334B70);
        island.draw(target.getCanvas(), 450, 182);
    }

    private static void save(Surface surface, Path path) throws Exception {
        try (Image image = surface.makeImageSnapshot(); Data data = image.encodeToData()) { Files.write(path, data.getBytes()); }
    }

    private static boolean close(double a, double b) { return Math.abs(a - b) < 0.0001; }
    private static void require(boolean condition, String description) { if (!condition) throw new AssertionError(description); }
}
