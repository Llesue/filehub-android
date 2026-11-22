# FileHub Android

Native Android client for [FileHub](https://github.com/Llesue/filehub).

Status: **v0.2.0 development** — second feedback round implemented, awaiting device QA and release sign-off.

- Kotlin + Jetpack Compose (Material 3), `minSdk 26`, `compileSdk 37.1`, `targetSdk 36`
- OkHttp + kotlinx.serialization, Room, WorkManager, Media3, Coil
- Builds run on the remote `parasite` host; local builds are not supported
- Release APKs and tags are not published until the user signs off

## Features

- Multiple FileHub instances, password login → per-device API token (Android Keystore)
- Multi-address failover; the last known working address is tried first
- Roots + remote mounts, compact file rows, folder sizes, cursor paging, thumbnails
- File operations: create, rename, delete with confirmation code, copy/move, ZIP
- Search across all roots / current root
- Image preview with swipe-to-page, double-tap 1:1 zoom, persistent blurred backdrop, EXIF and download
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

If the device token expires, the affected sync is disabled and the UI asks for a new login.

## Build

```bash
./scripts/provision-sdk.sh          # one-time, on the build host
./scripts/build-remote.sh           # debug APK -> dist/
./scripts/build-remote.sh check     # lint + unit tests
./scripts/build-remote.sh release   # unsigned release build, never published automatically
```

The debug APK is written to `dist/filehub-android-debug.apk`.

## Docs

- [`PLAN.md`](PLAN.md) — scope, architecture, milestones
- [`AGENTS.md`](AGENTS.md) — engineering constraints
- [`docs/ui-spec.md`](docs/ui-spec.md) — UI proposal
- [`docs/sync-spec.md`](docs/sync-spec.md) — photo/directory sync design
- [`docs/app-icon.svg`](docs/app-icon.svg) — source logo
- [`CHANGELOG.md`](CHANGELOG.md)
