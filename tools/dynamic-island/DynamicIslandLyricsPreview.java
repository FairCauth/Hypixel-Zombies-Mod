import com.example.client.skia.render.dynamicisland.*;
import io.github.humbleui.skija.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Production Skia pixels check karaoke, spring widths, clipping and native-text fallbacks. */
public final class DynamicIslandLyricsPreview {
    private static final Path OUTPUT = Path.of("build/dynamic-island-preview/lyrics");
    private static final String TITLE = "Life Is Beautiful", ARTIST = "Fixture artist";
    private static IslandLiveData.Music music(boolean playing, double position) {
        return new IslandLiveData.Music(TITLE, ARTIST, "", playing, position, 240,
                System.nanoTime(), false, "", null, true, true, true);
    }
    private static IslandLiveData.Lyric lyric(String title, String text, long age) {
        return new IslandLiveData.Lyric(title, ARTIST, text, System.nanoTime() - age);
    }
    private static IslandLiveData.Lyric timed(String text) {
        return new IslandLiveData.Lyric(TITLE, ARTIST, text, System.nanoTime(), 10, 14);
    }
    private static DynamicIsland island() {
        DynamicIsland value = new DynamicIsland(); value.endTask("demo-main");
        value.updateLiveTask("music", music(false, 10), 10); advance(value, 1); return value;
    }
    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT);
        try (DynamicIsland value = island()) {
            BufferedImage title = render(value, "title");
            value.setMusicLyrics(false, lyric(TITLE, "在希望的田野上", 0)); advance(value, 1);
            require(equal(title, render(value, "disabled"), 0, 1200), "disabled keeps title and width");
            value.setMusicLyrics(true, lyric("Other song", "错误歌曲的歌词", 0)); advance(value, 1);
            require(equal(title, render(value, "wrong-track"), 0, 1200), "wrong track keeps title and width");
            value.setMusicLyrics(true, lyric(TITLE, "过期歌词", 4_000_000_000L)); advance(value, 1);
            require(equal(title, render(value, "stale"), 0, 1200), "stale line does not stretch the island");
            value.setMusicLyrics(true, null); advance(value, 1);
            require(equal(title, render(value, "missing"), 0, 1200), "missing lyrics keep title");

            value.setMusicLyrics(true, timed("在希望的田野上")); advance(value, 1);
            float shortWidth = value.motionSnapshot().width();
            require(shortWidth >= 137.9 && shortWidth < 175, "short line has measured compact width");
            BufferedImage unsung = render(value, "unsung");
            value.updateLiveTask("music", music(false, 12), 10);
            value.setMusicLyrics(true, timed("在希望的田野上")); advance(value, .3);
            BufferedImage halfway = render(value, "halfway");
            int left = textLeft(value), right = textRight(value);
            require(!equal(unsung, halfway, left, right), "real playback position highlights sung glyphs");
            require(equal(unsung, halfway, 0, left) && equal(unsung, halfway, right, 1200), "highlight cannot reach cover or meter");
            checkCentered(halfway, left, right);
            advance(value, 1);
            require(equal(halfway, render(value, "paused-later"), left, right), "pause freezes the highlight");
            value.updateLiveTask("music", music(false, 10), 10);
            value.setMusicLyrics(true, timed("在希望的田野上")); advance(value, .3);
            require(equal(unsung, render(value, "seek-back"), left, right), "backwards seek removes sung highlight");

            String longLine = "Life is beautiful when you are here with me, every moment feels like home";
            value.setMusicLyrics(true, timed(longLine)); advance(value, 1.0 / 60);
            float firstWidth = value.motionSnapshot().width();
            require(firstWidth > shortWidth && firstWidth < 270, "width starts a continuous spring transition");
            advance(value, 1);
            require(Math.abs(value.motionSnapshot().width() - 280) < .1, "single task maximum is 280");
            value.updateLiveTask("music", music(false, 10), 10);
            BufferedImage start = render(value, "long-start");
            value.updateLiveTask("music", music(false, 13), 10);
            value.setMusicLyrics(true, timed(longLine)); advance(value, .3);
            BufferedImage scrolled = render(value, "long-scrolled");
            left = textLeft(value); right = textRight(value);
            require(!equal(start, scrolled, left, right), "long timed lyric follows the singing boundary");
            require(equal(start, scrolled, 0, left) && equal(start, scrolled, right, 1200), "scroll stays clear of cover and meter");

            value.startTask("timer", DynamicIsland.Activity.TIMER, 0); advance(value, 1);
            require(Math.abs(value.motionSnapshot().width() - 232) < .1, "second task reduces lyric maximum to 232");
            render(value, "dual-long");
            value.setMusicLyrics(true, timed("你好")); advance(value, 1);
            require(Math.abs(value.motionSnapshot().width() - 124) < .1, "short dual line shrinks to 124");
            render(value, "dual-short");
            value.expandTask("music"); advance(value, 1);
            require(Math.abs(value.motionSnapshot().width() - 350) < .1, "lyric length never changes expanded card width");
            value.setExpanded(false); value.setMusicLyrics(false, null); advance(value, 1);
            require(Math.abs(value.motionSnapshot().width() - 124) < .1, "disable restores normal dual layout");
            value.endTask("timer"); advance(value, 1);
            require(Math.abs(value.motionSnapshot().width() - 154) < .1, "removing task restores normal solo layout");
        }
        try (DynamicIsland value = island()) {
            String text = "没有时间戳的长句依旧可以滚动显示而不会假装已经唱到某个字";
            value.updateLiveTask("music", music(true, 12), 10);
            value.setMusicLyrics(true, lyric(TITLE, text, 0)); advance(value, 1);
            BufferedImage start = render(value, "untimed-start");
            advance(value, 2);
            BufferedImage scrolled = render(value, "untimed-scrolled");
            require(!equal(start, scrolled, textLeft(value), textRight(value)), "untimed native text retains marquee");
            value.updateLiveTask("music", music(false, 12), 10);
            BufferedImage paused = render(value, "untimed-paused"); advance(value, 1);
            require(equal(paused, render(value, "untimed-paused-later"), textLeft(value), textRight(value)), "pause freezes untimed marquee");
        }
        System.out.println("Lyric Skia checks passed: fallback, glyph centering, timed highlights, pause/seek, spring sizing, solo/dual limits, safe scrolling and expanded layout.");
    }
    private static int textLeft(DynamicIsland v) { return (int) Math.ceil((200 - v.motionSnapshot().width() / 2 + 38) * 3); }
    private static int textRight(DynamicIsland v) { return (int) Math.floor((200 + v.motionSnapshot().width() / 2 - 40) * 3); }
    private static void advance(DynamicIsland v, double seconds) {
        for (int i = 0; i < Math.round(seconds * 60); i++) v.advance(1.0 / 60);
    }
    private static BufferedImage render(DynamicIsland value, String name) throws Exception {
        try (Surface surface = Surface.makeRasterN32Premul(1200, 180)) {
            Canvas c = surface.getCanvas(); c.clear(0); c.scale(3, 3); value.draw(c, 200, 8);
            try (Image image = surface.makeImageSnapshot(); Data data = image.encodeToData()) {
                byte[] png = data.getBytes(); Files.write(OUTPUT.resolve(name + ".png"), png);
                return ImageIO.read(new ByteArrayInputStream(png));
            }
        }
    }
    private static boolean equal(BufferedImage a, BufferedImage b, int left, int right) {
        for (int y = 0; y < a.getHeight(); y++) for (int x = left; x < right; x++)
            if (a.getRGB(x, y) != b.getRGB(x, y)) return false;
        return true;
    }
    private static void checkCentered(BufferedImage pixels, int left, int right) {
        int top = 180, bottom = -1;
        for (int y = 45; y < 110; y++) for (int x = left; x < right; x++) {
            int color = pixels.getRGB(x, y);
            if ((color >>> 24) > 180 && (color >> 16 & 255) > 220 && (color >> 8 & 255) > 220 && (color & 255) > 220) {
                top = Math.min(top, y); bottom = Math.max(bottom, y);
            }
        }
        require(bottom >= top && Math.abs((top + bottom + 1) / 2f - 78) <= 2, "Chinese lyric aligns with cover");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
