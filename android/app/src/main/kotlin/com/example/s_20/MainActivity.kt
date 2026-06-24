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

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)

        methodChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
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
                    OverlayAccessibilityService.isIntercepting = false
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
        handleIntentExtra(intent)
    }

    private fun handleIntentExtra(intent: Intent) {
        val blockedApp = intent.getStringExtra("BLOCKED_APP")
        if (blockedApp != null) {
            methodChannel?.invokeMethod("showWaitingScreen", blockedApp)
            intent.removeExtra("BLOCKED_APP")
        }
    }

    // Ha a felhasználó lenyomja a Home gombot, vagy átvált más appra az Android 
    // feladatkezelőjéből, oldanunk kell az "isIntercepting" zárat, hogy a Kém újra élesedjen!
    override fun onStop() {
        super.onStop()
        OverlayAccessibilityService.isIntercepting = false
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