param(
  [string]$CmdFile = "",
  [int]$ParentPid = 0,
  [string]$Source = "auto"
)

$ErrorActionPreference = 'SilentlyContinue'
$ProgressPreference = 'SilentlyContinue'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)

Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
$null = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
$null = [Windows.Storage.Streams.IInputStream, Windows.Storage.Streams, ContentType = WindowsRuntime]
$null = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime]

$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
  $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]

function Await($op, [Type]$type) {
  try {
    $task = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
    if ($task.Wait(5000)) { return $task.Result }
  } catch {}
  return $null
}

function Emit($obj) {
  [Console]::Out.WriteLine(($obj | ConvertTo-Json -Compress))
  [Console]::Out.Flush()
}

$script:thumbErr = ''

function Get-ThumbB64($props) {
  $script:thumbErr = ''
  try {
    $ref = $props.Thumbnail
    if ($null -eq $ref) { $script:thumbErr = 'no thumbnail'; return $null }
    $stream = Await ($ref.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
    if ($null -eq $stream) { $script:thumbErr = 'could not open'; return $null }
    $bytes = $null

    # Method 1: call the .NET stream wrapper through reflection (skips PowerShell's type converter)
    try {
      $asRead = [System.IO.WindowsRuntimeStreamExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsStreamForRead' -and $_.GetParameters().Count -eq 1
      } | Select-Object -First 1
      $net = $asRead.Invoke($null, @($stream))
      $ms = New-Object System.IO.MemoryStream
      $net.CopyTo($ms)
      $bytes = $ms.ToArray()
      $net.Dispose()
    } catch { $script:thumbErr = "m1: $($_.Exception.Message)"; $bytes = $null }

    # Method 2: DataReader created through Activator
    if ($null -eq $bytes -or $bytes.Length -eq 0) {
      try {
        $size = [uint32]$stream.Size
        $reader = [Activator]::CreateInstance([Windows.Storage.Streams.DataReader], @($stream))
        $null = Await ($reader.LoadAsync($size)) ([uint32])
        $buf = New-Object byte[] $size
        $reader.ReadBytes($buf)
        $bytes = $buf
      } catch { $script:thumbErr += " m2: $($_.Exception.Message)"; $bytes = $null }
    }

    try { $stream.Dispose() } catch {}
    if ($null -ne $bytes -and $bytes.Length -gt 0) { $script:thumbErr = ''; return [Convert]::ToBase64String($bytes) }
    if ($script:thumbErr -eq '') { $script:thumbErr = 'empty image' }
  } catch { $script:thumbErr = "$($_.Exception.Message)" }
  return $null
}

$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
if ($null -eq $mgr) {
  Emit @{ ok = $false; error = 'SMTC unavailable' }
  exit
}

$script:spotifyOnly = ($Source -eq 'spotify')

function Select-Session {
  $sessions = @($mgr.GetSessions())
  foreach ($s in $sessions) {
    if ($s.SourceAppUserModelId -match 'spotify') { return $s }
  }
  if ($script:spotifyOnly) { return $null }
  foreach ($s in $sessions) {
    $st = $s.GetPlaybackInfo().PlaybackStatus.ToString()
    if ($st -eq 'Playing') { return $s }
  }
  $cur = $mgr.GetCurrentSession()
  if ($cur) { return $cur }
  if ($sessions.Count -gt 0) { return $sessions[0] }
  return $null
}

$lastKey = ''
$thumbSent = $false
$thumbTries = 0

while ($true) {
  if ($ParentPid -gt 0 -and -not (Get-Process -Id $ParentPid -ErrorAction SilentlyContinue)) { break }

  $commands = @()
  if ($CmdFile -and (Test-Path -LiteralPath $CmdFile)) {
    try {
      $raw = Get-Content -LiteralPath $CmdFile -Raw
      Remove-Item -LiteralPath $CmdFile -Force
      if ($raw) { $commands = @($raw -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ }) }
    } catch {}
  }

  foreach ($c in $commands) {
    if ($c -eq 'source:spotify') { $script:spotifyOnly = $true }
    elseif ($c -eq 'source:auto') { $script:spotifyOnly = $false }
  }

  $s = Select-Session

  if ($null -ne $s) {
    foreach ($c in $commands) {
      switch ($c) {
        'next'   { $null = Await ($s.TrySkipNextAsync()) ([bool]) }
        'prev'   { $null = Await ($s.TrySkipPreviousAsync()) ([bool]) }
        'toggle' { $null = Await ($s.TryTogglePlayPauseAsync()) ([bool]) }
        default {
          if ($c -match '^seek:(\d+)$') {
            $start = 0
            try { $start = $s.GetTimelineProperties().StartTime.Ticks } catch {}
            $null = Await ($s.TryChangePlaybackPositionAsync([long]($start + [long]$Matches[1] * 10000))) ([bool])
          }
        }
      }
    }
    if ($commands.Count -gt 0) { Start-Sleep -Milliseconds 150 }
  }

  if ($null -eq $s) {
    Emit @{ ok = $false }
    Start-Sleep -Milliseconds 700
    continue
  }

  $props = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
  if ($null -eq $props) {
    Emit @{ ok = $false }
    Start-Sleep -Milliseconds 700
    continue
  }

  $status = 'Stopped'
  $pos = 0.0
  $dur = 0.0
  try {
    $status = $s.GetPlaybackInfo().PlaybackStatus.ToString()
    $tl = $s.GetTimelineProperties()
    $dur = ($tl.EndTime - $tl.StartTime).TotalSeconds
    $pos = ($tl.Position - $tl.StartTime).TotalSeconds
    if ($status -eq 'Playing') {
      $delta = ([DateTimeOffset]::UtcNow - $tl.LastUpdatedTime).TotalSeconds
      if ($delta -gt 0 -and $delta -lt 36000) { $pos += $delta }
    }
    if ($dur -gt 0 -and $pos -gt $dur) { $pos = $dur }
    if ($pos -lt 0) { $pos = 0.0 }
  } catch {}

  $key = "$($props.Title)|$($props.Artist)|$($props.AlbumTitle)"
  if ($key -ne $lastKey) {
    $lastKey = $key
    $thumbSent = $false
    $thumbTries = 0
  }

  $payload = @{
    ok     = $true
    title  = [string]$props.Title
    artist = [string]$props.Artist
    album  = [string]$props.AlbumTitle
    status = $status
    pos    = [math]::Round($pos, 2)
    dur    = [math]::Round($dur, 2)
    app    = [string]$s.SourceAppUserModelId
  }

  if (-not $thumbSent -and $thumbTries -lt 60) {
    $thumbTries++
    $b64 = Get-ThumbB64 $props
    if ($b64) {
      $payload.thumb = $b64
      $thumbSent = $true
    } else {
      $payload.terr = [string]$script:thumbErr
    }
    $payload.tries = $thumbTries
  }

  Emit $payload
  Start-Sleep -Milliseconds 500
}
