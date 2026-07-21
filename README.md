# pi-mobile

**手机端远程使用 [pi](https://github.com/agegr/pi) 编码助手的 Android 全屏 WebView 壳。**

连接你笔记本上运行的 [pi-web](https://github.com/agegr/pi-web) 服务，在手机上获得与桌面一致的 pi 编程体验。pi-mobile 本身只是一个极简的 WebView 容器——不做任何 UI 重写，不代理你的代码或对话，所有 AI 能力都由你自己的 pi 实例提供。

```
┌─────────────────────────────┐
│        pi-mobile            │
│  (Android WebView 壳)       │
└──────────────┬──────────────┘
               │  HTTP (WebView 加载页面)
               │  SSE  (后台任务完成通知)
               │  局域网 / Tailscale / 隧道
               ▼
┌─────────────────────────────┐
│        pi-web               │
│  (运行在你的笔记本 / VPS)   │
└──────────────┬──────────────┘
               │
               ▼
┌─────────────────────────────┐
│   pi (AI 编码 Agent)        │
│   你自己的 key，你自己的账  │
└─────────────────────────────┘
```

---

## 功能

- **全屏沉浸 WebView** — `IMMERSIVE_STICKY` + `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`，WebView 铺满整块屏幕（包括状态栏后方），在三星等机型也能画到刘海后面
- **连接屏** — 输入 host:port + 选择 http/https 协议即可连接 pi-web，默认端口 `7777`
- **自动重连** — 上次连接的 URL 存入 SharedPreferences，下次打开 App 直接加载，无需重复输入
- **WebView 跨重建保活** — WebView 实例由 `Application` 单例持有，转屏 / 从最近任务返回时不重载页面（保留 JS 运行时、滚动位置、会话状态）
- **返回键导航** — 优先回退 WebView 浏览历史，无历史时退到后台保活（不杀进程）
- **后台任务完成通知** — App 在后台时，`specialUse` 前台服务通过 SSE 长连接监听 pi-web 的 `/api/agent/running/events`，任务完成时推送通知，正文显示该任务的原始 prompt，让你知道**哪个**任务完成了
- **安全 WebView 配置** — 关闭 `allowFileAccess` / `allowContentAccess`，开启 `domStorage`，自定义 UA `PiMobile/1.0`
- **明文 HTTP 支持** — `usesCleartextTraffic="true"`，方便局域网直连

---

## 快速开始

### 1. 在笔记本上启动 pi-web

确保 pi-web 正在运行并监听可访问的地址（例如 `0.0.0.0:7777`）。具体启动方式见 [pi-web 文档](https://github.com/agegr/pi-web)。

### 2. 安装 pi-mobile

```bash
cd android && ./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 3. 连接

打开 App，在连接屏选择协议（http / https），输入：
- **Host** — 笔记本的局域网 IP（如 `192.168.1.100`）或 Tailscale IP（如 `100.x.x.x`）或隧道域名
- **Port** — pi-web 端口，默认 `7777`

点 **Connect**，WebView 全屏加载 pi-web，开始使用。首次会请求通知权限（用于后台任务完成通知）。

---

## 连接方式

| 场景 | 协议 | Host 示例 | 说明 |
|---|---|---|---|
| 局域网 | http | `192.168.1.100` | 同一 WiFi 下直连，最简单 |
| Tailscale | http | `100.x.x.x` | 跨网络、端到端加密，推荐远程使用 |
| Cloudflare Tunnel / ngrok | https | `my-pi.trycloudflare.com` | 端口填 443，选 https 协议 |

---

## 后台任务完成通知

App 退到后台时自动启动 `specialUse` 前台服务，通过 OkHttp SSE 长连接监听 pi-web 的 `GET /api/agent/running/events`：

```
[App 退后台] → 启动 SSE 服务（specialUse FGS 保活）
  → 收到 running=true（任务开始，记住 session id）
  → 收到 running=false（持续 3s 去抖）
  → 后台拉 GET /api/sessions，匹配刚结束的 session
  → 取 firstMessage（你的原始 prompt）作为通知正文
  → 推送通知 "✅ <prompt> 完成"
[App 回前台] → 停止 SSE 服务
```

- **pi-web 零改动** — 使用其已有的全局 SSE 端点（无鉴权、无需 session id）
- **specialUse FGS** — 不受 Android 15 的 `dataSync` 6h 限制，适合长期保活 SSE 长连接
- **去抖动** — 任务结束状态持续 3s 才发通知，避免短暂中间态误报
- **富通知** — 通知正文显示刚结束任务的原始 prompt（截断 50 字）；拉取失败回退到通用"任务完成"

---

## 项目结构

```
pi-mobile/
├── android/
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/pimobile/app/
│   │   │   │   ├── MainActivity.kt      # 连接屏 + 全屏 WebView + 前后台检测（~230 行）
│   │   │   │   ├── PiApp.kt             # Application 单例：跨 Activity 保活 WebView
│   │   │   │   └── PiSseService.kt      # specialUse FGS：SSE 监听 + 任务完成通知
│   │   │   ├── AndroidManifest.xml
│   │   │   └── res/                     # 启动图标 + 主题
│   │   ├── build.gradle.kts
│   │   └── debug.keystore              # 固定 debug 签名（CI/本地一致，install -r 覆盖升级）
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── gradle/wrapper/
├── scripts/
│   └── build-apk.sh                     # 封装 gradle assembleDebug
├── package.json
└── README.md
```

三个 Kotlin 文件，没有 Fragment / Compose / DI——pi-web 升级时无需改 App。

---

## 技术栈

| 项 | 值 |
|---|---|
| 语言 | Kotlin |
| 最低 Android | 8.0（API 26） |
| 目标 Android | 14（API 34） |
| 构建 | Gradle 8.5 + AGP 8.2.2 |
| WebView 依赖 | `androidx.webkit:webkit:1.9.0`、`androidx.core:core-ktx:1.12.0` |
| 通知依赖 | `okhttp:4.12.0`、`okhttp-sse:4.12.0`、`lifecycle-process:2.7.0` |
| 前台服务类型 | `specialUse`（声明 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`） |
| 权限 | `INTERNET`、`ACCESS_NETWORK_STATE`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_SPECIAL_USE`、`POST_NOTIFICATIONS`、`WAKE_LOCK` |
| 应用 ID | `com.pimobile.app` |
| 版本 | 1.0.0 |

---

## 构建

需要本地配置 Android SDK（`android/local.properties` 里写 `sdk.dir=...`，该文件被 gitignore）。

```bash
# 方式一：脚本封装
sh scripts/build-apk.sh

# 方式二：直接 gradle
cd android && ./gradlew assembleDebug
```

产物：`android/app/build/outputs/apk/debug/app-debug.apk`

仓库内含固定 `debug.keystore`，CI 与本地用同一签名，`adb install -r` 可直接覆盖升级（不必先 uninstall，保留已保存的连接地址）。

---

## 许可

Apache 2.0
