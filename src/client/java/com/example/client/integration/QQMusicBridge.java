package com.example.client.integration;

import com.example.client.skia.render.dynamicisland.IslandLiveData;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** One background Windows media-session reader; never launches or waits on the render thread. */
public final class QQMusicBridge implements AutoCloseable {
    private volatile boolean closed;
    private volatile Process process;
    private volatile IslandLiveData.Music music;
    private volatile long lastReceived;
    private long lastTimelineReceived;
    private volatile String status = "正在连接 QQ 音乐";
    private volatile String timelineSource = "unknown";
    private volatile IslandLiveData.Audio audio;
    private volatile IslandLiveData.Lyric lyric;
    private volatile boolean lyricsEnabled;
    private final QQMusicLyrics lyricsReader;
    private final QQMusicPlaybackClock playbackClock = new QQMusicPlaybackClock();
    private volatile IslandLiveData.Palette palette = IslandLiveData.Palette.DEFAULT;
    private final Thread worker;
    private final java.util.concurrent.BlockingQueue<String> commands = new java.util.concurrent.ArrayBlockingQueue<>(8);
    private final Thread commandWorker;

    public QQMusicBridge() {
        this(false);
    }

    public QQMusicBridge(boolean lyricsEnabled) {
        this(lyricsEnabled, true);
    }

    QQMusicBridge(boolean lyricsEnabled, boolean connect) {
        this.lyricsEnabled = lyricsEnabled;
        lyricsReader = new QQMusicLyrics(connect);
        worker = Thread.ofPlatform().daemon().name("QQMusic-media-reader").unstarted(this::run);
        commandWorker = Thread.ofPlatform().daemon().name("QQMusic-media-controls").unstarted(this::writeCommands);
        if (connect) {
            worker.start();
            commandWorker.start();
        }
    }

    public IslandLiveData.Music snapshot() {
        return System.nanoTime() - lastReceived < TimeUnit.SECONDS.toNanos(6) ? music : null;
    }

    public String status() { return status; }
    public String timelineSource() { return timelineSource; }
    public IslandLiveData.Audio audioSnapshot() { return audio; }
    public IslandLiveData.Lyric lyricSnapshot() {
        return lyricSnapshot(snapshot());
    }
    public IslandLiveData.Lyric lyricSnapshot(IslandLiveData.Music current) {
        var line = lyric;
        if (!lyricsEnabled || current == null) return null;
        if (line == null || !line.title().equals(current.title()) || !line.artist().equals(current.artist())
                || System.nanoTime() - line.sampledNanos() >= TimeUnit.SECONDS.toNanos(3)) line = null;
        // Resolve native text against the recording's timeline, including bilingual timestamp groups.
        // A spelling/translation difference must not turn a timed line back into an untimed one.
        var timed = lyricsReader.snapshot(current, line);
        return timed != null ? timed : line;
    }
    public void setLyricsEnabled(boolean enabled) {
        if (enabled == lyricsEnabled) return;
        lyricsEnabled = enabled;
        lyric = null;
        lyricsReader.update(snapshot(), enabled);
    }
    public IslandLiveData.Palette paletteSnapshot() { return palette; }

    public void command(String action) {
        IslandLiveData.Music current = snapshot();
        if (current == null || closed) return;
        boolean supported = switch (action) {
            case "toggle" -> current.canToggle();
            case "previous" -> current.canPrevious();
            case "next" -> current.canNext();
            default -> false;
        };
        if (supported) commands.offer(action);
    }

    private void writeCommands() {
        Process configuredProcess = null;
        boolean configuredLyrics = false;
        while (!closed) {
            try {
                Process current = process;
                boolean enabled = lyricsEnabled;
                if (current != null && current.isAlive() && (current != configuredProcess || enabled != configuredLyrics)) {
                    writeCommand(current, enabled ? "lyrics-on" : "lyrics-off");
                    configuredProcess = current;
                    configuredLyrics = enabled;
                }
                String action = commands.poll(250, TimeUnit.MILLISECONDS);
                if (action == null) continue;
                current = process;
                if (current == null || !current.isAlive()) continue;
                // Only this worker writes the pipe. A blocked media control cannot stall Minecraft.
                writeCommand(current, action);
            } catch (InterruptedException e) { return; }
            catch (IOException ignored) { }
        }
    }

    private static void writeCommand(Process child, String action) throws IOException {
        OutputStream out = child.getOutputStream();
        out.write((action + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private void run() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
            status = "QQ 音乐读取仅支持 Windows";
            return;
        }
        Path helper = null;
        Path audioHelper = null;
        Path timelineHelper = null;
        Path helperDirectory = null;
        try {
            byte[] script;
            // Release the resource before polling: an open development resource blocks Gradle on Windows.
            try (InputStream source = QQMusicBridge.class.getResourceAsStream("/assets/zombies-mod/integration/qqmusic-session.ps1")) {
                if (source == null) throw new IOException("Missing media-session helper");
                script = source.readAllBytes();
            }
            helperDirectory = Files.createTempDirectory("zombies-qqmusic-");
            helper = helperDirectory.resolve("qqmusic-session.ps1");
            audioHelper = helperDirectory.resolve("qqmusic-audio.cs");
            timelineHelper = helperDirectory.resolve("qqmusic-timeline.cs");
            try (InputStream audioSource = QQMusicBridge.class.getResourceAsStream("/assets/zombies-mod/integration/qqmusic-audio.cs")) {
                if (audioSource != null) Files.copy(audioSource, audioHelper);
            }
            try (InputStream timelineSource = QQMusicBridge.class.getResourceAsStream("/assets/zombies-mod/integration/qqmusic-timeline.cs")) {
                if (timelineSource != null) Files.copy(timelineSource, timelineHelper);
            }
            // Windows PowerShell 5.1 needs a BOM for UTF-8 script literals.
            try (OutputStream output = Files.newOutputStream(helper)) {
                output.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
                output.write(script);
            }
            String root = System.getenv("SystemRoot");
            String powershell = Path.of(root == null ? "C:\\Windows" : root,
                    "System32", "WindowsPowerShell", "v1.0", "powershell.exe").toString();
            while (!closed) {
                Process child = new ProcessBuilder(powershell, "-NoLogo", "-NoProfile", "-NonInteractive",
                        "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass", "-File", helper.toString())
                        .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                process = child;
                // The command worker restores the lyric preference for each new helper.
                if (closed) { child.destroyForcibly(); break; }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (!closed && (line = reader.readLine()) != null) accept(line);
                } finally {
                    child.destroyForcibly();
                    if (process == child) process = null;
                    music = null;
                    lyric = null;
                    lyricsReader.update(null, false);
                    timelineSource = "unknown";
                    playbackClock.reset();
                }
                if (!closed) Thread.sleep(5000);
            }
        } catch (InterruptedException ignored) { }
        catch (Exception e) { status = "QQ 音乐连接失败：" + e.getMessage(); }
        finally {
            music = null;
            lyric = null;
            lyricsReader.update(null, false);
            audio = null;
            timelineSource = "unknown";
            if (helper != null) try { Files.deleteIfExists(helper); } catch (IOException ignored) { }
            if (audioHelper != null) try { Files.deleteIfExists(audioHelper); } catch (IOException ignored) { }
            if (timelineHelper != null) try { Files.deleteIfExists(timelineHelper); } catch (IOException ignored) { }
            if (helperDirectory != null) try { Files.deleteIfExists(helperDirectory); } catch (IOException ignored) { }
        }
    }

    /** Also used by the standalone bridge verification without starting native game code. */
    void accept(String line) {
        try {
            JsonObject json = JsonParser.parseString(line).getAsJsonObject();
            String state = string(json, "status");
            if (state.equals("meter")) {
                audio = new IslandLiveData.Audio((float) Math.min(1, number(json, "peak")), bool(json, "available"), System.nanoTime());
                return;
            }
            lastReceived = System.nanoTime();
            if (!state.equals("ready")) {
                music = null;
                lyric = null;
                lyricsReader.update(null, false);
                timelineSource = "unknown";
                playbackClock.reset();
                status = state.equals("idle") ? "未检测到 QQ 音乐媒体会话" : "QQ 音乐媒体会话不可用";
                return;
            }
            String source = string(json, "source").toLowerCase(Locale.ROOT);
            if (!source.contains("qqmusic") && !source.contains("qq音乐")) { music = null; lyric = null; lyricsReader.update(null, false); timelineSource = "unknown"; playbackClock.reset(); return; }
            String title = string(json, "title");
            if (title.isBlank()) { music = null; lyric = null; lyricsReader.update(null, false); timelineSource = "unknown"; playbackClock.reset(); status = "QQ 音乐未提供曲名"; return; }
            String key = string(json, "coverKey");
            byte[] cover = music != null && music.coverKey().equals(key) ? music.cover() : null;
            if (json.has("cover")) {
                String encoded = string(json, "cover");
                cover = encoded.isEmpty() || encoded.length() > 700000 ? null : Base64.getDecoder().decode(encoded);
            }
            if (music == null || music.cover() != cover) palette = CoverPalette.extract(cover);
            double duration = number(json, "duration");
            String artist = string(json, "artist"), album = string(json, "album");
            double position = number(json, "position");
            String sourceName = duration > 0 ? string(json, "timelineSource") : "unknown";
            boolean playing = bool(json, "playing");
            boolean advances = bool(json, "timelineAdvances") && duration > 0;
            boolean cachedTimeline = false;
            double rate = json.has("playbackRate") ? number(json, "playbackRate") : 1;
            double precision = json.has("positionPrecision") ? number(json, "positionPrecision")
                    : sourceName.equals("qq-playback-footer") ? 1 : 0;
            double age = number(json, "positionAge");
            long revision = json.has("timelineRevision") ? json.get("timelineRevision").getAsLong() : 0;
            if (duration > 0) lastTimelineReceived = lastReceived;
            else if (music != null && music.title().equals(title) && music.artist().equals(artist)
                    && music.album().equals(album) && music.duration() > 0
                    && lastReceived - lastTimelineReceived < TimeUnit.SECONDS.toNanos(3)) {
                // A short UI-provider dropout is not a seek to zero. Do not keep it alive indefinitely.
                duration = music.duration(); position = music.positionAt(lastReceived);
                sourceName = timelineSource; precision = 0; age = 0; revision = 0;
                advances = music.timelineAdvances(); cachedTimeline = true;
                if (music.clock() != null) rate = music.clock().rate();
            }
            timelineSource = sourceName;
            var reading = cachedTimeline && playing == music.playing()
                    ? new QQMusicPlaybackClock.Reading(music.position(), music.sampledNanos(), music.clock())
                    : playbackClock.update(new QQMusicPlaybackClock.Sample(
                    source + '\u0000' + title + '\u0000' + artist + '\u0000' + album, timelineSource,
                    position, duration, playing, advances, rate, precision, age, revision), lastReceived);
            music = new IslandLiveData.Music(title, string(json, "artist"), string(json, "album"),
                    playing, reading.position(), duration, reading.sampledNanos(), advances, key, cover,
                    bool(json, "canToggle"), bool(json, "canPrevious"), bool(json, "canNext"), reading.clock());
            String lineText = string(json, "lyric").replaceAll("\\s+", " ").strip();
            lyric = lyricsEnabled && !lineText.isBlank() && lineText.length() <= 500
                    ? new IslandLiveData.Lyric(title, music.artist(), lineText, lastReceived) : null;
            lyricsReader.update(music, lyricsEnabled);
            status = music.playing() ? "QQ 音乐 · 正在播放" : "QQ 音乐 · 已暂停";
        } catch (RuntimeException ignored) {
            // Reject malformed snapshots without keeping stale playback alive.
            music = null;
            lyric = null;
            lyricsReader.update(null, false);
            timelineSource = "unknown";
            playbackClock.reset();
            status = "QQ 音乐数据暂不可用";
        }
    }

    private static String string(JsonObject j, String k) { return j.has(k) && !j.get(k).isJsonNull() ? j.get(k).getAsString() : ""; }
    private static boolean bool(JsonObject j, String k) { return j.has(k) && j.get(k).getAsBoolean(); }
    private static double number(JsonObject j, String k) {
        double value = j.has(k) ? j.get(k).getAsDouble() : 0;
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }

    @Override public void close() {
        closed = true;
        music = null;
        lyric = null;
        audio = null;
        timelineSource = "unknown";
        commands.clear();
        lyricsReader.close();
        worker.interrupt();
        commandWorker.interrupt();
        Process child = process;
        if (child != null) child.destroyForcibly();
    }
}
