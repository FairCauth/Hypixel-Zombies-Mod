import com.example.client.skia.render.dynamicisland.*;
import com.google.gson.Gson;
import com.example.client.integration.CoverPalette;
import io.github.humbleui.skija.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Production renderer + real QQ metadata; game activity fixtures are explicitly simulated. */
public final class DynamicIslandLivePreview {
    private static final IslandLiveData.Wave WAVE = new IslandLiveData.Wave(22, "Alien Arcadium", 1,
            List.of(0, 10, 20, 30, 40), 17.4, 2.6, true, "GIANT");
    private static final IslandLiveData.Revive REVIVE = new IslandLiveData.Revive(List.of(
            new IslandLiveData.Rescue("Steve", true, 2.4, .40f),
            new IslandLiveData.Rescue("Alex", false, 4, .04f)));

    public static void main(String[] args) throws Exception {
        Path output = Path.of("build/dynamic-island-preview/live"); Files.createDirectories(output);
        IslandLiveData.Music music = new Gson().fromJson(Files.readString(output.resolve("qq-session.json")), IslandLiveData.Music.class);
        verifyScheduling(music);
        verifyHeldPeek(music);
        verifyMusicIdle(music);
        if (args.length == 0) {
            System.out.println("Live scheduling passed: priority, held peek, queued wave, 30-second wall-clock music idle, task promotion and immediate playback resume.");
            return;
        }
        BufferedImage source = ImageIO.read(Path.of(args[0]).toFile());
        BufferedImage game = source.getSubimage(0, 31, source.getWidth(), source.getHeight() - 31);
        Path background = output.resolve("background.png"); ImageIO.write(game, "png", background.toFile());
        try (Image sharp = Image.makeFromEncoded(Files.readAllBytes(background));
             Surface target = Surface.makeRasterN32Premul(game.getWidth(), game.getHeight());
             Surface blurSurface = Surface.makeRasterN32Premul(game.getWidth(), game.getHeight());
             ImageFilter blur = ImageFilter.makeBlur(8, 8, FilterTileMode.CLAMP);
             Paint blurPaint = new Paint().setImageFilter(blur);
             DynamicIslandHudRenderer hud = new DynamicIslandHudRenderer()) {
            blurSurface.getCanvas().drawImage(sharp, 0, 0, blurPaint);
            try (Image blurred = blurSurface.makeImageSnapshot()) {
            IslandLiveController live = new IslandLiveController(hud.island());
            hud.island().setMusicVisuals(null, CoverPalette.extract(music.cover()));
            live.update(music, null, null, 0, false); hud.advance(1);
            draw(target, hud, sharp, blurred); save(target, output.resolve("music-compact.png"));
            live.toggle(false); hud.advance(1); draw(target, hud, sharp, blurred); save(target, output.resolve("music-expanded.png"));
            hud.island().setExpanded(false);
            live.update(music, WAVE, null, 0, false); hud.advance(1);
            draw(target, hud, sharp, blurred); save(target, output.resolve("wave-music.png"));
            live.toggle(true); hud.advance(1); draw(target, hud, sharp, blurred); save(target, output.resolve("wave-expanded.png"));
            hud.island().setExpanded(false);
            live.update(music, WAVE, REVIVE, 0, false); hud.advance(1); live.toggle(false); hud.advance(1);
            draw(target, hud, sharp, blurred); save(target, output.resolve("revive-expanded.png"));
            live.update(music, WAVE, null, 0, false); hud.advance(1);
            draw(target, hud, sharp, blurred); save(target, output.resolve("rescue-ended.png"));
            for (int i = 0; i < 80; i++) {
                live.update(music, i % 3 == 0 ? null : WAVE, i % 5 == 0 ? REVIVE : null, 1. / 60, true);
                hud.advance(1. / 60); draw(target, hud, sharp, blurred);
                require(target.getCanvas().getSaveCount() == 1, "balanced canvas through live interruptions");
            }
            }
        }
        System.out.println("Live island checks passed: real QQ cover/Chinese title, priority, queued media, module removal, automatic glances, manual override, held Tab/release, 30-second music idle/resume, empty state, interrupted drawing.");
    }

    private static void verifyMusicIdle(IslandLiveData.Music music) {
        var clock = new java.util.concurrent.atomic.AtomicLong();
        var paused = playback(music, false);
        var playing = playback(music, true);
        try (DynamicIsland island = new DynamicIsland()) {
            var live = new IslandLiveController(island, clock::get);
            live.update(paused, WAVE, null, 0, false);
            live.toggle(false);
            clock.set(29_999_000_000L);
            live.update(paused, WAVE, null, .001, false);
            require(island.visibleTaskIds().contains(IslandLiveController.MUSIC), "brief pause retains music and its controls");
            clock.set(30_000_000_000L);
            live.update(paused, WAVE, null, .001, false);
            require(island.visibleTaskIds().equals(List.of(IslandLiveController.WAVE)) && !island.isExpanded(),
                    "wall-clock idle removes focused music and promotes wave even with tiny render dt");
            live.update(paused, WAVE, REVIVE, 0, false);
            require(island.visibleTaskIds().equals(List.of(IslandLiveController.REVIVE, IslandLiveController.WAVE)),
                    "idle music frees a slot for rescue and wave");
            live.update(playing, WAVE, null, 0, false);
            require(island.visibleTaskIds().equals(List.of(IslandLiveController.MUSIC, IslandLiveController.WAVE)),
                    "resuming restores music immediately");
            live.update(paused, null, null, 0, false);
            clock.set(59_999_000_000L);
            live.update(paused, null, null, 0, false);
            require(island.visibleTaskIds().contains(IslandLiveController.MUSIC), "new pause starts a fresh grace period");
            clock.set(60_000_000_000L);
            live.update(paused, null, null, 0, false);
            require(island.visibleTaskIds().isEmpty(), "idle music alone leaves no island");
            live.update(paused, null, null, 0, false);
            require(island.visibleTaskIds().isEmpty(), "repeated paused metadata cannot restore an expired island");
            live.update(playing, null, null, 0, false);
            require(island.visibleTaskIds().equals(List.of(IslandLiveController.MUSIC)), "playback recovers after complete hide");
        }
    }

    private static IslandLiveData.Music playback(IslandLiveData.Music music, boolean playing) {
        return new IslandLiveData.Music(music.title(), music.artist(), music.album(), playing, music.position(),
                music.duration(), music.sampledNanos(), music.timelineAdvances(), music.coverKey(), music.cover(),
                music.canToggle(), music.canPrevious(), music.canNext());
    }

    private static void verifyHeldPeek(IslandLiveData.Music music) {
        try (DynamicIsland island = new DynamicIsland()) {
            IslandLiveController live = new IslandLiveController(island);
            live.setPeekHeld(true, false);
            live.update(music, WAVE, null, 0, true);
            require(IslandLiveController.MUSIC.equals(island.expandedTaskId()), "music capsule opens when tasks arrive while Tab is held");
            live.update(music, WAVE, null, 5, true);
            require(island.isExpanded(), "automatic glance timeout cannot close held Tab");
            live.setPeekHeld(true, true);
            require(IslandLiveController.WAVE.equals(island.expandedTaskId()), "Shift+Tab opens countdown satellite");
            live.toggle(false);
            require(IslandLiveController.WAVE.equals(island.expandedTaskId()), "toggle cannot interrupt held peek");
            live.update(music, WAVE, REVIVE, .1, true);
            require(IslandLiveController.REVIVE.equals(island.expandedTaskId()), "held secondary follows rescue satellite without automatic interruption");
            live.update(null, null, null, .1, true);
            require(!island.isExpanded(), "empty sources clear held presentation");
            live.update(music, null, null, .1, true);
            require(IslandLiveController.MUSIC.equals(island.expandedTaskId()), "one task falls back to main during Shift+Tab");
            live.setPeekHeld(false, false);
            live.update(music, WAVE, REVIVE, .1, true);
            require(IslandLiveController.REVIVE.equals(island.expandedTaskId()), "a genuinely new rescue can interrupt ordinary quiet after release");
            live.setPeekHeld(true, false);
            live.setPeekHeld(false, false);
            live.update(music, WAVE, REVIVE, .1, true);
            require(!island.isExpanded(), "release does not reopen an already announced rescue");
            live.setPeekHeld(true, true);
            island.advance(.04);
            live.setPeekHeld(false, false);
            island.advance(1);
            require(!island.isExpanded() && !island.motionSnapshot().merging()
                    && island.motionSnapshot().reveal() < .01, "release reverses unfinished merge");
            live.toggle(false);
            require(island.isExpanded(), "persistent toggle still works after release");
            live.setPeekHeld(true, false);
            live.setPeekHeld(false, false);
            require(!island.isExpanded(), "release also closes an already expanded task");
        }
    }

    private static void verifyScheduling(IslandLiveData.Music music) {
        try (DynamicIsland island = new DynamicIsland()) {
            IslandLiveController live = new IslandLiveController(island);
            live.update(null, null, null, 0, true);
            require(island.visibleTaskIds().isEmpty(), "live mode has no demo fallback");
            live.update(music, WAVE, null, 0, false);
            require(island.visibleTaskIds().equals(List.of(IslandLiveController.MUSIC, IslandLiveController.WAVE)), "music capsule and wave satellite occupy two slots");
            live.toggle(true);
            require(IslandLiveController.WAVE.equals(island.expandedTaskId()), "secondary wave focus");
            live.update(music, WAVE, REVIVE, .1, true);
            require(island.visibleTaskIds().equals(List.of(IslandLiveController.REVIVE, IslandLiveController.MUSIC)), "new rescue takes the primary slot");
            require(IslandLiveController.REVIVE.equals(island.expandedTaskId()), "new rescue interrupts ordinary manual quiet");
            require(island.taskSnapshot(IslandLiveController.WAVE) != null, "wave remains queued during rescue");
            live.update(music, WAVE, null, .1, false);
            require(island.visibleTaskIds().contains(IslandLiveController.MUSIC), "rescue ending restores music");
            live.update(music, null, null, 0, false);
            require(island.visibleTaskIds().equals(List.of(IslandLiveController.MUSIC)), "module disable removes game activity");
        }
        try (DynamicIsland island = new DynamicIsland()) {
            IslandLiveController live = new IslandLiveController(island);
            live.update(music, WAVE, null, 0, true);
            require(!island.isExpanded(), "imminent wave stays compact with automatic glances enabled");
            live.update(music, WAVE, null, 2.3, true);
            require(!island.isExpanded(), "wave countdown never expands automatically");
            live.update(music, WAVE, REVIVE, 0, true);
            require(IslandLiveController.REVIVE.equals(island.expandedTaskId()), "new rescue glances");
            live.toggle(false);
            live.update(music, WAVE, REVIVE, 3, true);
            require(!island.isExpanded() && island.visibleTaskIds().equals(List.of(IslandLiveController.MUSIC, IslandLiveController.REVIVE)), "manual collapse places rescue in satellite");
            live.setPeekHeld(true, true);
            live.update(music, WAVE, REVIVE, 5, true);
            require(IslandLiveController.REVIVE.equals(island.expandedTaskId()), "secondary rescue stays focused while held");
            live.setPeekHeld(false, false);
            live.update(null, null, null, 0, true);
            require(island.visibleTaskIds().isEmpty() && !island.isExpanded(), "all sources gone hides island");
        }
        try (DynamicIsland island = new DynamicIsland()) {
            var live = new IslandLiveController(island);
            live.update(music, WAVE, REVIVE, 0, true);
            live.update(music, WAVE, REVIVE, 3.3, true);
            require(!island.isExpanded() && island.visibleTaskIds().equals(List.of(IslandLiveController.MUSIC, IslandLiveController.REVIVE)), "automatic rescue collapses into green satellite");
            var remaining = new IslandLiveData.Revive(List.of(REVIVE.rescues().getFirst()));
            live.update(music, WAVE, remaining, .1, true);
            require(!island.isExpanded(), "one teammate completing does not announce remaining teammate again");
            live.update(music, WAVE, null, .1, true);
            require(island.visibleTaskIds().equals(List.of(IslandLiveController.MUSIC, IslandLiveController.WAVE)), "rescue completion restores round satellite");
            live.update(null, WAVE, REVIVE, .1, false);
            require(island.visibleTaskIds().equals(List.of(IslandLiveController.REVIVE, IslandLiveController.WAVE)) && !island.isExpanded(), "without music rescue leads wave and disabled glances stay closed");
        }
    }

    private static void draw(Surface surface, DynamicIslandHudRenderer hud, Image background, Image blurred) {
        Canvas c = surface.getCanvas(); c.clear(0); c.drawImage(background, 0, 0);
        int saved = c.save();
        try {
            c.scale(2, 2);
            hud.draw(c, surface.getWidth() / 2f, surface.getHeight() / 2f, .65f, 8,
                    background, blurred, 2, 2);
        } finally { c.restoreToCount(saved); }
    }
    private static void save(Surface surface, Path file) throws Exception {
        try (Image image = surface.makeImageSnapshot(); Data data = image.encodeToData()) { Files.write(file, data.getBytes()); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
