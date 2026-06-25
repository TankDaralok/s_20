package com.example.s_20

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class OverlayAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile private var allowedPackage: String? = null
        @Volatile var isIntercepting: Boolean = false

        fun allowTemporarily(packageName: String) {
            allowedPackage = packageName
            isIntercepting = false // Reseteljük a zárat
            Log.d("DoomBreaker", "⏳ Munkamenet engedélyezve: $packageName")
        }
    }

    // BŐVÍTETT KÖZÖSSÉGI MÉDIA LISTA
    private val blockedApps = listOf(
        "com.zhiliaoapp.musically", // TikTok
        "com.instagram.android",    // Instagram
        "com.facebook.katana",      // Facebook
        "com.snapchat.android",     // Snapchat
        "com.twitter.android",      // X (Twitter)
        "com.instagram.barcelona",  // Threads
        "com.reddit.frontpage",     // Reddit
        "com.google.android.youtube",// YouTube
        "com.pinterest"             // Pinterest
    )

    private var windowManager: WindowManager? = null
    private var exemptionOverlay: View? = null
    @Volatile private var lastInterceptTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        // MUNKAMENET TÖRLÉSE (SESSION RESET)
        // Ha egy olyan app nyílik meg, ami:
        // 1. Nem a mi appunk (s_20)
        // 2. Nem rendszerfelület (pl. értesítési sáv, hangerő)
        // 3. Nem a billentyűzet (inputmethod)
        if (packageName != "com.example.s_20" &&
            !packageName.contains("systemui") &&
            !packageName.contains("inputmethod")) {
            
            // Ha volt engedélyezett app, de most valami mást nyitott meg a user:
            if (allowedPackage != null && packageName != allowedPackage) {
                Log.d("DoomBreaker", "🚫 App elhagyva ($packageName). Lakat visszazárva!")
                allowedPackage = null
            }
        }

        // BLOKKOLÁSI LOGIKA
        if (blockedApps.contains(packageName)) {
            // Ha épp benne vagyunk az engedélyezett munkamenetben, hagyjuk békén
            if (packageName == allowedPackage) {
                return
            }

            if (isIntercepting) return

            val now = System.currentTimeMillis()
            if (now - lastInterceptTime < 2000L) return
            lastInterceptTime = now

            Log.d("DoomBreaker", "🚨 BUMM! Tiltólistás app észlelve: $packageName")
            isIntercepting = true
            showWaitingScreen(packageName)
        }
    }

    private fun showWaitingScreen(blockedPackage: String) {
        ensureExemptionOverlay()

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) 
            putExtra("BLOCKED_APP", blockedPackage)
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("DoomBreaker", "Nem sikerült elindítani a MainActivity-t: ${e.message}")
            isIntercepting = false 
        }
    }

    private fun ensureExemptionOverlay() {
        if (exemptionOverlay != null) return
        if (!Settings.canDrawOverlays(this)) return

        windowManager = windowManager ?: getSystemService(WINDOW_SERVICE) as WindowManager
        val view = View(this)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        val params = WindowManager.LayoutParams(
            1, 1,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager?.addView(view, params)
            exemptionOverlay = view
        } catch (e: Exception) {
            Log.e("DoomBreaker", "Overlay hozzáadása sikertelen: ${e.message}")
        }
    }

    private fun removeExemptionOverlay() {
        exemptionOverlay?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {}
        }
        exemptionOverlay = null
    }

    override fun onInterrupt() {
        removeExemptionOverlay()
        isIntercepting = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ensureExemptionOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeExemptionOverlay()
        isIntercepting = false
    }
}