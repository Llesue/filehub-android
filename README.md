# FileHub Android

[简体中文](README.zh-CN.md) | English

Native Android client for [FileHub](https://github.com/Llesue/filehub).

Status: **v0.1.1 in development**. Latest published release: [v0.1.0](https://github.com/Llesue/filehub-android/releases/tag/v0.1.0).

- Kotlin + Jetpack Compose (Material 3), `minSdk 26`, `compileSdk 37.1`, `targetSdk 36`
- OkHttp + kotlinx.serialization, Room, WorkManager, Media3 (background audio session), Coil
- Builds run on a remote Linux host; local builds require a JDK and Android SDK

## Features

- Multiple FileHub instances, password login → per-device API token (Android Keystore)
- Multi-address failover; addresses are tried in the configured order (put LAN first)
- Roots + remote mounts, compact file rows, folder sizes, cursor paging, thumbnails
- File operations: create, rename, delete with confirmation code and countdown, copy/move, ZIP, with async job progress and cancel
- Search across all roots / current root, with index status and one-tap indexing
- Image preview with swipe-to-page, anchored pinch zoom, single-tap chrome toggle, original/preview switch, EXIF and download
- Media3 video/audio preview; title and landscape button follow player controls
- Markdown preview with tables, task lists, strikethrough and links
- Resumable background uploads/downloads, notifications, transfer center
- Photo sync: album selection, videos, mtime, separate photo/video Wi-Fi policies
- Folder sync: SAF folder, upload/download/two-way, three-way base state, conflicts kept without overwrite
- Per-server sync configs; switching the active server does not stop other servers' schedules
- Remote folder picker for sync target paths; typed paths still supported
- Shares with password/expiry/read-only, QR, public share viewer
- Theme and language override (`system` / 简体中文 / English), edge-to-edge UI
- Adaptive launcher icon generated from [`docs/app-icon.svg`](docs/app-icon.svg)

## Background behavior

Sync uses WorkManager periodic work (minimum 15 minutes, daily by default) with Wi-Fi/charging constraints.
It does not require the app UI to be open, but Android/OEM restrictions still apply:

- Force-stopping the app cancels background work until it is opened again.
- Xiaomi/HyperOS may defer or block work; set battery usage to unrestricted, allow autostart and notifications.
- Doze and app standby can delay periodic runs; this is best-effort, not an exact scheduler.
- Manual sync respects the same Wi-Fi/charging settings as the configured schedule.
- Opening the app after a missed interval enqueues a one-time catch-up run.

If the device token expires or the configured target location disappears, the affected sync is disabled until it is reconfigured.

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

## Docs

- [`PLAN.md`](PLAN.md) — scope, architecture, milestones
- [`docs/ui-spec.md`](docs/ui-spec.md) — UI proposal
- [`docs/sync-spec.md`](docs/sync-spec.md) — photo/directory sync design
- [`docs/app-icon.svg`](docs/app-icon.svg) — source logo
- [`docs/api-notes.md`](docs/api-notes.md) — API notes and contract quirks
- [`docs/release-notes/v0.1.0.md`](docs/release-notes/v0.1.0.md) — 0.1.0 release notes
- [`docs/release-notes/v0.1.1.md`](docs/release-notes/v0.1.1.md) — 0.1.1 release notes
- [`CHANGELOG.md`](CHANGELOG.md)
