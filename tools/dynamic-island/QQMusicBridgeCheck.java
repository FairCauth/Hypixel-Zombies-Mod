package com.example.client.integration;

import com.example.client.skia.render.dynamicisland.IslandLiveData;
import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;

/** Verifies the real persistent reader without changing playback or starting Minecraft. */
public final class QQMusicBridgeCheck {
    public static void main(String[] args) throws Exception {
        QQMusicBridge bridge = new QQMusicBridge();
        try {
            IslandLiveData.Music found = null;
            long deadline = System.nanoTime() + 12_000_000_000L;
            while (System.nanoTime() < deadline && (found = bridge.snapshot()) == null) Thread.sleep(100);
            if (found == null) throw new AssertionError("QQ snapshot unavailable: " + bridge.status());
            System.out.println("Real QQ session: " + found.title() + " / " + found.artist() + "; playing=" + found.playing()
                    + "; duration=" + found.duration() + "; cover=" + (found.cover() == null ? 0 : found.cover().length));
            if (args.length > 0) {
                Path dir = Path.of(args[0]); Files.createDirectories(dir);
                Files.writeString(dir.resolve("qq-session.json"), new Gson().toJson(found));
            }
            byte[] cover = found.cover();
            Thread.sleep(1200);
            IslandLiveData.Music again = bridge.snapshot();
            require(again != null && again.sampledNanos() > found.sampledNanos(), "persistent helper updates without stdin commands");
            require(again.cover() == cover, "unchanged cover retained between snapshots");
            long audioDeadline = System.nanoTime() + 3_000_000_000L;
            while (bridge.audioSnapshot() == null && System.nanoTime() < audioDeadline) Thread.sleep(50);
            var meter = bridge.audioSnapshot();
            require(meter != null, "Core Audio meter publishes snapshots");
            System.out.println("QQ-only audio meter: available=" + meter.available() + "; peak=" + meter.peak()
                    + "; palette=" + Integer.toHexString(bridge.paletteSnapshot().start()) + "/" + Integer.toHexString(bridge.paletteSnapshot().end()));
            Thread.sleep(100);
            require(bridge.audioSnapshot().sampledNanos() > meter.sampledNanos(), "audio meter refreshes faster than metadata");
            bridge.accept("{\"status\":\"meter\",\"available\":true,\"peak\":0.65}");
            require(bridge.snapshot() != null && Math.abs(bridge.audioSnapshot().peak() - .65) < .001,
                    "meter packets preserve track metadata");
            bridge.accept("{\"status\":\"ready\",\"source\":\"OtherPlayer.exe\",\"title\":\"Other\"}");
            require(bridge.snapshot() == null, "other apps never occupy QQ music island");
            bridge.accept("{\"status\":\"idle\"}");
            require(bridge.snapshot() == null, "no session clears activity");
            bridge.accept("bad json");
            require(bridge.snapshot() == null, "invalid snapshot clears stale track");
        } finally { bridge.close(); }
        Thread.sleep(600);
        require(bridge.snapshot() == null && bridge.audioSnapshot() == null, "close clears media and audio state");
        require(Thread.getAllStackTraces().keySet().stream().noneMatch(t -> t.isAlive() && t.getName().startsWith("QQMusic-media-")), "both bridge threads stop");
        System.out.println("QQ reader checks passed: actual metadata/cover, repeated polling, cover palette, QQ-only audio meter, cache, app filtering, invalid data, shutdown.");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
