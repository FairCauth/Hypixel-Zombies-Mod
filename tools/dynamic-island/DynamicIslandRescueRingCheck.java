import com.example.client.skia.render.dynamicisland.*;
import io.github.humbleui.skija.*;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Pixel regression: a confirmed rescue fills the satellite, pending confirmation never does. */
public final class DynamicIslandRescueRingCheck {
    private static final Path OUTPUT = Path.of("build/dynamic-island-preview/rescue-ring");
    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT);
        int pending = render(false, .8f, "pending");
        int empty = render(true, 0, "empty");
        int half = render(true, .5f, "half");
        int full = render(true, 1, "full");
        if (pending != empty || !(full > half && half > empty))
            throw new AssertionError("Rescue ring must follow confirmed progress: " + pending + ", " + empty + ", " + half + ", " + full);
        System.out.printf("Rescue ring passed: pending=%d, empty=%d, half=%d, full=%d; wave stays queued and rescue satellite is interactive.%n", pending, empty, half, full);
    }
    private static int render(boolean confirmed, float progress, String name) throws Exception {
        try (DynamicIsland island = new DynamicIsland(); Surface surface = Surface.makeRasterN32Premul(380, 100)) {
            var live = new IslandLiveController(island);
            var music = new IslandLiveData.Music("Fixture", "Artist", "", false, 82, 232, System.nanoTime(), false, "", null, true, true, true);
            var wave = new IslandLiveData.Wave(22, "Fixture", 0, List.of(0, 10), 8, 2, false, "");
            live.update(music, wave, new IslandLiveData.Revive(List.of(new IslandLiveData.Rescue("Steve", confirmed, 2, progress))), 0, false);
            if (!island.visibleTaskIds().equals(List.of(IslandLiveController.MUSIC, IslandLiveController.REVIVE))
                    || island.taskSnapshot(IslandLiveController.WAVE) == null) throw new AssertionError("Three tasks retained with rescue satellite");
            island.advance(1);
            surface.getCanvas().clear(0xFF11161E); island.draw(surface.getCanvas(), 180, 12);
            if (!island.satelliteContains(268, 30)) throw new AssertionError("Rescue satellite hit target");
            island.click(268, 30);
            if (!IslandLiveController.REVIVE.equals(island.expandedTaskId())) throw new AssertionError("Satellite opens rescue");
            try (Image image = surface.makeImageSnapshot(); Data data = image.encodeToData()) {
                byte[] bytes = data.getBytes(); Files.write(OUTPUT.resolve(name + ".png"), bytes);
                var pixels = ImageIO.read(new ByteArrayInputStream(bytes));
                int count = 0;
                for (int y = 16; y <= 44; y++) for (int x = 254; x <= 282; x++) {
                    double radius = Math.hypot(x + .5 - 268, y + .5 - 30);
                    int color = pixels.getRGB(x, y);
                    if (radius > 10.5 && radius < 14.5 && (color >> 8 & 255) > 160
                            && (color >> 16 & 255) < 145 && (color & 255) > 80) count++;
                }
                return count;
            }
        }
    }
}
