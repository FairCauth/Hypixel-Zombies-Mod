package com.example.client.integration;

import com.example.client.skia.render.dynamicisland.IslandLiveData;
import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads actual playback time, then verifies timeline state changes with explicitly injected fixtures. */
public final class QQMusicTimelineCheck {
    public static void main(String[] args) throws Exception {
        if (java.util.Arrays.asList(args).contains("fixtures")) {
            try (QQMusicBridge bridge = new QQMusicBridge(false, false)) {
                bridge.close(); // Keep native processes and lyric network queries out of these fixtures.
                verifySnapshots(bridge);
            }
            System.out.println("Bridge timeline/lyric packet fixtures passed.");
            return;
        }
        try (QQMusicBridge bridge = new QQMusicBridge(true)) {
            long deadline = System.nanoTime() + 15_000_000_000L;
            IslandLiveData.Music track = null;
            while (System.nanoTime() < deadline) {
                track = bridge.snapshot();
                if (track != null && track.duration() > 0) break;
                Thread.sleep(100);
            }
            require(track != null && track.duration() > 0, "real QQ playback duration is available");
            require(track.position() >= 0 && track.position() <= track.duration(), "actual elapsed time is within track duration");
            System.out.printf("Real QQ timeline: %s / %s; %.1f / %.1f seconds; playing=%s; source=%s%n",
                    track.title(), track.artist(), track.positionNow(), track.duration(), track.playing(), bridge.timelineSource());
            var lyric = bridge.lyricSnapshot();
            long initialLyricDeadline = System.nanoTime() + 12_000_000_000L;
            while (lyric == null && System.nanoTime() < initialLyricDeadline) { Thread.sleep(100); lyric = bridge.lyricSnapshot(); }
            if (java.util.Arrays.asList(args).contains("require-lyrics")) require(lyric != null, "actual QQ track has a synchronized lyric");
            System.out.println("Real QQ lyric: " + (lyric == null ? "unavailable (title fallback)" : lyric.text()));
            if (lyric != null) {
                bridge.setLyricsEnabled(false);
                require(bridge.lyricSnapshot() == null, "live lyric switch clears immediately");
                Thread.sleep(1200);
                require(bridge.lyricSnapshot() == null && bridge.snapshot() != null, "disabled lyrics stay hidden while media polling continues");
                bridge.setLyricsEnabled(true);
                long lyricDeadline = System.nanoTime() + 10_000_000_000L;
                while (bridge.lyricSnapshot() == null && System.nanoTime() < lyricDeadline) Thread.sleep(100);
                require(bridge.lyricSnapshot() != null, "live helper receives the re-enabled lyric preference");
                System.out.println("Live QQ lyric switch off/on passed.");
            }
            Path output = Path.of("build/dynamic-island-preview/live"); Files.createDirectories(output);
            Files.writeString(output.resolve("qq-session.json"), new Gson().toJson(track));
            if (java.util.Arrays.asList(args).contains("observe")) {
                IslandLiveData.Music previousTrack = null;
                double previousPosition = -1;
                for (int i = 0; i < 60; i++) {
                    var current = bridge.snapshot();
                    require(current != null && current.duration() > 0, "continuous real timeline stays available at sample " + i);
                    require(current.positionNow() >= 0 && current.positionNow() <= current.duration(), "continuous progress remains in range");
                    double position = current.positionNow();
                    if (previousTrack != null && current.playing() && previousTrack.playing()
                            && current.title().equals(previousTrack.title()) && current.artist().equals(previousTrack.artist()))
                        require(position >= previousPosition, "actual continuous playback does not rewind at sample " + i);
                    previousTrack = current; previousPosition = position;
                    if (java.util.Arrays.asList(args).contains("require-lyrics"))
                        require(bridge.lyricSnapshot() != null, "continuous lyric stays available at sample " + i);
                    Thread.sleep(500);
                }
                System.out.println("Continuous QQ polling passed: 60 samples over 30 seconds, timeline available; lyric availability asserted only with require-lyrics.");
            }
            if (args.length > 0 && args[0].equals("watch")) {
                for (int i = 0; i < 120; i++) {
                    var current = bridge.snapshot();
                    System.out.printf("QQ time: %.2f / %.0f; playing=%s; source=%s%n",
                            current == null ? -1 : current.positionNow(), current == null ? -1 : current.duration(),
                            current != null && current.playing(), bridge.timelineSource());
                    Thread.sleep(1000);
                }
            }
            bridge.close(); // Fixtures cannot race with genuine background snapshots.
            Thread.sleep(600);
            verifySnapshots(bridge);
        }
        System.out.println("QQ timeline checks passed: actual elapsed/duration read; fixture pause, interpolation, confirmed backwards seek, song change, bounded dropout and media packets.");
    }
    private static void verifySnapshots(QQMusicBridge bridge) throws Exception {
        bridge.accept(packet("Fixture", false, 42, 232));
        double paused = bridge.snapshot().positionNow(); Thread.sleep(80);
        require(bridge.snapshot().positionNow() == paused, "paused snapshot does not advance");
        bridge.accept(packet("Fixture", true, 42, 232)); Thread.sleep(80);
        require(bridge.snapshot().positionNow() > 42.05, "playing snapshot interpolates between real time readings");
        bridge.accept(packet("Fixture", true, 12, 232));
        require(bridge.snapshot().positionNow() > 42, "isolated bad packet cannot rewind the clock");
        bridge.accept(packet("Fixture", true, 12, 232));
        require(bridge.snapshot().positionNow() < 13, "confirmed backwards seek replaces old progress");
        bridge.accept(packet("New track", false, 0, 305));
        require(bridge.snapshot().positionNow() == 0 && bridge.snapshot().duration() == 305, "song change never reuses the previous timeline");
        String missing = packet("New track", true, 0, 0).replace("\"timelineAdvances\":true", "\"timelineAdvances\":false");
        bridge.accept(missing);
        require(bridge.snapshot().duration() == 305, "brief source dropout retains the real duration without resetting progress");
        Thread.sleep(80);
        require(bridge.snapshot().positionNow() > .05, "short source dropout continues the confirmed clock");
        Thread.sleep(3100);
        bridge.accept(missing);
        require(bridge.snapshot().duration() == 0, "missing timeline cannot retain invented duration");
        bridge.accept("{\"status\":\"meter\",\"available\":true,\"peak\":0.7}");
        require(bridge.snapshot() != null, "independent meter packet keeps current timeline metadata");
        verifyLyrics(bridge);
    }
    private static void verifyLyrics(QQMusicBridge bridge) {
        bridge.setLyricsEnabled(true);
        var json = com.google.gson.JsonParser.parseString(packet("Lyric track", true, 0, 0)).getAsJsonObject();
        json.addProperty("lyric", "  在希望的田野上  ");
        bridge.accept(json.toString());
        require(bridge.lyricSnapshot() != null && bridge.lyricSnapshot().text().equals("在希望的田野上"),
                "lyrics remain available without a timeline and strip outer whitespace");
        bridge.accept("{\"status\":\"meter\",\"available\":true,\"peak\":0.4}");
        require(bridge.lyricSnapshot() != null, "audio packets do not erase lyrics");
        bridge.setLyricsEnabled(false);
        require(bridge.lyricSnapshot() == null, "switching lyrics off clears the line immediately");
        bridge.accept(json.toString());
        require(bridge.lyricSnapshot() == null, "disabled lyric packets stay hidden");
        bridge.setLyricsEnabled(true);
        bridge.accept(json.toString());
        bridge.accept(packet("Next lyric track", true, 0, 240));
        require(bridge.lyricSnapshot() == null, "a new track never retains the previous lyric");
        json.addProperty("lyric", "x".repeat(501));
        bridge.accept(json.toString());
        require(bridge.lyricSnapshot() == null, "oversized lyric payload is rejected");
        bridge.accept("{\"status\":\"idle\"}");
        require(bridge.lyricSnapshot() == null, "idle media clears lyrics");
        System.out.println("QQ lyric checks passed: unknown duration, meter isolation, toggle, song change, oversized payload and idle.");
    }
    private static String packet(String title, boolean playing, double position, double duration) {
        return "{\"status\":\"ready\",\"source\":\"QQMusic.exe\",\"title\":\"" + title
                + "\",\"artist\":\"Fixture artist\",\"position\":" + position + ",\"duration\":" + duration
                + ",\"playing\":" + playing + ",\"timelineAdvances\":true,\"timelineSource\":\"fixture\"}";
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
