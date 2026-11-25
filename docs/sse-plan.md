# FileHub Android SSE 实时事件计划

状态：Phase 0/1 已实现，未发布，等待验收  
目标版本：0.1.5（暂定，也可作为 0.2.0）  
服务端基线：FileHub 0.2.5（只读核对，不改服务端代码）  
范围：Android 客户端接入 `GET /api/v1/events`，替换可替换的轮询，补上实时任务、配额和版本状态。

## 1. 先明确服务端实际能力

不是按旧版 `docs/plans/v0.1.7-sse.md` 里的理想设计做，当前 0.2.5 实际是：

- `GET /api/v1/events`
  - Bearer Token 鉴权可用
  - `text/event-stream`
  - 心跳 20 秒
  - 只推送当前用户事件，管理员额外收到 admin 事件
- 事件格式：`id`、`event`、`data`（JSON 字符串），没有 `retry` 字段
- 没有 `Last-Event-ID` 补发环形缓冲
  - 客户端断线重连后只能收到新事件
  - 服务端广播积压时发 `resync`，客户端必须重新拉快照
- 实际已发事件：
  - `job.updated`：`id`、`kind`、`status`、`progress`、`total`、`error`
  - `usage.updated`：`used_bytes`、`quota_bytes`、`computed_at`
  - `roots.changed`：`user_id`，可选 `root_id`、`online`；远程 root 上线/下线、root 增加/删除/改序都会发
  - `updates.changed`：`latest`、`available`
  - `settings.changed`
  - `user.updated`、`user.features_changed`
  - `thumbnail.ready` / `thumbnail.failed`：`root`、`share`、`item`、`path`、`width`
  - `share.updated/revoked/uploaded/downloaded/visited`
  - `notification`
  - `admin.status`、`audit.appended`（Android 无管理页，不需要）
- 没有 `fs.changed`
- 没有独立的 `dir_size.updated`
  - `dir_size` 只表现为 `job.updated(kind=dir_size)`，且 payload 里没有 `root/path`
  - 因此现有目录大小轮询不能可靠改成事件驱动

结论：Android 这一期做“任务 + 配额 + 版本/设置 + 重连兜底”，不承诺目录自动刷新和目录大小实时更新。

## 2. 总体结构

新增 `data/events`：

- `ServerEvent`
  - `profileId`、`id`、`name`、`data: JsonObject`、`receivedAt`
- `SseParser`
  - 纯 Kotlin，逐行解析 SSE
  - 支持 `event:`、`data:`、`id:`、注释心跳、空行分发、多行 data、CRLF/LF
  - 限制单事件大小，防止异常服务端把内存打满
- `EventStreamClient`
  - App 进程内单例，放在 `AppContainer`
  - 自己持有 `SupervisorJob + Dispatchers.IO` 连接协程
  - 暴露：
    - `events: SharedFlow<ServerEvent>`
    - `state: StateFlow<EventStreamState>`
  - 同一时间只连当前 active profile，不按页面重复建连
- `EventStreamState`
  - `Idle`、`Connecting`、`Connected`、`Reconnecting(attempt, delayMs)`、`Unsupported`、`Unauthorized`
- `ServerEventRouter`（可选）
  - 把原始事件解析成 `JobUpdated`、`UsageUpdated`、`UpdatesChanged`、`Resync` 等强类型
  - 解析失败只记录 debug 事件名，不崩

连接生命周期：

- App 进入前台且存在 active profile：启动
- App 进入后台：关闭连接，不发常驻通知
- 切换服务器：关闭旧连接，重新读取新服务器 baseUrl 和 token
- 切换账号 / token 失效：停止并进入 `Unauthorized`
- 服务端返回 404/405：标记 `Unsupported`，回退轮询；同一 profile 不反复尝试，重启 App 或重新登录后再探测
- 前台恢复时先重连，成功后触发一次本地 `resync`（重新拉 usage、server version、当前目录）

## 3. 连接与重连细节

请求：

```http
GET /api/v1/events
Accept: text/event-stream
Cache-Control: no-cache
Authorization: Bearer <token>
```

OkHttp：

- 单独专用 client
  - `connectTimeout = 15s`
  - `readTimeout = 0`（SSE 长连接）
  - `retryOnConnectionFailure = false`（自己控制重连）
  - 不挂 HTTP 日志拦截器；debug 日志也只记录事件名和 id
- 读取 `response.body.source()`，逐行解析
- 应用层心跳看门狗：连续 60 秒没有任何字节/事件则主动断开重连
- 重连退避：1s、2s、4s、8s、16s、30s，带 20% 抖动
- 连接稳定 30 秒后把退避重置
- 401：不重连，进入 `Unauthorized`，通知 SessionRepository 标记需要重新登录
- 429：按 `Retry-After`（如果有）或 30 秒退避
- 5xx / 网络错误：正常退避
- 404 / 405：`Unsupported`，本轮 App 生命周期不再尝试

不做：

- 不发送无意义的 `Last-Event-ID`（服务端没有补发）
- 不把 SSE 放进 WorkManager 做后台常驻
- 不引入第三方 SSE 库

## 4. 事件接入

### Phase 1：任务、配额、版本（MVP）

**`job.updated`**

- `FilesViewModel` 收到后按 `id` 匹配 `activeJobs`
- 更新 status/progress/total/error
- 终态：
  - completed：刷新当前目录，1.2 秒后移除任务
  - failed：显示 `operationError`，1.2 秒后移除
  - cancelled：移除
- 与现有 `trackJob` 轮询共存：
  - SSE `Connected`：不再每 1.5 秒轮询，只做首次 `jobStatus` 基线和重连后一次性补偿
  - SSE 不可用：保持现有 1.5 秒轮询
  - 后台/断线：恢复连接后对仍在列表里的 job 各拉一次 `jobStatus`
- 匹配不到 id 的事件直接忽略（例如其他设备的任务）

**`usage.updated`**

- `AppViewModel` 直接更新 `_usage`
- 首次进入和服务切换仍主动 `loadUsage()`
- SSE 不可用时保留低频兜底（建议 30 秒或仅用户打开“我的”时拉一次）
- `computing` 状态如果服务端仍返回，按现有逻辑处理

**`roots.changed`**

- `AppViewModel` 收到后 debounce 300ms，调用 `refreshRoots()`
- 事件带 `root_id/online` 时先本地快速更新在线徽标，再拉完整 `/roots` 校准
- 完整刷新后按 `root.id` 保持当前选中 root；root 被删除时退回默认 root 或空态
- 如果当前正在浏览的 root 变为离线：保留已加载内容并显示离线状态，不强制关闭
- 服务端 root 顺序变化也会通过这条事件触发全量刷新；没有单独的 `roots.reordered`
- 该事件只影响 root 列表，不会让目录内文件列表实时变化

**`updates.changed`**

- 更新服务端版本/可用更新状态
- 触发现有 `loadServerVersion()`，客户端更新检查继续用 24 小时缓存

**`settings.changed` / `user.features_changed`**

- 重新读取 branding / features / roots
- 影响功能的字段变化时刷新对应状态，不做整页重建

**`resync`**

- 收到或重连成功：
  - 重新拉 usage
  - 重新拉 server version
  - 刷新当前文件目录（只刷新可见 root/path）
  - 对 activeJobs 做一次 `jobStatus` 补偿

### Phase 2：分享、缩略图、通知

**`share.*`**

- `ShareViewModel` / `SharesScreen` 打开时收集事件
- `share.updated/revoked`：更新列表条目或重新加载当前页
- `share.visited/downloaded/uploaded`：更新计数/最近活动，不整页刷新
- 页面关闭后不保留订阅

**`thumbnail.ready/failed`**

- 只有切换到 `wait=0` 异步缩略图后收益才明显
- `FilesViewModel` 按 `root + path` 命中当前目录时 bump `thumbVersions`
- `thumbnail.failed` 不要反复重试，回退类型图标
- 如果继续使用 `wait=1`，这一项暂不做

**`notification`**

- 应用内 snackbar / 未读状态先做内存态
- 不引入通知中心数据库
- 后台系统通知仍由 WorkManager/传输通知负责，不依赖 SSE

## 5. 明确不做 / 暂缓

- 服务端当前没有的事件不纳入计划；目录内容和目录大小维持现有加载/轮询
- `admin.status` / `audit.appended`：Android 没有管理页
- `edit.presence`：Android 暂不做协作编辑提示
- 公开分享 SSE：Android 公开分享页暂不接入
- 多实例 SSE / 事件补发缓冲：不属于 Android 范围

## 6. 安全

- Token 只放在 Authorization header，不拼 URL、不写日志
- 事件 data 可能包含路径/文件名，日志默认不打印
- 401 不自动循环登录，交给现有登录流程
- 只信任系统 CA，不改证书策略
- 单连接、单用户；不把其他 profile 的事件混入当前 UI
- 公开分享事件不接，避免 token 作用域混乱

## 7. 测试

单元测试：

- SSE parser：
  - 单行/多行 data
  - 注释心跳
  - CRLF/LF
  - 分块到达
  - 超长事件拒绝
- 重连策略：
  - 网络失败退避
  - 401 停止
  - 404 标记 Unsupported
  - 稳定后重置退避
- 事件路由：
  - job id 匹配/不匹配
  - usage 更新
  - roots.changed 快速更新在线状态、全量刷新、选中 root 保持
  - resync 触发快照刷新
  - 未知事件忽略
- `MockWebServer`：
  - 校验 Authorization / Accept 请求头
  - 流式返回多个事件
  - 连接中断后重连
  - 401 / 404 分支

真机/集成：

- 触发 ZIP/移动等任务，进度条实时更新，完成后目录刷新
- 上传后 usage 自动变化（如果服务端发 `usage.updated`）
- 远程 root 在另一台设备断开/恢复，本机 root 徽标在事件到达后更新
- 服务端调整 root 顺序，本机列表按新顺序刷新且不丢失当前选中 root
- 服务端版本更新时侧栏/我的页刷新
- 断网 10 秒→恢复，自动重连，不重复弹错误
- 切后台 5 分钟后回前台，重新连接并 resync
- 使用旧版服务端（无 `/events`）时正常回退，不影响现有功能
- 检查 logcat 无 token、无完整路径

## 8. 交付顺序

1. SSE parser + client + state + lifecycle（不接业务）
2. `job.updated` 替换/降频任务轮询
3. `usage.updated` + `roots.changed` + `updates.changed`
4. `resync`、401/404 兜底、MockWebServer 测试
5. 分享/缩略图/通知按需追加

建议先做 1–4 作为一版可验收的 SSE MVP，只接服务端已经存在的事件；目录内容和目录大小不在这一版改成实时。

## 9. 实现状态（0.1.5 未发布）

- 新增 `data/events/`：`SseParser`、`EventStreamClient`、`ServerEvent`/`EventStreamState` 和事件 payload
- `AppContainer` 持有进程内单例连接，跟随 `ProcessLifecycleOwner` 前台启停，并跟随 active profile 切换 target
- `FilesViewModel`：`job.updated` 实时更新；SSE 在线时任务轮询降到 15 秒，重连后补偿；`resync` 刷新当前目录
- `AppViewModel`：`usage.updated`、`roots.changed`（300ms 防抖 + 在线状态即时更新）、`updates.changed`、`settings.changed`、`user.features_changed`、`resync`
- root 删除或当前 root 从列表消失时，文件浏览退回 root 列表；root 顺序变化保留当前选中 root
- 401 停止事件流并标记需要重新登录；404/405 标记不支持并保留轮询兜底
- debug 构建只记录事件名和 id，不记录 token 或路径

## 10. 风险

- 服务端没有事件补发，断线期间的变化只能 resync 快照，不能还原每一条
- 反代缓冲会让事件延迟；靠 20 秒心跳、60 秒看门狗和重连兜底，但不能保证所有部署都实时
- `job.updated` 没有 root/path，只能按 job id 匹配，不能按目录过滤
- 后台不保持 SSE，传输进度在后台仍以 WorkManager/通知为准
- SSE 连接本身会增加一点待机电量，因此必须前台才连
- 旧服务端和代理可能返回 200 但长期无事件，需要看门狗和降级
