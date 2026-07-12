# pi-mobile

把 Pi 编码助手装进口袋。

## 架构

```
┌─ 手机 ──────────────────────────────────┐
│                                          │
│  Chrome PWA / Android WebView App       │
│       │                                  │
│       │ Tailscale                        │
└───────┼──────────────────────────────────┘
        │
┌───────┼────────── 笔记本 ─────────────────┐
│       ▼                                   │
│  pi-mobile-wrapper server (端口 30142)    │
│    │  ← PWA manifest + 代理               │
│    ▼                                      │
│  pi-web (端口 30141)                      │
│    │  ← Next.js Web UI                    │
│    ▼                                      │
│  Pi (RPC / SDK)                           │
│    └─ RTK, codegraph, context-mode...     │
└───────────────────────────────────────────┘
```

## 快速开始（PWA 模式，今天就能用）

### 笔记本上

```bash
cd pi-mobile
node server/index.js
```

输出：

```
╔══════════════════════════════════════════════╗
║  🚀 Pi Mobile                               ║
║                                              ║
║  Web UI:  http://localhost:30141             ║
║  Mobile:  http://localhost:30142 (PWA proxy) ║
║                                              ║
║  On your phone (Tailscale):                  ║
║  http://100.x.x.x:30142                      ║
╚══════════════════════════════════════════════╝
```

### 手机上

1. 手机连 Tailscale
2. Chrome 打开 `http://<笔记本 Tailscale IP>:30142`
3. 点菜单 → **添加到主屏幕**
4. 现在它像一个原生 App——全屏、无地址栏、独立窗口

## Android WebView App（可选）

如果需要真正的 APK：

### 环境要求

- Android Studio Hedgehog (2023.1.1+) 或 Gradle 8.5+
- Android SDK 34
- JDK 17

### 编译

```bash
cd pi-mobile/android
./gradlew assembleDebug
# APK 在 app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 使用

1. 打开 App
2. 输入笔记本的 Tailscale IP 和端口 (30142)
3. 点 Connect
4. WebView 加载 pi-web，开始用 Pi

## Tailscale 设置

```
笔记本: tailscale up
手机:   安装 Tailscale App，登录同一账号

笔记本 IP: tailscale ip -4  # 记下这个 IP
```

## 配置

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `PI_MOBILE_PORT` | 30142 | PWA 代理端口 |
| `PI_WEB_PORT` | 30141 | pi-web 端口 |
| `PI_MOBILE_HOST` | 0.0.0.0 | 绑定地址 |

## 项目结构

```
pi-mobile/
├── server/               # PWA 代理服务器
│   ├── index.js          # 启动 pi-web + 注入 PWA 支持
│   └── public/icons/     # PWA 图标 (SVG)
├── android/              # Android WebView App
│   ├── app/
│   │   └── src/main/java/com/pimobile/app/
│   │       └── MainActivity.kt  # 连接屏 + WebView
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── scripts/
├── package.json
└── README.md
```
