# FileHub Android 同步设计

> 状态：v0.2（2026-11-22）｜对应 `PLAN.md §3.5`、`docs/ui-spec.md §7`
> 范围：相册同步（单向）+ 目录同步（上传/下载/双向）

---

## 1. 目标与非目标

### 目标

- 相册：选择相册后自动把新照片/视频备份到 FileHub，保留拍摄时间，可断点、可重试。
- 目录：用户选一个本地文件夹，与服务器目录做上传/下载/双向同步；冲突不静默覆盖。
- 系统杀进程、断网、切网后能继续；失败/冲突可在 UI 查看并处理。
- 任何自动路径都不能造成用户数据丢失。

### 非目标（v1）

- 全盘/后台自动扫描（只同步用户显式选择的相册/文件夹）。
- 重命名检测（改名按删+增；P1）。
- 同步回收站/历史版本（P1；删除同步默认关）。
- 双向同步的自动合并（冲突一律列出，人工解决）。
- 服务器端 mtime 之外的元数据（EXIF 写入、相册封面等）。

---

## 2. 模式

| 模式 | 适用 | 行为 |
|---|---|---|
| upload（默认） | 目录 | 本地为源：新增/修改上传；远端新增默认忽略；远端同路径不同内容 → 冲突，不覆盖 |
| download | 目录 | 远端为源：新增/修改下载；本地新增忽略；本地同路径不同内容 → 冲突 |
| two_way | 目录 | 双方变更都传播；用三方基线判断；冲突不覆盖 |
| photo_upload | 相册 | 固定单向上传；不下载、不删除远端；本地删除不传播 |

- 相册同步不允许选其他模式。
- 目录同步默认 `upload`；切换模式会清空"冲突/忽略"状态并重新扫描（基线保留）。
- 删除同步是独立开关，默认关；对 `two_way` 同样默认关。

---

## 3. 数据模型（Room）

### SyncPair

```
id, serverId, rootId, remotePath
kind: media | tree
localRef: bucketId | treeUri
mode: upload | download | two_way
options: {
  includeVideos, albumSubfolders,
  ignoreHidden, ignorePatterns,
  deleteSync, photosWifiOnly, videosWifiOnly, chargingOnly,
  schedule: manual | 15m | hourly | daily | weekly,
  maxFileBytes?
}
enabled, createdAt, lastRunAt, lastResult, nextRunAt
```

### SyncEntry

```
id, pairId, relativePath (unique per pair)
localUri, localDocId, localSize, localMtime
remotePath, remoteSize, remoteMtime
baseLocalSize, baseLocalMtime
baseRemoteSize, baseRemoteMtime
state: synced | pending | uploading | downloading | conflict | ignored | error
uploadId?, lastSyncedAt, error?, conflict?
```

- `base*` 是"上次成功同步后两端的状态"，不是当前状态。
- 相册条目的 `relativePath` 用相册名 + 文件名；`localKey` 存 MediaStore 签名，用于换机/恢复后匹配。
- 删除/改名后不立刻删 Entry；标记状态，下一次扫描决定。

---

## 4. 扫描

### 4.1 本地

- 相册：`MediaStore.Images`/`Video` 按 bucket 查询；字段 `_ID, DISPLAY_NAME, SIZE, DATE_TAKEN, DATE_ADDED, RELATIVE_PATH, MIME_TYPE`。
- 目录：SAF tree 递归；`DocumentFile` 的 name/size/lastModified；相对路径规范化（统一 `/`，拒绝 `..`）。
- 过滤：隐藏文件（`.` 开头）、`.filehub-sync-trash`、临时文件（`.part`, `~$` 等）、用户自定义 glob（P1）。
- 单次扫描先产出 `LocalSnapshot: Map<relativePath, LocalFile>`；不在此阶段读文件内容。

### 4.2 远端

- 从 `remotePath` 开始 BFS：
  - `GET /list?root&path&limit=1000` 分页；
  - 对已知目录先 `GET /fs/dir-version?root&path`，mtime 未变且本地目录未变则跳过列举；
  - 只处理 `is_dir=false` 的文件；目录递归。
- 产出 `RemoteSnapshot: Map<relativePath, RemoteFile>`。
- 失败目录记录错误，不阻塞其他目录；该目录本轮跳过。

### 4.3 合并

- Key = 相对路径。
- 对每个 key 读取 `SyncEntry` 基线；计算：
  - `localState = new | unchanged | changed | absent`
  - `remoteState = new | unchanged | changed | absent`
- 判断：
  - `unchanged`：存在且 size+mtime 等于 base 对应侧；
  - `changed`：存在但 size 或 mtime 与 base 不同；
  - `new`：存在但没有 base；
  - `absent`：不存在。
- 生成 `SyncAction` 列表（见 §5），不直接执行。

> 只用 size+mtime 作为变更信号。对于"同 size 不同内容"的修改，mtime 通常会变；若两者都没变但内容变了，v1 无法发现。P1 可对可疑项计算 SHA-256。

---

## 5. 动作矩阵

### 5.1 two_way

| 本地 | 远端 | 动作 |
|---|---|---|
| new | absent | upload |
| absent | new | download |
| new | new | 内容相同 → synced；不同 → conflict |
| unchanged | unchanged | synced |
| changed | unchanged | upload |
| unchanged | changed | download |
| changed | changed | 内容相同 → synced；不同 → conflict |
| absent | unchanged (base) | deleteSync ? deleteRemote : ignored |
| unchanged (base) | absent | deleteSync ? deleteLocal : ignored |
| absent | changed | conflict |
| changed | absent | conflict |
| absent | absent | remove entry |

- "内容相同"用 size + mtime 判断；不读取远端内容。
- `ignored`：保留另一端文件，不自动恢复/删除；UI 显示"仅一端存在（未同步）"。
- 冲突条目本轮不执行任何传输，继续处理其他文件。

### 5.2 upload

| 本地 | 远端 | 动作 |
|---|---|---|
| new | absent | upload |
| new | new/changed | 相同 → synced；不同 → conflict（不覆盖远端） |
| changed | absent | upload |
| changed | unchanged | upload |
| changed | changed | conflict |
| unchanged | absent | upload（恢复远端） |
| unchanged | changed | conflict（远端被外部修改） |
| absent | * | ignored（上传模式不删远端） |

### 5.3 download

| 本地 | 远端 | 动作 |
|---|---|---|
| absent | new/changed | download |
| unchanged | changed | download |
| changed | * | conflict（不覆盖本地修改） |
| new | new/changed | 相同 → synced；不同 → conflict |
| absent | unchanged (base) | ignored（下载模式默认不删本地；deleteSync 开启则 deleteLocal） |
| * | absent | ignored（下载模式不因远端删除而删本地） |

> 上传/下载模式的冲突默认"保留两端"，用户可逐文件选择覆盖方向；不自动。

---

## 6. 冲突

### 检测

- 同路径两端都有内容，且至少一侧 `changed`，且 size/mtime 不一致。
- 一方 absent、另一方 changed（删除 vs 修改）。
- 首次双向同步时两端同路径不同内容。

### 解决动作（v1）

| 动作 | 行为 |
|---|---|
| 用本地 | 上传本地覆盖远端（需二次确认；保留端无备份） |
| 用服务器 | 下载远端覆盖本地（需二次确认） |
| 两个都保留 | 本地重命名为 `name (local-YYYYMMDD-HHMMSS).ext`，上传；远端原文件下载为原名；任一步失败则回滚标记 |
| 跳过 | 保留冲突状态，下轮仍显示，不重试传输 |

- 批量解决 P1；v1 逐文件。
- "两个都保留"需要 SAF rename；失败时回退"复制到 cache → 新建 → 删除旧文件"，仍失败则只保留冲突状态并提示。
- 冲突不做自动"较新者胜"——不能信任设备/服务器时钟。

### 展示

- 同步详情 → 冲突 tab：路径、本地/远端大小与时间、缩略图（可选）、操作按钮。
- 并发冲突不阻塞其他文件；运行结束后状态为"完成，有 N 个冲突"。
- 通知：首次出现冲突时提示一次，不刷屏。

---

## 7. 删除

- `deleteSync` 默认关。
- 关闭时：
  - 本地删除 → 远端文件保留，Entry 标记 `ignored`；
  - 远端删除 → 本地文件保留，Entry 标记 `ignored`；
  - 不自动恢复、不重复传输。
- 开启时：
  - 本地删除且远端未改 → 删除远端；
  - 远端删除且本地未改 → 删除本地（SAF delete）；
  - 任一方已改 → 冲突，不删。
- 删除远端是永久删除（FileHub 无回收站）；开启开关时二次确认，并提示"改名会按删+增处理"。
- 批量删除失败：单文件失败记录，不阻塞；下轮重试。
- P1：同步回收站（远端移到 `.filehub-sync-trash/`）。

---

## 8. 上传 / 下载

- 复用传输引擎：分片、断点、2 并发、通知、401/429/507 处理。
- 上传：
  - `POST /uploads` 携带 `mtime`（FileHub 0.2.1）；
  - 目标路径 `remotePath/relativePath`；
  - 完成后以服务端返回的最终路径回写 Entry（服务端可能重命名）；
  - 若同名远端文件存在且内容不同，不依赖账号级冲突策略，先走冲突逻辑。
- 下载：
  - `GET /download` + Range；写临时文件；校验 size；
  - 写入 SAF 目标目录；SAF 无法设置 mtime，记录实际 mtime 作为 base；
  - 文件名冲突按冲突处理，不覆盖。
- mtime：
  - 相册：`DATE_TAKEN`（无则 `DATE_ADDED`）→ 秒；
  - 目录：本地 `lastModified` → 秒；
  - 服务端 0.2.1 落盘后写回；
  - 旧服务端不发送 mtime，提示升级；base 记录实际远端 mtime。

---

## 9. 相册同步细节

- 目标路径：`remotePath/<album>/<filename>`（`albumSubfolders` 默认开）；关闭则平铺。
- 相册名净化：去掉 `/`、`\`、控制字符；空名用 `未命名相册`；重名追加序号。
- 去重签名：`(DISPLAY_NAME, SIZE, DATE_TAKEN, RELATIVE_PATH)`；另存 MediaStore `_ID` 作为快速路径，但不作为唯一键。
- 修改过的照片（同 ID，size/mtime 变）：
  - 默认作为新版本上传，文件名冲突按"保留两者"（`name (1).ext`），不覆盖旧照片；
  - 用户可在同步详情看到重复项。
- 视频默认包含；大视频受 Wi-Fi/充电约束。
- 不传播删除：本地删除后远端保留；下次扫描不再上传（Entry 标记 ignored，签名仍保留以避免重传）。
- 权限：`READ_MEDIA_IMAGES`/`READ_MEDIA_VIDEO`；部分授权只扫描可读集合；UI 明示。
- 配额：开始前 `GET /usage` 预检；剩余空间不足时提示，不启动整批。

---

## 10. 调度与后台执行

### 10.1 触发方式

| 触发 | 行为 |
|---|---|
| 周期任务 | 每个 pair 一个唯一 `PeriodicWorkRequest`；最小 15 分钟，实际会被 Doze/厂商延迟 |
| 立即同步 | 一次性唯一 WorkRequest；应用可见时优先以前台任务启动 |
| App 启动/回前台 | 检查 `nextRunAt`/`lastRunAt`，过期则补一次；不重复入队 |
| 应用内拍照/写入同步目录 | 防抖 30s 后为该 pair 入队一次 |
| MediaStore 变化（App 存活） | `ContentObserver` 监听图片/视频，防抖后入队；App 被杀后不保证实时 |
| 外部文件变化（App 未运行） | 等下一次周期任务或用户手动同步 |
| 系统重启 | WorkManager 恢复持久任务；App 启动时再做一次过期检查 |

- 不使用精确闹钟；不启动常驻前台服务；不承诺"拍照后立刻上传"（App 在后台被冻结时做不到）。
- 用户手动"立即同步"同样遵守 Wi-Fi/充电约束；不提供静默绕过。
- 计费网络下被跳过的照片或视频保持 pending，并排一个仅 Wi-Fi 的一次性补跑任务。

### 10.2 运行预算与分批

- 每个 pair 同一时间只跑一个实例；全局同时只跑 1 个 pair；pair 内上传/下载 2 并发（服务端每用户上传并发默认 2）。
- **每个工作批次最多 5 分钟或 100 个文件**，先到为准；每完成一个文件写一次 checkpoint（Room），然后主动结束本批次。
- 批次结束后如有剩余：网络/约束仍满足则 1 分钟后入队下一批；否则等下一次周期/用户手动。
- 这样避免 WorkManager 普通 worker 的 10 分钟限制、Android 15 `dataSync` 6 小时限制、Android 16 job quota 以及厂商杀进程导致的整批丢失。
- 首次 5000 张照片同步就是"连续多个批次"；UI 显示剩余数量，不假装一个任务一口气跑完。
- 手动触发大批量时，应用可见 → 启动前台任务，拿到完整的用户发起预算；仍然每 5 分钟 checkpoint。
- Android 16 起，与前台服务并发运行的 Job 也受 job quota 限制；不依赖"FGS 在跑就无限执行"。

### 10.3 前台服务与通知

- Worker 实现 `getForegroundInfo()`；需要长任务时 `setForeground()`，类型 `dataSync`，声明 `FOREGROUND_SERVICE_DATA_SYNC`。
- `setForeground()` 可能抛 `ForegroundServiceStartNotAllowedException`（Android 12+ 后台启动限制）：捕获后**降级为普通 worker**继续跑当前批次；到 10 分钟/被系统停止前 checkpoint，下一次再继续。不能因此崩溃或丢状态。
- 通知：
  - 进行中：`同步相册 · 123/500 · 45%`，带暂停/取消；
  - 完成：成功/失败/冲突计数，点击进同步详情；
  - 冲突：只发一次汇总通知，不逐文件刷屏。
- Android 13+ 请求 `POST_NOTIFICATIONS`；被拒时同步仍可运行，但前台服务通知只能在系统任务管理器看到；UI 提示去开启。
- Android 15+：`dataSync` 前台服务 6 小时/24 小时上限；我们不跑到上限（5 分钟分批），但保留 `onTimeout`/被停止后的 checkpoint 恢复。
- 不在 `BOOT_COMPLETED` 里启动 `dataSync` 前台服务（Android 15 限制）；只恢复 WorkManager 任务。

### 10.4 进程死亡、重启、强停

- 每个文件的状态先落 Room 再开始传输；`uploadId`、已传 chunk、下载 `.part` 都可恢复。
- App 被杀/系统回收：WorkManager 重新调度；下一批先 `GET /uploads/{id}` 对齐已收 chunk。
- 上传会话过期（默认 24h）：重新创建会话，从头传该文件；不清空其他文件状态。
- 设备重启：WorkManager 持久任务恢复；App 启动时再检查一次过期 pair。
- 用户"强行停止"应用：WorkManager 不会运行，直到用户再次打开 App；UI/文档明确说明，不假装能后台自愈。
- SAF 权限被撤销：暂停 pair，提示重新选择文件夹；不静默跳过。

### 10.5 厂商后台限制（HyperOS）

- 首次启用同步时给一页说明 + 跳转：
  - 允许自启动；
  - 电池 → 无限制（或"不优化"）；
  - 最近任务里锁定 App（可选）。
- 用 `PowerManager.isIgnoringBatteryOptimizations` 检测；未开启时在同步卡片上显示警告，不阻止用户先手动同步。
- 不承诺在 HyperOS 上"永远准时"；周期任务被延迟是预期行为。

### 10.6 我们对用户的承诺

- **"立即同步"**：App 可见/前台任务期间可靠，可暂停/继续。
- **"每天/每 6 小时"**：尽力而为；Doze、省电、厂商策略可能延迟，UI 不显示"精确下次时间"，只显示"约"。
- **"拍照后自动备份"**：App 存活时可通过 MediaStore 观察触发；App 被杀后要等下一次周期或打开 App。
- 若用户需要"充电时持续同步"，P1 提供显式开关（前台服务 + 常驻通知），默认不开。

### 10.7 测试清单

- `adb shell am force-stop` 后重开：状态恢复、批次续跑。
- 设备重启：WorkManager 恢复；过期检查。
- `adb shell dumpsys deviceidle force-idle`：周期任务延迟，退出 Doze 后补跑。
- 断网/切网/飞行模式：当前批次失败可重试，不丢 checkpoint。
- 低电量/省电模式：按约束暂停。
- Android 15/16 真机：`dataSync` 长任务被停止后能续；通知权限拒绝可用。
- HyperOS：自启动/电池限制开启与关闭两种状态各跑一次。
- 服务器 401/403/413/429/507：暂停/退避/提示正确。
- 上传会话过期：重建并续传，不重复已完成文件。

---

## 11. 错误与状态

| 错误 | 处理 |
|---|---|
| 401 | 暂停 pair，提示重新登录；保留状态 |
| 403 / 只读 | 暂停并标红；提示目标只读 |
| 404（远端文件/目录消失） | 重新扫描该目录，转为删除/冲突逻辑 |
| 409 | 冲突；按 §6 处理 |
| 413 / 507 / 配额 | 暂停整批；提示清理或扩容 |
| 429 | 指数退避；暂停该 pair 一段时间 |
| 网络错误 | 重试 3 次；仍失败则本轮结束，保留断点 |
| SAF 权限丢失 | 暂停，提示重新选择文件夹 |
| 本地文件被删除 | 按删除逻辑处理 |

- 单文件失败不阻塞整批；错误列表可重试/忽略。
- 同步结果：`success | partial | conflict | failed | cancelled`；UI 显示计数。

---

## 12. 性能与上限

- 远端列举：200–1000/页；优先 `dir-version` 跳过未变目录；限制并发 2。
- 本地扫描：5000 张照片元数据 <10s；10 万文件目录需分批/可暂停（P1 优化）。
- Room：批量 upsert；每 200 条一个事务；进度更新节流 500ms。
- 上传/下载：单 chunk 8MiB；内存峰值 <20MB。
- 首轮 5000 张照片：显示预计时长；默认照片/视频都仅 Wi-Fi；失败可续。
- 远程目录已存在时先探测再 mkdir，兼容旧服务端对 EEXIST 返回 500 的行为。
- 服务端 rate limit：遇到 429 自动降速；不并行轰炸。

---

## 13. 测试

- 单元：动作矩阵全组合；base 状态迁移；冲突命名；相册签名；删除开关；relativePath 规范化。
- MockWebServer：list 分页、dir-version 跳过、mtime 字段、409/429/507、上传中断恢复。
- 真实契约：FileHub 0.2.1 + 0.2.0（降级 mtime）。
- 设备：5000 张照片、双向冲突、删除开关、杀进程、切网、HyperOS 后台。
- 安全/数据：冲突不覆盖、删除开关关闭不丢文件、首轮双向不自动合并。

---

## 14. 待对齐

1. FileHub 0.2.1 的 `mtime` 字段位置（`POST /uploads` vs `complete`）与校验范围。
2. 是否需要同步回收站（删除远端前移动）——建议 P1。
3. 双向"两个都保留"的 SAF rename 失败回退细节。
4. 重命名检测（P1）是否用 size+mtime 启发式。
