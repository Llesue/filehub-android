# FileHub Android

Native Android client for [FileHub](https://github.com/Llesue/filehub).

Status: **v0.1.0 development** — M0–M6 implemented, awaiting device QA and release sign-off.

- Kotlin + Jetpack Compose (Material 3), `minSdk 26`, `compileSdk 37.1`, `targetSdk 36`
- OkHttp + kotlinx.serialization, Room, WorkManager, Media3, Coil
- Builds run on the remote `parasite` host; local builds are not supported
- Release APKs are not published until the user signs off

## Features

- Multiple FileHub instances, password login → per-device API token (Android Keystore)
- Roots + remote mounts, tile card rows, sort, cursor paging, thumbnails
- File operations: create, rename, delete with confirmation code, copy/move, ZIP
- Search across all roots / current root
- Image, video/audio, PDF and text previews
- Resumable background uploads/downloads, notifications, transfer center
- Photo sync (selectable albums, videos, mtime) and folder sync (upload/download/two-way)
- Shares with password/expiry/read-only, QR, public share viewer

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
- [`CHANGELOG.md`](CHANGELOG.md)
