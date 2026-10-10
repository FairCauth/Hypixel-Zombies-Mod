# Run with Windows PowerShell 5.1 (.NET Framework WinRT projection).
param([switch]$Once, [switch]$Lyrics)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
[Console]::InputEncoding = New-Object System.Text.UTF8Encoding($false)
function Emit($value) {
    if ($value.status -eq 'ready') {
        # Cover/metadata reads can delay delivery. Keep that elapsed time on the same sample.
        $value.positionAge += [Math]::Max(0, ([Diagnostics.Stopwatch]::GetTimestamp() - $value.sampleTimestamp) / [double][Diagnostics.Stopwatch]::Frequency)
        $value.Remove('sampleTimestamp')
    }
    [Console]::WriteLine(($value | ConvertTo-Json -Compress -Depth 4))
}
try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media, ContentType=WindowsRuntime] | Out-Null
    [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media, ContentType=WindowsRuntime] | Out-Null
    [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType=WindowsRuntime] | Out-Null
    # WinRT stream interfaces returned through reflection lose their PowerShell projection.
    # Windows' standard IStream adapter avoids depending on an installed Windows SDK.
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Runtime.InteropServices.ComTypes;
using System.Collections.Concurrent;
using System.Threading;
public static class QQCommandReader {
    private static readonly ConcurrentQueue<string> Queue = new ConcurrentQueue<string>();
    public static volatile bool Closed;
    public static void Start() {
        Thread reader = new Thread(() => {
            try { string line; while ((line = Console.ReadLine()) != null) Queue.Enqueue(line); }
            finally { Closed = true; }
        });
        reader.IsBackground = true; reader.Start();
    }
    public static string Next() { string line; return Queue.TryDequeue(out line) ? line : null; }
}
public static class QQCoverReader {
    [DllImport("shcore.dll", PreserveSig=true)]
    private static extern int CreateStreamOverRandomAccessStream(
        [MarshalAs(UnmanagedType.IUnknown)] object input, ref Guid iid,
        [MarshalAs(UnmanagedType.Interface)] out IStream stream);
    public static byte[] Read(object input) {
        Guid iid = new Guid("0000000c-0000-0000-C000-000000000046");
        IStream stream;
        Marshal.ThrowExceptionForHR(CreateStreamOverRandomAccessStream(input, ref iid, out stream));
        IntPtr read = Marshal.AllocHGlobal(4);
        try {
            System.Runtime.InteropServices.ComTypes.STATSTG stat; stream.Stat(out stat, 1);
            if (stat.cbSize <= 0 || stat.cbSize > 524288) return new byte[0];
            byte[] bytes = new byte[(int)stat.cbSize];
            stream.Read(bytes, bytes.Length, read);
            Array.Resize(ref bytes, Marshal.ReadInt32(read));
            return bytes;
        } finally { Marshal.FreeHGlobal(read); Marshal.ReleaseComObject(stream); }
    }
}
'@
    $script:asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.IsGenericMethod -and $_.GetParameters().Count -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    } | Select-Object -First 1
    function Await($operation, [Type]$type) {
        $task = $script:asTask.MakeGenericMethod($type).Invoke($null, @($operation))
        if (!$task.Wait(3000)) { throw 'Windows media session timeout' }
        return $task.Result
    }
    $manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
    $lastCoverKey = ''
    $lastCoverAttempt = [DateTime]::MinValue
    $session = $null
    $previousMediaKey = ''
    $previousPlaying = $false
    $resumeUtc = [DateTimeOffset]::MinValue
    $timelineReader = $false
    $timelineReaderError = ''
    try {
        Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes, WindowsBase
        $uiReferences = @([System.Windows.Automation.AutomationElement].Assembly.Location,
            [System.Windows.Automation.ControlType].Assembly.Location, [System.Windows.Rect].Assembly.Location)
        Add-Type -Path (Join-Path $PSScriptRoot 'qqmusic-timeline.cs') -ReferencedAssemblies $uiReferences
        [QQTimelineReader]::Start()
        [QQTimelineReader]::SetLyricsEnabled([bool]$Lyrics)
        $timelineReader = $true
    } catch { $timelineReaderError = $_.Exception.Message }
    # Console.In.ReadLineAsync is synchronous under .NET Framework's SyncTextReader.
    # A dedicated C# background reader leaves the PowerShell polling thread unblocked.
    if (!$Once) {
        [QQCommandReader]::Start()
        try {
            Add-Type -Path (Join-Path $PSScriptRoot 'qqmusic-audio.cs')
            [QQAudioMeter]::Start()
        } catch { Emit @{ status='meter'; available=$false; peak=0 } }
    }
    do {
        try {
            $matches = @($manager.GetSessions() | Where-Object { $_.SourceAppUserModelId -match '(?i)qqmusic|qq音乐' })
            $session = $matches | Where-Object { $_.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing' } | Select-Object -First 1
            if (!$session) { $session = $matches | Select-Object -First 1 }
            if (!$Once -and [QQCommandReader]::Closed) { break }
            $command = if (!$Once) { [QQCommandReader]::Next() } else { $null }
            if ($null -ne $command) {
                if ($command -eq 'quit') { break }
                if ($command -eq 'lyrics-on') { if ($timelineReader) { [QQTimelineReader]::SetLyricsEnabled($true) } }
                if ($command -eq 'lyrics-off') { if ($timelineReader) { [QQTimelineReader]::SetLyricsEnabled($false) } }
                if ($session) {
                    switch ($command) {
                        'toggle' { $null = Await ($session.TryTogglePlayPauseAsync()) ([bool]) }
                        'previous' { $null = Await ($session.TrySkipPreviousAsync()) ([bool]) }
                        'next' { $null = Await ($session.TrySkipNextAsync()) ([bool]) }
                    }
                }
            }
            if (!$session) {
                if ($timelineReader) { [QQTimelineReader]::SetTrack('', '', $false) }
                Emit @{ status='idle' }; $lastCoverKey = ''; $previousMediaKey = ''; $previousPlaying = $false
            } else {
                $media = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
                $info = $session.GetPlaybackInfo()
                $timeline = $session.GetTimelineProperties()
                $key = $session.SourceAppUserModelId + '|' + $media.Title + '|' + $media.Artist + '|' + $media.AlbumTitle
                $playing = $info.PlaybackStatus.ToString() -eq 'Playing'
                $position = [Math]::Max(0, ($timeline.Position - $timeline.StartTime).TotalSeconds)
                $duration = [Math]::Max(0, ($timeline.EndTime - $timeline.StartTime).TotalSeconds)
                $sampleTimestamp = [Diagnostics.Stopwatch]::GetTimestamp()
                $positionAge = 0.0
                $positionPrecision = 0.0
                $playbackRate = 1.0
                if ($null -ne $info.PlaybackRate -and $info.PlaybackRate -gt 0) { $playbackRate = [double]$info.PlaybackRate }
                if ($key -ne $previousMediaKey) { $resumeUtc = [DateTimeOffset]::MinValue }
                elseif ($playing -and !$previousPlaying) { $resumeUtc = [DateTimeOffset]::UtcNow }
                $previousMediaKey = $key; $previousPlaying = $playing
                $updated = [DateTimeOffset]$timeline.LastUpdatedTime
                $timelineRevision = $updated.UtcDateTime.Ticks
                # SMTC Position belongs to LastUpdatedTime, not the instant of this poll.
                # Never count the paused interval when a provider resumes without updating its anchor.
                if ($playing -and $updated.Year -gt 2000) {
                    $anchorUtc = if ($updated -lt $resumeUtc) { $resumeUtc } else { $updated }
                    $positionAge = [Math]::Max(0, ([DateTimeOffset]::UtcNow - $anchorUtc).TotalSeconds)
                }
                $timelineSource = if ($duration -gt 0) { 'media-session' } else { 'unknown' }
                if ($timelineReader) {
                    [QQTimelineReader]::SetTrack($media.Title, $media.Artist, $playing)
                    # QQ's footer is authoritative when it is fresh and matches this recording.
                    # System time remains available when the UI provider suspends updates.
                    $fallback = [QQTimelineReader]::Snapshot($media.Title, $media.Artist)
                    if ($Once -and !$fallback) {
                        for ($attempt = 0; $attempt -lt 50 -and !$fallback; $attempt++) {
                            Start-Sleep -Milliseconds 100
                            $fallback = [QQTimelineReader]::Snapshot($media.Title, $media.Artist)
                        }
                    }
                    if ($fallback) {
                        $duration = $fallback.Duration
                        $position = $fallback.Position
                        $positionAge = $fallback.AgeSeconds
                        $sampleTimestamp = [Diagnostics.Stopwatch]::GetTimestamp()
                        $positionPrecision = 1.0
                        $timelineRevision = $fallback.Revision
                        $timelineSource = 'qq-playback-footer'
                    }
                }
                $result = @{
                    status='ready'; source=$session.SourceAppUserModelId; title=$media.Title; artist=$media.Artist; album=$media.AlbumTitle
                    playing=$playing; position=$position; duration=$duration
                    timelineAdvances=($duration -gt 0); timelineSource=$timelineSource
                    positionAge=$positionAge; positionPrecision=$positionPrecision; timelineRevision=$timelineRevision
                    playbackRate=$playbackRate; sampleTimestamp=$sampleTimestamp
                    canToggle=[bool]$info.Controls.IsPlayPauseToggleEnabled
                    canPrevious=[bool]$info.Controls.IsPreviousEnabled; canNext=[bool]$info.Controls.IsNextEnabled
                    coverKey=$key
                    lyric=''
                }
                if ($timelineReader) {
                    $lyric = [QQTimelineReader]::LyricsSnapshot($media.Title, $media.Artist)
                    if ($lyric) { $result.lyric = $lyric.Text }
                }
                if ($Once) { $result.timelineDiagnostic = if ($timelineReader) { [QQTimelineReader]::Diagnostics } else { $timelineReaderError } }
                if ($key -ne $lastCoverKey -and ([DateTime]::UtcNow - $lastCoverAttempt).TotalSeconds -ge 2) {
                    $lastCoverAttempt = [DateTime]::UtcNow
                    $result.cover = ''
                    if ($media.Thumbnail) {
                        $random = $null
                        try {
                            $random = Await ($media.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
                            $result.cover = [Convert]::ToBase64String([QQCoverReader]::Read($random))
                        } catch { $result.coverError = $_.Exception.Message } finally {
                            if ($random -and [System.Runtime.InteropServices.Marshal]::IsComObject($random)) {
                                $null = [System.Runtime.InteropServices.Marshal]::ReleaseComObject($random)
                            }
                        }
                    }
                    # QQ can publish a new title before its thumbnail becomes available.
                    if ($result.cover) { $lastCoverKey = $key }
                }
                Emit $result
            }
        } catch { Emit @{ status='error'; message=$_.Exception.Message } }
        if (!$Once) { Start-Sleep -Milliseconds 500 }
    } while (!$Once)
} catch { Emit @{ status='unsupported'; message=$_.Exception.Message } }
finally {
    if ('QQAudioMeter' -as [type]) { [QQAudioMeter]::Stop() }
    if ('QQTimelineReader' -as [type]) { [QQTimelineReader]::Stop() }
}
