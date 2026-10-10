using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Windows.Automation;

// Read only the QQ playback footer. This worker never focuses a window or changes playback.
public static class QQTimelineReader {
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern int GetWindowText(IntPtr window, StringBuilder text, int count);
    [DllImport("user32.dll")] private static extern bool IsWindow(IntPtr window);
    [DllImport("user32.dll")] private static extern bool IsIconic(IntPtr window);
    [DllImport("user32.dll")] private static extern bool IsWindowVisible(IntPtr window);
    [DllImport("user32.dll")] private static extern bool EnumWindows(EnumWindowCallback callback, IntPtr state);
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr window, out uint processId);
    private delegate bool EnumWindowCallback(IntPtr window, IntPtr state);
    private static readonly Regex Clock = new Regex(@"^(?:\d{1,2}:)?\d{1,3}:[0-5]\d$", RegexOptions.CultureInvariant);
    private static readonly object Gate = new object();
    private static string title = "", artist = "";
    private static bool playing, stopping, readLyrics;
    private static int generation;
    private static Thread worker;
    private static Sample latest;
    private static Lyric latestLyric;
    public sealed class Lyric {
        public readonly string Title, Artist, Text;
        public readonly DateTime ObservedUtc = DateTime.UtcNow;
        public Lyric(string song, string singer, string text) { Title = song; Artist = singer; Text = text; }
    }
    public static void SetLyricsEnabled(bool enabled) { lock (Gate) { readLyrics = enabled; if (!enabled) latestLyric = null; } }
    public static Lyric LyricsSnapshot(string song, string singer) {
        lock (Gate) { return latestLyric != null && latestLyric.Title == song && latestLyric.Artist == singer
            && (DateTime.UtcNow - latestLyric.ObservedUtc).TotalSeconds < 2.5 ? latestLyric : null; }
    }
    public static string Diagnostics = "waiting for track";
    public sealed class Sample {
        public readonly string Title, Artist;
        public readonly double Position, Duration;
        public readonly DateTime ObservedUtc;
        public readonly long ObservedTimestamp, Revision;
        public double AgeSeconds { get { return Math.Max(0, (Stopwatch.GetTimestamp() - ObservedTimestamp) / (double)Stopwatch.Frequency); } }
        public Sample(string title, string artist, double position, double duration, long revision, long observedTimestamp = 0) {
            Title = title; Artist = artist; Position = position; Duration = duration; ObservedUtc = DateTime.UtcNow;
            ObservedTimestamp = observedTimestamp == 0 ? Stopwatch.GetTimestamp() : observedTimestamp; Revision = revision;
        }
    }
    public static void SetTrack(string nextTitle, string nextArtist, bool isPlaying) {
        lock (Gate) {
            if (title != nextTitle || artist != nextArtist) { generation++; latest = null; latestLyric = null; }
            title = nextTitle ?? ""; artist = nextArtist ?? ""; playing = isPlaying;
        }
    }
    public static Sample Snapshot(string expectedTitle, string expectedArtist) {
        lock (Gate) {
            return latest != null && latest.Title == expectedTitle && latest.Artist == expectedArtist
                && latest.AgeSeconds < 2.5 ? latest : null;
        }
    }
    public static double ParseClock(string text) {
        if (text == null || !Clock.IsMatch(text.Trim())) return -1;
        string[] parts = text.Trim().Split(':');
        if (parts.Length == 3 && int.Parse(parts[1]) > 59) return -1;
        double seconds = 0;
        foreach (string part in parts) seconds = seconds * 60 + int.Parse(part);
        return seconds;
    }
    private static string WindowTitle(IntPtr window) {
        StringBuilder text = new StringBuilder(1024); GetWindowText(window, text, text.Capacity); return text.ToString();
    }
    private static string MetadataKey(string value) {
        return Regex.Replace((value ?? "").Normalize(NormalizationForm.FormKC), @"\s+", "");
    }
    public static bool MatchesMetadata(string windowTitle, string song, string singer) {
        string prefix = MetadataKey(song) + "-", text = MetadataKey(windowTitle);
        if (prefix.Length <= 1 || !text.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)) return false;
        // QQ's title formats multi-artist credits as "A / B" while SMTC publishes "A/B".
        // Compare the artist portion separately so the song title cannot satisfy this check.
        string credit = text.Substring(prefix.Length), expected = MetadataKey(singer);
        return expected.Length == 0 || credit.IndexOf(expected, StringComparison.OrdinalIgnoreCase) >= 0;
    }
    private static bool Matches(IntPtr window, string song, string singer) {
        return IsWindow(window) && MatchesMetadata(WindowTitle(window), song, singer);
    }
    private static IntPtr FindPlayer(string song, string singer) {
        var owners = new HashSet<uint>();
        foreach (Process process in Process.GetProcessesByName("QQMusic")) using (process) {
            owners.Add((uint)process.Id);
        }
        IntPtr found = IntPtr.Zero;
        // MainWindowHandle can point to desktop lyrics. Inspect QQ-owned top-level windows,
        // including minimized ones, and accept only the window for the selected media track.
        EnumWindows((candidate, state) => {
            uint owner; GetWindowThreadProcessId(candidate, out owner);
            if (owners.Contains(owner) && IsWindowVisible(candidate) && Matches(candidate, song, singer)) {
                found = candidate; return false;
            }
            return true;
        }, IntPtr.Zero);
        return found;
    }
    private static bool SameElement(AutomationElement a, AutomationElement b) {
        if (a == null || b == null) return false;
        int[] x = a.GetRuntimeId(), y = b.GetRuntimeId();
        if (x.Length != y.Length) return false;
        for (int i = 0; i < x.Length; i++) if (x[i] != y[i]) return false;
        return true;
    }
    private static AutomationElement NearbyParent(AutomationElement a, AutomationElement b) {
        var parents = new List<AutomationElement>();
        for (int i = 0; a != null && i < 4; i++, a = TreeWalker.ControlViewWalker.GetParent(a)) parents.Add(a);
        for (int i = 0; b != null && i < 4; i++, b = TreeWalker.ControlViewWalker.GetParent(b))
            foreach (var parent in parents) if (SameElement(parent, b)) return parent;
        return null;
    }
    public static bool IsLyricText(string value) {
        if (String.IsNullOrWhiteSpace(value) || value.Length > 500 || ParseClock(value) >= 0) return false;
        foreach (char c in value) if (Char.IsLetterOrDigit(c) && !(c >= '\uE000' && c <= '\uF8FF'))
            return !Regex.IsMatch(value.Trim(), @"^\d+(?:\.\d+)?[wWkK万亿]?\+?$");
        return false;
    }
    private static bool IsTrackLabel(string text, string song, string singer) {
        string key = MetadataKey(text);
        return key == MetadataKey(song) || key == MetadataKey(singer) || MatchesMetadata(text, song, singer);
    }
    private static AutomationElement LocateLyric(AutomationElement clockParent, AutomationElement root, string song, string singer) {
        var button = new PropertyCondition(AutomationElement.ControlTypeProperty, ControlType.Button);
        var text = new PropertyCondition(AutomationElement.ControlTypeProperty, ControlType.Text);
        var next = new AndCondition(button, new PropertyCondition(AutomationElement.NameProperty, "下一曲"));
        // The time container's nearest ancestor containing transport controls is the footer.
        // Its lyric pane has text but no buttons: exclude comment counts, clocks and playlists,
        // including when minimization makes every element's bounds empty.
        var footer = clockParent;
        for (int depth = 0; footer != null && depth < 4 && !SameElement(footer, root); depth++, footer = TreeWalker.ControlViewWalker.GetParent(footer)) {
            if (footer.FindFirst(TreeScope.Descendants, next) == null) continue;
            foreach (AutomationElement pane in footer.FindAll(TreeScope.Children, Condition.TrueCondition)) {
                if (pane.Current.ControlType == ControlType.Button || pane.FindFirst(TreeScope.Descendants, button) != null) continue;
                var labels = new List<AutomationElement>();
                if (pane.Current.ControlType == ControlType.Text) labels.Add(pane);
                foreach (AutomationElement label in pane.FindAll(TreeScope.Descendants, text)) labels.Add(label);
                bool hasClock = false;
                foreach (var label in labels) if (ParseClock(label.Current.Name) >= 0) { hasClock = true; break; }
                if (hasClock) continue;
                foreach (var label in labels) {
                    string value = label.Current.Name;
                    if (IsLyricText(value) && !IsTrackLabel(value, song, singer)) return label;
                }
            }
            break;
        }
        return null;
    }
    private static bool Locate(IntPtr window, bool lyricsEnabled, string song, string singer, out AutomationElement elapsed, out AutomationElement duration, out AutomationElement lyric) {
        elapsed = duration = lyric = null;
        Diagnostics = "locating playback footer";
        AutomationElement root = AutomationElement.FromHandle(window);
        var bounds = root.Current.BoundingRectangle;
        bool hasBounds = !IsIconic(window) && !bounds.IsEmpty && bounds.Width > 0 && bounds.Height > 0;
        // Limit traversal to the playback footer. Reading every playlist item's properties
        // can take longer than a snapshot's lifetime on a large QQ library.
        var next = root.FindFirst(TreeScope.Descendants, new AndCondition(
            new PropertyCondition(AutomationElement.ControlTypeProperty, ControlType.Button),
            new PropertyCondition(AutomationElement.NameProperty, "下一曲")));
        if (next == null) { Diagnostics = "transport controls unavailable"; return false; }
        AutomationElement footer = TreeWalker.ControlViewWalker.GetParent(next);
        for (int depth = 0; footer != null && depth < 4 && !SameElement(footer, root); depth++, footer = TreeWalker.ControlViewWalker.GetParent(footer)) {
            var cache = new CacheRequest();
            cache.Add(AutomationElement.NameProperty); cache.Add(AutomationElement.BoundingRectangleProperty);
            AutomationElementCollection elements;
            using (cache.Activate()) {
                elements = footer.FindAll(TreeScope.Descendants, new PropertyCondition(AutomationElement.ControlTypeProperty, ControlType.Text));
            }
            var clocks = new List<AutomationElement>();
            foreach (AutomationElement element in elements) {
                var rectangle = element.Cached.BoundingRectangle;
                if (ParseClock(element.Cached.Name) < 0) continue;
                if (!hasBounds || (!rectangle.IsEmpty
                    && rectangle.Top >= bounds.Top + bounds.Height * .75)) clocks.Add(element);
            }
            Diagnostics = "texts=" + elements.Count + "; clocks=" + clocks.Count + "; bounds=" + bounds;
            // Playlist durations are labelled "duration:" and are outside the footer. Both time labels
            // must share a parent and a baseline, with elapsed to the left of total duration.
            foreach (AutomationElement left in clocks) foreach (AutomationElement right in clocks) {
                if (left == right) continue;
                var a = left.Cached.BoundingRectangle; var b = right.Cached.BoundingRectangle;
                double position = ParseClock(left.Cached.Name), total = ParseClock(right.Cached.Name);
                if ((hasBounds && (a.Left >= b.Left || Math.Abs(a.Top - b.Top) > Math.Max(4, a.Height * .25)))
                    || total <= 0 || position > total || total > 86400) continue;
                var parent = NearbyParent(left, right);
                if (parent == null || (hasBounds && b.Left - a.Right > bounds.Width * .3)) continue;
                if (lyricsEnabled) try { lyric = LocateLyric(parent, root, song, singer); }
                catch { lyric = null; /* Optional lyrics must not discard the two valid clocks. */ }
                elapsed = left; duration = right; return true;
            }
        }
        return false;
    }
    public static void Start() {
        if (worker != null) return;
        worker = new Thread(() => {
            AutomationElement elapsed = null, duration = null;
            AutomationElement lyric = null;
            bool cachedLyricsEnabled = false;
            IntPtr window = IntPtr.Zero;
            int cachedGeneration = -1;
            double lastPosition = -1;
            bool lastAdvancing = false;
            long timelineRevision = 0;
            DateTime changed = DateTime.MinValue, searched = DateTime.MinValue, discovered = DateTime.MinValue;
            DateTime trackChanged = DateTime.MinValue;
            DateTime lyricSearched = DateTime.MinValue;
            while (true) {
                string song, singer; bool advancing, lyricsEnabled; int version;
                lock (Gate) { if (stopping) break; song = title; singer = artist; advancing = playing; version = generation; lyricsEnabled = readLyrics; }
                try {
                    if (song.Length > 0) {
                        if (lyricsEnabled != cachedLyricsEnabled) { lyric = null; lyricSearched = DateTime.MinValue; cachedLyricsEnabled = lyricsEnabled; }
                        if (cachedGeneration != version) {
                            // Footer labels normally survive song changes, even while minimized.
                            // Keep the references but invalidate the previous song's sample immediately.
                            lastPosition = -1; changed = DateTime.MinValue; trackChanged = DateTime.UtcNow;
                            lyric = null; lyricSearched = DateTime.MinValue;
                            discovered = DateTime.MinValue;
                            cachedGeneration = version;
                        }
                        if (!Matches(window, song, singer) && (DateTime.UtcNow - discovered).TotalSeconds >= 2) {
                            discovered = DateTime.UtcNow;
                            IntPtr candidate = FindPlayer(song, singer);
                            if (candidate != IntPtr.Zero && candidate != window) {
                                elapsed = duration = lyric = null; searched = DateTime.MinValue;
                                window = candidate;
                            }
                            Diagnostics = "matching window=" + window;
                        }
                        // QQ can update its title before the footer during a song switch.
                        if (Matches(window, song, singer) && (DateTime.UtcNow - trackChanged).TotalSeconds >= 1) {
                            if (elapsed == null && (DateTime.UtcNow - searched).TotalSeconds >= 3) {
                                searched = DateTime.UtcNow;
                                try {
                                    AutomationElement nextElapsed, nextDuration, nextLyric;
                                    if (Locate(window, lyricsEnabled, song, singer, out nextElapsed, out nextDuration, out nextLyric)) {
                                        elapsed = nextElapsed; duration = nextDuration; lyric = nextLyric;
                                        lyricSearched = DateTime.UtcNow;
                                    }
                                } catch (Exception error) { Diagnostics = error.ToString(); }
                            }
                            if (lyricsEnabled && lyric == null && elapsed != null && duration != null
                                && (DateTime.UtcNow - lyricSearched).TotalSeconds >= 3) {
                                lyricSearched = DateTime.UtcNow;
                                try { lyric = LocateLyric(NearbyParent(elapsed, duration), AutomationElement.FromHandle(window), song, singer); }
                                catch { /* Keep the cached time controls when only the lyric pane is unavailable. */ }
                            }
                            if (lyricsEnabled && lyric != null) {
                                // Optional lyrics must never invalidate a valid timeline if their
                                // accessibility element is recreated or disabled by QQ.
                                try {
                                    string text = lyric.Current.Name;
                                    if (IsLyricText(text) && !IsTrackLabel(text, song, singer) && Matches(window, song, singer)) {
                                        lock (Gate) { if (generation == version && readLyrics) latestLyric = new Lyric(song, singer, text.Trim()); }
                                    } else { lyric = null; lock (Gate) { if (generation == version) latestLyric = null; } }
                                } catch { lyric = null; lock (Gate) { if (generation == version) latestLyric = null; } }
                            }
                            if (elapsed != null && duration != null) {
                                double position = ParseClock(elapsed.Current.Name);
                                long observedTimestamp = Stopwatch.GetTimestamp();
                                double total = ParseClock(duration.Current.Name);
                                if (position >= 0 && total > 0 && position <= total && Matches(window, song, singer)) {
                                    if (position != lastPosition || advancing != lastAdvancing) {
                                        lastPosition = position; lastAdvancing = advancing;
                                        changed = DateTime.UtcNow; timelineRevision = Stopwatch.GetTimestamp();
                                    }
                                    // Some QQ builds suspend footer updates while minimized. Do not keep
                                    // renewing a frozen sample and fabricate progress across seeks.
                                    if (!advancing || (DateTime.UtcNow - changed).TotalSeconds < 2) {
                                        lock (Gate) { if (generation == version) latest = new Sample(song, singer, position, total, timelineRevision, observedTimestamp); }
                                    }
                                } else { elapsed = duration = null; }
                            }
                        }
                    }
                    else { elapsed = duration = lyric = null; window = IntPtr.Zero; }
                } catch (Exception error) {
                    Diagnostics = error.ToString(); elapsed = duration = lyric = null;
                    // Back off failed accessibility providers rather than traversing every frame.
                    searched = DateTime.UtcNow;
                }
                Thread.Sleep(500);
            }
        });
        worker.IsBackground = true; worker.Name = "QQMusic-timeline-reader";
        worker.SetApartmentState(ApartmentState.MTA); worker.Start();
    }
    public static void Stop() { lock (Gate) { stopping = true; latest = null; latestLyric = null; } if (worker != null) worker.Join(300); }
}
