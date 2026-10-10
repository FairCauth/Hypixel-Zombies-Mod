import com.example.client.integration.CoverPalette;
import com.example.client.skia.render.dynamicisland.*;
import com.google.gson.Gson;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Actual production HUD with a saved QQ metadata snapshot and explicitly simulated wave/audio values. */
public final class DynamicIslandWaveMusicPreview {
    public static void main(String[] args) throws Exception {
        boolean rescue = args.length > 0 && args[0].equals("rescue");
        boolean lyrics = args.length > 0 && args[0].equals("lyrics");
        boolean countdown = lyrics || args.length > 0 && args[0].equals("countdown");
        Path output = Path.of("build/dynamic-island-preview/" + (lyrics ? "lyrics-motion" : rescue ? "rescue-triple" : countdown ? "wave-countdown-motion" : "wave-music-round"));
        int frames = rescue ? 330 : 210;
        Files.createDirectories(output.resolve("frames"));
        var saved = new Gson().fromJson(Files.readString(Path.of("build/dynamic-island-preview/live/qq-session.json")), IslandLiveData.Music.class);
        var palette = CoverPalette.extract(saved.cover());
        try (Image sharp = Image.makeFromEncoded(Files.readAllBytes(Path.of("build/dynamic-island-preview/live/background.png")));
             Surface surface = Surface.makeRasterN32Premul(sharp.getWidth(), sharp.getHeight());
             Surface blurSurface = Surface.makeRasterN32Premul(sharp.getWidth(), sharp.getHeight());
             Surface detail = Surface.makeRasterN32Premul(820, 270);
             ImageFilter blur = ImageFilter.makeBlur(8, 8, FilterTileMode.CLAMP);
             Paint blurPaint = new Paint().setImageFilter(blur);
             DynamicIslandHudRenderer hud = new DynamicIslandHudRenderer()) {
            blurSurface.getCanvas().drawImage(sharp, 0, 0, blurPaint);
            try (Image blurred = blurSurface.makeImageSnapshot()) {
                var live = new IslandLiveController(hud.island());
                live.update(music(saved, 0), countdown ? countdown(0) : wave(0), null, 0, false);
                hud.advance(1);
                for (int frame = 0; frame < frames; frame++) {
                    double time = frame / 30.0;
                    if (rescue) {
                        if (frame == 174) live.setPeekHeld(true, true);
                        if (frame == 225) live.setPeekHeld(false, false);
                    } else if (!countdown) {
                        if (frame == 96) live.setPeekHeld(true, true);
                        if (frame == 117 || frame == 171) live.setPeekHeld(false, false);
                        if (frame == 126) live.setPeekHeld(true, false);
                    }
                    live.update(music(saved, time), countdown ? countdown(time) : wave(time), rescue ? rescue(time) : null, 1.0 / 30, rescue);
                    if (lyrics) hud.island().setMusicLyrics(true, demoLyric(saved, time));
                    if (countdown && hud.island().isExpanded()) throw new AssertionError("Countdown must remain compact");
                    float peak = (float) (.05 + .6 * Math.pow(.5 + .5 * Math.sin(time * 9), 3));
                    hud.island().setMusicVisuals(new IslandLiveData.Audio(peak, true, System.nanoTime()), palette);
                    hud.advance(1.0 / 30);
                    Canvas canvas = surface.getCanvas(); canvas.clear(0); canvas.drawImage(sharp, 0, 0);
                    int state = canvas.save();
                    try {
                        canvas.scale(2, 2);
                        hud.draw(canvas, sharp.getWidth() / 2f, sharp.getHeight() / 2f, .65f, 8, sharp, blurred, 2, 2);
                    } finally { canvas.restoreToCount(state); }
                    if (!rescue && !countdown && frame == 93) save(surface, output.resolve("dual.png"));
                    if ((rescue || countdown) && frame == 25) save(surface, output.resolve("music-compact.png"));
                    if (!rescue && !countdown && frame == 111) save(surface, output.resolve("wave-expanded.png"));
                    if (!rescue && !countdown && frame == 159) save(surface, output.resolve("music-expanded.png"));
                    if (rescue && frame == 95) save(surface, output.resolve("rescue-expanded.png"));
                    if (rescue && frame == 150) save(surface, output.resolve("rescue-satellite.png"));
                    if (rescue && frame == 215) save(surface, output.resolve("rescue-peek.png"));
                    if (rescue && frame == 315) save(surface, output.resolve("restored.png"));
                    try (Image snapshot = surface.makeImageSnapshot()) {
                        detail.getCanvas().clear(0);
                        detail.getCanvas().drawImageRect(snapshot,
                                Rect.makeXYWH((sharp.getWidth() - 820) / 2f, 0, 820, 270), Rect.makeXYWH(0, 0, 820, 270));
                    }
                    save(detail, output.resolve("frames/%04d.png".formatted(frame)));
                }
                if (!hud.island().visibleTaskIds().equals(List.of(IslandLiveController.MUSIC)))
                    throw new AssertionError("Expected only music outside the final three seconds");
                if (hud.island().isExpanded()) throw new AssertionError("Release returns to compact music");
            }
        }
        System.out.println("Rendered " + output + ": " + frames + " actual Skia frames; " + (rescue ? "automatic rescue, teammate progress, satellite, held peek and restored wave." : countdown ? "compact 3-2-1 blur/scale handoff and completion fade." : "music + round countdown, held peek and release."));
    }
    /** Text and line times are explicit visual fixtures, not a claimed QQ reading. */
    private static IslandLiveData.Lyric demoLyric(IslandLiveData.Music saved, double time) {
        String[] text = {"在希望的田野上", "让每一个平凡的瞬间都闪闪发亮，把温柔的星光留在你身旁", "一起听风，慢慢走"};
        double[] starts = {0, 2, 4.8, 7};
        int index = time < 2 ? 0 : time < 4.8 ? 1 : 2;
        return new IslandLiveData.Lyric(saved.title(), saved.artist(), text[index], System.nanoTime(),
                saved.position() + starts[index], saved.position() + starts[index + 1]);
    }
    private static IslandLiveData.Music music(IslandLiveData.Music saved, double time) {
        return new IslandLiveData.Music(saved.title(), saved.artist(), saved.album(), true,
                saved.position() + time, saved.duration(), System.nanoTime(), false, saved.coverKey(), saved.cover(), true, true, true);
    }
    private static IslandLiveData.Wave wave(double time) {
        double elapsed = 14 + time;
        int current = (int) (elapsed / 10);
        return new IslandLiveData.Wave(22, "Alien Arcadium", current, List.of(0, 10, 20, 30, 40),
                elapsed, (current + 1) * 10 - elapsed, true, "GIANT");
    }
    private static IslandLiveData.Wave countdown(double time) {
        int current = time < 5 ? 0 : 1;
        return new IslandLiveData.Wave(22, "Alien Arcadium", current, List.of(0, 5, 15),
                time, (current == 0 ? 5 : 15) - time, false, "");
    }
    private static IslandLiveData.Revive rescue(double time) {
        if (time < 1 || time >= 9) return null;
        return new IslandLiveData.Revive(List.of(
                new IslandLiveData.Rescue("Steve", true, 9 - time, (float) ((time - 1) / 8)),
                new IslandLiveData.Rescue("Alex", time >= 2, 9 - time, (float) Math.max(0, (time - 2) / 7))));
    }
    private static void save(Surface surface, Path file) throws Exception {
        try (Image image = surface.makeImageSnapshot(); Data data = image.encodeToData()) { Files.write(file, data.getBytes()); }
    }
}
