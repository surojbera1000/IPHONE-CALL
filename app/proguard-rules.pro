# Keep the JavaScript bridge reachable from the WebView.
-keepclassmembers class com.deto.phone.MainActivity$WebBridge {
    @android.webkit.JavascriptInterface <methods>;
}
