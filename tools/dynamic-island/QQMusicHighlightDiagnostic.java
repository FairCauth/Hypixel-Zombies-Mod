package com.example.client.integration;

import com.example.client.skia.render.dynamicisland.IslandLiveData;

/** Read-only live observations; never sends playback commands or changes QQ settings. */
public final class QQMusicHighlightDiagnostic {
    public static void main(String[] args) throws Exception {
        try (var bridge = new QQMusicBridge(true)) {
            for (int sample = 0; sample < 25; sample++) {
                var current = bridge.snapshot();
                if (current != null) {
                    var music = current.atTime(System.nanoTime());
                    var line = bridge.lyricSnapshot(music);
                    var reader = (QQMusicLyrics) get(bridge, "lyricsReader");
                    var result = (QQMusicLyrics.Result) get(reader, "result");
                    var nativeLine = (IslandLiveData.Lyric) get(bridge, "lyric");
                    System.out.printf("%s / %s; %.3f/%.0f; playing=%s; source=%s; LRC=%s; native=%s; text=%s; interval=%s; cues=%s; highlight=%s%n",
                            music.title(), music.artist(), music.positionNow(), music.duration(), music.playing(), bridge.timelineSource(),
                            result == null ? "unavailable" : result.lines().size(), nativeLine == null ? "none" : nativeLine.text(),
                            line == null ? "none" : line.text(), line == null ? "none" : line.startSeconds() + ".." + line.endSeconds(),
                            line == null ? "none" : line.wordCues(), line == null ? "none" : line.sungFraction(music, .25));
                } else System.out.println(bridge.status());
                Thread.sleep(1000);
            }
        }
    }
    private static Object get(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
}
