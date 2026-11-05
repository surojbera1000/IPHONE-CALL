package com.deto.phone

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Deto Phone — iPhone-style dialer.
 *
 * The UI is a crystal-glass web page (app/src/main/assets/index.html) shown in a
 * full-screen, edge-to-edge WebView. The page calls window.Android.placeCall(number),
 * which this Activity turns into a real cellular call via Android telephony.
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var pendingNumber: String? = null
    private var lastInsets: Insets? = null

    companion object {
        private const val REQ_CALL = 101
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ----- Edge-to-edge: draw the UI behind transparent system bars -----
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            // dark UI -> keep white status/nav icons
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            with(settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                @Suppress("DEPRECATION")
                allowFileAccess = true
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    if (url.startsWith("tel:")) {
                        dial(Uri.decode(url.removePrefix("tel:")))
                        return true
                    }
                    return false
                }
                override fun onPageFinished(view: WebView, url: String?) {
                    applyInsetsToPage()
                }
            }
            addJavascriptInterface(WebBridge(), "Android")
            loadUrl("file:///android_asset/index.html")
        }
        setContentView(webView)

        // Feed the real safe-area insets into the page as CSS variables so the
        // web UI can lay itself out edge-to-edge correctly.
        ViewCompat.setOnApplyWindowInsetsListener(webView) { _, insets ->
            lastInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            applyInsetsToPage()
            insets
        }
    }

    private fun pxToCss(px: Int): Float = px / resources.displayMetrics.density

    private fun applyInsetsToPage() {
        val i = lastInsets ?: return
        val js = buildString {
            append("document.documentElement.style.setProperty('--safe-top','${pxToCss(i.top)}px');")
            append("document.documentElement.style.setProperty('--safe-bottom','${pxToCss(i.bottom)}px');")
            append("document.documentElement.style.setProperty('--safe-left','${pxToCss(i.left)}px');")
            append("document.documentElement.style.setProperty('--safe-right','${pxToCss(i.right)}px');")
            append("document.body && document.body.classList.add('native');")
        }
        webView.post { webView.evaluateJavascript(js, null) }
    }

    /** Exposed to JavaScript as `window.Android`. */
    inner class WebBridge {
        @JavascriptInterface
        fun placeCall(number: String) {
            runOnUiThread { dial(number) } // JS interface runs off the UI thread
        }
    }

    private fun dial(rawNumber: String) {
        val number = rawNumber.filter { it.isDigit() || it in "+*#" }
        if (number.isEmpty()) return
        pendingNumber = number

        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            startCall(number)
        } else {
            // Ask once; place the call as soon as it's granted (see callback below).
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CALL_PHONE), REQ_CALL
            )
        }
    }

    private fun startCall(number: String) {
        try {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
        } catch (e: SecurityException) {
            openDialer(number)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to place call", Toast.LENGTH_SHORT).show()
        }
    }

    /** System dialer with the number pre-filled (no permission needed). */
    private fun openDialer(number: String) {
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
        } catch (e: Exception) {
            Toast.makeText(this, "No dialer available", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CALL) {
            val num = pendingNumber ?: return
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCall(num)           // granted -> place the real call
            } else {
                openDialer(num)          // denied  -> fall back to the system dialer
            }
        }
    }

    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
