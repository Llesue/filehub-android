# Changelog

## v0.1.2 - 2026-11-24

### Added

- File browser: long-press multi-select with a persistent bottom action bar (move, delete, share, more), no per-row overflow menus, and back clears the selection.
- Bottom-right FAB with one create/upload sheet (folder, file, upload, photos/videos, camera); pull-to-refresh replaces the old overflow refresh.
- List and grid views (2 columns on phones, 3–4 on tablets), with thumbnails, video badges, selection state, paging and empty/skeleton states.
- Per-directory sort, view mode and scroll positions, persisted by server/root/path; list and grid remember separate positions.
- Compact custom bottom navigation with 22dp icons, a 2dp label gap and a 58dp bar.
- APK recognition with a green package icon, plus ebook, font, disk-image, design-file and torrent types; AAB is treated as an archive and subtitles as text.
- Multi-file collection shares (pure files only, up to 100, forced read-only) from the selection bar, with collection-aware share lists.
- Share QR card images: server identity, file/folder name or “N files”, type icon, expiry and password hint; forward, copy and long-press save, with TalkBack save action.
- Permanent share deletion via `DELETE /shares/{id}/purge`, separate from revoke, with a clear irreversible warning.
- Unified document screen: preview is the read-only mode and Edit switches in place; one status bar (encoding, characters, EOL, size) and an Edit/Save action.
- Encoding quick switch when detection is lossy: tap cycles candidates, long-press opens the full list; no silent automatic switching.
- Undo/redo in the document overflow menu.
- Image preview has an independent fullscreen mode: single tap toggles, double-tap zoom enters fullscreen, zoom-out keeps it, and pinch only zooms.
- Video controls hidden means fullscreen: top bar and system status bar hide together and return with the controls.
- Music resumes after a video ends or the preview closes, unless the user paused/stopped/changed music during the video.
- Unified directory picker for move/copy and both sync screens: root switch, breadcrumbs, manual path, paging, new folder, refresh, read-only/offline badges, self/subfolder protection.
- Missing target folders ask for confirmation and are created segment by segment before selection.
- Client update check from GitHub Releases with a 24-hour cache and a “new version” chip beside the client and server versions.
- Search results support long-press multi-select, download and delete.
- Download name collisions append ` (1)`, ` (2)` on Android 9 and below instead of overwriting.

### Changed

- Text and Markdown share a 15sp/23sp baseline; Markdown headings use 1.6/1.4/1.25/1.15/1.05/1.0 and code blocks 13sp; code files stay 14sp monospace.
- The old text preview header, duplicate encoding chip, copy-all and preview download action are gone.
- Share entry is hidden on remote roots because the deployed server cannot create shares there.

### Fixed

- Search selection keyed by root and path so identical names in different folders cannot collide.

## v0.1.1 - 2026-11-23

### Added

- Remote mounts expose online/offline/unknown state; browsing falls back to cached listings with an explicit offline banner, and directory/photo sync preflights the remote root and reports one failure instead of per-file spam.
- Image preview: single-tap chrome toggle, anchored pinch zoom with bounds, double-tap at 150% of 1:1, and a bottom-left original/preview switch with automatic fallback.
- Startup auto-connects the active server in address order while keeping the server page visible, without overriding notification deep links or the user's tab choice.
- Camera shortcut in the browser toolbar and empty folder state; photos upload to the folder where the shutter was pressed.
- Modal delete confirmation that lists every selected file name, with a numeric-code field when the server requires one; dismissal is disabled while the request runs.
- Server-side text probe with legacy encoding detection (UTF-16 without BOM, GBK/GB18030, Big5, Shift_JIS, EUC-KR, Windows-125x, ISO-8859-x); unknown files can open as text with lossy warnings.
- Reader-style text preview: document header with size, time, encoding, line-ending, character and BOM chips, a padded page surface, copy-all, download and direct encoding switching; lossy text gets an inline "try another encoding" action.
- Syntax highlighting for Kotlin, Java, Python, JavaScript/TypeScript, C/C++, Rust, Go, shell and other common grammars, using the bundled JetBrains Mono font; search matches are highlighted without losing syntax colours.
- Text editor with saved/unsaved state, a filled save action, tappable encoding and line-ending chips, LF/CRLF switching, find-in-file with match count and next/previous navigation, a symbol/Tab toolbar, a Markdown formatting bar, and side-by-side Markdown preview on wide screens.
- Editor themes: follow the app, fixed light, or sepia, persisted per device.
- Unsupported file types open an action dialog (download, share, properties, open as text when possible) instead of a dead-end full-screen message; read failures use the same dialog.
- Shared empty/error states with an icon, an explanation and a single recovery action.
- On-demand directory sizes for local and remote roots, with a concurrency limit, polling for server-side scans, cache and `≥` marker for partial results.
- Unified relative time across file cards, details, search, sync pages and token validity, with unit tests.
- Global audio player backed by MediaSessionService: queue from the current folder, previous/next, seek, background and notification controls, collapsed/expanded layouts, and automatic stop on logout.
- Audio track discovery and transcoding fallback, with a track picker and clear ffmpeg/transcode errors.
- Long filenames keep their real suffix (`tar.gz`, `7z`, `3gp`) without misreading `.gitignore`, `Dockerfile`, `v1.2.3` or `file.2026`.
- PDF pages render at the available width and density, with a pixel cap and graceful failure.
- Asynchronous job polling for copy/move/ZIP/unzip with progress, cancel, completion refresh and server error messages.
- Storage quota card with computing/stale/error states and a 90% warning colour.
- Server version display and search index status with start-index action, progress and partial-result warning.
- Share editing: read-only, pause/resume, expiry, set/clear password; secrets stay masked.
- Thumbnail status and retry from the file menu and details dialog.
- Image details now include dimensions, format and localized EXIF keys with readable orientation values.
- Built-in file names (`Dockerfile`, `Makefile`, `LICENSE`, `.gitignore`, …) are recognized as code/text.

### Fixed

- Tapping an image in search results crashed with `IndexOutOfBoundsException` when the preview pager shrank to one page; search previews are now pinned to the selected image.
- Uploads now refresh the current directory after completion, debounced for batches, without refreshing unrelated folders.
- Content URIs keep `DATE_MODIFIED`, `DATE_TAKEN` or document modification time when uploading; unknown-size copies preserve the original mtime.
- Drag-reordering server addresses animates neighbouring rows using measured heights and snaps before committing the new order.
- Downloads use `Pictures/FileHub`, `Movies/FileHub` and `Downloads/FileHub` consistently, including public shares.

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
