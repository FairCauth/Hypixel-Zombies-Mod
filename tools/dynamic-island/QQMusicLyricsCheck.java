package com.example.client.integration;

import com.example.client.skia.render.dynamicisland.IslandLiveData;
import com.google.gson.JsonObject;
import java.util.List;

/** Regression fixtures use no network; the optional live flag checks the fallback service on a known track. */
public final class QQMusicLyricsCheck {
    public static void main(String[] args) throws Exception {
        var lines = QQMusicLyrics.parse("[ti:Metadata]\n[offset:500]\n[00:12.34][00:22.345]Repeated line\n[00:01.2]First\n[00:16.00]\n[bad]Ignored");
        require(lines.size() == 4 && Math.abs(lines.get(0).seconds() - .7) < .001, "sort timestamps and apply positive offset");
        require(QQMusicLyrics.lineAt(lines, 0).isEmpty(), "intro has no fabricated lyric");
        require(QQMusicLyrics.lineAt(lines, 1).equals("First"), "fractional timestamp");
        require(QQMusicLyrics.lineAt(lines, 12).equals("Repeated line"), "first repeated timestamp");
        require(QQMusicLyrics.lineAt(lines, 17).isEmpty(), "instrumental blank clears the previous line");
        require(QQMusicLyrics.lineAt(lines, 22).equals("Repeated line"), "second repeated timestamp");
        require(QQMusicLyrics.lineAt(lines, 2).equals("First"), "backwards seek selects the earlier line");
        require(QQMusicLyrics.parse("[00:01]" + "x".repeat(501)).isEmpty(), "reject oversized line");
        require(QQMusicLyrics.parse("x".repeat(131073)).isEmpty(), "reject oversized payload");
        var timedLines = QQMusicLyrics.parse("[00:10]First line\n[00:14]\n[00:20]Last line");
        var halfway = QQMusicLyrics.timedLineAt(timedLines, music("Fixture", 12, false));
        require(halfway != null && halfway.startSeconds() == 10 && halfway.endSeconds() == 14,
                "timing ends at the next line, including an instrumental blank");
        require(Math.abs(halfway.sungFraction(music("Fixture", 12, false)) - .5) < .001, "halfway karaoke sweep");
        require(halfway.sungFraction(music("Fixture", 5, false)) == 0
                && halfway.sungFraction(music("Fixture", 19, false)) == 1, "seek clamps both ends");
        require(QQMusicLyrics.timedLineAt(timedLines, music("Fixture", 15, false)) == null, "instrumental has no highlight");
        var last = QQMusicLyrics.timedLineAt(timedLines, music("Fixture", 22, false));
        require(last.endSeconds() == 232, "last line uses the real track end");
        require(new IslandLiveData.Lyric("Fixture", "The Afters", "Untimed", System.nanoTime())
                .sungFraction(music("Fixture", 12, false)) == -1, "native text without timestamps invents no progress");
        require(new IslandLiveData.Lyric("Fixture", "The Afters", "Invalid", System.nanoTime(), 10, 10)
                .sungFraction(music("Fixture", 12, false)) == -1, "zero-length intervals have no progress");
        var repeated = QQMusicLyrics.parse("[00:10]Same\n[00:14]Same\n[00:18]");
        var repeat = QQMusicLyrics.timedLineAt(repeated, music("Fixture", 15, false));
        require(repeat.startSeconds() == 14 && Math.abs(repeat.sungFraction(music("Fixture", 15, false)) - .25) < .001,
                "identical consecutive lyric text restarts its sweep");
        var track = new QQMusicLyrics.Request("明日坐标", "林俊杰/王者荣耀", 284);
        JsonObject json = new JsonObject();
        json.addProperty("trackName", "明日坐标"); json.addProperty("artistName", "林俊杰 / 王者荣耀");
        json.addProperty("duration", 283); json.addProperty("syncedLyrics", "[00:01]Fixture");
        require(QQMusicLyrics.decode(track, json.toString()) != null, "normalize multi-artist spacing with rounded duration");
        json.addProperty("duration", 300);
        require(QQMusicLyrics.decode(track, json.toString()) == null, "reject a different recording's duration");
        json.addProperty("duration", 284); json.addProperty("trackName", "Other song");
        require(QQMusicLyrics.decode(track, json.toString()) == null, "reject wrong song");
        json.addProperty("trackName", "明日坐标"); json.addProperty("artistName", "Other artist");
        require(QQMusicLyrics.decode(track, json.toString()) == null, "reject wrong artist");
        require(QQMusicLyrics.decode(track, "{}") == null && QQMusicLyrics.decode(track, "bad JSON") == null, "malformed service response is ignored");
        System.out.println("Synchronized lyric fixtures passed: matching, duration, multi-artist spacing, fractional/repeated timestamps, offset, intro, instrumental, seek and limits.");
        if (List.of(args).contains("live")) verifyLive();
    }
    private static void verifyLive() throws Exception {
        try (QQMusicLyrics reader = new QQMusicLyrics()) {
            var music = music("Life Is Beautiful", 82, false);
            reader.update(music, true);
            long deadline = System.nanoTime() + 12_000_000_000L;
            while (reader.snapshot(music) == null && System.nanoTime() < deadline) Thread.sleep(100);
            var line = reader.snapshot(music);
            require(line != null, "real synchronized fallback at the actual QQ timestamp");
            System.out.println("Live fallback available at 82 / 232 seconds; lyric length=" + line.text().length());
            Thread.sleep(300);
            require(reader.snapshot(music).text().equals(line.text()), "paused timeline keeps the lyric");
            var intro = music("Life Is Beautiful", 0, false);
            require(reader.snapshot(intro) == null, "seeking into the intro clears lyrics");
            reader.update(music, false);
            require(reader.snapshot(music) == null, "disable hides cached lyrics immediately");
            reader.update(music, true);
            deadline = System.nanoTime() + 2_000_000_000L;
            while (reader.snapshot(music) == null && System.nanoTime() < deadline) Thread.sleep(50);
            require(reader.snapshot(music) != null, "reenable reuses cached lyrics");
            reader.update(music("Other song", 82, false), true);
            require(reader.snapshot(music) == null, "track switch cannot reuse the previous lyric");
            reader.close(); require(reader.snapshot(music) == null, "close clears the snapshot");
        }
        System.out.println("Live synchronized fallback checks passed.");
    }
    private static IslandLiveData.Music music(String title, double position, boolean playing) {
        return new IslandLiveData.Music(title, "The Afters", "", playing, position, 232,
                System.nanoTime(), true, "", null, false, false, false);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
