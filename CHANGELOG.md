# Changelog

## v0.1.0 - Unreleased

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

### Notes

- Release APK is not published until the user signs off.
- FileHub 0.2.1 is required for preserving photo timestamps; older servers degrade with a warning.
