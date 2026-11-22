# FileHub API notes

Notes collected while building the Android client. Paths are relative to
`/api/v1`. All IDs in examples are placeholders.

## Remote mounts

- `GET /roots` returns an `online` boolean for every root. Local roots are
  always `true`; remote roots are probed with a short, bounded request.
  Older servers omit the field, which the client treats as unknown.
- `REMOTE_OFFLINE` (HTTP 503) means the peer never answered: DNS, connect,
  timeout or a broken connection. Do not confuse it with HTTP 401/403 from
  the peer, which keep their own `REMOTE_TOKEN_INVALID` /
  `REMOTE_GRANT_REVOKED` / `FORBIDDEN` meanings.
- A remote root that disappears from `/roots` still maps to
  `sync_root_missing` on the client.

## Async jobs

- `POST /fs/copy`, `POST /fs/move`, `POST /archive/zip` and
  `POST /archive/unzip` return `{ "job_id": "...", "status": "queued" }`.
- `GET /jobs?since=` lists jobs; `GET /jobs/{id}` returns one job;
  `DELETE /jobs/{id}` cancels it.
- Status values: `queued`, `running`, `completed`, `failed`, `cancelled`.
  `progress`/`total` count items; `bytes_done`/`bytes_total` are optional.
- Treat a missing job after a server restart as a failure, not as success.

## Text

- `GET /file/probe?root&path` returns `is_text`, `reason`, `size`,
  `encoding`, `bom`, `lossy`.
- `reason` is one of `text`, `lossy`, `empty`, `binary`, `too_large`.
  `lossy` still means text: the client shows a warning and can retry with a
  manually chosen encoding.
- `GET /file?root&path[&encoding]` returns the decoded content, `etag`,
  `size`, `modified`, `line_ending`, `writable`, `read_only_reason`,
  `encoding`, `bom`, `lossy`.
- `PUT /file?root&path` accepts `content`, `encoding`, `bom`, `force`.
  Send `If-Match: <etag>` for a normal save. `412` means the file changed;
  `413` is too large; `415` is not text; `422` means the chosen encoding
  cannot represent the content.
- A `404` from the editor endpoints means the server has text editing
  disabled, not that the file is missing.

## Storage and index

- `GET /usage` returns `quota_bytes`, `used_bytes`, `entry_count`,
  `max_entries`, `disks`, `computed_at`, `stale`, `computing`.
  `used_bytes: null` with `computing: true` means the first scan is running.
- `GET /version` returns `version`, `latest`, `update_available`,
  `check_enabled`.
- `GET /index/status?root&path` returns `status`, `entry_count`,
  `indexed_at`, `total_size_bytes`, `partial`, and the active `job`.
- `POST /index` with `{root, path, recursive}` returns a job id; poll the
  status endpoint until the directory is ready.

## Shares

- `PUT /shares/{id}` accepts `read_only`, `expires_at`, `clear_expires`,
  `password`, `clear_password`, `revoked`. It returns `{ "ok": true }` and
  does not return the password.
- A request that changes nothing returns `404`; send only changed fields.

## Media

- `GET /media/audio-info?root&path` returns `ffmpeg`, `tracks`,
  `default_track`, `has_supported_track`. Tracks include `index`, `codec`,
  `channels`, `language`, `default`, `supported`.
- `POST /media/transcode` with `{root, path, track}` returns `ready`/`job_id`.
  Poll the job, then play `GET /media/transcoded?root&path&track`.
- `GET /thumb/status?root&path&edge` returns `ready`, `missing` or `failed`
  with `reason`/`message`. `POST /thumb/retry` clears cached failures.
- `GET /fs/dir-size?root&path` returns `status`, `size`, `partial`; remote
  peers can truncate very large directories.
