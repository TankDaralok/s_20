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
        // Amikor a user explicit "Open anyway"-t választott, ezt a csomagot
        // egy ideig nem szabad újra blokkolni — különben abban a pillanatban,
        // hogy a TikTok ablaka tényleg előtérbe kerül, a WINDOW_STATE_CHANGED
        // event újra kiváltja a blokkolást, és körbe-körbe megy a dolog.
        @Volatile private var allowedPackage: String? = null
        @Volatile private var allowedUntil: Long = 0L
        private const val GRACE_PERIOD_MS = 90_000L // 90 mp "nyugalmi idő"

        fun allowTemporarily(packageName: String) {
            allowedPackage = packageName
            allowedUntil = System.currentTimeMillis() + GRACE_PERIOD_MS
            Log.d("DoomBreaker", "⏳ Ideiglenes engedély: $packageName (${GRACE_PERIOD_MS / 1000}s)")
        }

        private fun isCurrentlyAllowed(packageName: String): Boolean {
            return packageName == allowedPackage && System.currentTimeMillis() < allowedUntil
        }
    }

    // Ezt a listát később a Flutterből fogjuk dinamikusan frissíteni
    private val blockedApps = listOf(
        "com.zhiliaoapp.musically", // TikTok
        "com.instagram.android",    // Instagram
        "com.facebook.katana"       // Facebook
    )

    private var windowManager: WindowManager? = null
    private var exemptionOverlay: View? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: return

        Log.d("DoomBreaker", "Megnyitva: $packageName | Osztály: $className")

        if (blockedApps.contains(packageName)) {
            if (isCurrentlyAllowed(packageName)) {
                Log.d("DoomBreaker", "⏳ $packageName ideiglenesen engedélyezve, kihagyva.")
                return
            }
            Log.d("DoomBreaker", "🚨 BUMM! Tiltólistás app észlelve: $packageName")
            showWaitingScreen(packageName)
        }
    }

    private fun showWaitingScreen(blockedPackage: String) {
        // KULCSLÉPÉS a Background Activity Launch restriction ellen:
        // Android kivételt ad a háttér-indítási tilalom alól, amíg az
        // appnak aktív TYPE_APPLICATION_OVERLAY ablaka van. Ezt MINDIG
        // a startActivity() előtt kell biztosítani, különben a hívás
        // némán elhal és a tiltott app simán megnyílik.
        ensureExemptionOverlay()

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("BLOCKED_APP", blockedPackage)
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("DoomBreaker", "Nem sikerült elindítani a MainActivity-t: ${e.message}")
        }
    }

    /**
     * 1x1px, nem fókuszálható, nem érinthető overlay ablak hozzáadása,
     * ha még nincs fent. Vizuálisan semmit nem csinál — egyetlen célja,
     * hogy az app "látható" státuszba kerüljön, ami megadja a BAL
     * kivételt. Csak akkor működik, ha a SYSTEM_ALERT_WINDOW jog
     * (Settings.canDrawOverlays) megadva van; ha nincs, ezt logoljuk,
     * és a Flutter oldalnak kell elkérnie a usertől (lásd MainActivity
     * "requestOverlayPermission" bridge metódusát).
     */
    private fun ensureExemptionOverlay() {
        if (exemptionOverlay != null) return
        if (!Settings.canDrawOverlays(this)) {
            Log.w("DoomBreaker", "Nincs SYSTEM_ALERT_WINDOW jog – a BAL kivétel nem fog működni.")
            return
        }

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
            Log.d("DoomBreaker", "✅ Exemption overlay hozzáadva.")
        } catch (e: Exception) {
            Log.e("DoomBreaker", "Overlay hozzáadása sikertelen: ${e.message}")
        }
    }

    private fun removeExemptionOverlay() {
        exemptionOverlay?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e("DoomBreaker", "Overlay eltávolítása sikertelen: ${e.message}")
            }
        }
        exemptionOverlay = null
    }

    override fun onInterrupt() {
        Log.d("DoomBreaker", "A szerviz megszakadt.")
        removeExemptionOverlay()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("DoomBreaker", "✅ Doom Breaker Accessibility Service sikeresen elindult!")
        // Előmelegítjük az overlay-t azonnal, hogy már az első detektálás
        // is rendelkezzen a BAL kivétellel.
        ensureExemptionOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeExemptionOverlay()
    }
}