package com.pimobile.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = androidx.compose.ui.graphics.Color(0xFF0F3460),
                    secondary = androidx.compose.ui.graphics.Color(0xFFE94560),
                    background = androidx.compose.ui.graphics.Color(0xFF1A1A2E),
                    surface = androidx.compose.ui.graphics.Color(0xFF16213E),
                    onPrimary = androidx.compose.ui.graphics.Color.White,
                    onSecondary = androidx.compose.ui.graphics.Color.White,
                    onBackground = androidx.compose.ui.graphics.Color.White,
                    onSurface = androidx.compose.ui.graphics.Color.White,
                )
            ) {
                val navController = rememberNavController()
                NavHost(navController, startDestination = "connect") {
                    composable("connect") {
                        ConnectScreen(
                            onConnected = { url -> navController.navigate("webview/$url") }
                        )
                    }
                    composable("webview/{url}") { backStackEntry ->
                        val url = backStackEntry.arguments?.getString("url") ?: return@composable
                        PiWebView(url, onDisconnect = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectScreen(onConnected: (String) -> Unit) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("30142") }
    var token by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "π Pi Mobile",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Connect to your Pi coding agent",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Tailscale IP / Hostname") },
                placeholder = { Text("100.x.x.x or hostname.ts.net") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.secondary,
                    cursorColor = MaterialTheme.colorScheme.secondary,
                ),
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text("Port") },
                placeholder = { Text("30142") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.secondary,
                    cursorColor = MaterialTheme.colorScheme.secondary,
                ),
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Auth Token (optional)") },
                placeholder = { Text("Bearer token if set") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.secondary,
                    cursorColor = MaterialTheme.colorScheme.secondary,
                ),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    testing = true
                    error = null
                    val cleanHost = host.trim().removePrefix("http://").removePrefix("https://")
                    val url = "http://$cleanHost:$port"
                    // Quick connectivity test
                    Thread {
                        try {
                            val conn = java.net.URL("$url/health").openConnection() as java.net.HttpURLConnection
                            conn.connectTimeout = 5000
                            conn.readTimeout = 5000
                            if (token.isNotBlank()) {
                                conn.setRequestProperty("Authorization", "Bearer $token")
                            }
                            val code = conn.responseCode
                            testing = false
                            if (code == 200) {
                                onConnected(url)
                            } else {
                                // Still try connecting even if health fails
                                testing = false
                                onConnected(url)
                            }
                        } catch (e: Exception) {
                            testing = false
                            error = "Connection failed: ${e.message}"
                        }
                    }.start()
                },
                enabled = host.isNotBlank() && !testing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                ),
            ) {
                    if (testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onSecondary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Connect")
                    }
                }

            if (error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Make sure pi-web is running on your laptop:\npi-mobile-server",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PiWebView(url: String, onDisconnect: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf("Pi Mobile") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDisconnect) {
                        Text("← Disconnect", color = MaterialTheme.colorScheme.secondary)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }

            // WebView
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )

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
                            // Enable modern web features
                            loadWithOverviewMode = true
                            useWideViewPort = true
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                loading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                return false // Load all URLs inside WebView
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, t: String?) {
                                title = t ?: "Pi Mobile"
                            }
                        }

                        loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
