# Changelog

## v0.1.0 - 2026-11-22

### Added

- Native Android client skeleton: Kotlin, Compose Material 3, three-tab shell (Servers / Browse / Me).
- Multiple FileHub instances, password login, automatic per-device write token stored with Android Keystore.
- Location page with local roots and remote mounts; single-column tile cards, sort, cursor paging, thumbnails and read-only states.
- File operations: new folder/file, rename, multi-select delete with confirmation code, copy/move, properties, ZIP archive/extract.
- Search across all roots or the current root, with kind filters and result navigation.
- In-app previews: image zoom, Media3 video/audio, PDF pages, UTF-8 text.
- Background uploads (4 MiB chunks, resume, session recovery) and downloads (Range resume, MediaStore Downloads), with notifications and a transfer center.
- Photo sync: selectable albums, videos, album folders, mtime upload, Room dedupe state, periodic WorkManager sync.
- Folder sync: SAF folder, upload/download/two-way, three-way base state, conflicts kept without overwrite, optional deletion sync.
- Shares: create from a file/folder, password/expiry/read-only, QR code, revoke/rotate, public share viewer with ticket unlock and downloads.
- Theme override (system/light/dark), Chinese and English resources, edge-to-edge UI.
- Server favicons (branding icon with favicon fallback), editable server profiles, multi-address failover with per-server background sync.
- Drag handles in Add Server and Edit Server reorder addresses; the list order is the connection attempt order.
- Remote folder picker can create folders and load additional pages.
- FileHub-matched colored file icons (yellow folders), 8dp card rhythm, theme-aware text preview and Markdown rendering.
- Image long-press/more menu with properties, EXIF metadata and download.
- Compact file rows with folder sizes and a persistent directory metadata cache (Room, max 200 directories).
- Path pill with automatic scroll to the current directory.
- Separate photo/video Wi-Fi policies and one-time Wi-Fi catch-up after a metered run.
- Remote folder picker for photo and directory sync targets; typed paths remain supported.
- Shared remote-directory ensurer that handles existing folders on both new and older FileHub servers.
- Sync locking per profile, per-profile notifications with server name, and cleanup on logout/remove.
- Catch-up sync when the app opens after a missed schedule; token failures disable the affected sync.
- Search result paths are normalized before opening.
- Server failover probes addresses in the configured order instead of always trying the last working address first.
- Adaptive launcher icon and in-app brand mark generated from the supplied SVG.
- Markdown tables, task lists, strikethrough and links.
- Double-tap image zoom and a persistent blurred image backdrop.
- All editable fields dismiss the keyboard/focus on Done; Save/Connect actions still submit.
- Edit Server address fields accept Done again instead of leaving the field in edit mode.

### Fixed

- Photo sync failed after the first file when the remote directory already existed (HTTP 500 from legacy servers).
- Folder sync could upload the same file twice per decision pass.
- Sync notifications from multiple servers overwrote each other.
- Missing target roots or expired tokens now disable the affected sync instead of retrying forever.
- Manual sync ignored Wi-Fi/charging constraints; the sync screen now explains when the run is queued behind those constraints.
- Changing the photo sync target folder now asks before clearing completed records; switching roots shows that the target path will be recreated under the new root.
- Sync notifications now open the matching photo/folder sync screen.
- Delete confirmation now displays the server-provided 4-digit code instead of an empty input.
- Sign-in validity shows the expiry suffix and remaining days so 2027 is not mistaken for tomorrow.

### Notes

- Release APK is not published until the user signs off.
- FileHub 0.2.1 is required for preserving photo timestamps; older servers degrade with a warning.
- Periodic WorkManager runs are best-effort; OEM battery policies and force-stop can delay or cancel them.
