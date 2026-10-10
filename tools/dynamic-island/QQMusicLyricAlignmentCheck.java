package com.example.client.integration;

import com.example.client.skia.render.dynamicisland.DynamicIsland;
import com.example.client.skia.render.dynamicisland.IslandLiveData;
import com.google.gson.JsonObject;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Exercises production Bridge resolution with a validated, injected LRC; no QQ or network access. */
public final class QQMusicLyricAlignmentCheck {
    private static final String TITLE = "Alignment fixture", ARTIST = "Fixture artist";
    private static final List<QQMusicLyrics.Line> LINES = QQMusicLyrics.parse("""
            [00:10.00]First phrase
            [00:10.00]第一句
            [00:10.00]
            [00:14.00]Second phrase
            [00:18.00]Same chorus
            [00:22.00]Another phrase
            [00:26.00]Same chorus
            [00:30.00]
            [00:34.00]Last phrase
            """);

    public static void main(String[] args) throws Exception {
        check(12, "First phrase", "First phrase", 10, 14, .5f);
        check(12, "第一句", "第一句", 10, 14, .5f);
        check(12, "First phrase  第一句", "First phrase 第一句", 10, 14, .5f);
        check(12, "Ｆｉｒｓｔ\u200B phrase! ♪", "Ｆｉｒｓｔ\u200B phrase! ♪", 10, 14, .5f);
        check(12, "A different QQ translation", "First phrase", 10, 14, .5f);
        check(12, "First phr…", "First phrase", 10, 14, .5f);
        check(14.4, "First phrase", "First phrase", 10, 14, 1);
        check(13.6, "Second phrase", "Second phrase", 14, 18, 0);
        check(15.2, "First phrase", "Second phrase", 14, 18, .3f);
        check(12, "Same chorus", "First phrase", 10, 14, .5f);
        check(28, "Same chorus", "Same chorus", 26, 30, .5f);
        check(20, "Same chorus", "Same chorus", 18, 22, .5f);
        check(12, "", "First phrase", 10, 14, .5f);
        check(12, "First phrase", "First phrase", 10, 14, .5f, 4_000_000_000L);

        require(QQMusicLyrics.timedLineAt(LINES, music(32), null) == null, "instrumental blank stays blank");
        require(QQMusicLyrics.timedLineAt(LINES, music(8), null) == null, "intro has no invented line");
        var wrong = new IslandLiveData.Lyric("Wrong song", ARTIST, "第一句", System.nanoTime());
        require(QQMusicLyrics.timedLineAt(LINES, music(12), wrong).text().equals("First phrase"), "wrong-song native text cannot select translation");
        var enhanced = QQMusicLyrics.parse("[00:10]<00:10.0>First <00:12.0>phrase\n[00:14]Next");
        require(enhanced.getFirst().text().equals("First phrase"), "enhanced word timestamp tags do not break line matching");
        require(QQMusicLyrics.timedLineAt(enhanced, music(12), nativeLine("First phrase")).sungFraction(music(12)) == .5f,
                "enhanced timestamp selects the exact word boundary");

        checkWordTiming();

        checkContinuous();
        checkUnavailable();
        System.out.println("Lyric alignment passed: production Bridge, bilingual/combined labels, boundaries, repeats, seeks, word timing, terminal silence, Unicode, offset/repeated word schedules, invalid schedule fallback, highlight-only advance, pause and continuous highlights.");
        if (List.of(args).contains("render")) checkPixels();
    }

    private static void checkWordTiming() {
        var lines = QQMusicLyrics.parse("[00:10]<00:10>First <00:11>phrase<00:12>\n[00:18]Next");
        var line = QQMusicLyrics.timedLineAt(lines, music(11.5), nativeLine("First phrase!"));
        require(line.wordCues().size() == 3 && line.text().equals("First phrase"), "native punctuation retains actual word offsets");
        require(Math.abs(line.sungFraction(music(11.5)) - .75) < .001, "word timing follows singing rather than the long gap before next line");
        require(line.sungFraction(music(15)) == 1, "terminal word timestamp finishes before the inter-line pause");
        require(QQMusicLyrics.timedLineAt(lines, music(17.9)).text().equals("First phrase"), "word end does not advance the line switch");
        require(QQMusicLyrics.timedLineAt(lines, music(18)).text().equals("Next"), "line switch still uses the unshifted playback time");
        require(Math.abs(line.sungFraction(music(11.5), .25) - .875) < .001, "independent 250ms highlight advance");
        require(Math.abs(line.sungFraction(music(11.5), -.25) - .625) < .001, "negative advance delays only the sweep");
        require(line.sungFraction(music(9.9), 2) == 0, "early native next line cannot highlight before it actually starts");
        require(line.sungFraction(music(11.5), Double.NaN) == line.sungFraction(music(11.5)), "invalid configuration cannot poison the sweep");
        var held = music(11.5);
        float paused = line.sungFraction(held, .25);
        require(line.sungFraction(held, .25) == paused && held.positionNow() == 11.5, "pause and working music progress remain unchanged");
        float previous = -1;
        for (int frame = 0; frame < 480; frame++) {
            float value = line.sungFraction(music(10 + frame / 60.0), .25);
            require(value >= previous && value >= 0 && value <= 1, "word sweep remains monotonic through cues and silence");
            previous = value;
        }
        require(line.sungFraction(music(10.5), .25) < paused, "seek recomputes word progress without a second clock");
        var shifted = QQMusicLyrics.parse("[offset:500]\n[00:10][00:20]  <00:10>你🎵<00:11>好<00:12>  \n[00:30]End");
        var repeat = QQMusicLyrics.timedLineAt(shifted, music(20.5));
        require(repeat.text().equals("你🎵好") && repeat.wordCues().get(1).textOffset() == 2, "trim and Unicode code-point offsets remain aligned");
        require(repeat.wordCues().getFirst().seconds() == 19.5 && repeat.sungFraction(music(20.5)) > .66,
                "repeated word schedules and LRC offset shift together");
        var invalid = QQMusicLyrics.parse("[00:10]<00:11>First <00:10>phrase\n[00:14]Next");
        var fallback = QQMusicLyrics.timedLineAt(invalid, music(12));
        require(fallback.wordCues().isEmpty() && fallback.sungFraction(music(12)) == .5f,
                "broken word schedules retain ordinary line highlight rather than losing it");
        var plain = QQMusicLyrics.timedLineAt(LINES, music(12));
        require(plain.sungFraction(music(12), .25) == .5625f && plain.startSeconds() == 10 && plain.endSeconds() == 14,
                "plain LRC compensation leaves the real line interval intact");
        var untimed = nativeLine("No word times");
        require(untimed.sungFraction(music(12), .25) == -1, "advance invents no timing for untimed lyrics");
    }

    private static void check(double position, String nativeText, String expected, double start, double end, float fraction) throws Exception {
        check(position, nativeText, expected, start, end, fraction, 0);
    }

    private static void check(double position, String nativeText, String expected, double start, double end,
                              float fraction, long age) throws Exception {
        try (var bridge = fixture(position, nativeText)) {
            if (age > 0) set(bridge, "lyric", new IslandLiveData.Lyric(TITLE, ARTIST, nativeText, System.nanoTime() - age));
            var music = bridge.snapshot().atTime(System.nanoTime());
            var line = bridge.lyricSnapshot(music);
            require(line != null && line.text().equals(expected), "resolved text at " + position + ": " + nativeText);
            require(line.startSeconds() == start && line.endSeconds() == end, "real distinct timestamp interval at " + position);
            require(Math.abs(line.sungFraction(music) - fraction) < .001, "highlight fraction at " + position);
            require(music.positionNow() == position, "lyric resolution never changes the working playback clock");
        }
    }

    private static void checkContinuous() {
        float previous = -1;
        for (int frame = 0; frame < 240; frame++) {
            double position = 10 + frame / 60.0;
            String[] nativeTexts = {"First phrase", "第一句", "Different translation", "First phr…", "First phrase 第一句"};
            var line = QQMusicLyrics.timedLineAt(LINES, music(position), nativeLine(nativeTexts[(frame / 30) % nativeTexts.length]));
            float progress = line.sungFraction(music(position));
            require(progress >= previous && progress >= 0, "native changes must not drop or rewind a timed sweep, frame " + frame);
            require(line.startSeconds() == 10 && line.endSeconds() == 14, "all variants share one interval");
            previous = progress;
        }
    }

    private static void checkUnavailable() throws Exception {
        try (var bridge = new QQMusicBridge(true, false)) {
            bridge.accept(packet(12, "QQ only line").toString());
            var music = bridge.snapshot();
            require(bridge.lyricSnapshot(music).text().equals("QQ only line"), "no synchronized recording preserves QQ text");
            require(bridge.lyricSnapshot(music).sungFraction(music) == -1, "missing timestamps invent no karaoke timing");
            bridge.setLyricsEnabled(false);
            require(bridge.lyricSnapshot(music) == null, "disabled lyrics stay hidden even with a cached recording");
        }
        try (var bridge = fixture(12, "First phrase")) {
            bridge.setLyricsEnabled(false);
            require(bridge.lyricSnapshot() == null, "disabled cached timed text stays hidden");
            bridge.setLyricsEnabled(true);
            require(bridge.lyricSnapshot() == null, "toggle clears old line until recording is refreshed");
        }
    }

    private static QQMusicBridge fixture(double position, String nativeText) throws Exception {
        var bridge = new QQMusicBridge(true, false);
        bridge.accept(packet(position, nativeText).toString());
        var reader = (QQMusicLyrics) get(bridge, "lyricsReader");
        set(reader, "result", new QQMusicLyrics.Result(QQMusicLyrics.Request.of(bridge.snapshot()), LINES));
        return bridge;
    }

    private static JsonObject packet(double position, String nativeText) {
        var packet = new JsonObject();
        packet.addProperty("status", "ready"); packet.addProperty("source", "QQMusic.exe");
        packet.addProperty("title", TITLE); packet.addProperty("artist", ARTIST); packet.addProperty("album", "");
        packet.addProperty("playing", false); packet.addProperty("position", position); packet.addProperty("duration", 60);
        packet.addProperty("timelineSource", "media-session"); packet.addProperty("timelineAdvances", true);
        packet.addProperty("lyric", nativeText);
        return packet;
    }

    private static IslandLiveData.Music music(double position) {
        return new IslandLiveData.Music(TITLE, ARTIST, "", false, position, 60, System.nanoTime(), false,
                "", null, false, false, false);
    }

    private static IslandLiveData.Lyric nativeLine(String text) {
        return new IslandLiveData.Lyric(TITLE, ARTIST, text, System.nanoTime());
    }

    private static void checkPixels() throws Exception {
        Path output = Path.of("build/dynamic-island-preview/lyrics"); Files.createDirectories(output);
        for (String nativeText : List.of("第一句", "Different QQ translation")) {
            BufferedImage[] images = new BufferedImage[2];
            for (int i = 0; i < 2; i++) {
                try (var bridge = fixture(10 + i * 2, nativeText); var island = new DynamicIsland();
                     var surface = Surface.makeRasterN32Premul(1200, 180)) {
                    var music = bridge.snapshot().atTime(System.nanoTime());
                    island.endTask("demo-main"); island.updateLiveTask("music", music, 10);
                    island.setMusicLyrics(true, bridge.lyricSnapshot(music));
                    for (int frame = 0; frame < 60; frame++) island.advance(1.0 / 60);
                    var canvas = surface.getCanvas(); canvas.clear(0); canvas.scale(3, 3); island.draw(canvas, 200, 8);
                    try (Image image = surface.makeImageSnapshot(); Data data = image.encodeToData()) {
                        byte[] png = data.getBytes();
                        Files.write(output.resolve((nativeText.equals("第一句") ? "aligned-bilingual-" : "aligned-fallback-") + i + ".png"), png);
                        images[i] = ImageIO.read(new ByteArrayInputStream(png));
                    }
                }
            }
            int changed = 0;
            for (int y = 40; y < 110; y++) for (int x = 470; x < 660; x++)
                if (images[0].getRGB(x, y) != images[1].getRGB(x, y)) changed++;
            require(changed > 10, "production Bridge's " + nativeText + " draws a visible Skia highlight");
        }
        System.out.println("Production Bridge-to-Skia highlights passed for bilingual and unmatched native text.");
        var wordLines = QQMusicLyrics.parse("[00:10]<00:10>First <00:11>phrase<00:12>\n[00:18]Next");
        BufferedImage[] advanced = new BufferedImage[2];
        for (int i = 0; i < 2; i++) {
            try (var island = new DynamicIsland(); var surface = Surface.makeRasterN32Premul(1200, 180)) {
                var music = music(11.5);
                island.endTask("demo-main"); island.updateLiveTask("music", music, 10);
                island.setMusicLyrics(true, QQMusicLyrics.timedLineAt(wordLines, music), i * .25);
                for (int frame = 0; frame < 60; frame++) island.advance(1.0 / 60);
                var canvas = surface.getCanvas(); canvas.clear(0); canvas.scale(3, 3); island.draw(canvas, 200, 8);
                try (Image image = surface.makeImageSnapshot(); Data data = image.encodeToData()) {
                    byte[] png = data.getBytes();
                    Files.write(output.resolve("word-advance-" + i + ".png"), png);
                    advanced[i] = ImageIO.read(new ByteArrayInputStream(png));
                }
                require(music.positionNow() == 11.5, "render compensation leaves real progress untouched");
            }
        }
        int changed = 0;
        for (int y = 40; y < 110; y++) for (int x = 470; x < 740; x++)
            if (advanced[0].getRGB(x, y) != advanced[1].getRGB(x, y)) changed++;
        require(changed > 15, "250ms advance visibly moves the real Skia word highlight");
        System.out.println("Enhanced word timing and highlight-only advance draw different Skia pixels at the same paused music position.");
    }

    private static Object get(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
