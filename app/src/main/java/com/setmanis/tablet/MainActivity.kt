package com.setmanis.tablet

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.integration.android.IntentIntegrator

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var prefs: SharedPreferences

    private val PREFS = "setmanis_tablet_prefs"
    private val KEY_QR = "qr_value"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tablet ekranı kapanmasın
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)
        web = findViewById(R.id.web)
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        enterKioskFullscreen()

        val saved = prefs.getString(KEY_QR, null)
        if (saved.isNullOrBlank()) {
            startQrScan()
        } else {
            openUrl(saved.trim())
        }
    }

    private fun enterKioskFullscreen() {
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    private fun startQrScan() {
        val integrator = IntentIntegrator(this)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        integrator.setPrompt("QR kodu okut")
        integrator.setBeepEnabled(true)
        integrator.setOrientationLocked(true)
        integrator.initiateScan()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            val content = result.contents?.trim()
            if (content.isNullOrBlank()) {
                showRetryDialog("QR okutma iptal edildi. Tekrar dene?")
            } else {
                prefs.edit().putString(KEY_QR, content).apply()
                openUrl(content)
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun showRetryDialog(msg: String) {
        AlertDialog.Builder(this)
            .setMessage(msg)
            .setPositiveButton("Tekrar") { _, _ -> startQrScan() }
            .setNegativeButton("Kapat") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openUrl(raw: String) {
        // Token/base yok. QR ne okutursa, onu açıyoruz.
        val url = raw.trim()

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(web, true)

        web.isFocusable = true
        web.isFocusableInTouchMode = true
        web.requestFocus()

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true

            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            useWideViewPort = true
            loadWithOverviewMode = true

            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false

            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)

            mediaPlaybackRequiresUserGesture = false
            userAgentString = userAgentString + " SetmaniSTablet/1.0"
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                // Kiosk: web izinlerini otomatik onayla (kamera/konum vb.)
                request.grant(request.resources)
            }
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return false
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                // Sertifika hatalarında devam et
                handler?.proceed()
            }

            override fun onPageFinished(view: WebView, url: String) {
                CookieManager.getInstance().flush()
                super.onPageFinished(view, url)
            }
        }

        web.loadUrl(url)
    }

    override fun onBackPressed() {
        if (web.canGoBack()) {
            web.goBack()
        } else {
            AlertDialog.Builder(this)
                .setTitle("QR Sıfırla")
                .setMessage("QR'ı sıfırlayıp yeniden okutmak ister misin?")
                .setPositiveButton("Sıfırla") { _, _ ->
                    prefs.edit().remove(KEY_QR).apply()
                    startQrScan()
                }
                .setNegativeButton("Kapat") { _, _ -> finish() }
                .show()
        }
    }
}
