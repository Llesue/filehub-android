# FileHub Android 客户端 — 实施计划

> 状态：草案 v0.1（2026-11-22），待确认
> 后端契约：服务端仓库的 `docs/openapi.yaml` + FileHub 0.2.x 实际实现
> 仓库：`filehub-android`（当前为空目录）

---

## 0. 结论摘要

- **原生 Android**：Kotlin + Jetpack Compose（Material 3），`applicationId = filehub.android`（按"不用域名"确认），`minSdk 26` / `compileSdk 37.1` / `targetSdk 36`。
- **只做移动端最值钱的四件事**：可靠浏览/预览、可恢复的后台传输、自动同步（相册/目录）、分享（含公开分享页）。
- **鉴权不采用 cookie + CSRF**：登录一次后自动创建本机专用 API Token（`scope=write`），之后全部请求走 `Authorization: Bearer`。原因：FileHub session 默认 TTL 只有 24h（示例配置 168h），移动端每周重登不可接受；而 token 无 CSRF 负担、可在服务端按设备撤销。
- **不引入 Retrofit / Hilt / Firebase**：OkHttp + kotlinx.serialization + 手写轻量 DI，减少构建复杂度与不透明行为；传输、Range、SSE、chunk PUT 都需要直接控制 OkHttp。
- **构建在远程 Linux 构建机 + GitHub Actions**：本机无 JDK、无 KVM（不能跑模拟器），磁盘空间有限。构建机需 JDK 21 和 Android SDK；真机为 Android 16 / API 36，无线 adb 验收。
- **分 7 个里程碑，约 5–6.5 周（AI 辅助、含真机调试）**：M0 工具链与骨架 → M1 服务器/浏览 → M2 文件操作/搜索/预览 → M3 传输引擎 → M4 相册同步 → M5 目录同步（上传/下载/双向）→ M6 分享/设置/适配与发布。
- **必须同步补契约**：现有 `openapi.yaml` 缺不少客户端要用的字段和端点（见 §2.4），实施时在 filehub 仓库补文档并用契约测试兜底。

---

## 1. 产品范围

### 1.1 定位

FileHub 的自托管 NAS 移动入口：在手机上完成"看、传、管、分享"，不复制 Web 端的桌面能力（终端、批量运维、复杂编辑器）。

目标用户场景：

1. 手机照片/视频随时上传到 NAS 指定目录；
2. 在外查看、预览、下载家里的文件，弱网可断点续传；
3. 把文件/目录生成带密码和有效期的分享链接，直接发微信；
4. 快速搜索文件名并定位到所在目录；
5. 同时管理多个 FileHub 实例（家里 NAS、公司服务器）。

### 1.2 v1（P0）范围

| 模块 | 内容 |
|---|---|
| 服务器与登录 | 服务器 tab：多实例列表、在线状态、切换、添加/编辑/移除；URL 规范化与连通性检测；品牌名/图标；账号密码登录；自动创建/撤销设备 Token；Token 失效重登；HTTP 明文风险提示 |
| 位置与浏览 | 浏览 tab：当前服务器的本地 root + 远程目录卡片行；点击进入文件浏览；面包屑；**单列 tile 卡片行默认（一行一个文件）** + 紧凑列表切换；按名称/大小/修改时间排序；游标分页（200/页）；下拉刷新；隐藏文件开关（同步服务端偏好）；只读目录标识 |
| 缩略图 | 图片缩略图（异步 pending 占位 + 有限重试）；无缩略图时按类型显示彩色图标；仅 Wi-Fi 加载缩略图选项 |
| 文件操作 | 新建文件夹/空文件；重命名；删除（两段式确认，含 confirm_code）；复制/移动（目录选择器 + 后台任务）；ZIP 打包/解压（服务端任务）；属性面板（含图片尺寸/EXIF、目录大小异步统计） |
| 预览 | 图片（缩放/滑动切换、超大图预检）；视频/音频（Media3，Range 流式）；PDF（本地缓存 + PdfRenderer）；文本查看（编辑 P1）；不支持的类型走"用其他应用打开/下载" |
| 搜索 | 全库 `/search/all`（≥3 字符，2 个中文可用）+ 当前 root `/search`；筛选：类型/大小/时间；搜索历史；结果直接预览/进入，行尾菜单"在文件夹中显示" |
| 传输 | 分片可恢复上传（多文件、进度、暂停/取消/重试、进程被杀后恢复）；后台通知；单文件下载（Range 断点续传）；多选 ZIP 下载；保存到系统 Downloads（API 29+ MediaStore，26–28 走 SAF）；仅 Wi-Fi 开关 |
| 相册同步 | 选择相册（可多选/全选）；默认含视频；目标服务器 + root + 目录；仅 Wi-Fi/仅充电；周期同步；去重、断点恢复、保留两者；按相册分目录；单向，不传播删除；上传时携带拍摄时间（依赖 FileHub 0.2.1 `mtime`） |
| 目录同步 | SAF 选择本地文件夹；方向：手机→服务器 / 服务器→手机 / 双向；目标服务器 + root + 目录；冲突不覆盖、保留双方并列出；删除同步可选（默认关）；周期/立即；断点恢复；改名按"删+增"处理（重命名检测 P1） |
| 分享 | 我的分享列表（分页）；从文件/目录创建分享；多选文件集合分享（服务端限制：集合内不能含目录）；密码、有效期、只读；复制链接/二维码；修改/撤销/轮换链接；公开分享查看器（免登录、密码解锁、目录浏览、下载、可写分享上传） |
| 我的/设置 | 账号信息/配额/用量；同步管理；分享管理；传输记录；修改密码；设备 Token 管理；外观；语言；上传冲突策略；关于/诊断日志导出 |
| 基础质量 | 中英双语；深色模式；tile 卡片行/紧凑列表双视图；手机 + 平板基础适配（Rail，单栏）；TalkBack；空态/错误态/骨架屏；无网提示；429 退避；401 统一重登 |

### 1.3 后续（P1/P2）

- **P1**：双向同步的重命名检测与逐文件冲突批量解决；同步回收站/历史版本入口；SSE 实时事件（`/events`、分享 `/events`、`thumbnail.ready`）替代部分轮询；管理端（用户/配额/root/审计/服务端路径/品牌）；目录索引触发与状态；分享访问统计；文本 Markdown 预览；Android 分享目标（从相册/文件管理器直接上传）；一次性目录上传（SAF tree）；生物识别应用锁；自签名证书 TOFU（确认指纹后固定）。
- **P2**：文件收藏/最近访问；离线目录缓存；SAF DocumentsProvider（把 FileHub 挂成系统文件提供器）；多窗口拖放上传；服务端音频转码播放；应用内更新检查；平板拖拽移动；常驻双栏。

### 1.4 明确不做

- 终端/`/cmd`/WebSocket 命令面板；
- 远程联邦挂载的创建与管理（已挂载的 remote root 正常浏览/下载/上传，按服务端能力）；
- 自定义 CSS、品牌图标上传、服务端重启、原始 settings JSON 编辑；
- 代码编辑器（语法高亮、多光标）、Office 文档编辑；
- 系统级全盘/后台自动扫描同步——只同步用户显式选择的相册与文件夹；
- 离线写入队列（服务端不可达时先本地排队）——v1 只做传输/同步断点，不做离线文件操作。

---

## 2. 后端契约核对（实施依据）

### 2.1 基础约定

- 前缀 `/api/v1`，JSON；错误统一 `{"error":{"code","message"}}`。
- 稳定错误码：`BAD_REQUEST`、`INVALID_PATH`、`TOO_LARGE`、`QUOTA_EXCEEDED`、`ENTRY_LIMIT_EXCEEDED`、`UNAUTHORIZED`、`FORBIDDEN`、`PERMISSION_DENIED`、`CONFLICT`、`CONFIRM_REQUIRED`、`PRECONDITION_FAILED`、`UNSUPPORTED_MEDIA`、`RATE_LIMITED`、`QUEUE_FULL`、`NOT_FOUND`、`SHARE_REVOKED`、`SHARE_EXPIRED` 等。
- 所有文件访问都是 `root`（int64）+ 相对 `path`；客户端禁止拼本地绝对路径。
- 时间戳是 Unix 秒；大小是字节。

### 2.2 鉴权结论

| 方式 | 现状 | 客户端取舍 |
|---|---|---|
| Session cookie | `filehub_session`，默认 24h（示例配置 168h）；写请求需 `X-CSRF-Token` | 不用 |
| API Token | `Authorization: Bearer fh_xxx`；`scope=read` 只读，`scope=write` 可直接写、无需 CSRF；可设 `expires_at`（null = 永不过期） | **采用 write token（已确认）** |

登录流程（只走一次）：

1. `GET /api/v1/setup/status`（公开）确认已初始化；
2. `GET /api/v1/branding`（公开）拉品牌名/图标；
3. `POST /api/v1/login` → 拿 session + `csrf_token` + user；
4. `POST /api/v1/auth/tokens`（带 CSRF），名称如 `FileHub Android · Pixel 8`，`scope=write`，`expires_at = now + 365d`；
5. `POST /api/v1/logout` 丢弃 session；
6. 之后统一 Bearer；`GET /api/v1/me` 验证（返回 `auth:"token"`、`csrf_token:null`）；
7. 退出登录 → `DELETE /api/v1/auth/tokens/{id}` + 清本地密钥。

> 安全取舍：写 token 等价于长期会话，但可按设备撤销、不存密码、不受 CSRF 影响。本地用 Android Keystore 加密；P1 加生物识别锁。Token 名称带机型，便于用户在 Web 端识别和撤销。

### 2.3 功能 → 端点映射

| 功能 | 端点 | 关键点 |
|---|---|---|
| 初始化检测 | `GET /setup/status` | 公开；未初始化时引导去 Web 完成 setup |
| 品牌 | `GET /branding`、`GET /branding/icon` | 公开；名称/图标版本 |
| 登录/当前用户 | `POST /login`、`GET /me` | `me` 同时用于 token 有效性校验 |
| 设备 Token | `GET/POST /auth/tokens`、`DELETE /auth/tokens/{id}` | 创建需 session+CSRF；删除可用 token 自身 |
| 根 | `GET /roots` | `{id, alias, read_only, kind, mount_id}` |
| 目录列表 | `GET /list?root&path&sort&order&limit&cursor&sizes&focus` | 默认 200/页，上限 1000；`cursor` 是 offset 字符串；返回 `read_only/read_only_reason/next_cursor/stale` |
| 属性 | `GET /properties`、`GET /fs/dir-size`、`GET /fs/image-info` | 目录大小是异步状态机（ready/queued/running/error） |
| 缩略图 | `GET /thumb?root&path&w&fit&wait`、`GET /thumb/status`、`POST /thumb/retry` | `wait=0` 未命中返回 200 占位 + `X-FileHub-Thumb-State: pending` |
| 下载 | `GET /download?root&path&inline` | Range/ETag/304/416；预览用 `inline=true` |
| ZIP 下载 | `POST /download/zip` | JSON `{root,paths}`，流式返回；默认上限 2GiB/1000 文件 |
| 上传 | `POST /uploads`；`GET/DELETE /uploads/{id}`；`PUT /uploads/{id}/chunks/{index}`；`POST /uploads/{id}/complete` | 默认 chunk 8MiB（1–64MiB），单文件默认上限 10GiB，会话 TTL 24h，每用户并发 2；**0.2.1 起 `POST /uploads` 接受可选 `mtime`（Unix 秒），完成时写回文件** |
| 文件操作 | `POST /fs/mkdir`、`/fs/touch`、`/fs/rename`、`/fs/copy`、`/fs/move` | 写 token 直接可用；大目录建议走后台任务 |
| 删除 | `POST /fs/delete/preview` → `POST /fs/delete` | 预览返回 `token/expires_at/confirm_code/requires_code`；二次提交 |
| 后台任务 | `POST /fs/transfer-jobs`、`GET/DELETE /jobs/{id}`、`GET /jobs?since=` | 复制/移动/zip/unzip/索引用统一 JobInfo 轮询 |
| 压缩/解压 | `POST /archive/zip`、`POST /archive/unzip` | 返回 job id；用 `/jobs/{id}` 跟踪 |
| 搜索 | `GET /search`、`GET /search/all` | `/search/all` 游标分页、≥3 字符（2 个中文例外）；`/search` 单 root、limit ≤500 |
| 索引 | `POST /index`、`GET /index/status` | 搜索未建索引时给"建立索引"入口 |
| 文本 | `GET /file/probe`、`GET /file`、`PUT /file` | 保存必须 `If-Match: etag`；412 → 冲突对话框 |
| 偏好 | `GET/PATCH /me/preferences` | `show_hidden`/`show_thumbnails`/`upload_conflict` 会影响 Web 端 |
| 用量 | `GET /usage` | `used_bytes` 可能为 null（后台统计中），需轮询/重试 |
| 分享管理 | `GET/POST /shares`、`PUT/DELETE /shares/{id}`、`/shares/{id}/url`、`/shares/{id}/rotate` | 创建支持单路径或 `paths` 集合；链接形如 `/s/{token}` |
| 公开分享 | `GET /public/shares/{token}`、`POST .../unlock`、`GET .../download`、`POST .../upload(s)`、`GET .../thumb`、`POST .../zip` | 密码分享用 `X-Share-Ticket`（thumb/events 用 query）；可写目录才显示上传 |
| 事件（P1） | `GET /events`（SSE） | 20s 心跳；仅 session 鉴权，**token 不可用** → 若要 SSE，需保留 session 或走轮询 |

> SSE 只支持 session cookie，这是 token 方案的一个明确取舍：v1 用"操作后刷新 + 任务轮询"，不建立长连接。P1 若要做实时，需要额外维持一个短期 session（登录时保留 cookie）或改用轮询。

### 2.4 契约缺口（实施时回补 filehub 仓库）

现有 `docs/openapi.yaml` 与真实实现不一致/缺失，Android 端以实际实现为准，同时提 PR 补文档：

- `/list` 缺 `limit`、`cursor`、`sizes`，以及 `next_cursor`、`stale`、`focus_index/focus_offset/total`；
- `/shares` 创建缺集合分享 `paths`；列表缺分页参数；缺 `/shares/{id}/purge`；
- 缺 `/thumb/status`、`/thumb/retry`、`/uploads/{id}` DELETE；
- 缺 `/me/preferences`、`/usage`、`/version`、`/me/avatar`、`/auth/tokens` 响应结构；
- `/fs/delete` 缺 `confirm_code`；
- `/jobs`、`/jobs/{id}` 响应结构未定义；
- 公开分享的 upload/thumb/zip/events 子路由未完整定义；
- `/download/zip` 未注明需要 CSRF（session）或 write token。

交付物包含 `docs/api-notes.md`（本仓库）+ filehub 仓库的 OpenAPI 补丁。

### 2.5 `mtime` 依赖（FileHub 0.2.1）

同步必须保留原始修改时间，否则相册时间线会变成"上传时间"。已确认把该能力排进 FileHub 0.2.1：

- `POST /api/v1/uploads` 增加可选 `mtime`（Unix 秒，int64）；服务端在 `complete` 原子落盘后 `utimensat` 写回；校验范围（1970-01-01 ～ now+1 天）。
- `complete` 仍不需要 body；值随上传会话保存。
- 公开分享上传是否支持 mtime 不在 v1 范围（相册/目录同步都走已登录上传）。
- OpenAPI、迁移/测试、`/version` 能力标识由 FileHub 0.2.1 落地；客户端按服务端版本做能力判断：
  - `>= 0.2.1`：创建上传会话时携带 `mtime`；
  - `< 0.2.1`：不发送，同步设置页提示"服务器版本过旧，拍摄时间可能不准，建议升级"；不阻塞同步。
- 客户端不能假设字段一定被接受；若服务端返回 400/未知字段错误，降级重试一次并记录。

---

## 3. 技术方案

### 3.1 栈与版本（M0 锁定到 version catalog）

| 层 | 选型 | 说明 |
|---|---|---|
| 语言/UI | Kotlin 2.4.21 + Compose BOM `2026.11.01` + Material 3 | 使用 AGP 9.5 内置 Kotlin；Compose 编译器插件版本与 Kotlin 对齐 |
| 构建 | AGP 9.5.1 + Gradle 9.8.2 + JDK 21 | `compileSdk 37.1`（`compileSdk { version = release(37) { minorApiLevel = 1 } }`）、`targetSdk 36`、`minSdk 26` |
| 网络 | OkHttp 5.x + kotlinx.serialization | 手写类型化客户端；流式下载/上传、Range、SSE 都可控 |
| 并发 | Coroutines + Flow | ViewModel `StateFlow`，传输用 `CoroutineWorker` |
| 本地存储 | DataStore（偏好/profile）+ Room（传输队列，KSP） | Token 单独走 Keystore AES-GCM；Room entity 不用 `@Parcelize`（AGP 9 内置 Kotlin + KSP 已知冲突） |
| 后台任务 | WorkManager | 上传/下载/ZIP/同步的持久队列、约束、前台通知；周期性同步最小 15 分钟 |
| 本地媒体/目录 | MediaStore + SAF（`DocumentFile`） | 相册同步读 MediaStore；目录同步用持久化 tree URI；不申请全盘存储权限 |
| 同步调度 | WorkManager + Room + 前台服务 | 相册/目录增量扫描、去重、断点、冲突状态；单次长任务用 `dataSync` |
| 图片 | Coil 3（`coil-network-okhttp`） | 自定义 OkHttp fetcher，处理 401/`pending` 占位 |
| 媒体 | Media3 ExoPlayer + `media3-datasource-okhttp` | 带 Bearer 头的 Range 流；音频/视频共用 |
| PDF | `android.graphics.pdf.PdfRenderer` | 先下到 cache 再渲染；加密/损坏回退外部打开 |
| 二维码 | ZXing core | 只生成，不内置扫码（用系统相机） |
| 导航 | Navigation Compose（type-safe routes） | 单 Activity |
| 自适应 | Material3 adaptive / WindowSizeClass | 手机 NavigationBar，平板 NavigationRail + 双栏 |
| 测试 | JUnit4、MockWebServer、Robolectric、Roborazzi、Turbine、Compose UI Test、WorkManager Test | 见 §6 |

不选：Flutter（与 FileHub 视觉/平台能力贴合度低，团队栈是 Kotlin/Rust）、Retrofit（收益小）、Hilt（构建成本高，手写 DI 足够）、Firebase/Crashlytics（自托管产品不引入第三方遥测）。

### 3.2 仓库与包结构

单模块 `:app` 起步，按包边界分层；构建时间明显变差再拆 `core:*` / `feature:*`。

```
app/src/main/java/filehub/android/
  FileHubApp.kt              # Application + AppContainer（手写 DI）
  MainActivity.kt
  navigation/                # 路由、深层链接、Scaffold
  core/
    model/                   # Root, Entry, Share, Transfer, User...
    net/                     # ApiClient, DTO, AuthInterceptor, ApiError
    store/                   # ProfileStore, SettingsStore, TokenVault, TransferDao
    transfer/                # UploadEngine, DownloadEngine, Workers, Notifier
    media/                   # Coil fetcher, Media3 factories, PDF cache
    ui/                      # theme, 通用组件, 格式化, 文件类型
  feature/
    servers/ auth/ browse/ preview/ search/ transfers/ sync/ shares/ settings/ admin/
  data/                      # Repository：Files/Shares/Transfers/Search/Sync/Admin/Session
```

### 3.3 网络层

- 每个服务器 profile 一个 `FileHubApi` 实例；共享 OkHttp 连接池/Dispatcher，独立 `AuthInterceptor`。
- Base URL 规范化：补 scheme、去尾斜杠、保留可选子路径；所有 query 用 `HttpUrl.Builder` 编码。
- Header：`Authorization: Bearer`、`Accept: application/json`、`User-Agent: FileHub-Android/<ver> (...)`；上传/下载不带 JSON Content-Type。
- 超时：connect 15s；普通请求 read 30s；流式传输 read 0；写 60s。
- 重试：GET 幂等请求 2 次指数退避（避免 401/403/404/412）；chunk PUT 按 index 幂等，可重试；不自动重试 POST（防重复删除/分享）。
- 错误映射：HTTP + `error.code` → 领域错误；`401` 清 token → 登录；`429` 退避并提示；`507/413` 给配额/大小文案；`403` 区分只读/权限；`404` 刷新目录。
- Token 只存在内存 + 加密存储；日志拦截器仅在 debug 启用且脱敏 `Authorization`/`Cookie`。

### 3.4 本地数据

- `ServerProfile`：id、baseUrl、显示名、username、userId、tokenId、加密 token、品牌图标版本、lastRootId/lastPath、lastUsedAt。
- `SettingsStore`：主题、语言、仅 Wi-Fi、缩略图、并发数、自动刷新。
- `TransferEntity`（Room）：kind、serverId、rootId、path、name、size、bytesDone、uploadId、chunkSize、etag、status、error、localUri、createdAt/updatedAt；用于进程死亡后恢复。
- `SyncPairEntity`（Room）：serverId、rootId、remotePath、localKind（media/tree）、localRef、mode（upload/download/two_way）、options JSON、schedule、enabled、lastRunAt/lastResult/nextRunAt。
- `SyncEntryEntity`（Room）：pairId、relativePath、localKey、localUri、remotePath、localSize/localMtime、remoteSize/remoteMtime、**baseLocalSize/baseLocalMtime、baseRemoteSize/baseRemoteMtime**（上次成功同步的基线，双向判断用）、status、lastSyncedAt、error、uploadId、conflictInfo。
- 相册同步需要 `READ_MEDIA_IMAGES`/`READ_MEDIA_VIDEO`（API 33+）或旧版读权限；用户只给"部分照片"时按实际可读集合同步，并在 UI 明示。目录同步只用 SAF 持久化权限。
- TokenVault：Keystore 生成 AES-256-GCM 密钥，IV 随机前置；解密失败视为凭据损坏，清 token 要求重登；关闭 auto backup / 排除敏感文件。
- 不使用 `EncryptedSharedPreferences`（已进入维护/弃用路径），自己封装 30 行 Keystore 工具更好控制。

### 3.5 传输引擎

**上传**

1. 选择文件（`OpenMultipleDocuments` / Photo Picker；目录上传 P1 用 `OpenDocumentTree`）。
2. 读元数据；大小未知时先落 cache 计算；超服务端上限直接拒绝。
3. `POST /uploads` 创建会话（服务端返回实际 `chunk_size`/`chunks`），写 TransferEntity。
4. WorkManager 唯一任务 `upload-<uploadId>`，`CoroutineWorker` + 前台通知（`dataSync`）。
5. 顺序 PUT chunk，流式 `RequestBody`（`skip` + 限定长度，不整块读内存）；每 chunk 最多 3 次指数退避。
6. 进程重启/网络恢复：`GET /uploads/{id}` 拿 `received_chunks`，只补缺失块；会话过期（404/GONE）则重建并从头传。
7. `POST /complete`；服务端按用户偏好处理重名（rename/overwrite/skip），返回最终路径，UI 展示"已重命名"。
8. 取消：`DELETE /uploads/{id}` + 取消 worker + 删临时状态。

并发：默认同时 2 个上传、每个 1 chunk 串行；可配置；队列按添加顺序。通知节流 500ms。

**下载**

1. 单文件：GET + `Range: bytes=<done>-`，带 `If-Range: <etag>`；206 续传，200 重下，416 重新探测。
2. 写 `<name>.part`（app cache），完成后原子移动并写入 MediaStore Downloads（API 29+）或 SAF 导出（26–28）。
3. 多选 ZIP：`POST /download/zip` 流式写临时文件；先本地预检大小/数量，413 直接提示。
4. 取消/暂停/重试；进程死亡后按 `.part` + TransferEntity 恢复。
5. Android 15+ 前台 `dataSync` 有 6h/24h 限制：超长任务被系统停止后保留断点，回前台可继续。

**同步**

详细算法与状态机见 `docs/sync-spec.md`；这里是实现约束。

1. **模式**：相册同步固定"手机→服务器"；目录同步支持"手机→服务器 / 服务器→手机 / 双向"三种。`SyncPair` 存 mode、过滤、调度、删除开关。
2. **基线**：`SyncEntry` 同时存本地/远端上次同步的 size+mtime（baseLocal/baseRemote）。判断变更必须做三方比较（base vs local vs remote），不能只看两端当前值；否则无法区分"谁改了"。
3. **相册扫描**：MediaStore 按 bucket 查询图片/视频；用 `(DISPLAY_NAME, SIZE, DATE_TAKEN, RELATIVE_PATH)` 组合签名去重，不能只依赖 `_ID`。只同步用户授权范围；部分授权必须明示。
4. **目录扫描**：SAF tree 递归枚举，存相对路径 + size + mtime；忽略隐藏文件/临时文件；首次扫描显示计数与进度。
5. **远端状态**：目标目录用 `/list` 分页 BFS，缓存到 Room；首次全量、后续只列变更目录；限制并发，避免打爆服务端。
6. **上传**：复用分片引擎，`SyncEntry.uploadId` 持久化；目标路径 = `remotePath + 相对路径`；上传前对比同名文件，相同则跳过；冲突默认不覆盖。完成后以服务端返回的最终路径回写。
7. **下载**：Range 下载到临时文件，校验大小后写入 SAF/本地；保留远端 mtime（SAF 无法设 mtime 时记录实际值作为基线）。
8. **双向动作**：
   - 仅本地新增/修改 → 上传；
   - 仅远端新增/修改 → 下载；
   - 双方都改且内容不同 → **冲突**，不覆盖，进入冲突列表；
   - 双方相同 → 标记已同步；
   - 一方删除、另一方未改 → 按"删除同步"开关处理；关闭时保留另一端文件、不自动恢复/删除；
   - 一方删除、另一方有修改 → 冲突；
   - 改名 = 删 + 增（v1 不做重命名检测，P1）。
9. **冲突解决**：逐文件操作：用本地覆盖服务器 / 用服务器覆盖本地 / 两个都保留（带后缀）/ 跳过。任何自动路径都不静默覆盖用户数据。
10. **mtime**：FileHub 0.2.1 起创建上传会话携带 `mtime`，服务端落盘后写回；旧服务端降级并提示"拍摄时间可能不准"。相册时间线以 mtime 为准。
11. **调度**：WorkManager 周期（最小 15 分钟）+ 立即同步；每个批次最多 5 分钟或 100 个文件，checkpoint 后续跑；长任务前台通知，FGS 启动失败降级为普通 worker；Android 15/16 与 HyperOS 限制见 `docs/sync-spec.md §10`。
12. **失败**：单文件失败不阻塞整批；401 暂停并重登；403/只读暂停并标红；413/配额暂停整批；失败/冲突可查看、重试、忽略。
13. **P1**：重命名检测、同步回收站、逐文件冲突批量解决。

### 3.6 缩略图与预览

- 列表用 `/thumb?w=<按密度>&wait=0`；若响应头 `X-FileHub-Thumb-State: pending`，Coil 视为失败不缓存，1s/3s 两次延迟重试；仍失败显示类型图标。
- 灯箱用 `wait=1` + `edge=2048`，并先 `GET /fs/image-info` 判断 `previewable`（超大图/超像素不打开，避免 OOM）。
- 图片：Coil 自动降采样；支持双指缩放、双击、左右滑动（当前页已加载条目）。
- 视频/音频：Media3 + 自定义 OkHttp DataSource（Bearer、Range、`inline=true`）；播放失败回退外部应用。
- PDF：下载到 cache，`PdfRenderer` 逐页渲染，缓存页 bitmap；加密/损坏提示。
- 文本：v1 只读 `/file`；编辑（`/file/probe` + `If-Match`）放 P1，412 冲突时提示重新加载/覆盖。
- 其他类型：属性 + "用其他应用打开"（FileProvider）+ 下载。

### 3.7 安全

- 只信任系统 CA；TLS 错误给可操作提示（换地址/装 CA），**不提供"忽略证书错误"开关**；自签名 TOFU 放 P1 并需指纹确认。
- 允许用户输入 `http://`（LAN 常见），但登录页显示明文风险条；不全局信任明文之外的降级。
- Token 加密、日志脱敏、`allowBackup=false`；分享密码不落盘；分享 ticket 仅内存。
- 密码只用于换取 token，不保存；输入框禁用自动纠正，支持密码管理器。
- 导出组件最小化：仅 launcher、分享接收（P1）、FileProvider；deep link 入口严格校验参数。
- 发布版 `minifyEnabled + shrinkResources`，保留 kotlinx.serialization 规则。

---

## 4. UI / UX

> 界面与交互的详细提案见 `docs/ui-spec.md`（讨论稿）；本节只保留约束性结论，二者冲突时先更新提案再改实现。

### 4.1 信息架构

底部导航（手机）/ 侧边栏（平板）：

1. **服务器**（首次无服务器时默认）— 多个 FileHub 实例列表、在线状态、切换、添加/编辑/移除；
2. **浏览** — 当前服务器的本地 root + 远程目录卡片行；点击进入文件浏览；搜索、上传、分享、传输入口；
3. **我的** — 账号/配额、同步管理、分享管理、传输记录、外观/语言、设备授权、关于。

顶层之上是登录/公开分享页（独立栈，隐藏底部导航）。启动默认：无服务器进"服务器"，已有当前服务器进"浏览"上次位置。全局传输/同步进度用底部导航上方的进度胶囊呈现，点击进入传输中心。

### 4.2 关键页面

| 页面 | 要点 |
|---|---|
| 服务器 | 卡片/列表：品牌图标、实例名、URL、账号、在线状态、当前选中标记；点击切换并进入浏览；行尾菜单测试/编辑/重新登录/移除；底部"添加服务器" |
| 登录 | 服务器 URL → 品牌预览 → 用户名/密码；错误内联；HTTP 风险提示；"在 Web 完成初始化"引导 |
| 浏览（位置） | 顶栏：品牌/服务器切换、搜索、更多；内容：本地 root + 远程目录单列卡片行，带只读/远程徽标；空态引导 |
| 文件浏览 | 顶栏：返回、搜索 pill、排序 chip、上传按钮、更多；面包屑；**单列 tile 卡片行默认（一行一个文件）**，可切紧凑列表；长按多选；底部选择栏；同步中显示进度条 |
| 选择栏 | 下载、移动、复制、分享、删除、更多（ZIP、属性） |
| 搜索 | 全屏、300ms 防抖、历史、范围（当前 root/全部）、筛选 sheet；结果带 root 别名与父路径；点击直接预览/进入，行尾"在文件夹中显示" |
| 预览 | 全屏 pager；图片缩放；视频播放器控件；音频迷你播放器；PDF 页码；文本只读（编辑 P1） |
| 同步 | 相册同步/目录同步卡片：开关、上次同步、待传数量、进度、错误；新建向导；冲突/失败列表；立即同步 |
| 传输中心 | 进行中/已完成/失败；上传、下载、服务器任务、同步任务统一列表；暂停/继续/取消/重试 |
| 分享 | 我的分享列表、创建分享、公开分享页（免登录）；入口在"我的"和文件选择栏 |
| 我的 | 账号/配额；同步管理；分享管理；传输记录；修改密码；设备 Token；外观/语言；上传冲突策略；关于/诊断 |
| 属性 | 名称/路径/大小/时间/MIME/权限；图片尺寸与 EXIF；目录大小异步刷新；符号链接状态 |

### 4.3 视觉规范

沿用 FileHub Web 的 "Drive Shell, FileHub Detail"（`PLAN.md §9.13`），不复制 Google 品牌资产：

| Token | Light | Dark |
|---|---|---|
| bg | `#F7F8FA` | `#202124` |
| surface | `#FFFFFF` | `#292A2D` |
| surface-2 | `#F1F3F4` | `#35363A` |
| text | `#202124` | `#E8EAED` |
| text-2 | `#5F6368` | `#9AA0A6` |
| border | `#E3E5E8` | `#3C4043` |
| accent | `#1A73E8` | `#8AB4F8` |
| accent-soft | `#E8F0FE` | `#1E3A5F` |
| danger | `#D93025` | `#F28B82` |

- 文件类型色只用于 20–24dp 图标；文件夹统一中性灰；缩略图是唯一彩色主体。
- **tile 卡片行**：白色 surface、1px `border`、12dp 圆角、12dp 内边距、卡片间距 12dp；图标容器 48×48dp、10dp 圆角、`surface-2` 底色；文件名 15sp/600 单行省略；元信息 12sp `text-2`；行尾 40–48dp `⋯`。
- 字体用系统 sans（中文系统字体），数字启用 tabular；正文 14sp、元信息 12sp；紧凑列表行高 56dp。
- 圆角 8dp，搜索框胶囊 20dp；静态卡片只用 1px 边框；浮层阴影克制。
- 动效 120/180ms，只动 opacity/transform；尊重系统"移除动画"。
- 不启用 Material You 动态取色，保证与 Web 品牌一致；深色模式独立映射。

### 4.4 自适应与输入

- `<600dp`：底部导航、单栏、单列 tile 卡片行、全屏预览、底部选择栏。
- `600–839dp`：NavigationRail、单栏、单列卡片行（内容最大宽度 840dp 居中）。
- `≥840dp`：NavigationRail、单列卡片行；可选右侧滑出预览；常驻双栏 P1；拖拽移动 P2。
- 横屏视频/图片全屏沉浸；折叠屏按 WindowSizeClass 重排。
- 所有触控目标 ≥48dp；长按多选之外提供每项"更多"菜单，避免只靠手势。

### 4.5 状态、文案与可访问性

- 每个列表/页面都有：骨架、空态、错误（含重试）、无权限、只读；禁止白屏。
- 错误文案按 code 映射，保留服务端 message 作为可展开详情；不直接抛英文技术错误给用户。
- 破坏性操作（删除、覆盖、撤销分享、退出登录清 token）二次确认；删除按服务端 `requires_code` 要求输入确认码。
- 中英双语，跟随系统，可在设置覆盖；日期/大小本地化；服务端返回的路径不翻译。
- TalkBack：行内容合并语义、进度百分比播报、图标有 contentDescription；对比度 ≥4.5:1。

---

## 5. 关键流程

### 5.1 首次使用

1. 启动无 profile → 服务器 tab 空态 → 输入服务器地址；
2. 探测 `setup/status`：失败给网络/DNS/证书的具体原因；未初始化提示"先在浏览器打开 <url> 完成初始化"；
3. 拉品牌 → 登录 → 自动创建设备 token → `me` 校验 → 拉 roots；
4. 成功后被选中并切到"浏览"；位置页展示本地 root + 远程目录；记住上次位置；
5. 若 Token 失效：保留 profile 与服务器地址，弹重登，不丢本地传输/同步记录（标记等待授权）。

### 5.2 上传（含进程被杀）

1. 文件浏览顶栏上传按钮 → 照片和视频 / 文件 / 拍照上传 / 新建文件夹；目录一次性上传 P1；
2. 校验大小/配额/只读 → 创建会话 → 入队；
3. 前台通知显示总进度；应用内"我的 → 传输"与进度胶囊显示每文件进度；
4. 杀进程后重启：读 Room 中未完成项 → `GET /uploads/{id}` → 补缺失 chunk → 完成；
5. 网络切换/弱网：chunk 重试；超过阈值暂停并提示"等待网络"；
6. 完成：刷新当前目录并高亮新文件；重名按服务端策略处理并提示最终名称。

### 5.3 下载

1. 单文件/多选 → 选择"下载"或"打包下载"；
2. 预检大小/数量（ZIP 上限）；
3. 后台任务 + 通知；可暂停/继续/取消；
4. 完成：通知"打开"（FileProvider）/"保存到…"（SAF）；API 29+ 默认写入系统 Downloads/FileHub；
5. 失败：区分网络/空间不足/权限/服务端 413，给对应操作。

### 5.4 分享

1. 文件/目录/多选 → 分享；
2. 设置只读、密码、有效期 → 创建；
3. 展示绝对链接 + 二维码；复制到剪贴板；
4. 公开链接打开方式：
   - App 内"打开分享链接"粘贴；
   - 系统分享文本到本 App（P1）；
   - 自定义 scheme `filehub://share?url=...`（P1）；
5. 免登录查看：解析 host 对应已有 profile 则复用 baseUrl；否则匿名模式只调公开端点；
6. 密码分享：解锁拿 ticket（内存保存，过期重输）；目录浏览分页；可写目录显示上传。

### 5.5 删除

1. 选中 → 删除 → `POST /fs/delete/preview`；
2. 展示条目数/总大小/不可恢复警告；
3. `requires_code` 为 true 时要求输入 `confirm_code`；
4. `POST /fs/delete`；409 → 重新预览；204 → 刷新并清选择。

### 5.6 搜索

1. 输入 ≥2 字符（服务端规则：≥3 或 2 个中文）→ 防抖 300ms；
2. 默认 `/search/all` 跨 root；可切当前 root；
3. 筛选：类型（image/video/audio/text/pdf/dir/file）、大小、修改时间；
4. 结果分页（`next_cursor`）；空结果显示"未建索引 → 建立索引"（`POST /index` + 轮询状态）；
5. 点击结果：文件直接预览、目录直接进入；行尾菜单"在文件夹中显示"跳到对应 root 的父目录并用 `focus=<name>` 定位分页与高亮。

### 5.7 相册同步

1. 我的 → 同步 → 相册同步 → 新建；
2. 选择服务器 → 目标 root/目录 → 相册（可多选；默认全选）→ 选项（含视频默认开、仅 Wi-Fi、仅充电、按相册分目录、频率）；
3. 申请媒体权限；部分授权时明示"只同步已授权照片"；
4. 首次预览：待传数量/总大小/预计时长 → 开始；
5. 前台任务：扫描 MediaStore → 去重 → 逐文件上传（携带 mtime，可断点）→ 回写 SyncEntry；
6. 进度在通知 + 我的同步卡片；暂停/取消/重试；
7. 后续周期任务按增量扫描；失败文件保留错误与重试入口；
8. 单向；不传播删除（本地删除后服务器文件保留）。

### 5.8 目录同步

1. 我的 → 同步 → 目录同步 → 新建；
2. SAF 选择本地文件夹 → 选择服务器/root/目标目录 → 方向（上传/下载/双向）→ 选项（忽略隐藏文件、删除同步默认关、仅 Wi-Fi/充电、频率）；
3. 首次扫描：本地文件数/总大小 + 远端盘点；双向且两端都非空时先给"潜在冲突"预览；
4. 预览变更：新增/更新/删除/冲突 → 开始；
5. 执行：上传/下载复用传输引擎；双向按三方基线判断；冲突不覆盖，进入列表；
6. 周期任务增量扫描；失败/冲突列表可查看、重试、逐文件解决；
7. 删除同步关闭时，一端删除不影响另一端；开启时按方向传播，遇到"另一端已修改"转为冲突。

---

## 6. 测试与验收

### 6.1 测试分层

| 层 | 工具 | 覆盖 |
|---|---|---|
| 单元 | JUnit4 + kotlin.test | URL 规范化、路径拼接/编码、chunk 计算与恢复、Range 续传、文件类型识别、错误映射、格式化、同步签名/去重/冲突命名、三向动作矩阵/删除开关 |
| 网络契约 | MockWebServer | 登录→建 token→me；list 分页；上传 3 块中断后恢复；chunk 409/429；下载 206/416/ETag 变化；删除 confirm_code；分享解锁/上传；同步远端盘点/增量 |
| 真实契约 | JVM 集成测试（env `FILEHUB_TEST_URL`） | 对本机/演示 FileHub 跑完整读写链路 + 同步目标目录；默认跳过，CI 可选 |
| UI | Robolectric + Roborazzi | 服务器列表、位置卡片行、文件卡片行/紧凑列表、选择栏、同步卡片/向导、分享卡、深色/中英、空态/错误态截图 |
| 设备 | Compose UI Test + 手工 | 真机安装、前后台传输、进程死亡恢复、相册/目录同步、旋转/分屏、TalkBack |
| 静态 | Android Lint（`abortOnError`）、编译警告 | 无新增 warning；禁止类型压制/空 catch |

### 6.2 测试矩阵

- 真机：Android 16 / API 36 无线 adb；低版本（API 26–28）暂无真机，用 Robolectric 覆盖，发布前借旧机或云真机做一次冒烟。
- 系统：API 26（最低）、29（Scoped Storage 分界）、33（通知/媒体权限）、35/36（前台服务与 edge-to-edge）。
- 网络：Wi-Fi、4G/5G、断网重连、高延迟、代理、HTTP、HTTPS、自签名（预期失败提示）。
- 服务器：FileHub 0.2.x 最新、缺少部分能力的旧版本、只读 root、配额满、未建索引、公开分享（密码/过期/撤销）。
- 文件：空文件、0 字节、中文/emoji 名、超长名、10GB 大文件、1000+ 条目目录、HEIC（无服务端缩略图）、超大图片、损坏视频/PDF、非 UTF-8 名（服务端拒绝路径）。
- 同步：5000 张照片首次同步、增量新增/修改、部分媒体授权、相册多选/视频默认；SAF 文件夹改名/移动/删除；目录同步上传/下载/双向三模式；双向双方修改冲突、单方删除+另一方修改、删除同步开关；同步中杀进程/重启、断网/切网、服务器重名、配额满、HyperOS 电池限制。
- 生命周期：上传中杀进程/重启、后台 30 分钟、旋转、分屏、深色切换、语言切换。

### 6.3 验收标准（v1）

- 从零添加服务器、登录、浏览、上传、下载、分享全链路可用；
- 相册同步可选择相册/目标目录，默认含视频，首次 5000 张照片可断点完成；新增照片增量同步；拍摄时间保留（FileHub 0.2.1）；杀进程/重启后继续；
- 目录同步可选择 SAF 文件夹，上传/下载/双向三种模式；双向能识别双方修改/删除并给出冲突；删除同步可开关；失败文件可重试；
- 上传/下载在断网、切网、杀进程后可恢复（至少上传；下载保留 `.part`）；
- 1000 条目目录滚动不掉帧，缩略图内存不超预算；
- 所有写操作在只读 root/文件系统只读时被正确阻止并给出原因；
- 401/403/404/409/413/429/507 均有可理解文案与下一步动作；
- 屏幕关闭时传输继续（前台服务通知可见）；通知权限被拒时功能仍可用；
- `lintDebug`、单元测试、截图测试全绿；Release APK 在 API 26 与 API 36 真机安装运行；
- 无 token/密码出现在日志与崩溃信息中。

### 6.4 性能预算

- 冷启动到文件列表 <1.5s（中端机，已登录）；
- 列表滚动 60fps；缩略图并发 ≤4，内存缓存 ≤15% 可用堆；
- APK（universal, minified）<15MB；
- 后台传输内存 <80MB；单 chunk 峰值内存 <20MB；
- 同步扫描 5000 张照片的元数据 <10s（不生成缩略图）；同步期间前台列表滚动不掉帧。

---

## 7. 工程与交付

### 7.1 仓库结构

```
filehub-android/
  app/
  gradle/libs.versions.toml
  scripts/
    provision-sdk.sh      # 构建机安装 cmdline-tools/platform 37.1/build-tools（幂等）
    build-remote.sh       # rsync → 构建机 Gradle 构建 → 拉回 APK
    dev-server.sh         # 起临时 FileHub 实例做契约测试
  docs/
    api-notes.md
    ui-spec.md
    sync-spec.md
    screenshots/
  .github/workflows/      # ci.yml / release.yml
  AGENTS.md               # 工程硬约束（借鉴 filehub）
  PLAN.md
  README.md
```

远端：`origin = git@github.com:Llesue/filehub-android.git`，主分支 `main`（本地仓库已初始化并接好 remote，尚未提交）。

### 7.2 构建环境

本机：无 JDK、无 KVM、磁盘紧张 → **不在本机构建**（已写入 `AGENTS.md`）。

默认构建机：

```bash
# JDK 21 已装；只装 SDK（约 3G）
mkdir -p ~/android-sdk/cmdline-tools && cd ~/android-sdk/cmdline-tools
curl -LO https://dl.google.com/android/repository/commandlinetools-linux-<latest>_latest.zip
unzip ... && mv cmdline-tools latest
yes | latest/bin/sdkmanager --licenses
latest/bin/sdkmanager "platform-tools" "platforms;android-37.1" "build-tools;36.0.0"
```

日常构建（与 filehub 的 `scripts/build-remote.sh` 同思路）：

```bash
./scripts/build-remote.sh              # 默认 debug APK
./scripts/build-remote.sh release      # release APK（需要 keystore）
./scripts/build-remote.sh check        # lint + unit test，不拉 APK
```

脚本行为：`rsync --delete` 同步源码（排除 `.git`/`app/build`/`.gradle`）→ 远端 `./gradlew` → 拉回 `app/build/outputs/apk/**` 到本地 `dist/`；远端保留 `~/.gradle` 与 `app/build` 做增量。SSH 主机默认读取本地 ssh config，可用 `FILEHUB_ANDROID_BUILD_HOST` 覆盖。

Gradle 内存：构建机内存有限，设置 `org.gradle.jvmargs=-Xmx2g`、`kotlin.daemon.jvmargs=-Xmx1g`、`org.gradle.workers.max=4`；OOM 时优先调 Hyper-V 内存或改用 GitHub Actions，不降并发硬扛。

本地命令（仅适用于 CI/有 JDK 的环境）：

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :app:testDebugUnitTest
./gradlew :app:assembleRelease   # 需要 keystore
```

### 7.3 CI / 发布

- `ci.yml`：JDK 21 + Android SDK 37.1，跑 lint、单元测试、`assembleDebug`，缓存 Gradle；PR 必过。
- `release.yml`：打 `v*` tag → 解码 keystore secrets → `assembleRelease` → 上传签名 APK + `sha256` 到 GitHub Release；版本号来自 tag（`versionName`），`versionCode` 用 run number/提交数。
- 签名 keystore 放仓库外（本机 `~/.keys/` + GitHub Secrets），不进 Git；debug 用默认签名。
- 分发：只发 GitHub Releases APK；不上架应用商店，不适配 F-Droid。
- 更新检查：P2 通过 GitHub Releases API，默认关闭。

### 7.4 文档

- `README.md`：截图、安装、连接服务器、HTTP/HTTPS 说明、FAQ（token 是什么、如何撤销）。
- `docs/api-notes.md`：实际使用的端点、字段、坑（异步缩略图、token/SSE 限制、上传冲突策略）。
- filehub 仓库：补 `docs/openapi.yaml` 缺口。

### 7.5 开发规范（借鉴 FileHub，已固化到 `AGENTS.md`）

- **最终形态交付**：边界、失败路径、并发、权限、资源上限一次考虑清楚；不做"先凑合"。
- **不换技术栈**：要换先改本计划并确认。
- **失败协议**：同一问题失败 2 次，先查官方文档/issue，再找用户，附证据。
- **重活远程**：构建、测试、截图、真机安装走远程构建机或 CI；本机只做编辑与 git。构建脚本与内存限制见 §7.2。
- **验证门禁**：`lintDebug + testDebugUnitTest + assembleDebug` 全绿才算完成；UI 改动过 Roborazzi 视觉 QA；安全敏感模块（token/上传/下载/分享/FileProvider/深链）单独 review。
- **不擅自 commit/push/tag**；未验收不发版。Conventional Commits（英文），SemVer + `CHANGELOG.md`，发布物附 SHA-256。
- **契约优先**：以 FileHub 源码实际行为为准；开放 API 缺口先记录 `docs/api-notes.md`，改 `openapi.yaml` 需确认。
- **代码红线**：禁止 `!!`、无 issue 的 `@Suppress`、空 catch、`GlobalScope`、请求路径阻塞调用；不删测试、不跳测试。
- **UI 红线**：颜色只用 tokens；文案全走 i18n；390dp/840dp × 浅/深 × zh/en 视觉 QA；触控 ≥48dp；TalkBack。
- **不做第三方遥测**；Token/密码/ticket 不进日志；明文 HTTP 必须提示风险。

---

## 8. 里程碑

| 阶段 | 交付物 | 退出标准 | 估时 |
|---|---|---|---|
| **M0 工具链/骨架** | 远程构建机 SDK、`build-remote.sh`、Gradle 工程、版本目录、主题骨架、CI、登录+token、MockWebServer 基建 | Hello Compose APK 可安装；能登录演示站；CI 绿 | 1–1.5d |
| **M1 服务器/浏览** | 服务器 tab（多实例/切换/添加）、登录、位置页（root + 远程目录卡片行）、文件 tile 卡片行/紧凑列表、面包屑、排序、分页、刷新、缩略图、只读态 | 多服务器切换；1000 条目录流畅滚动；缩略图 pending 正确重试 | 3–4d |
| **M2 操作/搜索/预览** | mkdir/touch/rename/delete/copy/move、属性、ZIP 任务、搜索+筛选、图片/视频/PDF/文本预览、拍照上传 | 全操作闭环；删除二次确认；搜索定位；预览不 OOM | 3–4d |
| **M3 传输** | 上传/下载引擎、WorkManager、通知、进度胶囊、暂停/恢复、进程死亡恢复、MediaStore/SAF | 断网/杀进程可恢复；屏幕关闭继续；大文件不 OOM | 3–4d |
| **M4 相册同步** | MediaStore 扫描、相册选择、去重、增量、目标目录、周期/立即、前台通知、权限引导、失败重试 | 5000 张照片首次同步可断点完成；新增照片增量；杀进程恢复 | 4–5d |
| **M5 目录同步** | SAF tree 扫描、远端盘点、上传/下载/双向、三方基线、冲突检测/列表/解决、删除开关、周期/立即、错误列表 | 文件夹三模式可用；双向冲突不丢数据；删除开关行为正确；失败可重试 | 5–7d |
| **M6 分享/设置/适配/发布** | 分享管理/公开分享、二维码、我的/设置、i18n、深色、平板 Rail、无障碍、Roborazzi、签名 Release | 分享全链路；中英/深浅/平板 QA；Release APK + sha256 | 4–5d |

合计约 24–33 个工作日；双向同步冲突、FileHub 0.2.1 mtime 联调、厂商后台限制可能再延长 2–4 天。

---

## 9. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| AGP 9.5 内置 Kotlin + Compose BOM 2026.11 较新 | M0 卡构建 | 先用最小 Hello Compose 验证；失败则回退 AGP 8.13 + Kotlin 2.2.20 + BOM 2026.09（compileSdk 36/37），记录决策 |
| 无模拟器（无 `/dev/kvm`），真机只有一台 Android 16 设备 | 低版本/厂商 ROM 行为差异漏测 | 低版本用 Robolectric 覆盖；发布前借旧机或云真机跑一次 API 26 冒烟；HyperOS 后台限制单独验证 |
| 同步的后台限制 | 周期任务不精确、长任务被系统停止、HyperOS 杀进程 | WorkManager + 前台服务 + 断点；引导用户关闭电池限制/允许自启动；不承诺实时 |
| 相册权限只有"部分照片" | 用户以为全量备份，实际只传了一部分 | 权限页明示；扫描前检测授权范围；提供"选择更多照片"入口 |
| 上传 mtime 依赖 FileHub 0.2.1 | 服务端未升级时相册时间线失真，或字段不被接受 | 版本检测；旧版降级并提示升级；若字段报错自动不带 mtime 重试一次 |
| 双向同步冲突/删除 | 误覆盖或误删用户数据 | 三方基线判断；冲突不覆盖、逐文件解决；删除同步默认关；改名按删+增并提示 |
| SAF 大目录枚举慢 | 首次同步耗时长、耗电 | 显示扫描进度；限制并发；忽略规则；可暂停；后续增量 |
| 服务器端无批量 stat/递归列表 | 同步大量文件时 API 调用多 | 本地缓存远端树；按目录增量拉取；限制并发；失败重试 |
| 同步冲突处理 | 重名/双方修改可能丢数据 | 双向用 base/local/remote 三方比较；冲突不覆盖、逐文件解决；删除同步默认关；改名 v1 按删+增 |
| 构建机内存不足 | Gradle/Kotlin daemon OOM | 限制 worker/heap；调 Hyper-V 内存；CI 兜底 |
| 自托管常见 HTTP/自签名证书 | 登录被系统拦截 | HTTP 允许 + 风险提示；自签名 P1 TOFU；绝不静默忽略证书 |
| SSE 只支持 session，token 用不了 | 无法实时刷新/缩略图事件 | v1 轮询 + 操作后刷新；P1 评估保留短期 session 或纯轮询 |
| 服务端上传冲突策略是账号级偏好 | 用户以为能按次选择 | UI 明示"当前策略：重命名/覆盖/跳过"，设置页可改并提示影响 Web 端 |
| 异步缩略图返回 200 占位 | 误缓存占位图 | 自定义 Coil fetcher 检查响应头，pending 不缓存并重试 |
| 长传输被 Android 15 前台服务时限中断 | 大文件传不完 | 断点持久化，恢复后续传；通知说明；避免承诺"永不断" |
| OpenAPI 不完整/漂移 | 客户端按错字段实现 | 以源码 + 真实契约测试为准；同步补文档；DTO `ignoreUnknownKeys` 容错 |
| 公开分享链接 host 不固定 | 深链不可静态注册 | 用"粘贴链接/系统分享文本"入口 + 自定义 scheme；不假装支持 App Links |
| 手机本地下载路径/权限碎片化 | 保存失败 | API 29+ MediaStore Downloads；26–28 SAF；失败回退 app 私有目录 + 分享 |

---

## 10. 决策与待确认

**已确认**

- 包名/applicationId：`filehub.android`（不使用域名反写）。
- 底部导航：**服务器 / 浏览 / 我的** 三 tab；浏览默认 **tile 卡片行（一行一个文件）**，可切紧凑列表。
- 相册同步、目录同步、拍照上传：v1 要做。
- 目录同步方向：**上传 / 下载 / 双向** 三选一；删除同步可选（默认关）；冲突不覆盖。
- 相册同步：可选相册；默认含视频；单向；不传播删除。
- 上传 `mtime`：FileHub 0.2.1 计划内；客户端按版本能力启用，旧版降级提示。
- 公开分享查看器：v1 必做。
- 鉴权：登录一次 → 自动创建 1 年期 `write` token，之后全走 Bearer。
- 分发：只发 GitHub Releases APK（不上架应用商店 / 不适配 F-Droid）。
- 契约补丁：允许修改服务端仓库的 `docs/openapi.yaml`，只补文档。
- 仓库：`Llesue/filehub-android`（本地 `origin` 已接）。
- 真机：Android 16 / API 36。

**我按"看情况"定的默认值（可推翻）**

- 启动默认：无服务器时进"服务器"；已有当前服务器时进"浏览"上次位置。
- 目录同步默认方向：上传；删除同步默认关；冲突逐文件解决，绝不自动覆盖。
- 相册同步默认：全部相册、含视频、仅 Wi-Fi、每天；"仅充电"默认关。
- 管理端、文本编辑、平板常驻双栏、生物识别锁：P1。
- 视觉：沿用 FileHub Web 中性 Drive 风。
- 低版本：`minSdk 26`；APK 26 用 Robolectric + 发布前借旧机/云真机冒烟。
- 相册/目录同步的周期任务默认"每天 + 仅 Wi-Fi"，不承诺实时。

**待确认**

1. FileHub 0.2.1 的 `mtime` 字段最终形态（创建上传时传，还是 complete 时传）——等后端定；客户端先按"创建时传"实现，兼容两种。
2. 是否需要在 v1 就做"同步冲突逐文件解决 UI"（我建议要，否则双向同步遇到冲突只能卡住）。
3. 低版本真机若借不到，是否接受只做 Robolectric 覆盖（我会在发布说明里标注）。
