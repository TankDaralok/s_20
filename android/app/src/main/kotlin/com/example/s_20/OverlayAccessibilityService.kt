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
        @Volatile private var allowedUntil: Long = 0L
        private const val GRACE_PERIOD_MS = 90_000L // 90 mp nyugalmi idő

        @Volatile var isIntercepting: Boolean = false

        fun allowTemporarily(packageName: String) {
            allowedPackage = packageName
            allowedUntil = System.currentTimeMillis() + GRACE_PERIOD_MS
            isIntercepting = false
            Log.d("DoomBreaker", "⏳ Ideiglenes engedély: $packageName")
        }

        private fun isCurrentlyAllowed(packageName: String): Boolean {
            return packageName == allowedPackage && System.currentTimeMillis() < allowedUntil
        }
    }

    private val blockedApps = listOf(
        "com.zhiliaoapp.musically", // TikTok
        "com.instagram.android",    // Instagram
        "com.facebook.katana",      // Facebook
        "com.twitter.android",      // X (Twitter)
        "com.google.android.youtube"// YouTube
    )

    private var windowManager: WindowManager? = null
    private var exemptionOverlay: View? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        // A Flutterből át is küldhetjük ezt a listát a jövőben, de egyelőre a UI-ban van szűrve
        if (blockedApps.contains(packageName)) {
            if (isCurrentlyAllowed(packageName)) {
                return
            }

            if (isIntercepting) {
                return
            }

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