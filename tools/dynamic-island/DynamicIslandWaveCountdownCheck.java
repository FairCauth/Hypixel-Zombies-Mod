import com.example.client.skia.render.dynamicisland.*;
import io.github.humbleui.skija.*;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Verifies real interval math and pixels in the production countdown satellite. */
public final class DynamicIslandWaveCountdownCheck {
    private static final Path OUTPUT = Path.of("build/dynamic-island-preview/wave-countdown");
    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT);
        require(close(wave(-1, 5, 5).countdownFraction(), .5), "first wave counts from the round start");
        require(close(wave(0, 15, 5).countdownFraction(), .5), "current spawn interval");
        require(close(wave(1, 20, 15).countdownFraction(), 1), "unequal next interval resets to full");
        require(close(wave(2, 40, -1).countdownFraction(), 0), "clearing has no countdown");
        require(close(new IslandLiveData.Wave(22, "Fixture", -1, List.of(), 0, -1, false, "").countdownFraction(), 0), "missing schedule stays empty");
        require(close(wave(0, 10, 100).countdownFraction(), 1), "remaining is clamped");
        int full = amberPixels(render(wave(0, 10, 10), "full"));
        int half = amberPixels(render(wave(0, 15, 5), "half"));
        int near = amberPixels(render(wave(0, 19, 1), "near-zero"));
        int zero = amberPixels(render(wave(0, 20, 0), "zero"));
        require(full > half && half > near && near > zero, "visible amber ring shrinks with actual remaining time");
        BufferedImage three = render(wave(0, 17, 3), "three");
        BufferedImage two = render(wave(0, 18, 2), "two");
        BufferedImage one = render(wave(0, 19.5, .5), "one");
        require(centerHash(three) != centerHash(two) && centerHash(two) != centerHash(one)
                && centerHash(three) != centerHash(one), "three, two and one have distinct countdown digits");
        var otherRound = new IslandLiveData.Wave(99, "Fixture", 0, List.of(10, 20, 35), 17, 3, false, "");
        require(centerHash(three) == centerHash(render(otherRound, "other-round")), "center depends on countdown, never round number");
        checkMotion();
        checkVisibility();
        var music = music();
        try (DynamicIsland island = new DynamicIsland()) {
            var live = new IslandLiveController(island);
            for (double remaining : new double[] {5, 3, 2, 1, .1, 0, -1}) {
                live.update(music, wave(0, 20 - Math.max(0, remaining), remaining), null, .1, true);
                require(!island.isExpanded(), "automatic glances never expand wave during countdown");
            }
            live.toggle(false);
            live.update(music, wave(0, 18, 2), null, 0, true);
            require(IslandLiveController.MUSIC.equals(island.expandedTaskId()), "wave countdown leaves manually expanded music focused");
            live.toggle(false);
            live.update(music, wave(0, 18, 2), null, 0, false);
            live.setPeekHeld(true, true); island.advance(.08);
            require(IslandLiveController.WAVE.equals(island.expandedTaskId()), "secondary hold selects countdown");
            live.setPeekHeld(false, false); island.advance(1);
            require(!island.isExpanded() && !island.motionSnapshot().merging(), "release reverses docking");
            live.update(null, wave(0, 18, 2), null, 0, false);
            require(island.visibleTaskIds().equals(List.of(IslandLiveController.WAVE)), "music exit promotes wave");
            live.update(music, wave(0, 18, 2), null, 0, false);
            require(island.visibleTaskIds().equals(List.of(IslandLiveController.MUSIC, IslandLiveController.WAVE)), "music return restores requested roles");
        }
        System.out.printf("Wave countdown checks passed: ring pixels %d > %d > %d > %d, last-three-second digits only, no round label or auto expansion, manual click/hold/reversal and task promotion.%n", full, half, near, zero);
    }
    private static void checkVisibility() {
        try (DynamicIsland island = new DynamicIsland()) {
            var live = new IslandLiveController(island);
            for (double remaining : new double[] {10, 5, 3.01, 0, -1, Double.NaN}) {
                live.update(music(), wave(0, 10, remaining), null, .1, true);
                require(island.visibleTaskIds().equals(List.of(IslandLiveController.MUSIC)), "no empty wave island before countdown");
                require(island.taskSnapshot(IslandLiveController.WAVE) == null, "inactive wave does not occupy a queued task");
            }
            live.update(music(), wave(0, 17, 3), null, .1, true);
            require(island.visibleTaskIds().equals(List.of(IslandLiveController.MUSIC, IslandLiveController.WAVE)),
                    "countdown appears at the three-second boundary");
            require(!island.isExpanded(), "countdown arrival remains compact");
            live.update(music(), wave(0, 19.9, .1), null, .1, true);
            live.update(music(), wave(1, 20, 15), null, .05, true);
            require(island.taskSnapshot(IslandLiveController.WAVE) != null, "completion keeps time for digit exit");
            live.update(music(), wave(1, 20.3, 14.7), null, .3, true);
            require(island.visibleTaskIds().equals(List.of(IslandLiveController.MUSIC)), "wave exits before the next countdown");
            live.update(null, wave(1, 32, 3), null, .1, true);
            require(island.visibleTaskIds().equals(List.of(IslandLiveController.WAVE)), "countdown can run as the sole task");
            live.update(null, wave(2, 35, -1), null, .3, true);
            require(island.visibleTaskIds().isEmpty(), "clearing hides the sole wave island");
            live.update(music(), wave(0, 17, 3), null, .1, true);
            live.setPeekHeld(true, true);
            live.update(music(), null, null, .01, true);
            require(island.taskSnapshot(IslandLiveController.WAVE) == null && IslandLiveController.MUSIC.equals(island.expandedTaskId()),
                    "disabled wave exits immediately and held peek follows music");
        }
    }
    private static void checkMotion() throws Exception {
        try (DynamicIsland island = new DynamicIsland(); Surface surface = Surface.makeRasterN32Premul(380, 100)) {
            var live = new IslandLiveController(island);
            live.update(music(), wave(0, 17, 3), null, 0, true); island.advance(1);
            live.update(music(), wave(0, 18, 2), null, 0, true);
            island.advance(.03);
            BufferedImage start = capture(island, surface);
            island.advance(.06);
            BufferedImage middle = capture(island, surface);
            for (int i = 0; i < 30; i++) {
                live.update(music(), wave(0, 18, 2), null, 1.0 / 60, true);
                island.advance(1.0 / 60);
            }
            BufferedImage settled = capture(island, surface);
            require(centerHash(start) != centerHash(middle) && centerHash(middle) != centerHash(settled),
                    "digit handoff changes over multiple rendered frames");
            require(centerHash(settled) == centerHash(render(wave(0, 18, 2), "motion-settled")),
                    "repeated live updates do not restart digit animation");
            require(amberPixels(start) == amberPixels(middle) && amberPixels(middle) == amberPixels(settled),
                    "digit blur and scaling never alter the countdown ring");
            live.update(music(), wave(0, 19, 1), null, 0, true); island.advance(1);
            BufferedImage one = capture(island, surface);
            live.update(music(), wave(0, 20, 0), null, 0, true);
            require(centerHash(one) == centerHash(capture(island, surface)), "last digit does not disappear abruptly at zero");
            island.advance(.3);
            require(centerHash(capture(island, surface)) == centerHash(render(wave(0, 20, 0), "motion-finished")),
                    "completion fade finishes without a stale digit");
            require(!island.isExpanded(), "animated countdown stays compact");
        }
    }
    private static BufferedImage capture(DynamicIsland island, Surface surface) throws Exception {
        var canvas = surface.getCanvas(); canvas.clear(0xFF11161E); island.draw(canvas, 180, 12);
        try (Image image = surface.makeImageSnapshot(); Data data = image.encodeToData()) {
            return ImageIO.read(new ByteArrayInputStream(data.getBytes()));
        }
    }
    private static BufferedImage render(IslandLiveData.Wave wave, String name) throws Exception {
        try (DynamicIsland island = new DynamicIsland(); Surface surface = Surface.makeRasterN32Premul(380, 100)) {
            new IslandLiveController(island);
            // Render the ring independently of task admission, including full/empty math fixtures.
            island.updateLiveTask(IslandLiveController.MUSIC, music(), 50);
            island.updateLiveTask(IslandLiveController.WAVE, wave, 40);
            island.advance(1);
            var canvas = surface.getCanvas(); canvas.clear(0xFF11161E); island.draw(canvas, 180, 12);
            require(island.satelliteContains(268, 30), "circular satellite is interactive");
            island.click(268, 30);
            require(IslandLiveController.WAVE.equals(island.expandedTaskId()), "click opens the wave task");
            try (Image image = surface.makeImageSnapshot(); Data data = image.encodeToData()) {
                byte[] bytes = data.getBytes(); Files.write(OUTPUT.resolve(name + ".png"), bytes);
                BufferedImage result = ImageIO.read(new ByteArrayInputStream(bytes));
                int digits = 0;
                for (int y = 24; y < 36; y++) for (int x = 260; x < 276; x++) {
                    int color = result.getRGB(x, y);
                    if ((color >> 16 & 255) > 210 && (color >> 8 & 255) > 210 && (color & 255) > 210) digits++;
                }
                boolean finalSeconds = wave.countdownFraction() > 0 && wave.nextSeconds() > 0 && wave.nextSeconds() <= 3;
                require(finalSeconds ? digits > 5 : digits == 0, "digits appear only during the final three seconds");
                return result;
            }
        }
    }
    private static int centerHash(BufferedImage image) {
        int hash = 1;
        for (int y = 24; y < 36; y++) for (int x = 260; x < 276; x++) hash = 31 * hash + image.getRGB(x, y);
        return hash;
    }
    private static int amberPixels(BufferedImage image) {
        int count = 0;
        for (int y = 16; y <= 44; y++) for (int x = 254; x <= 282; x++) {
            double radius = Math.hypot(x + .5 - 268, y + .5 - 30);
            int color = image.getRGB(x, y);
            if (radius > 10.5 && radius < 14.5 && (color >> 16 & 255) > 180
                    && (color >> 8 & 255) > 100 && (color & 255) < 140) count++;
        }
        return count;
    }
    private static IslandLiveData.Music music() {
        return new IslandLiveData.Music("Fixture", "Artist", "", false, 82, 232, System.nanoTime(), false, "", null, true, true, true);
    }
    private static IslandLiveData.Wave wave(int current, double elapsed, double remaining) {
        return new IslandLiveData.Wave(22, "Fixture", current, List.of(10, 20, 35), elapsed, remaining, false, "");
    }
    private static boolean close(float a, double b) { return Math.abs(a - b) < .001; }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
