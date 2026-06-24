package com.example.s_20

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.doombreaker.app/bridge"
    private var methodChannel: MethodChannel? = null

    // Ahelyett, hogy minden indításnál friss Dart VM-et bootolna, ráakad
    // a DoomBreakerApplication.onCreate()-ben már bemelegített engine-re.
    override fun getCachedEngineId(): String? = DoomBreakerApplication.ENGINE_ID

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)

        // Figyeljük, mit kér a Flutter (pl. gombnyomások a WaitingScreen-en)
        methodChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "proceedToApp" -> {
                    // A user explicit kérte, hogy mégis megnyissa a tiltott appot.
                    // Adjunk neki egy ideiglenes engedélyt, különben a service
                    // azonnal újra blokkolja, amint a TikTok ablaka előtérbe kerül.
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

                // --- NEW: SYSTEM_ALERT_WINDOW bridge ---
                // The AccessibilityService relies on this permission being
                // granted to bypass background-activity-start restrictions.
                // Call "checkOverlayPermission" on app start; if false, call
                // "requestOverlayPermission" from a UI button to send the
                // user to system settings.
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

                // --- NEW: Accessibility Service bridge ---
                // There is NO request dialog for this, unlike camera/location.
                // The user has to flip a manual toggle in system settings.
                // "checkAccessibilityPermission" tells you the real current
                // state; "openAccessibilitySettings" deep-links them to the
                // exact screen where the toggle lives.
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

    // Amikor az app már fut a háttérben, és a Kém újra előtérbe hozza.
    // Ez csak akkor tüzel megbízhatóan (engine-restart nélkül), ha az
    // Activity launchMode="singleTask" az AndroidManifest.xml-ben.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentExtra(intent)
    }

    // Kinyerjük az extra adatot (hogy melyik appot blokkoltuk) és átküldjük Flutterbe
    private fun handleIntentExtra(intent: Intent) {
        val blockedApp = intent.getStringExtra("BLOCKED_APP")
        if (blockedApp != null) {
            methodChannel?.invokeMethod("showWaitingScreen", blockedApp)
            // Töröljük, hogy normál megnyitáskor ne mutassa újra
            intent.removeExtra("BLOCKED_APP")
        }
    }

    // Egyetlen megbízható módja annak, hogy kiderítsük, a user tényleg
    // bekapcsolta-e a mi Accessibility Service-ünket. (Az előző verzió
    // a Settings.Secure stringet próbálta manuálisan parse-olni, de a
    // rendszer ott a TELJES, fully-qualified osztálynevet tárolja, nem a
    // manifestben használt ".OverlayAccessibilityService" rövidített
    // formát — ezért az soha nem talált egyezést, akkor sem, ha a
    // szolgáltatás valójában fut. Ez itt a hivatalos, ajánlott API.)
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