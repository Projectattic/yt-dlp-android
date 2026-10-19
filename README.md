# yt-dlp Direct for Android

> Personal-use project. Downloading from YouTube may go against YouTube's Terms of Service, so only download what you have the right to keep. Not affiliated with YouTube, yt-dlp or VLC.

The Android version of [yt-dlp-direct-ios](https://github.com/Projectattic/yt-dlp-direct-ios): **yt-dlp runs inside the app**. It shows live progress and keeps going in the background, and finished files land where VLC finds them.

On Android, the [youtubedl-android](https://github.com/yausername/youtubedl-android) library bundles Python, yt-dlp, **ffmpeg** and a JavaScript runtime. So unlike the iPhone version, nothing is held back: 4K, any container, subtitles, SponsorBlock, and no "JavaScript runtime" warning.

## Install

1. On your phone, open the [latest release](https://github.com/Projectattic/yt-dlp-android/releases/latest) and download **`yt-dlp-direct-arm64-v8a.apk`**. That's the right file for practically every phone from the last 8 years.
2. Open it and allow installing from your browser when Android asks.
3. On first launch the app unpacks Python and updates yt-dlp. This takes a few seconds.

Every push to `main` builds new APKs via GitHub Actions (`.github/workflows/build.yml`). Releases are signed with the same key each time, so a new APK installs as an update.

## Features

- **Share to download:** in the YouTube app, tap Share › *yt-dlp Direct* and the download starts. You can also paste links; copied YouTube links are filled in automatically.
- **Live progress** in the app and in a notification. Downloads continue in the background, and you can queue several links, playlists or channels.
- **Video:** best quality up to 4K, saved as MP4 or MKV. **Audio:** M4A, MP3, Opus or FLAC.
- **Tags:** the file is named after the video. The title tag is the video name, the artist and album artist are the channel, and the thumbnail is embedded as cover art, cropped square for audio. Chapters are embedded too.
- **Saved to** `Music/yt-dlp` (audio) and `Movies/yt-dlp` (video). VLC and other players pick them up automatically.
- **Library** with thumbnails, search and filters. Tap to play in VLC. Long-press to share, open on YouTube or delete.
- **Options:** subtitles, SponsorBlock, speed limit, extra yt-dlp arguments, and daily automatic yt-dlp updates.

## Build it yourself

Open the folder in Android Studio and press Run, or from a terminal:

```
./gradlew assembleRelease
```

APKs land in `app/build/outputs/apk/release/`, one per CPU type, because Python and ffmpeg are large.

## Code

```
app/src/main/java/dk/benjy/ytdlp/
  App.kt              start-up; playing/sharing in VLC or another app
  Engine.kt           starts Python/yt-dlp/ffmpeg, keeps yt-dlp updated
  CommandBuilder.kt   settings → yt-dlp arguments
  DownloadManager.kt  queue, progress parsing, playlist expansion
  DownloadService.kt  foreground service + notifications
  Library.kt          saving to Music/Movies via MediaStore, listing, deleting
  Settings.kt         settings model and storage
  ui/                 Download, Library and Settings screens (Jetpack Compose)
```
