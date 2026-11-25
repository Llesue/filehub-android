# Changelog

## v0.1.6 - 2026-11-26

### Added

- Daily photo sync scans only new, modified or failed photos through MediaStore watermarks, and logs a no-change run without walking the whole library.
- A fast skip reuses a completed entry when the local size/mtime and read mode are unchanged and the remote directory still lists the same name and size, avoiding per-photo hash requests.
- A full sync action with confirmation re-checks every photo; folder sync re-verifies every same-size file.
- Folder sync hashes and uploads SAF documents through `MediaStore.setRequireOriginal` when the document maps to a MediaStore item, so photos in folders keep GPS metadata.
- Sync run state exposes the current action (skip/upload/verify/rename/download/delete/conflict), and both sync screens label the normal action "Daily sync" and the deep scan "Full sync".
- The sync log follows the newest entry after the 200-entry cap and when the log dialog opens.

### Fixed

- Photo sync opens MediaStore items with `ACCESS_MEDIA_LOCATION` and `setRequireOriginal`, preserving GPS EXIF instead of uploading a location-redacted copy.
- Photo sync reuses the previous remote name, or the canonical name when its hash matches, so a renamed file no longer grows into `(2)`, `(3)` on every run.
- Legacy photo entries without an original-stream hash are adopted once instead of being renamed or re-uploaded.
- Cached photo and folder hashes are versioned by read mode, so hashes computed from redacted streams are ignored after location access is granted.
- A denied location permission is surfaced in both sync screens and logged, instead of silently dropping GPS metadata.
- Manual uploads of MediaStore URIs also use the original stream when location access is granted.
- Background sync probes the configured server addresses top to bottom and persists the first working one instead of failing on a stale base URL; a working LAN address is never skipped in favour of a public one.

### Improved

- Photo sync stops after a no-change incremental scan and reports it in the log.
- The sync action row places Full sync on the left and the daily action on the right.
- Folder sync skips unchanged files without hashing or transferring them and shows "Skipped (already present)" in the current action.

## v0.1.5 - 2026-11-26

### Added

- Shared sync run state for photo and folder sync with phase, block reason, cancel reason, run id, heartbeat, item/byte progress and stop/cancel flags.
- A bounded sync log (200 entries per sync kind) recording start, scan, plan, progress, failures and completion, shown in the right-hand pane.
- SHA-256 content hashing for photo and folder sync: local streaming hash with a per-entry cache, server capability detection, upload verification, same-name content comparison and same-size cross-name dedupe.
- A four-step state machine strip in the sync status card, with photo steps (prepare/scan/apply/done) and folder steps (scan/compare/apply/done).
- Notification progress for both sync kinds: overall bytes and counts, current file, transfer speed, stop/cancel/continue/retry actions, a completion summary and a privacy-safe lock-screen version.
- Conflicts page for folder sync with per-file keep-phone / keep-server resolution, followed by an automatic re-sync.
- Two-column share cards on wide screens, keeping the copy/QR/open actions on each card.

### Fixed

- MediaStore buckets that report the same `BUCKET_ID` under several case variants (`Camera` / `camera`) now resolve to one canonical album name for both the picker and the uploader.
- Changing a sync target now cancels the old worker and marks the run as target-changed instead of letting it keep writing into the old folder.
- A finishing worker can no longer overwrite a newer sync configuration; only the result fields are updated.
- Folder sync clears its three-way baseline when the target or direction changes, so the next run compares against the right target.
- Delete sync refuses to run when its target overlaps the photo sync target.
- Photo and folder sync can no longer run at the same time for one profile; the later run reports that another sync is running.
- Saving an overlapping folder/photo target now requires explicit confirmation.
- Stale active runs are shown as interrupted instead of a frozen progress bar.
- Servers without hash support, or hash endpoints returning 404/501, fall back to size+mtime without blocking sync.
- A `HASH_MISMATCH` response clears the upload session so the next run starts a fresh upload instead of reusing it.

### Improved

- Sync settings lock while a run is active, and the save button reflects dirty, saving, saved, running and target-changed states.
- Sync notifications are throttled and keep working when notification permission is denied.
- Sync log entries are one line each with long text ellipsized; start lines are blue, successful completions green, failures red, warnings amber, and timestamps use a lighter outline colour.
- The shares top bar no longer offers a paste-a-share-link entry; copy link and the QR card remain.
- All four utility pages use a 48dp compact toolbar, and the sync save action moved into the toolbar instead of occupying a bottom row.
- Photo and folder sync place options on the left and the live state machine plus log on the right; below 720dp they collapse to one column and the log opens as a dialog.
- The shares page no longer has a search field or filter chips; the card grid uses the full page width.
- Sync storage targets use a full-width picker with yellow folder icons, hidden offline/read-only roots, local/remote labels, and a top-right close button.
- Selecting a remote root shows a small circled warning next to "Storage"; tapping it explains that remote targets work without content hashing and fall back to name, size and modified time.
- Remote roots in the browser show the sharer's avatar, with a colored initial fallback; their "Remote" and "Online" badges sit on the name row and long names ellipsize instead of pushing badges away.
- Root cards use a bare yellow folder icon instead of a grey icon tile; folder contents are unchanged.
- The sync root field shows "Remote" as a blue inline badge on the same line as the directory name.
- First sign-in and admin password resets are handled inside the app: the login dialog collects a new password and calls the forced-change endpoint before creating the device token.
- An expired server token opens a prefilled "Sign in again" screen with only the password field; the server profile id is preserved so photo/folder sync configuration stays attached.
- Server cards show the last known server version as a `vX.Y.Z` badge next to the name, fetched for the active server and cached for the others when the list opens.
- An expired server card shows a "Sign in again" badge instead of a long error sentence; the whole card opens the re-authentication screen.
- Server order can be changed by long-pressing and dragging a card, in both the phone server list and the landscape/tablet server picker; the dragged card follows the finger, the other cards animate into their new slots, and the order is saved immediately.
- Phone, landscape and tablet server cards share one layout with the server icon, account name, version badge and re-authentication badge.
- Add-server, re-authentication and edit-server forms use a centred, width-limited column on landscape phones and tablets instead of stretching edge to edge.
- Share management calls the stop action "停止分享" and the rotate action "重置链接", matching the server and web UI.
- Stopped and expired share cards are dimmed, their copy/QR/open actions are disabled, and their menu only offers enable and delete.
- Re-enabling an expired share extends it by one day, the same behaviour as the web UI; the edit dialog no longer carries a redundant pause switch.
- Active share cards show a relative expiry ("过期：3天后") instead of a generic "有效" label.

## v0.1.4 - 2026-11-25

### Added

- Foreground-only SSE connection to `GET /api/v1/events` with automatic reconnect and backoff, a 60-second idle watchdog, a 401 stop and a 404/405 fallback to polling.
- Real-time job progress from `job.updated`; while SSE is connected the job poll drops from 1.5 seconds to a 15-second safety net, and reconnect/resync compensates once.
- Real-time quota from `usage.updated`, server version refresh from `updates.changed`, root online/order refresh from `roots.changed`, and settings/feature refresh; `resync` refetches the visible snapshot.
- Tablet and car layouts keep the same two-pane shell in portrait and landscape; the sidebar and list columns adapt to the available width instead of falling back to the phone portrait layout.
- PDF preview zooms down to a computed fit-page scale, so pinch-out stops with the whole page filling the viewport instead of shrinking the pager layer.
- Tablet-mode image previews on Android 11 and below use a neutral gradient lightbox instead of a thumbnail background or pure black.
- Deleting a root, or removing the root currently being browsed, returns the file browser to the root list instead of leaving stale contents.
- SSE parser and connection tests for multi-line data, CRLF, chunked input, request headers, reconnect, 401 and 404.
- Self-built syntax highlighting for JSON, XML, HTML, CSS and SQL in both preview and edit, without adding a dependency.
- Phone landscape uses a permanent left column with the server selector, roots, share management, quota and versions, while the right pane starts empty and follows folder drill-down; the left list switches to folder contents after a folder is opened. The server header, roots, shares, quota and versions share one scrolling column, so nothing is sticky.
- Car and tablet layouts use a fixed root sidebar and a right-hand file browser, with 1–4 list columns that adapt to width; tablet list rows are 76dp tall with 56dp thumbnails and 8dp spacing.
- The tablet now has a full-width top bar with the server picker, search aligned to the file list, list/grid switch, sort, theme toggle, upload and avatar.
- Selecting a folder on tablet navigates inside the right pane; the sidebar root selection stays fixed.
- Tablet list cards have a thin grey outline, a selection checkbox on the icon and a thicker row with a larger thumbnail; long-press opens an action menu with details, download, ZIP, share, rename, copy, move and delete.
- The tablet breadcrumb is a thin row, the shell hides the system status bar, and the context menu is wider with tighter item spacing.
- Images and video open in the existing fullscreen viewer/player, PDFs in the fullscreen PDF viewer with pinch zoom, a left page rail capped at eight visible page numbers, full-width pages in landscape and a top bar that overlays the page and hides while scrolling without resizing the document, and text/code/Markdown in the unified document screen.
- Phone landscape drops the bottom tab bar and drawer in favour of the permanent left column; the avatar enters My and the back arrow returns to browsing.
- Offline remote roots are collapsed by default on the Locations page; expanding shows them dimmed and tapping one only shows a “Directory offline” toast.

### Improved

- The inline preview pane and its text-preview fallback were removed; text always uses the unified document screen.
- The directory picker hides offline roots completely instead of offering them.
- File grid columns adapt from 2 to 6 instead of stopping at 3–4; root cards adapt up to 4 outlined columns on wide screens.
- The tablet sidebar holds roots, offline roots, share management, a thicker quota bar and server/client version lines with update hints; the avatar opens a narrow My popover without quota or versions, and the server name opens the server picker.
- Document text is capped at 880dp, and the tablet header is a single row with a separate breadcrumb.
- Landscape and wide layouts move upload and selection actions to the top instead of the FAB and bottom selection bar.
- Tablet text editing opens in a modal editor, and the text editor auto-saves 1.5 seconds after typing stops plus every 15 seconds while dirty.
- Landscape My actions use four columns when space allows, and the logout button is centred across layouts.
- Uploads that collide with an existing name are always auto-renamed; the client never overwrites or skips.

## v0.1.3 - 2026-11-24

### Fixed

- Moving or copying files from subdirectories now sends root-relative paths, so bulk operations no longer fail with “not found”.
- The move/copy directory picker no longer offers manual path entry; manual entry remains available for sync targets.
- A lost server connection marks the active server offline, and the directory retry re-probes every configured address before reloading.

## v0.1.2 - 2026-11-24

### Added

- File browser: long-press multi-select with a persistent bottom action bar showing the selected count plus move, share, delete and more; no per-row overflow menus, and back clears the selection.
- Bottom-right FAB with one icon-labelled create/upload sheet (folder, file, camera, upload files, photos/videos); pull-to-refresh replaces the old overflow refresh.
- List and grid views (2 columns on phones, 3–4 on tablets), with card-style grid tiles (image, name, size and time), large type icons, sharper shared thumbnails, video badges in grid only (no play badge in the list), selection state, paging and empty/skeleton states.
- Per-directory sort, view mode and scroll positions, persisted by server/root/path; list and grid remember separate positions.
- Compact custom bottom navigation with 22dp icons, a 2dp label gap and a 58dp bar.
- APK recognition with a green package icon, plus ebook, font, disk-image, design-file and torrent types; AAB is treated as an archive and subtitles as text.
- Multi-file collection shares (pure files only, up to 100, forced read-only) from the selection bar, with collection-aware share lists.
- Share QR card images: server identity, file/folder name or “N files”, type icon, expiry and password hint; forward, copy and long-press save, with TalkBack save action; the raw URL is omitted and the expiry/footer use the same size as the title, centred with a safe bottom margin.
- Permanent share deletion via `DELETE /shares/{id}/purge`, separate from revoke, with a clear irreversible warning.
- Unified document screen: preview is the read-only mode and Edit switches in place; one status bar (encoding, characters, EOL, size) and an Edit/Save action; the title collapses when scrolling and Back in edit mode returns to the preview.
- Encoding quick switch when detection is lossy: tap cycles candidates, long-press opens the full list; no silent automatic switching.
- Text and code wrap by default, remember the wrap preference per file, and keep line numbers aligned on wrapped visual lines; preview and edit use the same content margins and Markdown body typography.
- Undo/redo in the document overflow menu.
- Image preview has an independent fullscreen mode: single tap toggles, double-tap zoom enters fullscreen, zoom-out keeps it, and pinch only zooms.
- Video controls hidden means fullscreen: top bar and system status bar hide together and return with the controls.
- Music resumes after a video ends or the preview closes, unless the user paused/stopped/changed music during the video.
- Unified directory picker for move/copy and both sync screens: root switch, breadcrumbs, manual path, paging, new folder, refresh, read-only/offline badges, self/subfolder protection.
- Missing target folders ask for confirmation and are created segment by segment before selection.
- My page actions are visually consistent: Transfer and the sync/share/refresh entries are outlined buttons, Logout is filled in the error colour, and server removal lives only on the server list.
- Client update check from GitHub Releases with a 24-hour cache and a “new version” chip beside the client and server versions; the version card sits directly below the server card and has no refresh button.
- Storage quota is shown as a compact 38dp progress ring in the account card (percentage plus used/total), replacing the separate quota card; long server URLs are ellipsized on the account and edit-server screens.
- Search results support long-press multi-select, download and delete.
- Download name collisions append ` (1)`, ` (2)` on Android 9 and below instead of overwriting.
- Large code files stay responsive in the editor by skipping syntax colouring above 40k characters; the preview still highlights the full file.
- File names keep suffixes for APK, AAB, EPUB, fonts, disk images, design files, torrents and subtitles when middle-ellipsized.

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
- Editor themes: follow the app, fixed light, or sepia, persisted per device; the selected page colour paints the status/navigation bar area, the document header and the bottom status bar while a document is open.
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
