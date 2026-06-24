package com.example.s_20

import android.app.Application
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.dart.DartExecutor

class DoomBreakerApplication : Application() {

    companion object {
        const val ENGINE_ID = "doombreaker_engine"
    }

    override fun onCreate() {
        super.onCreate()

        // Előmelegítjük a Flutter engine-t MÁR az app process indulásakor —
        // tehát akkor is, ha a process csak az AccessibilityService miatt
        // indul el a háttérben (pl. eszköz-bootkor). Így amikor a service
        // TikTokot/Instát észlel és elindítja a MainActivity-t, a Dart VM
        // és a widget fa már fut — nincs hidegindítási fekete képernyő
        // az első detektálásnál sem, csak a 2.+-nál.
        val flutterEngine = FlutterEngine(this)
        flutterEngine.dartExecutor.executeDartEntrypoint(
            DartExecutor.DartEntrypoint.createDefault()
        )
        FlutterEngineCache.getInstance().put(ENGINE_ID, flutterEngine)
    }
}