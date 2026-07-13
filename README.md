# pi-mobile

手机端 Pi 编码助手 — Android WebView App。

## 构建

```bash
cd android && ./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

打开 App，输入 pi-web 服务器地址，点 Connect。

## 项目结构

```
pi-mobile/
├── android/
│   ├── app/src/main/java/com/pimobile/app/
│   │   └── MainActivity.kt
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── package.json
└── README.md
```
