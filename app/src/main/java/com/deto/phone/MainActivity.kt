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

/**
 * Deto Phone — iPhone-style dialer.
 *
 * The whole UI is a crystal-glass web page in app/src/main/assets/index.html,
 * shown in a full-screen WebView. The page calls window.Android.placeCall(number)
 * which this Activity turns into a real cellular call via Android telephony.
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var pendingNumber: String? = null

    companion object {
        private const val REQ_CALL = 101
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Solid black system bars so the real status bar never overlaps the UI.
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

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
                // Fallback: if a tel: link is used anywhere, dial it.
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    if (url.startsWith("tel:")) {
                        dial(Uri.decode(url.removePrefix("tel:")))
                        return true
                    }
                    return false
                }
            }
            addJavascriptInterface(WebBridge(), "Android")
            loadUrl("file:///android_asset/index.html")
        }
        setContentView(webView)
    }

    /** Exposed to JavaScript as `window.Android`. */
    inner class WebBridge {
        @JavascriptInterface
        fun placeCall(number: String) {
            // JS interface runs off the UI thread — hop back before touching the UI.
            runOnUiThread { dial(number) }
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
            // Ask for permission; meanwhile open the system dialer pre-filled so the
            // user is never stuck. Once granted we place the call directly next time.
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CALL_PHONE), REQ_CALL
            )
            openDialer(number)
        }
    }

    private fun startCall(number: String) {
        try {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
            notifyJs("Calling $number…")
        } catch (e: SecurityException) {
            openDialer(number)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to place call", Toast.LENGTH_SHORT).show()
        }
    }

    /** Opens the system dialer with the number filled in (no permission needed). */
    private fun openDialer(number: String) {
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
        } catch (e: Exception) {
            Toast.makeText(this, "No dialer available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun notifyJs(message: String) {
        val safe = message.replace("'", "\\'")
        webView.post {
            webView.evaluateJavascript(
                "window.detoOnCallStatus && window.detoOnCallStatus('$safe');", null
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CALL &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            pendingNumber?.let { startCall(it) }
        }
    }

    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
