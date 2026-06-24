package com.example.s_20

import android.accessibilityservice.AccessibilityServiceInfo
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

    // A getCachedEngineId() OVERRIDE TÖRÖLVE LETT! 
    // Visszatérünk a normál Flutter bootoláshoz.

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)

        // Figyeljük, mit kér a Flutter (pl. gombnyomások a WaitingScreen-en)
        methodChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "proceedToApp" -> {
                    val pkg = call.arguments as? String
                    if (pkg != null) {
                        OverlayAccessibilityService.allowTemporarily(pkg)
                    }
                    // Letesszük a mi appunkat a háttérbe, hogy a user lássa a TikTokot
                    moveTaskToBack(true)
                    result.success(null)
                }
                "dismissOverlay" -> {
                    // Visszadobjuk a usert a telefon főképernyőjére (Home)
                    val homeIntent = Intent(Intent.ACTION_MAIN)
                    homeIntent.addCategory(Intent.CATEGORY_HOME)
                    homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(homeIntent)
                    result.success(null)
                }
                "checkInitialIntent" -> {
                    // Amikor a Flutter elindul, megkérdezi, hogy tiltott app miatt indult-e
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
        handleIntentExtra(intent)
    }

    private fun handleIntentExtra(intent: Intent) {
        val blockedApp = intent.getStringExtra("BLOCKED_APP")
        if (blockedApp != null) {
            methodChannel?.invokeMethod("showWaitingScreen", blockedApp)
            intent.removeExtra("BLOCKED_APP")
        }
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
}