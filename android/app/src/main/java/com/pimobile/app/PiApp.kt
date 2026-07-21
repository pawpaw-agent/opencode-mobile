package com.pimobile.app

import android.app.Application
import android.content.Context
import android.webkit.WebView

/**
 * Application 级单例 WebView 持有器。
 *
 * 目的：让 WebView 跨 Activity 实例存活。Activity 因配置变化/从最近任务返回而重建时，
 * 复用同一个 WebView 实例，避免重新 loadUrl —— pi-web 的 Next.js bundle 不必重新下载/解析/执行，
 * 滚动位置、JS 运行时、会话状态全部保留。
 *
 * 注意：仅进程存活期间有效。进程被系统杀死后，WebView 实例随之销毁，下次仍需重新加载
 * （此为系统行为，无解；但 specialUse 前台服务保活可降低进程被杀频率）。
 */
class PiApp : Application() {

    var retainedWebView: WebView? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        @Volatile
        private var instance: PiApp? = null

        fun get(context: Context): PiApp =
            instance ?: (context.applicationContext as PiApp)
    }
}
