package com.pimobile.app

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
class MainActivity : Activity() {
    private var webView: WebView? = null
    private var connectView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("pi-mobile", Context.MODE_PRIVATE)
        val app = PiApp.get(this)

        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.parseColor("#1A1A2E"))
        }

        // ── 复用 Application 级单例 WebView ──────────────────────────────
        // 进程存活期间，WebView 跨 Activity 实例存活：
        // - 转屏（configChanges 声明后 Activity 不重建，WebView 不销毁）
        // - 从最近任务返回（进程存活时 Activity 可能重建，但 WebView 复用）
        // 关键：只在没有加载过任何 URL 时才 loadUrl，避免覆盖上次的页面状态。
        val reused = app.retainedWebView != null
        webView = (app.retainedWebView ?: WebView(this).also { app.retainedWebView = it }).apply {
            // 若之前 attach 到别的父 ViewGroup，先移除
            (parent as? ViewGroup)?.removeView(this)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            if (!reused) {
                // 首次创建：配置 settings + webViewClient
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    builtInZoomControls = true
                    displayZoomControls = false
                    setSupportZoom(true)
                    userAgentString = settings.userAgentString.replace(
                        "Android", "PiMobile/1.0 Android"
                    )
                    loadWithOverviewMode = true
                    useWideViewPort = true
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) = Unit
                }
            }
        }
        root.addView(webView)

        // Connect screen (initially hidden if URL saved OR WebView already loaded)
        connectView = createConnectView(prefs)
        root.addView(connectView)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        setContentView(root)

        val savedUrl = prefs.getString("url", null)
        // 只在"从未加载过"时才 loadUrl；复用情况下 WebView 保留着上次的页面，不动它
        val currentUrl = webView?.url
        val needLoad = currentUrl.isNullOrBlank() && savedUrl != null
        Log.d("PiMobile", "onCreate: reused=$reused currentUrl=$currentUrl needLoad=$needLoad")
        if (needLoad && savedUrl != null) {
            connectView?.visibility = View.GONE
            webView?.loadUrl(savedUrl)  // smart-cast: 非空
        } else if (reused && !currentUrl.isNullOrBlank()) {
            // WebView 已有页面，直接隐藏连接屏
            connectView?.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        // 恢复 WebView 定时器与渲染，避免后台时 JS 定时器空转耗电
        webView?.onResume()
        webView?.resumeTimers()
    }

    override fun onPause() {
        super.onPause()
        // 暂停 WebView 定时器与渲染（省电）。注意：这会暂停 pi-web 内的 EventSource 吗？
        // EventSource 是网络流，pauseTimers 暂停的是 JS 定时器，不直接断开 SSE 连接。
        // 但 App 切后台后 WebView 进程可能被冻结，SSE 会被系统暂停 —— 这是后续后台
        // 通知方案（specialUse FGS + 原生 OkHttp SSE）要解决的问题。
        webView?.onPause()
        webView?.pauseTimers()
    }

    private fun createConnectView(prefs: android.content.SharedPreferences): View {
        val accent = 0xFFE94560.toInt()

        val wrapper = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.parseColor("#1A1A2E"))
        }

        val column = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = android.view.Gravity.CENTER }
            orientation = LinearLayout.VERTICAL
        }
        wrapper.addView(column)

        val title = TextView(this).apply {
            text = "π Pi Mobile"
            textSize = 28f
            setTextColor(0xFF0F3460.toInt())
        }
        column.addView(title)

        val subtitle = TextView(this).apply {
            text = "Connect to pi-web on your laptop"
            textSize = 14f
            setTextColor(0xB3FFFFFF.toInt())
        }
        column.addView(subtitle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        // Spacer
        column.addView(createSpacer(dp(32)))

        val hostInput = EditText(this).apply {
            hint = "100.x.x.x or hostname.ts.net"
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0x80FFFFFF.toInt())
            setBackgroundColor(0x33FFFFFF.toInt())
        }
        column.addView(hostInput, LinearLayout.LayoutParams(
            dp(300), ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })

        val portInput = EditText(this).apply {
            hint = "30141"
            setText("30141")
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0x80FFFFFF.toInt())
            setBackgroundColor(0x33FFFFFF.toInt())
        }
        column.addView(portInput, LinearLayout.LayoutParams(
            dp(300), ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })

        column.addView(createSpacer(dp(24)))

        val connectBtn = Button(this).apply {
            text = "Connect"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(accent)
            setOnClickListener {
                val host = hostInput.text.toString().trim()
                if (host.isBlank()) return@setOnClickListener
                val port = portInput.text.toString().trim().ifEmpty { "30141" }
                val url = "http://$host:$port"
                connectView?.visibility = View.GONE
                prefs.edit().putString("url", url).apply()
                webView?.loadUrl(url)
            }
        }
        column.addView(connectBtn, LinearLayout.LayoutParams(
            dp(300), dp(48)
        ).apply { topMargin = dp(12) })

        val footer = TextView(this).apply {
            text = "Enter your pi-web server address to connect"
            textSize = 12f
            setTextColor(0x80FFFFFF.toInt())
            gravity = android.view.Gravity.CENTER
        }
        column.addView(footer, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(24) })

        return wrapper
    }

    private fun createSpacer(h: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, h)
        }
    }

    private fun dp(n: Int): Int {
        val scale = resources.displayMetrics.density
        return (n * scale + 0.5f).toInt()
    }

    private fun applyFullscreen() {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullscreen()
    }

    // 不在 onDestroy 里 destroy WebView —— 它是 Application 级单例，要跨 Activity 存活。
    // 系统会在进程销毁时自动清理。
}
