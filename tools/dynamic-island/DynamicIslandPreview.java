import com.example.client.skia.render.dynamicisland.DynamicIslandDemoScene;
import com.example.client.skia.render.dynamicisland.IslandSpring;
import io.github.humbleui.skija.*;

import java.nio.file.Files;
import java.nio.file.Path;

/** Offline rendering of the exact in-game Skia scene, without launching Minecraft. */
public final class DynamicIslandPreview {
    public static void main(String[] args) throws Exception {
        verifySpring();
        Path output = Path.of(args.length > 0 ? args[0] : "build/dynamic-island-preview");
        Files.createDirectories(output.resolve("frames"));
        try (DynamicIslandDemoScene scene = new DynamicIslandDemoScene();
             Surface surface = Surface.makeRasterN32Premul(900, 560)) {
            for (int frame = 0; frame < 720; frame++) {
                scene.advance(1.0 / 30);
                scene.draw(surface.getCanvas(), 900, 560);
                try (Image image = surface.makeImageSnapshot(); Data data = image.encodeToData()) {
                    byte[] bytes = data.getBytes();
                    Files.write(output.resolve("frames").resolve(String.format("%04d.png", frame)), bytes);
                    if (frame == 66) Files.write(output.resolve("music.png"), bytes);
                    if (frame == 240) Files.write(output.resolve("timer.png"), bytes);
                    if (frame == 600) Files.write(output.resolve("call.png"), bytes);
                    if (frame == 180) Files.write(output.resolve("dual.png"), bytes);
                    if (frame == 112) Files.write(output.resolve("collapse-blur.png"), bytes);
                }
            }
        }
        verifyInterruptionAndResize(output);
        System.out.println("Skia rendered 720 frames; spring and interaction checks passed. Output: " + output.toAbsolutePath());
    }

    private static void verifySpring() {
        IslandSpring fast = new IslandSpring(36, 21, 0.73);
        IslandSpring slow = new IslandSpring(36, 21, 0.73);
        fast.target(168);
        slow.target(168);
        for (int i = 0; i < 144; i++) fast.advance(1.0 / 144);
        for (int i = 0; i < 30; i++) slow.advance(1.0 / 30);
        require(Math.abs(fast.value() - slow.value()) < 0.001, "frame rate independence");
        require(Math.abs(fast.value() - 168) < 0.01, "spring settles");
        float before = fast.value();
        fast.target(36);
        require(fast.value() == before, "target changes without discontinuity");
        for (int i = 0; i < 6; i++) fast.advance(1.0 / 60);
        before = fast.value();
        fast.target(168);
        require(fast.value() == before, "reversal keeps current position");
        for (int i = 0; i < 120; i++) fast.advance(1.0 / 60);
        require(Math.abs(fast.value() - 168) < 0.01, "interrupted spring settles");
    }

    private static void verifyInterruptionAndResize(Path output) throws Exception {
        try (DynamicIslandDemoScene scene = new DynamicIslandDemoScene();
             Surface surface = Surface.makeRasterN32Premul(480, 270)) {
            scene.select(0);
            for (int i = 0; i < 90; i++) {
                if (i % 7 == 0) scene.toggleExpanded();
                if (i % 13 == 0) scene.select((i / 13) % 3);
                scene.advance(1.0 / 60);
                scene.draw(surface.getCanvas(), 480, 270);
                require(surface.getCanvas().getSaveCount() == 1, "canvas stack balanced");
            }
            scene.select(0);
            scene.island().setExpanded(false);
            scene.advance(2);
            scene.draw(surface.getCanvas(), 480, 270);
            // Design (450, 200) is inside the compact island. In this aspect ratio
            // scale = 270 / 560 and horizontal offset = (480 - 900 * scale) / 2.
            require(scene.click(240, 200 * 270.0 / 560), "scaled hit target");
            require(scene.island().isExpanded(), "island click expands");
            scene.advance(1);
            scene.draw(surface.getCanvas(), 480, 270);
            try (Image image = surface.makeImageSnapshot(); Data data = image.encodeToData()) {
                Files.write(output.resolve("small-window.png"), data.getBytes());
            }
        }
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
