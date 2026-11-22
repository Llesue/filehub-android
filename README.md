# FileHub Android

Native Android client for [FileHub](https://github.com/Llesue/filehub).

Status: **M0** — Gradle/Compose skeleton, server profiles, password login → device token, roots listing.

- Kotlin + Jetpack Compose (Material 3)
- `minSdk 26`, `compileSdk 37.1`, `targetSdk 36`
- OkHttp + kotlinx.serialization, no Retrofit/Hilt/Firebase
- Builds run on the remote `parasite` host; local builds are not supported

## Build

```bash
./scripts/provision-sdk.sh          # one-time, on the build host
./scripts/build-remote.sh           # debug APK -> dist/
./scripts/build-remote.sh check     # lint + unit tests
```

The APK is written to `dist/filehub-android-debug.apk`. Release builds are not
published until the user signs off.

## Docs

- [`PLAN.md`](PLAN.md) — scope, architecture, milestones
- [`AGENTS.md`](AGENTS.md) — engineering constraints
- [`docs/ui-spec.md`](docs/ui-spec.md) — UI proposal
- [`docs/sync-spec.md`](docs/sync-spec.md) — photo/directory sync design
