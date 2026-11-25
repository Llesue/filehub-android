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

- **P1**：双向同步的重命名检测与逐文件冲突批量解决；**相册同步内容哈希去重（0.1.5，见 §11）**；同步回收站/历史版本入口；SSE 实时事件（`/events`、分享 `/events`、`thumbnail.ready`）替代部分轮询；管理端（用户/配额/root/审计/服务端路径/品牌）；目录索引触发与状态；分享访问统计；文本 Markdown 预览；Android 分享目标（从相册/文件管理器直接上传）；一次性目录上传（SAF tree）；生物识别应用锁；自签名证书 TOFU（确认指纹后固定）。
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
5. 前台任务：扫描 MediaStore → 去重（0.1.5 起按内容哈希，见 §11）→ 逐文件上传（携带 mtime，可断点）→ 回写 SyncEntry；
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


---

## 11. 0.1.5 计划：相册同步内容哈希

状态：计划，未实施  
目标版本：0.1.5  
范围：只改 Android 客户端；服务端内容哈希能力单独评估，不直接改服务端代码。

### 11.1 要解决的问题

当前相册同步判断“已存在”只看文件名和大小：

- 同名同大小但内容不同的文件会被误判为已存在而跳过。
- 同一张照片改名后会重复上传。
- 同名同大小的其他设备文件无法可靠判断是否同一份内容。

0.1.5 改为按内容哈希识别，文件名只作为显示名。

### 11.2 客户端方案

- 使用 `MessageDigest("SHA-256")` 流式计算，不新增依赖。
- 按固定缓冲区（建议 1 MiB）读取 `ContentResolver.openInputStream`，支持取消，不把整个文件读入内存。
- 哈希任务串行执行（同一时间 1 个），避免大视频同时读取拖慢前台服务。
- 每个 `PhotoSyncEntry` 增加：
  - `contentHash`：SHA-256，可为空（旧数据/尚未计算）
  - `hashSize`：计算哈希时的文件大小
  - `hashMtime`：计算哈希时的拍摄/修改时间
  - `hashUpdatedAt`：哈希计算时间
- 媒体文件大小或 mtime 变化时让旧哈希失效，下次重新计算。
- 哈希失败不静默降级为“文件名+大小”判断；该文件记录失败原因并允许重试。

### 11.3 去重流程

1. 扫描 MediaStore，得到候选照片。
2. 查本地 `PhotoSyncEntry`：签名一致且 `hashSize`/`hashMtime` 未变且已有 `contentHash` 时直接复用。
3. 没有可用哈希时计算 SHA-256，并写回条目。
4. 本地已有相同 `contentHash` 且状态为完成、远端路径仍存在：跳过，不重复上传。
5. 相同内容但目标目录没有对应文件（例如远端被删）：重新上传。
6. 不同内容但同名：保留现有策略，自动改名 `name (1).ext` 后上传。
7. 不同内容且不同名：正常上传。
8. 同步结果区分：
   - `skipped_same_content`：内容相同跳过
   - `skipped_exists`：仅文件名+大小命中（旧数据兼容）
   - `uploaded`
   - `renamed`
   - `failed_hash` / `failed_upload`

### 11.4 本地数据与迁移

- Room `photo_sync_entries` 增加哈希字段，新增 `MIGRATION_6_7`，不删旧表。
- 旧条目哈希为空时，下次同步按需补算；不阻塞已有完成记录。
- 可提供“重新计算相册哈希”入口，清空当前 profile 的哈希字段后重新扫描。
- 哈希缓存只用于去重，不作为文件完整性校验或秒传依据。

### 11.5 服务端依赖（关键边界）

当前 FileHub 0.2.5 没有内容哈希接口：

- `/list` 只有名称、大小、mtime、MIME，没有 SHA-256。
- 文件 ETag 目前是 `size-mtime`，不是内容哈希。
- 上传表有 `sha256` 字段但接口没有使用。

因此客户端只能可靠去重：

- 本机已经同步过、且远端路径仍在的文件；
- 本机相册内改名/跨相册重复的同一内容。

不能仅靠客户端识别：

- 服务器上原本就存在、非本 App 上传的文件；
- 其他设备上传的同内容文件；
- 远端同名同大小但内容被替换的文件。

要完整跨设备去重，需要服务端提供以下任一项（需单独评审，不在本次直接修改）：

1. `/list` 或 `/properties` 返回 `sha256`/`content_hash`；
2. `GET /hash?root&path` 返回内容哈希；
3. 上传接口接受并校验客户端 `sha256`，服务端按哈希建立索引；
4. 服务端提供按哈希查询/去重接口。

在服务端能力到位前，0.1.5 明确标注为“本机内容哈希去重”，不承诺跨设备内容去重。

### 11.6 性能与边界

- 大视频哈希会增加首次同步时间，进度条显示“计算哈希”阶段。
- 已成功的条目复用缓存，后续周期同步只计算新增/变化文件。
- 哈希与上传共用前台服务；取消/锁屏/断网时保存已算出的哈希，下次继续。
- 部分授权相册只对已授权媒体计算哈希。
- 不把哈希写日志；日志只记录文件 ID/结果，不记录路径或内容。

### 11.7 测试

单元测试：

- 分块流式 SHA-256 与标准向量一致。
- 大小/mtime 变化使缓存失效。
- 同内容不同文件名只上传一次。
- 同名同大小不同内容不会被跳过。
- 远端文件被删后重新上传。
- 旧条目无哈希时按需补算。
- 哈希失败记录错误且不误判为已存在。

集成/真机：

- 5000 张相册首次同步可断点完成。
- 同步中杀进程，哈希与 uploadId 都能恢复。
- 同一照片改名后不重复上传。
- 服务器已有同名同大小但不同内容的文件，客户端能正确改名上传。
- 检查 logcat 不含路径、哈希原文或 token。

### 11.8 交付顺序

1. Room 字段与迁移、哈希计算器、单测。
2. 同步流程接入哈希、缓存与跳过/重试状态。
3. UI 结果统计和重新计算入口。
4. 真机相册验证。
5. 若确认做服务端哈希，另开契约申请与联调，不与客户端 0.1.5 绑定发布。

---

## 12. 0.1.5 计划：同步状态机与准确进度

状态：计划，未实施  
目标版本：0.1.5（与 §11 内容哈希同版，可分两次提交）  
范围：相册同步与目录同步都纳入本版；目录同步复用同一套状态模型和通知框架。

### 12.1 目标

用户必须随时知道：

- 现在有没有同步在跑；
- 在准备、排队、扫描、上传、停止还是取消；
- 已经处理多少、成功多少、跳过多少、失败多少；
- 当前正在处理哪个相册/文件；
- 在等 Wi-Fi、等充电、等重试，还是需要用户处理权限/登录/目标/配额；
- 当前状态下哪个按钮可以点，哪个不能点。

不再用“已启用/已停用 + 上次运行时间 + 一个临时 queued 提示”表达全部状态。

### 12.2 状态模型

状态分两层：`phase` 表示当前阶段，`blockReason` 表示为什么停下。避免把“等待 Wi-Fi”和“失败”做成互相排斥的一维状态。

```kotlin
enum class SyncPhase {
    Disabled,
    Idle,
    Preparing,
    Queued,
    Scanning,
    Syncing,
    Stopping,
    Cancelling,
    Stopped,
    Cancelled,
    Interrupted,
    Completed,
    Partial,
    Failed,
}

enum class SyncBlockReason {
    None,
    WaitingWifi,
    WaitingCharging,
    WaitingNetwork,
    MissingPermission,
    NeedsLogin,
    TargetUnavailable,
    QuotaExceeded,
    RetryBackoff,
    AnotherRun,
}
```

说明：

- `Preparing`：校验权限、登录、目标 root/目录、网络；还没进入 WorkManager 队列。
- `Queued`：WorkManager 已入队，但可能被约束或已有任务挡住。
- `Scanning`：Worker 已启动，MediaStore 扫描、统计待传数量/总大小；0.1.5 还会包含哈希计算阶段。
- `Syncing`：逐文件上传。
- `Stopping`：用户请求“停止”，正在完成当前文件并保存断点。
- `Stopped`：已停止，保留进度和 uploadId，可继续。
- `Cancelling`：用户请求“取消”，等待 Worker 确认。
- `Cancelled`：本轮已取消；已完成文件保留，未完成文件下次重查。
- `Interrupted`：进程被杀、重启或 Worker 被系统中断，等待恢复。
- `Completed`：全部成功。
- `Partial`：本轮结束但有文件失败/跳过需要关注。
- `Failed`：致命错误，整轮没有完成。
- `Disabled` / `Idle`：未启用 / 已配置但当前没跑。

### 12.3 状态与按钮

| phase | 主按钮 | 次按钮 | 设置字段 |
|---|---|---|---|
| Disabled | 启用相册同步 | — | 可编辑 |
| Idle | 立即同步 | 编辑设置 | 可编辑 |
| Preparing | 取消准备 | — | 锁定 |
| Queued | 取消排队 | — | 锁定 |
| Queued + WaitingWifi | 取消排队 | 本次用流量 | 锁定 |
| Queued + WaitingCharging | 取消排队 | 本次允许充电外同步 | 锁定 |
| Scanning | 停止 | 取消 | 锁定 |
| Syncing | 停止 | 取消 | 锁定 |
| Stopping | 正在停止…（禁用） | — | 锁定 |
| Cancelling | 正在取消…（禁用） | — | 锁定 |
| Stopped | 继续同步 | 重新开始 | 锁定 |
| Cancelled | 重新同步 | 查看结果 | 可编辑 |
| Interrupted | 继续同步 | 重新开始 | 可编辑 |
| Completed | 再次同步 | 查看上次结果 | 可编辑 |
| Partial | 重试失败项 | 再次同步 | 可编辑 |
| Failed | 重试 | 查看错误 | 可编辑 |
| 任意 blocked | 对应修复按钮 | 取消 | 按原因 |

阻塞原因的按钮：

- `MissingPermission` → 授权照片权限
- `NeedsLogin` → 重新登录
- `TargetUnavailable` → 重新选择目标
- `QuotaExceeded` → 查看配额 / 清理空间
- `WaitingNetwork` / `RetryBackoff` → 等待重试；可提供“立即重试”
- `AnotherRun` → 查看正在运行的同步

规则：

- 运行中不允许修改 root、远程目录、相册、视频开关、Wi-Fi/充电、按相册分目录和频率；保存按钮禁用。
- 更改目标必须先停止/取消当前同步，再弹出“清空同步记录”确认。
- 同一 profile 同时只允许一个相册同步 Worker；重复点“立即同步”不能生成第二个任务。
- `Stopping` / `Cancelling` 期间所有操作禁用，避免用户连点。

### 12.4 必须展示的进度信息

状态卡第一行：大状态文案 + 状态色/图标。

第二行：

```text
正在上传 37 / 248 · 126.4 MB / 1.8 GB
```

第三行：

```text
当前：Camera/IMG_20261125_1032.jpg · 相册：Camera
```

补充信息：

- 触发来源：手动 / 定时 / 补跑 / 网络恢复 / 重试
- 已用时间
- 上传速度（采样最近 10 秒）
- 预计剩余时间（可选，样本不足时不显示，避免乱跳）
- 成功 / 跳过 / 失败计数
- 失败文件数量和“查看失败项”
- 等待条件的具体原因和下一次重试时间

不确定阶段（准备、扫描、停止、取消）使用不确定进度条；上传阶段使用确定进度条。

### 12.5 数据持久化

新增 `photo_sync_runs` 单行表（每个 profile 一行当前/上次运行），字段：

```text
profileId, runId, trigger, phase, blockReason,
total, processed, uploaded, skipped, failed,
bytesTotal, bytesDone, currentAlbum, currentName,
stopRequested, cancelRequested,
startedAt, updatedAt, finishedAt,
lastErrorCode, lastErrorMessage, recoveryCount
```

要求：

- 每次运行生成新的 `runId`；Worker 写状态时校验 `runId`，旧 Worker 不能覆盖新运行。
- 状态同时写入 Room 和 WorkManager `setProgress`；UI 优先观察 Room，WorkManager 只用于排队/约束/取消确认。
- 每处理一个文件、每个上传分片完成、进入 Stopping/Cancelling 时更新状态。
- `updatedAt` 作为心跳；超过约定时间没有心跳且 WorkInfo 不是 RUNNING，显示 `Interrupted`。
- `lastResult` 保留上一次完成的摘要；当前运行不覆盖历史摘要，直到本轮结束。

### 12.6 停止与取消的区别

- **停止**：协作式停止。设置 `stopRequested`，当前文件上传完成后保存 `uploadId`/bytesDone，状态写 `Stopped`；下次“继续同步”从断点恢复。
- **取消**：调用 WorkManager `cancelUniqueWork`。当前分片可能被中断，已完成文件保留，未完成条目标记可重试；状态写 `Cancelled`；下次重新检查远端，不保证从当前分片继续。
- 进程被杀：状态先记为 `Interrupted`，WorkManager 恢复后重新进入 `Preparing/Scanning/Syncing`；不把“被杀”误显示成“取消”。
- 系统因约束停止（Wi-Fi 断开、充电断开）按 `Queued + WaitingWifi/WaitingCharging` 处理，不算用户取消。

### 12.7 错误分类

| 情况 | 状态 |
|---|---|
| 401 / token 失效 | Failed 或 Queued + NeedsLogin，停止并提示重新登录 |
| 403 / 权限 | Blocked + NeedsLogin/Permission |
| 目标 root 删除、离线 | Blocked + TargetUnavailable |
| QUOTA_EXCEEDED | Blocked + QuotaExceeded |
| 429 / 5xx / 网络超时 | RetryBackoff，按退避重试 |
| 单文件失败 | 继续整轮，计入 failed；结束时 Partial |
| 全部文件失败 | Failed |
| 用户停止 | Stopped |
| 用户取消 | Cancelled |
| 本进程被杀 | Interrupted |

### 12.8 通知与列表卡片

- 通知标题跟随 phase：准备同步 / 等待 Wi-Fi / 正在扫描 / 正在上传 / 正在停止 / 已停止 / 同步完成，有失败。
- 通知正文包含 `processed/total`、当前文件、失败数。
- 通知操作：运行中“停止”；排队中“取消”；Stopped/Partial 提供“继续/重试”。
- “我的”同步卡片和相册同步详情页显示同一个 phase 文案，不能一个显示“同步中”、另一个显示“已启用”。
- 完成后保留一条摘要：成功 N、跳过 N、失败 N、用时、目标目录。

### 12.9 测试与验收

单元测试：

- 每个 phase 的按钮可见/可用矩阵。
- Stopping/Cancelling 期间重复点击不产生第二个任务。
- runId 防止旧 Worker 覆盖新状态。
- 停止后 resume 复用 uploadId；取消后重新检查远端。
- 约束不满足显示 WaitingWifi/WaitingCharging。
- 401/403/目标丢失/配额/429 的状态映射。
- 心跳超时显示 Interrupted。

真机：

- Wi-Fi 断开/恢复，状态从 WaitingWifi 回到 Queued 再 Syncing。
- 同步中停止，界面显示“正在停止”，结束后“已停止”，继续能断点续传。
- 同步中取消，界面显示“正在取消”，结束后“已取消”，已完成文件不丢。
- 杀进程/重启，显示“已中断”，恢复后继续。
- 单文件失败不阻塞整轮，结束显示“部分完成”并可重试失败项。
- 目标被删除/账号退出/配额满时显示对应阻塞状态和修复按钮。
- 运行中所有设置字段锁定，保存按钮禁用。

### 12.10 交付顺序

1. 数据表/迁移、phase/blockReason 定义、状态写入与 runId 校验。
2. Worker 协作式停止、取消确认、心跳和中断恢复。
3. 状态卡、进度、按钮矩阵、设置锁定。
4. 通知和“我的”同步卡片统一状态。
5. 错误映射、测试、真机验收。

### 12.11 目标变更与旧 Worker（本次问题）

当前 `PhotoSyncRepository.save()` 只写配置和安排周期任务，不取消 `periodic / now / catchup / wifi` 四个 unique work；Worker 启动时读取的是旧配置快照。因此改目标后旧 Worker 仍会：

- 按旧 `remotePath` 继续上传；
- 每张照片前调用 `RemoteDirEnsurer.ensure()`，把刚删掉的旧目录重新创建；
- 继续更新同一个通知；
- 在 `resetEntries=true` 清空记录后继续写回旧目标条目。

修复要求：

- 目标变更必须先停止/取消当前运行，并 `cancelAll` 四个 unique work。
- 等 Worker 心跳结束或 WorkManager 确认取消后，再清空 entries、保存新配置。
- Worker 每个文件前校验 `runId`/目标 generation；发现配置已变就退出，不能重建旧目录。
- 通知和完成结果带 `runId`，旧 Worker 不得覆盖新状态。
- 运行中锁定目标、相册、开关和保存按钮。

### 12.12 相册同步保存按钮状态

状态：计划，未实施

当前按钮固定显示“保存并开启”，无论是否已启用、是否修改过配置。需要改成由表单脏状态和保存状态驱动的智能按钮。

#### 表单状态

- 首次设置：没有任何已保存配置。
- 无变更：已保存配置与当前表单完全一致。
- 有变更：root、远程目录、相册、包含视频、照片/视频仅 Wi-Fi、仅充电、按相册分目录、频率任意一项变化。
- 目标变化：root 或远程目录变化，会清空同步记录，属于破坏性变更。
- 同步运行中：设置字段和保存按钮全部锁定。

#### 按钮文案

| 状态 | 文案 | 可点 |
|---|---|---|
| 首次设置 | 保存并开启 | 是 |
| 已启用 + 有变更 | 保存变更 | 是 |
| 未启用 + 有变更 | 保存配置 | 是 |
| 已启用 + 无变更 | 无未保存变更 / 已保存 | 否 |
| 正在保存 | 正在保存…（带小转圈） | 否 |
| 保存成功 | 已保存 ✓（保持 1.5–2 秒） | 否 |
| 保存失败 | 保存失败，重试 | 是 |
| 目标变化 | 应用并重新同步 | 是，点击后二次确认 |
| 同步运行中 | 同步进行中 | 否 |

#### 反馈规则

- 点击后立即禁用按钮，不能等保存完成再变。
- 按钮内显示转圈，文案立即变成“正在保存…”。
- 保存成功后按钮显示“已保存 ✓”，下方内联显示“设置已保存，下次同步将使用新目标”。
- 目标变化成功后显示“已切换目标，同步记录已重置，将重新上传”。
- 保存失败恢复按钮，显示具体错误，文案“保存失败，重试”。
- Snackbar 只作为补充，不能替代按钮和内联反馈。
- 目标变化使用单独确认框；确认按钮文案“开始重新同步/应用变更”，避免和普通保存混淆。
- 同步运行中锁定所有设置和保存按钮，提示“停止同步后才能修改”。

#### 校验与防重复

- 未选相册、无权限、目标不可用、保存中：按钮禁用并显示原因。
- ViewModel 保留 `saving` 防重复提交；UI 只做第一层保护。
- 保存成功后更新基准表单，重新计算 dirty；不能因为页面重组把“已保存”又变回“保存变更”。
- 保存失败不能更新基准，按钮保持可重试。

#### 布局

- 相册同步页面较长，建议增加粘性底部操作条：
  - 左侧状态：有未保存变更 / 已保存
  - 右侧保存按钮
- 滚动到任何位置都能看到按钮和状态，避免用户以为按钮没生效。
- 目标变化确认框继续保留“清空记录、旧目录不删除”的说明。

#### 测试

- 首次设置、已启用无变更、已启用有变更、未启用有变更四种文案。
- 连续点击保存只产生一次写入。
- 保存成功后按钮变“已保存”，2 秒后变“无未保存变更”。
- 保存失败后可重试，且不会误显示成功。
- 目标变化才能出现“应用并重新同步”，普通字段变化不能触发清空记录。
- 同步运行中所有字段和按钮锁定。
- dirty 状态在旋转/重组后保持正确。

### 12.13 相册 bucket 大小写归一化

问题：MediaStore 可能给同一个 `BUCKET_ID` 返回多个 `BUCKET_DISPLAY_NAME`（例如 `Camera` 和 `camera`），因为不同 App 写入时用了不同大小写路径，但 Android 共享存储把它们解析到同一个物理目录。

当前实现不一致：

- `MediaScanner.buckets()` 以 `BUCKET_ID` 为 key 做 map，后出现的名字覆盖前一个，所以相册选择器只显示一个 `Camera`。
- `MediaScanner.photos()` 逐行读取 `BUCKET_DISPLAY_NAME` 作为 `album`，同一个 bucket 会产生 `Camera` 和 `camera` 两种 album。
- `PhotoSyncWorker.remoteDir()` 用这个 album 建目录，于是服务端出现 `Camera` 和 `camera` 两个目录。

修复要求：

- 先按 `BUCKET_ID` 建立唯一 bucket，再为每个 bucket 选一个 canonical 显示名。
- canonical 规则要稳定，例如优先使用大写形式或 `_data` 父目录的实际大小写；同一 bucket 内所有照片复用同一个 album 名。
- `buckets()` 和 `photos()` 必须使用同一份 canonical map，不能一个按 ID 去重、一个按行名分组。
- 不按文件名/大小重新合并两个 bucket；它们只是同一个物理相册的元数据分裂。
- 增加单测：同一 `BUCKET_ID` 同时出现 `Camera`/`camera` 时只生成一个相册和一个远端目录。
- 服务端已有的 `Camera` / `camera` 在修复后合并为一个目录，保留备份直到验证完成。

---

## 13. 0.1.5 计划：同步通知栏

状态：计划，未实施  
范围：相册同步优先，目录同步复用。

### 13.1 目标

用户不打开 App 也能准确知道：

- 现在是在准备、排队、扫描还是上传；
- 整体处理了多少、总大小多少；
- 当前文件传到哪、百分比多少；
- 成功、跳过、失败数量；
- 为什么停下，以及下一步能做什么。

### 13.2 通知内容

标题按状态变化：

```text
准备同步 · 服务器名
等待 Wi-Fi · 服务器名
正在扫描相册 · 服务器名
正在同步 37/248 · 服务器名
正在停止…
正在取消…
已停止
同步完成
部分完成，3 张失败
```

正文：

```text
126.4 MB / 1.8 GB · 6.3 MB/s
```

第二行：

```text
当前：Camera/IMG_20261125_1032.jpg
```

或：

```text
当前文件 4.2 / 8.1 MB · 52%
```

补充信息按可用宽度展示：

- 当前相册
- 触发来源：手动 / 定时 / 补跑 / 网络恢复
- 已用时间
- 预计剩余时间（样本不足则不显示）
- 成功 / 跳过 / 失败计数

### 13.3 进度条

- 默认显示整体进度：`processed / total` 或 `bytesDone / bytesTotal`
- 当前文件百分比放在正文，不单独占第二条进度条
- 准备、扫描、停止、取消阶段使用不确定进度
- 上传阶段使用确定进度
- 更新频率节流到 500ms–1s，避免每传一个分片就刷新通知

### 13.4 操作按钮

| 状态 | 操作 |
|---|---|
| 运行中 | 停止 |
| 排队中 / 等待条件 | 取消 |
| 已停止 / 已中断 | 继续 |
| 部分完成 | 重试失败项 |
| 完成/失败 | 查看详情 |
| 需要权限/登录/目标/配额 | 对应修复入口 |

通知点击进入相册同步详情页。停止和取消必须和状态机语义一致，不能只改通知文案。

### 13.5 完成与失败通知

- 进行中的前台通知 ID 保持固定
- 完成后额外发一条可自动消失的摘要通知：
  - 成功 N、跳过 N、失败 N
  - 用时
  - 目标目录
- 有失败时提供“查看失败项 / 重试失败项”
- 摘要通知不覆盖进行中的通知，避免状态混乱

### 13.6 Android 16 与低版本

- Android 16 使用进度中心通知/ProgressStyle，展示多文件分段进度
- Android 12–15 使用标准确定进度条
- Android 11 及以下回退普通通知
- 所有版本都必须有可读的文字进度，不能只依赖进度条

### 13.7 隐私与权限

- 通知默认隐藏具体文件名/相册名，锁屏显示“正在上传照片”
- 解锁后的展开通知显示详情
- POST_NOTIFICATIONS 未授权时不重复弹权限，不阻塞同步
- 通知通道低重要性、无声、只提示一次
- 通知内容不包含 token、服务器路径、完整本地路径

### 13.8 通知与 WorkManager

- 前台服务启动后 5 秒内必须 post 通知
- 等待 Wi-Fi/充电时可以不启动前台服务，但要发普通等待通知
- 进程被杀后通知不能残留“正在同步”
- 停止/取消后通知状态必须和数据库状态一致

### 13.9 测试

- 各 phase 的标题、正文、进度条和按钮矩阵
- 整体字节进度和当前文件进度同时正确
- 停止/取消过程中按钮不重复触发
- 完成摘要与进行中通知不互相覆盖
- 锁屏不泄露文件名
- Android 11、12、16 各跑一次通知展示
- 无通知权限时不崩、不阻塞同步

---

## 14. 0.1.5 范围汇总与交付顺序

### 14.1 范围

本版本包含：

1. 相册 bucket 大小写归一化（§12.13）
2. 目标变更取消旧 Worker（§12.11）
3. 同步状态机与准确进度（§12）
4. 保存按钮智能状态与反馈（§12.12）
5. 同步通知栏（§13）
6. 相册同步内容哈希（§11，本机哈希；跨设备依赖服务端）
7. 目录同步状态机、进度、保存按钮和通知（本版纳入，见 §15）

### 14.2 交付顺序

1. bucket 归一化 + 目标变更取消旧 Worker：先修当前重复目录和旧任务问题
2. 同步状态表、phase/blockReason、runId、心跳
3. 停止/取消/中断恢复、设置锁定
4. 保存按钮 dirty/保存/成功/失败状态
5. 通知栏进度、当前文件、操作按钮和完成摘要
6. 内容哈希、Room 迁移、补算与去重
7. 目录同步状态机、进度、保存按钮和通知

### 14.3 验收门槛

- 手机和车机同一 bucket 只生成一个远端相册目录
- 改目标后旧 Worker 不再重建旧目录
- 运行中所有设置锁定，按钮状态和实际 phase 一致
- 通知栏能准确显示整体和当前文件进度
- 停止可继续、取消不误显示、进程被杀显示中断
- 内容哈希能识别同内容不同文件名，不误判同名同大小不同内容
- `check`、单测、release 构建、手机/车机真机验收全部通过


---

## 15. 0.1.5 计划：目录同步状态、进度与通知

状态：计划，未实施  
范围：目录同步与相册同步共用 §12 的 phase / blockReason / runId / 心跳模型；额外补充扫描、比对、冲突和删除同步状态。

### 15.1 目标

目录同步必须和相册同步一样，让用户随时知道：

- 当前方向：上传 / 下载 / 双向
- 在扫描本地、扫描远端、比对变更，还是正在执行
- 新增、更新、删除、冲突各多少
- 当前正在上传/下载哪个文件、多少字节
- 为什么停下：等 Wi-Fi、等充电、权限、目标、登录、配额、重试、冲突
- 当前按钮会做什么

### 15.2 目录同步专属 phase

复用 §12 的 `Disabled / Idle / Preparing / Queued / Stopping / Cancelling / Stopped / Cancelled / Interrupted / Completed / Partial / Failed`，增加：

- `ScanningLocal`：扫描 SAF 本地目录
- `ScanningRemote`：盘点远端目录
- `Comparing`：生成本地/远端/基线差异
- `Applying`：执行上传、下载、删除
- `ConflictsPending`：本轮结束但存在待解决冲突

`blockReason` 增加：

- `MissingSafPermission`：SAF 授权失效
- `ConflictsPending`：需要逐文件处理冲突
- `DeleteConfirmationRequired`：删除同步需要用户确认

### 15.3 进度与计数

状态卡和通知显示：

```text
扫描本地 1,240 项
扫描远端 1,180 项
比对：新增 32 · 更新 8 · 删除 3 · 冲突 2
执行：12 / 40 · 86.4 MB / 320 MB
当前：上传 Camera/IMG_...jpg
```

要求：

- 扫描/比对阶段使用不确定进度
- 执行阶段使用确定进度
- 上传和下载分别显示方向
- 删除同步执行时显示“正在删除 N 项”
- 冲突不阻塞其他文件；结束时显示 `Partial` + 冲突数量
- 全部成功才显示 `Completed`

### 15.4 数据持久化

新增 `folder_sync_runs` 单行表（每个 profile 一行当前/上次运行），字段与 `photo_sync_runs` 对齐，额外包含：

- `mode`：upload / download / bidirectional
- `localScanned` / `remoteScanned` / `planned`
- `created` / `updated` / `deleted` / `conflicts`
- `bytesTotal` / `bytesDone`
- `currentDirection` / `currentPath`
- `deleteSync` / `deletePending`
- `runId` / 心跳 / stopRequested / cancelRequested

要求：

- Worker 每个文件/目录扫描批次更新状态
- 旧 Worker 不能覆盖新 `runId`
- 停止保留三方基线和断点；取消不丢已完成条目
- 进程被杀显示 `Interrupted`，恢复后继续

### 15.5 UI 与按钮

与 §12.3 共用按钮矩阵，目录同步额外：

- `ConflictsPending`：主按钮“查看冲突”，次按钮“重新同步”
- 冲突列表逐文件选择保留本地/保留远端/跳过
- 删除同步开启前二次确认
- 运行中锁定方向、目标、删除同步、过滤规则和保存按钮
- 目标/方向变化必须先停止当前运行，再确认是否重置基线

保存按钮复用 §12.12：

- 首次“保存并开启”
- 已启用有变更“保存变更”
- 运行中“同步进行中”
- 目标/方向变化“应用并重新同步”
- 保存中“正在保存…”，成功“已保存 ✓”

### 15.6 通知栏

复用 §13：

- 标题：`目录同步 · 上传/下载/双向`
- 正文：阶段 + 已处理/总数 + 字节
- 当前文件/目录名、方向
- 冲突数量
- 操作：停止、取消、继续、查看冲突、重试
- 完成摘要：新增/更新/删除/冲突/失败

### 15.7 冲突与失败

- 冲突绝不自动覆盖
- 单文件冲突不阻塞整批
- 失败文件进入列表，可重试
- 删除同步默认关；开启后删除前确认
- 401/403/目标丢失/配额/网络映射到统一阻塞原因

### 15.8 测试

- 三种方向的状态和计数
- 扫描、比对、执行、完成/部分完成/失败
- 停止后继续，取消后重查
- 杀进程/重启恢复
- 双向冲突不覆盖，逐文件解决
- 删除同步关闭/开启行为
- 目标/方向变化停旧 Worker
- 通知栏阶段、进度、方向、冲突和操作按钮
- 手机与车机真机验证

### 15.9 交付顺序

1. 复用 phase/blockReason/runId/心跳，新增目录同步扫描/比对阶段
2. `folder_sync_runs` 与 Worker 状态写入
3. 停止/取消/中断/恢复
4. 状态卡、进度、冲突入口、保存按钮锁定
5. 通知栏
6. 冲突解决和删除同步验收

### 15.10 相册同步与目录同步并发

当前 `SyncLocks.photo(profileId)` 和 `SyncLocks.folder(profileId)` 是两个独立锁，两种同步可以同时运行；服务端也没有跨同步的目标占用检测。

会发生冲突的情况：

- 相册同步和目录同步使用同一个 root，且远程路径相同或互为父子目录。
- 目录同步开启双向或删除同步时，可能覆盖/删除相册同步刚上传的文件。
- 两个 Worker 同时写同一个文件名，服务端按冲突策略覆盖或自动改名。
- 两边同时扫描、上传、下载，网络和磁盘负载叠加。

不会冲突的情况：

- 两个同步使用完全不同的 root，或同一 root 下互不包含的目录。
- 目录同步只读、且目标不覆盖相册目录。

计划要求：

- 保存配置时做目标重叠检测：规范化 `rootId + relative path`，判断相同或祖先/后代关系。
- 目标重叠时禁止直接保存，提示用户改成独立目录；至少要求二次确认。
- 运行期增加共享的 profile 级同步锁：相册和目录同步不能同时执行，后启动的显示 `Queued + AnotherRun`。
- 删除同步额外保护：目录同步的删除范围不得包含相册同步目标目录。
- 状态卡和通知显示“另一个同步正在运行”，不把等待误显示为排队无进展。
- 单元测试覆盖相同路径、父子路径、不同 root、删除同步保护。

### 11.9 服务端哈希验证契约（0.2.6 已支持）

状态：服务端 0.2.6 Gate 1–2 已实现；客户端 0.1.5 接入。实际契约以服务端 `docs/sync-hash.md` 为准：`GET /me` capabilities、`GET /hash`、`POST /hashes`、上传 `sha256` + `verify`、`HASH_MISMATCH`。

早期设想如下，保留作背景：

1. **上传校验**
   - `POST /uploads` 或完成请求接受可选 `sha256`（hex）。
   - 服务端在写入/完成时计算并校验；不一致返回稳定错误码，例如 `CHECKSUM_MISMATCH`，删除临时文件，不产生目标文件。
   - 未提供 `sha256` 的旧客户端保持兼容。

2. **哈希查询**
   - `/list` 可选返回 `sha256`，或新增 `GET /hash?root&path`。
   - 目录列表不要求一次性返回全部哈希；客户端可按需查询。
   - 服务端可缓存哈希，按文件大小/mtime 失效。

3. **索引与去重**
   - 服务端索引记录内容哈希，支持按哈希查询/去重。
   - 已有文件在索引任务中补算哈希。
   - 删除/重命名后同步失效。

4. **能力协商**
   - `GET /version` 或 `/roots` 返回是否支持哈希字段/校验，客户端据此启用跨设备去重。
   - 不支持时客户端只做本机内容哈希。

5. **错误码**
   - `CHECKSUM_MISMATCH`：客户端重试或提示文件损坏。
   - `HASH_UNSUPPORTED`：客户端降级到本机哈希。
   - 校验失败不能静默覆盖已有文件。

6. **测试**
   - 正确哈希上传成功；错误哈希失败且无残留文件。
   - 旧客户端无哈希仍可上传。
   - `/list` 哈希与文件内容一致；改名/修改后失效。
   - 服务端重启/索引重建后哈希仍可用。

### 12.14 取消原因可解释

状态：计划，未实施。

每次停止/取消/中断必须记录并显示原因，不能只留下 WorkManager 的 `Work ... was cancelled`：

- `USER_STOP`：用户点停止
- `USER_CANCEL`：用户点取消
- `TARGET_CHANGED`：目标目录或方向变化
- `CONFIG_CHANGED`：运行中修改了会影响同步的配置
- `LOGOUT` / `SERVER_REMOVED`
- `AUTH_FAILED`：Token 失效/账号禁用
- `SYSTEM_STOPPED`：应用被强制停止、更新、系统回收
- `WORK_REPLACED`：重新点“立即同步”导致旧 now 任务被替换
- `PROCESS_DEATH`：进程被杀，恢复时显示“已中断”
- `UNKNOWN`：兜底，附带内部 worker 类型，但不显示原始 UUID

要求：

- 数据库 `photo_sync_runs` / `folder_sync_runs` 保存 `cancelReason`。
- 状态卡、通知和调试日志使用同一原因。
- 日志可以包含 worker 类型和 runId，不显示无意义的短 ID 或 token。
- 用户停止和系统中断不能混用文案。
- 下一轮同步开始时清除上一轮的临时取消原因，但保留上次结果摘要。

---

## 16. 0.1.5 计划：同步设置页横屏/平板适配

状态：计划，未实施  
范围：相册同步设置页、目录同步设置页；窄屏手机布局保持现状。

### 16.1 当前问题

两个页面都是 `DetailScaffold + 单列 verticalScroll`：

- 横屏/平板上表单被拉满整屏，输入框和开关过宽
- 状态卡、相册列表、保存按钮都在同一条长滚动里
- 目标、选项、相册数量多时要滚很远才能看到状态和保存
- 目录同步的冲突列表没有独立空间
- 没有利用平板双栏空间，和主浏览页的宽屏体验不一致

### 16.2 布局规则

按可用宽度切换：

- `< 720dp`：保持单列，和手机一致
- `>= 720dp` 横屏或平板：双栏
- `>= 1200dp`：双栏 + 更宽内容区，但表单最大宽度限制，不无限拉伸

双栏结构：

**相册同步**

- 左栏：服务器/root、目标目录、同步开关、Wi-Fi/充电、频率、相册选择
- 右栏：同步状态卡、当前进度、选中相册摘要、上次结果、失败项、保存操作条
- 左栏和右栏各自滚动，互不影响
- 相册列表在左栏独立滚动，最大高度受视口约束

**目录同步**

- 左栏：本地目录、服务器/root、目标目录、方向、删除同步、Wi-Fi/充电、频率
- 右栏：同步状态、方向进度、冲突列表、失败项、上次结果、保存操作条
- 冲突列表只在右栏，不挤占设置表单
- 删除同步和双向模式的风险提示在右栏状态区显著展示

### 16.3 宽屏表单

- 表单内容最大宽度约 560–640dp
- 两个短选项可以并排：照片/视频 Wi-Fi、仅充电/按相册分目录
- 目标 root 和路径不要无限拉长，路径字段可省略中间
- 保存按钮放在右侧或粘性底栏，不跟随表单滚出屏幕
- 状态卡在右栏顶部，切换设置时始终可见

### 16.4 交互

- 横竖屏切换保持表单内容、相册选择、滚动位置
- 运行中双栏都锁定设置，右栏显示状态
- 目标/方向变化仍走确认流程
- 保存按钮复用 §12.12 的 dirty/保存中/已保存/失败状态
- 冲突解决在右栏打开列表或独立页面，返回后保留筛选和滚动位置

### 16.5 测试

- 390dp 手机竖屏保持单列
- 840dp 手机横屏双栏
- 1280×800 平板横屏双栏
- 720×1280 平板竖屏：按宽度决定单列或窄双栏，不能挤压表单
- 车机 1920×1080 双栏
- 中英文、浅色/深色截图
- 相册列表很长、目录冲突很多时不出现嵌套滚动冲突
- 保存按钮在任何滚动位置可见
- 旋转后表单和冲突列表状态不丢

---

## 17. 0.1.5 计划：分享与传输页横屏/平板适配

状态：计划，未实施  
范围：分享管理、分享详情/创建、传输记录；窄屏手机保持现有单列。

### 17.1 分享管理

窄屏：

- 保持现有单列分享卡片
- 点击卡片进入现有详情/操作

宽屏（>=720dp）：

- 双栏：左侧分享列表，右侧选中分享详情
- 左栏包含搜索、状态筛选（有效/过期/撤销）、排序和分页
- 右栏显示：
  - 分享类型（单文件/目录/集合）
  - 文件数量和路径
  - 有效期、密码、只读状态
  - 二维码卡片
  - 复制链接、打开、轮换、撤销、永久删除
- 列表和详情各自滚动，选中项保持
- 没有选中时右栏显示空态，不自动打开第一个分享

创建分享：

- 窄屏保持全屏对话框/底部表单
- 宽屏使用双栏弹窗：左侧配置（文件、密码、有效期、只读），右侧实时二维码和摘要
- 弹窗最大宽度约 900–1000dp，不拉满整屏

### 17.2 传输记录

窄屏：

- 保持现有卡片列表

宽屏（>=840dp）：

- 表格化列表，列：
  - 文件名/方向
  - 大小
  - 进度/速度
  - 状态
  - 操作
- 顶部显示汇总：进行中、已完成、失败、总上传/下载量
- 支持按方向（上传/下载）和状态（进行中/完成/失败/已取消）筛选
- 进行中和历史分区，或同一列表用状态分组
- 选中一行可在右侧查看详情：本地路径、远端路径、错误、重试入口
- 操作按钮固定在行尾，不能因为长文件名被挤走
- 大量记录时不使用超宽单列，表格保持可读行高

### 17.3 公开分享页

- 宽屏限制正文最大宽度，文件列表和预览并排或使用更宽的卡片
- 窄屏保持单列
- 密码输入、下载按钮和文件列表不能因为宽屏被拉成超长输入框

### 17.4 共用规则

- 使用 `BoxWithConstraints` / 统一断点，不在各页面重复魔法数字
- 宽屏内容区最大宽度限制，避免表单和卡片无限拉伸
- 左右栏独立滚动，避免嵌套滚动冲突
- 旋转后保持筛选、选中项、滚动位置和弹窗状态
- 分享/传输页的错误、空态、加载态在宽屏下居中且不贴边
- 复用主浏览页的平板视觉：细边框、圆角、留白和触控目标 >=48dp

### 17.5 测试

- 390dp 手机竖屏单列
- 840dp 手机横屏双栏
- 1280×800 平板横屏双栏
- 720×1280 平板竖屏不挤压
- 车机 1920×1080 双栏/表格
- 中英文、浅色/深色截图
- 分享列表很长、传输记录很多时滚动和选中状态正确
- 创建分享弹窗、二维码、文件选择和操作按钮不溢出

---

## 18. 0.1.5 实现记录

状态：进行中（哈希校验按约定暂缓，等服务端契约）。

已实现的决策：

- 运行状态用单表 `sync_runs`，主键 `(profileId, kind)`，`kind` 为 `photo` / `folder`。字段覆盖 phase、blockReason、cancelReason、计数、字节、当前项、方向、runId、心跳、stop/cancel 标记。和原计划的分表等价，减少一套迁移和 DAO。
- `SyncRunStore.mutate` 在事务里做读改写，并校验 `runId`；旧 Worker 不能覆盖新运行。终态记录不再接受 `progress` 更新，避免停止/取消后又被写回“正在同步”。
- 心跳超过 120 秒且仍处于活动 phase 时，UI 按 `INTERRUPTED` 展示；进程被杀不需要额外写入。
- 相册与目录同步共用一个 profile 级共享锁；后启动的一方显示 `QUEUED` + `ANOTHER_RUN`，等共享锁释放后自动继续。
- 保存目标时检查另一类同步的目标重叠（同 root，相同或父子路径）；重叠时返回 `SyncTargetOverlapException`，UI 二次确认后才允许保存。
- 目录同步开启删除同步且与相册目标重叠时，本轮直接 `FAILED` + `DELETE_CONFIRMATION_REQUIRED`，不允许执行删除。
- 目标/方向变更会取消旧 WorkManager 任务、把运行标记为 `TARGET_CHANGED`；目录同步同时清空三方基线，避免拿旧基线比对新目标。
- 结果写回改为只更新 `lastRunAt/lastResult/lastError/pendingCount`，不再整行 REPLACE 配置，旧 Worker 不会把新目标写回旧值。
- 相册 album 归一化按 `BUCKET_ID` 聚合；名字选择规则为数量最多 → 首字母大写优先 → 字典序。选择器和同步共用同一份 canonical map。
- 通知栏支持阶段标题、整体字节/条目进度、当前文件、速度、停止/取消/继续/重试、完成摘要、锁屏私密版本；更新节流 800ms。
- 目录同步冲突提供独立页面：逐文件选择保留手机或服务器版本；选择后删除另一侧副本、清除基线并触发重同步。
- 相册/目录设置页、分享页、传输页按 720/840dp 断点切换宽屏；设置与状态/列表与详情独立滚动，保存条固定底部。

未完成/待补：

- 服务端 SHA-256 契约与客户端内容哈希（按用户要求暂缓）。
- Android 16 `Notification.ProgressStyle` 分段进度（当前使用标准确定进度条）。
- 通知栏“查看冲突”直达按钮（目前从目录同步状态卡进入）。

### 18.1 视觉调整（用户反馈后）

- 四个工具页（相册同步、目录同步、分享、传输）改用 48dp 紧凑标题栏，标题 16sp；内容顶部间距从 16dp 收到 8dp。
- 同步页保存按钮从独占一整行改为标题栏右侧动作；底部固定操作条整体删除。
- 相册/目录同步恢复双栏：左栏设置，右栏状态机 + 同步日志。
- 右栏状态卡压缩为：阶段标题 + 操作 + 四步状态机 + 进度 + 当前项 + 阻塞/取消原因；不再重复上次同步摘要。
- 新增 `sync_log` 表（Room v8，auto id，按 profileId+kind 保留最近 200 条）。Worker 记录开始、扫描、计划、进度、冲突、删除、失败、停止/取消、完成。
- 窄屏（<720dp）单列显示状态卡和设置；日志改为弹窗，避免在滚动列里嵌套滚动。
- 分享页删除搜索框、筛选 chip 和右栏详情；宽屏（>=780dp 内容宽度）两列卡片，窄屏单列。
- 分享卡片保留复制链接/二维码/打开链接和溢出菜单；不再有“选中卡片 + 右侧详情”的状态。

### 18.2 内容哈希实现

- 能力发现：`GET /me` 的 `capabilities.hash`、`hash_batch`、`hash_upload_verify`、`hash_verify_max_bytes`、`hash_algo`。
- 本地流式 SHA-256，缓存到 `photo_sync_entries` / `folder_sync_entries` 的 `sha256/hashSize/hashMtime`（Room v9）。
- 相册：同名同大小 → `GET /hash` 比较；内容不同自动改名；同目录同大小候选 → `POST /hashes` 批量比较，同内容不重复上传。
- 上传：`POST /uploads` 携带 `sha256` + `verify=auto`；`HASH_MISMATCH` 时丢弃会话，失败项下次重传。
- 目录：仅对两边都存在、大小相同且 mtime 不同的候选计算/批量查询哈希；相同则视为已同步，不相同继续走冲突/上传。
- 降级：404 / 501 / 缺少 capabilities → 保留原 size+mtime 行为；202 pending 不阻塞，按 `retry_after_ms` 重试一次后回退。

### 18.3 同步目标选择器

- 相册/目录同步的 root 选择从窄 DropdownMenu 改为全宽弹窗；行高 56dp，黄色文件夹图标，选中打勾。
- 过滤离线 root 和只读 root；本地 root 不显示“本地”，远程 root 显示“远程”。
- 远程 root 被选中时，“存储位置”后显示 16dp 圆圈惊叹号；点击提示远程目标不支持内容哈希，按文件名/大小/mtime 回退。
- 远程 root 的 Worker 关闭哈希能力：不调用 `/hash`、`/hashes`，上传不带 `sha256`，避免服务端 `HASH_UNSUPPORTED`。
- 弹窗右上角关闭按钮替代底部“关闭”，避免大下巴。

### 18.4 浏览页 root 卡片与远程头像

- RootCard 去掉灰色图标底；本地 root 直接显示黄色文件夹图标。
- 远程 root 通过 `GET /remote/mounts` 取 `owner_name`、`avatar_version`，用 `/remote/mounts/{id}/avatar` 显示头像；无头像时用名字首字母和稳定 HSL 颜色兜底。
- `remote`、`online`、`read-only` badge 与目录名同行；名字 `weight(1f, fill=false)` + ellipsis，长名不会把 badge 挤走。
- 同步页的 root 字段/选择弹窗把“远程”做成蓝色内联 badge；本地 root 无 badge。
- 圆圈惊叹号保持 16dp，去掉 48dp 最小行高，恢复原标签行间距。

### 18.5 同步日志配色

- 日志级别扩展为 `info`、`start`、`success`、`warn`、`error`。
- 相册和目录 Worker 开始行写 `start`，无失败完成写 `success`，有失败写 `error`，仅冲突写 `warn`。
- UI：开始蓝、成功绿、失败红、警告琥珀、普通信息灰；时间戳用 outline 色，和正文区分。
- 每条日志单行，正文 `maxLines=1` + ellipsis，时间固定宽度。
