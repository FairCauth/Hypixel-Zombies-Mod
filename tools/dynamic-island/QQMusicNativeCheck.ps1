# Pure regression fixtures for the production native matcher; does not query or operate QQ.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes, WindowsBase
$taskReferences = @([System.Windows.Automation.AutomationElement].Assembly.Location,
    [System.Windows.Automation.ControlType].Assembly.Location, [System.Windows.Rect].Assembly.Location)
$taskNative = Join-Path $PSScriptRoot '../../src/client/resources/assets/zombies-mod/integration/qqmusic-timeline.cs'
Add-Type -Path $taskNative -ReferencedAssemblies $taskReferences
function Require([bool]$condition, [string]$message) { if (!$condition) { throw $message } }
Require ([QQTimelineReader]::MatchesMetadata('明日坐标 - 林俊杰 / 王者荣耀   ', '明日坐标', '林俊杰/王者荣耀')) 'multi-artist spacing'
Require ([QQTimelineReader]::MatchesMetadata('Life Is Beautiful - The Afters', 'Life Is Beautiful', 'The Afters')) 'single artist'
Require ([QQTimelineReader]::MatchesMetadata('Ｓｏｎｇ - Ａｒｔｉｓｔ', 'Song', 'Artist')) 'full-width metadata'
Require (![QQTimelineReader]::MatchesMetadata('明日坐标 (Live) - 林俊杰 / 王者荣耀', '明日坐标', '林俊杰/王者荣耀')) 'different song version rejected'
Require (![QQTimelineReader]::MatchesMetadata('Artist - Other singer', 'Artist', 'Artist')) 'song name cannot satisfy artist check'
Require (![QQTimelineReader]::MatchesMetadata('QQ音乐', '', '')) 'empty title rejected'
Require (![QQTimelineReader]::IsLyricText('10w+')) 'comment count rejected'
Require (![QQTimelineReader]::IsLyricText('02:59')) 'clock rejected as lyric'
Require ([QQTimelineReader]::IsLyricText('你')) 'single character lyric accepted'
Require ([QQTimelineReader]::IsLyricText('A real lyric')) 'lyric accepted'
Require ([QQTimelineReader]::ParseClock('01:02:03') -eq 3723) 'hour clock'
Require ([QQTimelineReader]::ParseClock('01:60:00') -lt 0) 'invalid minute rejected'
$taskObserved = [Diagnostics.Stopwatch]::GetTimestamp() - [long]([Diagnostics.Stopwatch]::Frequency * 0.25)
$taskSample = New-Object 'QQTimelineReader+Sample' ('Fixture', 'Artist', 42.0, 232.0, 123L, $taskObserved)
Require ($taskSample.AgeSeconds -ge 0.25 -and $taskSample.AgeSeconds -lt 1) 'sample age includes time spent reading the duration control'
Require ($taskSample.Revision -eq 123 -and $taskSample.Position -eq 42) 'source revision and raw whole-second position remain unchanged'
Write-Output 'Native matcher/lyric/clock/sample-age regression checks passed.'
