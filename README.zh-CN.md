# FileHub Android

[English](README.md) | 简体中文

[FileHub](https://github.com/Llesue/filehub) 是一个自托管文件管理器：服务端跑在自己的 NAS 或 Linux 主机上，通过网页或原生客户端访问文件。本项目是 FileHub 的原生 Android 客户端。

它主要面向同时运行一个或多个 FileHub 实例的用户：在一台手机上管理所有服务器，自动备份相册和文件夹，并在手机、平板和车机上使用同一套客户端。

- Kotlin + Jetpack Compose（Material 3），`minSdk 26`、`compileSdk 37.1`、`targetSdk 36`
- OkHttp + kotlinx.serialization、Room、WorkManager、Media3、Coil

## 侧重点

- **多个 FileHub 实例。** 可以添加多台服务器，切换时不需要退出登录；每台服务器有独立的设备 Token、目录、同步配置和定时任务。
- **像备份一样的相册同步。** 选择相册、包含视频、保留拍摄时间和 GPS EXIF、内容哈希、上传校验、跳过未变更文件，并在冲突时不覆盖任何一边。
- **任意 SAF 文件夹的目录同步。** 支持上传、下载、双向同步，三方基线、逐文件冲突处理和可选的删除同步。
- **一套客户端适配多种设备。** 同一份安装包适配手机竖屏、手机横屏、平板和车机，用布局利用可用宽度，而不是把手机界面横向拉满。

## 功能

### 服务器与登录

- 多个 FileHub 账号；密码登录后为每台设备创建独立 API Token，保存在 Android Keystore
- 每台服务器可配置多个地址，严格按从上到下的顺序尝试，内网可用时不会跳到公网
- 设备 Token 过期时打开预填地址和用户名的重新登录页；保留 profile ID，同步任务和缓存继续有效
- 服务器卡片显示版本徽标和“需要重新登录”状态
- 手机、横屏和平板都支持长按拖动服务器卡片排序

### 文件与预览

- 本地 root、远程挂载、目录大小、游标分页和缩略图
- 新建、重命名、带确认码和倒计时的删除、复制/移动、ZIP 打包/解压，支持后台任务进度和取消
- 全库搜索或当前 root 搜索，显示索引状态并支持一键建立索引
- 图片预览：左右翻页、锚点双指缩放、EXIF、原图/预览切换
- Media3 视频和音频播放，支持后台音频会话
- PDF 查看器：按页适配缩放、页码栏
- Markdown 预览：表格、任务列表、删除线、链接
- 文本预览和编辑，支持 JSON、XML、HTML、CSS、SQL 等语法高亮

### 同步

- 相册同步：选择相册、可选视频、保留拍摄时间、照片/视频分别设置仅 Wi-Fi、仅充电
- MediaStore 水位的增量扫描；未变更文件直接跳过，不再逐张请求哈希
- SHA-256 内容哈希、上传校验、同名内容比较、同大小跨文件名去重
- 通过 `ACCESS_MEDIA_LOCATION` 和 `MediaStore.setRequireOriginal` 保留照片 GPS EXIF
- 目录同步：SAF 选择文件夹、上传/下载/双向、三方基线、冲突不覆盖、删除同步需要显式确认
- 冲突页逐文件选择“保留手机”或“保留服务器”，处理后自动重新同步
- 同步运行状态、实时步骤、有界日志、通知（停止/取消/继续/重试）和隐私安全的锁屏版本
- 同步频率支持 15 分钟到每周，或仅手动；每台服务器独立配置
- 应用启动时自动检查和重建丢失的周期任务；设置页显示真实调度状态和下次运行时间

### 分享

- 密码、有效期、只读权限
- 二维码卡片和稳定链接
- 应用内公开分享查看器
- 停止分享而不删除记录；过期分享可重新启用并延长一天
- 分享卡片显示相对过期时间；停止或过期后操作置灰

### 设备与界面

- 手机竖屏：单列卡片和底部导航
- 手机横屏：左侧固定栏显示服务器、root、分享、配额和版本，右侧显示内容
- 平板和车机：宽屏双栏，左侧 root 侧栏，右侧自适应文件浏览器
- 三种模式下服务器卡片和选择器使用同一套布局
- 文件、root 和分享网格自适应列数；宽屏表单居中限宽
- 主题（跟随系统/浅色/深色）、语言（跟随系统/简体中文/English）、edge-to-edge 和自适应启动图标

## 后台行为

同步使用 WorkManager 周期任务，最小间隔 15 分钟。App 界面不需要保持打开，但 Android 和厂商限制仍然有效：

- 应用启动时会检查并重建周期任务，包括升级或强制停止之后
- 同步设置页显示任务是否已排定、下次运行时间，未排定时可以一键重建
- 小米/HyperOS 仍可能延迟或阻止后台任务；建议省电策略设为“无限制”，允许自启动和通知
- Doze 和应用待机可能延迟运行；定时任务只能尽力而为，不保证准点
- 手动同步同样遵守配置的 Wi-Fi 和充电条件
- 设备 Token 或目标目录失效时，该同步会暂停，直到重新配置

## 构建

```bash
./scripts/provision-sdk.sh          # 构建机首次执行
./scripts/build-remote.sh           # debug APK -> dist/
./scripts/build-remote.sh check     # lint + 单元测试
./scripts/build-remote.sh release   # 有 keystore.properties 时输出签名 release APK
```

release 和 debug APK 都在 `dist/`。

## Release 签名

Release 构建读取仓库根目录的 `keystore.properties`（已 gitignore），指向仓库外的 keystore，并由
`scripts/build-remote.sh` 同步到构建机。keystore 和密码备份必须放在 Git 之外。

签名证书 SHA-256：
`F3:FB:0B:7C:94:43:79:1F:0C:A2:B2:B8:E4:2F:91:B0:09:23:7E:6F:B2:B0:95:F3:21:41:82:3B:0D:B1:7E:71`

## 许可证

- 内置 **JetBrains Mono** 字体：SIL Open Font License 1.1，见 `licenses/JetBrainsMono-OFL.txt`。
- 语法高亮：[`dev.snipme:highlights`](https://github.com/SnipMeDev/Highlights)，Apache License 2.0。
