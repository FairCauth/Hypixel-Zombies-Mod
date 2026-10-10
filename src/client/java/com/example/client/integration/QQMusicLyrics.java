package com.example.client.integration;

import com.example.client.skia.render.dynamicisland.IslandLiveData;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;

/** Optional synchronized lyrics for native text and fallback. Never runs network I/O on the HUD thread. */
final class QQMusicLyrics implements AutoCloseable {
    private static final Pattern TIMESTAMP = Pattern.compile("\\[(\\d{1,3}):([0-5]\\d)(?:[.:](\\d{1,3}))?]");
    private static final Pattern OFFSET = Pattern.compile("(?im)^\\[offset:([+-]?\\d{1,7})]\\s*$");
    private static final Pattern WORD_TIMESTAMP = Pattern.compile("<(\\d{1,3}):([0-5]\\d)(?:[.:](\\d{1,3}))?>");
    private static final Pattern NON_LYRIC_TEXT = Pattern.compile("[^\\p{L}\\p{M}\\p{N}]+");
    // QQ's text control is sampled separately at 2 Hz; only adjacent lines get this grace period.
    private static final double NATIVE_BOUNDARY_GRACE = 1;
    record Line(double seconds, String text, List<IslandLiveData.WordCue> wordCues) {
        Line(double seconds, String text) { this(seconds, text, List.of()); }
    }
    record Request(String title, String artist, long duration) {
        static Request of(IslandLiveData.Music music) {
            return music == null || music.duration() <= 0 || music.artist().isBlank() ? null
                    : new Request(music.title(), music.artist(), Math.round(music.duration()));
        }
    }
    record Result(Request track, List<Line> lines) {}
    private volatile Request requested;
    private volatile Result result;
    private volatile boolean closed;
    private final Thread worker;

    QQMusicLyrics() {
        this(true);
    }

    QQMusicLyrics(boolean background) {
        worker = Thread.ofPlatform().daemon().name("QQMusic-synced-lyrics").unstarted(this::run);
        if (background) worker.start();
    }

    void update(IslandLiveData.Music music, boolean enabled) {
        Request next = enabled && !closed ? Request.of(music) : null;
        requested = next;
        Result current = result;
        if (current != null && !current.track().equals(next)) result = null;
    }

    IslandLiveData.Lyric snapshot(IslandLiveData.Music music) {
        return snapshot(music, null);
    }

    IslandLiveData.Lyric snapshot(IslandLiveData.Music music, IslandLiveData.Lyric nativeLine) {
        Result current = result;
        Request track = Request.of(music);
        if (closed || track == null || current == null || !track.equals(requested) || !track.equals(current.track())) return null;
        return timedLineAt(current.lines(), music, nativeLine);
    }

    private void run() {
        var cache = new LinkedHashMap<Request, Result>();
        var retryTimes = new LinkedHashMap<Request, Long>();
        HttpClient client = null;
        try {
            while (!closed) {
                Request next = requested;
                if (next != null) {
                    Result cached = cache.get(next);
                    Long retryAt = retryTimes.get(next);
                    if (cached != null) {
                        if (next.equals(requested)) result = cached;
                    } else if (retryAt == null || System.nanoTime() - retryAt >= 0) {
                        retryTimes.put(next, System.nanoTime() + Duration.ofMinutes(2).toNanos());
                        if (retryTimes.size() > 16) retryTimes.remove(retryTimes.keySet().iterator().next());
                        try {
                            // Disabled/closed lyrics do not create network resources, even during startup.
                            if (client == null) client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                                    .followRedirects(HttpClient.Redirect.NEVER).build();
                            Result found = fetch(client, next);
                            if (found != null) {
                                cache.put(next, found);
                                if (cache.size() > 16) cache.remove(cache.keySet().iterator().next());
                                if (next.equals(requested) && !closed) result = found;
                            }
                        } catch (InterruptedException e) { throw e; }
                        catch (Exception ignored) { /* Missing or offline lyrics keep the song-title fallback. */ }
                    }
                }
                Thread.sleep(250);
            }
        } catch (InterruptedException ignored) { }
        finally { if (client != null) client.close(); result = null; }
    }

    private static Result fetch(HttpClient client, Request track) throws Exception {
        String query = "track_name=" + encode(track.title()) + "&artist_name=" + encode(track.artist()) + "&duration=" + track.duration();
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://lrclib.net/api/get?" + query))
                .timeout(Duration.ofSeconds(6)).header("User-Agent", "ZombiesMod/1.0 (synced lyrics)").GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200 || response.body().length() > 131072) return null;
        return decode(track, response.body());
    }

    static Result decode(Request track, String json) {
        if (json == null || json.length() > 131072) return null;
        try {
            var data = JsonParser.parseString(json).getAsJsonObject();
            if (!sameMetadata(data.get("trackName").getAsString(), track.title())
                    || !sameMetadata(data.get("artistName").getAsString(), track.artist())
                    || !Double.isFinite(data.get("duration").getAsDouble())
                    || Math.abs(data.get("duration").getAsDouble() - track.duration()) > 2
                    || !data.has("syncedLyrics") || data.get("syncedLyrics").isJsonNull()) return null;
            List<Line> lines = parse(data.get("syncedLyrics").getAsString());
            return lines.isEmpty() ? null : new Result(track, lines);
        } catch (RuntimeException ignored) { return null; }
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static boolean sameMetadata(String a, String b) {
        return Normalizer.normalize(a, Normalizer.Form.NFKC).replaceAll("\\s+", "")
                .equalsIgnoreCase(Normalizer.normalize(b, Normalizer.Form.NFKC).replaceAll("\\s+", ""));
    }

    static List<Line> parse(String lrc) {
        if (lrc == null || lrc.length() > 131072) return List.of();
        var offsetMatch = OFFSET.matcher(lrc);
        double offset = offsetMatch.find() ? Integer.parseInt(offsetMatch.group(1)) / 1000.0 : 0;
        var lines = new ArrayList<Line>();
        for (String row : lrc.split("\\R")) {
            row = row.strip().replace("\uFEFF", "");
            var times = new ArrayList<Double>();
            var matcher = TIMESTAMP.matcher(row);
            int end = 0;
            while (matcher.find() && matcher.start() == end) {
                String fraction = matcher.group(3);
                times.add(Integer.parseInt(matcher.group(1)) * 60.0 + Integer.parseInt(matcher.group(2))
                        + (fraction == null ? 0 : Integer.parseInt(fraction) / Math.pow(10, fraction.length())) - offset);
                end = matcher.end();
            }
            if (times.isEmpty()) continue;
            String body = row.substring(end);
            var words = WORD_TIMESTAMP.matcher(body);
            var clean = new StringBuilder();
            var cues = new ArrayList<IslandLiveData.WordCue>();
            int cursor = 0;
            while (words.find()) {
                clean.append(body, cursor, words.start());
                String fraction = words.group(3);
                double time = Integer.parseInt(words.group(1)) * 60.0 + Integer.parseInt(words.group(2))
                        + (fraction == null ? 0 : Integer.parseInt(fraction) / Math.pow(10, fraction.length())) - offset;
                cues.add(new IslandLiveData.WordCue(time, clean.codePointCount(0, clean.length())));
                cursor = words.end();
            }
            clean.append(body, cursor, body.length());
            String untrimmed = clean.toString(), text = untrimmed.strip();
            if (text.length() > 500 || cues.size() > 512) continue;
            int removed = untrimmed.length() - untrimmed.stripLeading().length();
            int leading = untrimmed.codePointCount(0, removed), length = text.codePointCount(0, text.length());
            for (double time : times) if (time >= 0 && time <= 86400) {
                // Repeated line timestamps repeat the word schedule at the new line's origin.
                double shift = time - times.getFirst();
                var shifted = new ArrayList<IslandLiveData.WordCue>();
                for (var cue : cues) shifted.add(new IslandLiveData.WordCue(cue.seconds() + shift,
                        Math.clamp(cue.textOffset() - leading, 0, length)));
                lines.add(new Line(time, text, List.copyOf(shifted)));
                if (lines.size() >= 4096) break;
            }
            if (lines.size() >= 4096) break;
        }
        lines.sort(Comparator.comparingDouble(Line::seconds));
        return List.copyOf(lines);
    }

    static String lineAt(List<Line> lines, double position) {
        int index = indexAt(lines, position);
        return index < 0 ? "" : lines.get(index).text();
    }

    static IslandLiveData.Lyric timedLineAt(List<Line> lines, IslandLiveData.Music music) {
        return timedLineAt(lines, music, null);
    }

    static IslandLiveData.Lyric timedLineAt(List<Line> lines, IslandLiveData.Music music,
                                          IslandLiveData.Lyric nativeLine) {
        double position = music.positionNow();
        int index = indexAt(lines, position);
        if (index < 0) return null;
        int first = groupStart(lines, index), after = groupEnd(lines, first);
        if (nativeLine != null && nativeLine.title().equals(music.title()) && nativeLine.artist().equals(music.artist())
                && System.nanoTime() - nativeLine.sampledNanos() < 3_000_000_000L) {
            String key = lyricKey(nativeLine.text());
            int matched = matchesGroup(lines, first, after, key) ? first : -1;
            // Native UI text can arrive just before/after its boundary. Preserve its actual interval,
            // never stretch a line or create a second clock to compensate for polling latency.
            if (matched < 0 && first > 0 && position - lines.get(first).seconds() <= NATIVE_BOUNDARY_GRACE) {
                int previous = groupStart(lines, first - 1);
                if (matchesGroup(lines, previous, first, key)) matched = previous;
            }
            if (matched < 0 && after < lines.size() && lines.get(after).seconds() - position <= NATIVE_BOUNDARY_GRACE) {
                int nextEnd = groupEnd(lines, after);
                if (matchesGroup(lines, after, nextEnd, key)) matched = after;
            }
            if (matched >= 0) {
                int matchedEnd = groupEnd(lines, matched);
                for (int i = matched; i < matchedEnd; i++) {
                    Line line = lines.get(i);
                    if (!line.wordCues().isEmpty() && key.equals(lyricKey(line.text())))
                        return timedLyric(line, music, nativeLine.sampledNanos(), intervalEnd(lines, matched, music.duration()));
                }
                // Combined original/translation labels cannot use a single line's character offsets.
                return new IslandLiveData.Lyric(music.title(), music.artist(), nativeLine.text(),
                        nativeLine.sampledNanos(), lines.get(matched).seconds(), intervalEnd(lines, matched, music.duration()));
            }
        }
        // The recording has been validated by title, artist and duration. Use its timed text when
        // QQ abbreviates/translates differently, instead of dropping the sweep for selected lines.
        for (int i = first; i < after; i++) {
            Line line = lines.get(i);
            if (!line.text().isBlank()) return timedLyric(line, music, System.nanoTime(), intervalEnd(lines, first, music.duration()));
        }
        return null;
    }

    private static IslandLiveData.Lyric timedLyric(Line line, IslandLiveData.Music music, long sampled, double end) {
        return new IslandLiveData.Lyric(music.title(), music.artist(), line.text(), sampled, line.seconds(), end, line.wordCues());
    }

    private static int groupStart(List<Line> lines, int index) {
        while (index > 0 && lines.get(index - 1).seconds() == lines.get(index).seconds()) index--;
        return index;
    }

    private static int groupEnd(List<Line> lines, int first) {
        int after = first + 1;
        while (after < lines.size() && lines.get(after).seconds() == lines.get(first).seconds()) after++;
        return after;
    }

    private static double intervalEnd(List<Line> lines, int first, double duration) {
        int after = groupEnd(lines, first);
        return after < lines.size() ? lines.get(after).seconds() : duration;
    }

    private static boolean matchesGroup(List<Line> lines, int first, int after, String nativeKey) {
        if (nativeKey.isEmpty()) return false;
        var combined = new StringBuilder();
        String previous = "";
        for (int i = first; i < after; i++) {
            String key = lyricKey(lines.get(i).text());
            if (nativeKey.equals(key)) return true;
            if (!key.equals(previous)) combined.append(key);
            previous = key;
        }
        // Some QQ layouts expose original + translation as a single accessibility label.
        return nativeKey.contentEquals(combined);
    }

    private static String lyricKey(String text) {
        return NON_LYRIC_TEXT.matcher(Normalizer.normalize(text, Normalizer.Form.NFKC)).replaceAll("")
                .toLowerCase(java.util.Locale.ROOT);
    }

    private static int indexAt(List<Line> lines, double position) {
        if (!Double.isFinite(position)) return -1;
        int low = 0, high = lines.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (lines.get(middle).seconds() <= position) low = middle + 1;
            else high = middle;
        }
        return low - 1;
    }

    @Override public void close() {
        closed = true; requested = null; result = null; worker.interrupt();
    }
}
