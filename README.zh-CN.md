# FileHub Android

[English](README.md) | 简体中文

[FileHub](https://github.com/Llesue/filehub) 的原生 Android 客户端。

当前版本：**v0.1.0**，首个公开版本。下载：[GitHub Releases](https://github.com/Llesue/filehub-android/releases/latest)。

- Kotlin + Jetpack Compose（Material 3），`minSdk 26`、`compileSdk 37.1`、`targetSdk 36`
- OkHttp + kotlinx.serialization、Room、WorkManager、Media3、Coil
- 构建在远程 `parasite` 主机执行，不支持本机构建

## 功能

- 多 FileHub 实例；账号密码登录后自动创建设备 API Token，保存在 Android Keystore
- 多地址故障切换，按配置顺序逐个尝试（内网地址建议放最上面）
- 本地 root 与远程挂载、紧凑文件列表、目录大小、游标分页、缩略图
- 新建、重命名、删除（带确认码）、复制/移动、ZIP 打包/解压
- 全库搜索 / 当前 root 搜索
- 图片预览：左右翻页、双击 1:1 放大、全屏模糊背景、EXIF、下载
- Media3 视频/音频播放，标题与横屏按钮跟随播放器控制栏显示/隐藏
- Markdown 预览：表格、任务列表、删除线、链接
- 可恢复的后台上传/下载、通知、传输中心
- 相册同步：选择相册、包含视频、保留拍摄时间、照片/视频分别设置仅 Wi-Fi
- 目录同步：SAF 文件夹、上传/下载/双向、三方基线、冲突不覆盖
- 每个服务器独立同步配置；切换服务器不会停止其他服务器的定时任务
- 同步目标支持远程目录选择器，也允许直接输入路径
- 分享：密码、有效期、只读、二维码、公开分享查看器
- 主题和语言覆盖（跟随系统 / 简体中文 / English），edge-to-edge
- 自适应启动图标，源文件见 [`docs/app-icon.svg`](docs/app-icon.svg)

## 后台行为

同步使用 WorkManager 周期任务（最小 15 分钟，默认每天），受 Wi-Fi/充电约束。
不需要 App 保持打开，但 Android 和厂商限制仍然有效：

- 在系统设置里强制停止 App，会停止后台任务，直到重新打开
- 小米/HyperOS 可能延迟或阻止任务；建议省电策略设为“无限制”、允许自启动和通知
- Doze 和应用待机可能延迟周期任务，只能尽力而为，不保证准点
- 手动「立即同步」同样遵守 Wi-Fi/充电约束
- 超过一个周期没同步时，打开 App 会补跑一次

设备令牌过期或配置的目标位置失效时，该同步会自动关闭，直到重新配置。

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

## 文档

- [`PLAN.md`](PLAN.md) — 范围、架构、里程碑
- [`AGENTS.md`](AGENTS.md) — 工程约束
- [`docs/ui-spec.md`](docs/ui-spec.md) — UI 方案
- [`docs/sync-spec.md`](docs/sync-spec.md) — 相册/目录同步设计
- [`docs/app-icon.svg`](docs/app-icon.svg) — 图标源文件
- [`docs/release-notes/v0.1.0.md`](docs/release-notes/v0.1.0.md) — 发布说明
- [`CHANGELOG.md`](CHANGELOG.md)
