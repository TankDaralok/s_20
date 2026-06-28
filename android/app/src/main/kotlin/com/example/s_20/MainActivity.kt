package com.example.s_20

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.doombreaker.app/bridge"
    private var methodChannel: MethodChannel? = null

    // Új állapotváltozó, jelzi hogy éppen most lett bezárva a várakozó képernyő
    private var wasJustDismissed = false

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)

        methodChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "getWeeklyUsage" -> {
                    val pkg = call.argument<String>("package")
                    if (pkg != null) {
                        val usageMs = getWeeklyUsageForPackage(pkg)
                        result.success(usageMs)
                    } else {
                        result.success(0L)
                    }
                }
                "proceedToApp" -> {
                    val pkg = call.arguments as? String
                    if (pkg != null) {
                        OverlayAccessibilityService.allowTemporarily(pkg)
                    } else {
                        OverlayAccessibilityService.isIntercepting = false
                    }
                    moveTaskToBack(true)
                    result.success(null)
                }
                "dismissOverlay" -> {
                    // Ha a felhasználó a "Stay focused" gombot választotta, jelöljük, hogy éppen most zártuk be
                    OverlayAccessibilityService.isIntercepting = false
                    wasJustDismissed = true

                    // Kisebb UX: toljuk hátra a taskot mielőtt a Home képernyőt indítjuk
                    moveTaskToBack(true)

                    val homeIntent = Intent(Intent.ACTION_MAIN)
                    homeIntent.addCategory(Intent.CATEGORY_HOME)
                    homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(homeIntent)
                    result.success(null)
                }
                "checkInitialIntent" -> {
                    handleIntentExtra(intent)
                    result.success(null)
                }
                "checkOverlayPermission" -> {
                    result.success(Settings.canDrawOverlays(this))
                }
                "requestOverlayPermission" -> {
                    if (!Settings.canDrawOverlays(this)) {
                        val permissionIntent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(permissionIntent)
                    }
                    result.success(null)
                }
                "checkAccessibilityPermission" -> {
                    result.success(isAccessibilityServiceEnabled())
                }
                "openAccessibilitySettings" -> {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Csak akkor mutassuk a várakozó képernyőt, ha tényleg blokkolva van az alkalmazás és
        // nem éppen most zártuk be a várakozó képernyőt a "Stay focused" gombbal.
        if (intent.hasExtra("BLOCKED_APP")) {
            // Ha éppen most zártuk be, ne indítsuk újra
            if (!wasJustDismissed && OverlayAccessibilityService.isIntercepting) {
                handleIntentExtra(intent)
            } else {
                // Töröljük az extra-t, hogy ne hívja meg később véletlenül
                intent.removeExtra("BLOCKED_APP")
            }
        }
    }

    private fun handleIntentExtra(intent: Intent) {
        val blockedApp = intent.getStringExtra("BLOCKED_APP")
        if (blockedApp != null) {
            // Ellenőrizzük, hogy ne legyen duplikált hívás
            methodChannel?.invokeMethod("showWaitingScreen", blockedApp)
            intent.removeExtra("BLOCKED_APP")
        }
    }

    override fun onStop() {
        super.onStop()
        OverlayAccessibilityService.isIntercepting = false
        // reseteljük a wasJustDismissed jelzőt, hogy később újra engedélyezett legyen a várakozó képernyő
        wasJustDismissed = false
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices =
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == packageName &&
                it.resolveInfo.serviceInfo.name == OverlayAccessibilityService::class.java.name
        }
    }

    private fun getWeeklyUsageForPackage(packageName: String): Long {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (7 * 24 * 60 * 60 * 1000L) // Pontosan 1 hete
        
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        var totalTime = 0L
        
        if (stats != null) {
            for (stat in stats) {
                if (stat.packageName == packageName) {
                    totalTime += stat.totalTimeInForeground
                }
            }
        }
        return totalTime
    }
}
