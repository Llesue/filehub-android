# FileHub Android

[简体中文](README.zh-CN.md) | English

[FileHub](https://github.com/Llesue/filehub) is a self-hosted file manager: you run the server on your own NAS or Linux host and access your files from the web or a native client. This is the native Android client.

It is built for people who run one or more FileHub instances. Manage every server from one phone, keep photos and folders backed up automatically, and use the same app on a phone, tablet or car head unit.

- Kotlin + Jetpack Compose (Material 3), `minSdk 26`, `compileSdk 37.1`, `targetSdk 36`
- OkHttp + kotlinx.serialization, Room, WorkManager, Media3, Coil

## Focus

- **Multiple FileHub instances.** Add several servers, switch between them without logging out, and keep a separate device token, roots, sync settings and schedule for each one.
- **Photo sync that behaves like a backup.** Pick albums, include videos, preserve capture time and GPS EXIF, hash content, verify uploads, skip unchanged files, and resolve conflicts without overwriting either side.
- **Folder sync for any SAF folder.** Upload, download or sync both ways with a three-way baseline, per-file conflict handling and optional delete propagation.
- **One app across devices.** The same build adapts to phone portrait, phone landscape, tablet and car head units, with layouts that use the available width instead of stretching a phone UI.

## Features

### Servers and access

- Multiple FileHub profiles with password login and a per-device API token stored in Android Keystore
- Multiple addresses per server, tried strictly in the configured order, so a working LAN address is never skipped in favour of a public one
- Prefilled re-authentication when a device token expires; the profile ID is preserved, so sync jobs and caches stay attached
- Server version badges and per-profile "sign in again" state
- Long-press drag to reorder server cards on phone, landscape and tablet

### Files and previews

- Local roots, remote mounts, folder sizes, cursor paging and thumbnails
- Create, rename, delete with confirmation code and countdown, copy/move, ZIP packing and extraction, with background job progress and cancellation
- Search across all roots or the current root, with index status and one-tap indexing
- Image preview with paging, anchored pinch zoom, EXIF and original/preview switching
- Video and audio playback through Media3, with a background audio session
- PDF viewer with fit-page zoom and a page rail
- Markdown preview with tables, task lists, strikethrough and links
- Text preview and editor with syntax highlighting for JSON, XML, HTML, CSS, SQL and more

### Sync

- Photo sync: album selection, optional videos, capture-time preservation, separate photo/video Wi-Fi rules and charging-only mode
- Incremental photo scans with MediaStore watermarks; unchanged files are skipped without per-file hash requests
- SHA-256 content hashing, upload verification, same-name comparison and same-size cross-name dedupe
- GPS EXIF preservation through `ACCESS_MEDIA_LOCATION` and `MediaStore.setRequireOriginal`
- Folder sync: SAF folder selection, upload/download/two-way modes, three-way base state, conflicts kept as separate entries, and delete sync guarded by explicit confirmation
- Per-file conflict page with keep-phone / keep-server resolution followed by an automatic re-sync
- Sync run state, live step indicator, bounded log, notifications with stop/cancel/continue/retry and a privacy-safe lock-screen version
- Schedule from 15 minutes to weekly, or manual only; each server has its own schedule
- Periodic jobs rebuild themselves on app startup after an update or force-stop, and the settings screen shows the real schedule state and next run time

### Sharing

- Password, expiry and read-only controls
- QR cards and stable links
- Public share viewer inside the app
- Stop and re-enable sharing without deleting the record; expired shares can be re-enabled with a new one-day window
- Relative expiry labels and dimmed actions for stopped or expired shares

### Devices and UI

- Phone portrait: compact single-column cards and a bottom tab bar
- Phone landscape: permanent left column for servers, roots, shares, quota and versions; content on the right
- Tablet and car: wide two-pane shell with a root sidebar and an adaptive file browser
- One server-card and server-picker layout across all modes
- Adaptive file, root and share grids; centred, width-limited forms on wide screens
- Theme override (system / light / dark), language override (system / 简体中文 / English), edge-to-edge layout and an adaptive launcher icon

## Background behaviour

Sync uses WorkManager periodic work with a minimum interval of 15 minutes. Jobs run without the app UI being open, subject to Android and OEM restrictions:

- Periodic jobs are checked and rebuilt when the app starts, including after an update or force-stop.
- The sync settings screen shows whether the job is scheduled, the next run time, and a rebuild action when it is missing.
- Xiaomi/HyperOS may still defer or block background work; set battery usage to unrestricted and allow autostart and notifications.
- Doze and app standby can delay a run; schedules are best-effort, not exact alarms.
- Manual sync respects the same Wi-Fi and charging settings as the configured schedule.
- A missing device token or target location disables the affected sync until it is reconfigured.

## Build

```bash
./scripts/provision-sdk.sh          # one-time, on the build host
./scripts/build-remote.sh           # debug APK -> dist/
./scripts/build-remote.sh check     # lint + unit tests
./scripts/build-remote.sh release   # signed release APK when keystore.properties exists
```

Release and debug APKs are written to `dist/`.

## Release signing

Release builds read `keystore.properties` from the repository root (gitignored). The file points at the
keystore outside the repository and is synced to the build host by `scripts/build-remote.sh`.
Keystore and password backups must be kept outside Git.

Signing certificate SHA-256:
`F3:FB:0B:7C:94:43:79:1F:0C:A2:B2:B8:E4:2F:91:B0:09:23:7E:6F:B2:B0:95:F3:21:41:82:3B:0D:B1:7E:71`

## Licenses

- Bundled **JetBrains Mono** font: SIL Open Font License 1.1, see `licenses/JetBrainsMono-OFL.txt`.
- Syntax highlighting: [`dev.snipme:highlights`](https://github.com/SnipMeDev/Highlights), Apache License 2.0.
