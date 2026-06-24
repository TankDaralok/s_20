import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'waiting_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // Ne engedjük elfordítani az appot — a WaitingScreen layoutja nincs
  // landscape-re tervezve, és felesleges is lenne ráépíteni.
  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]);
  runApp(const DoomBreakerApp());
}

class DoomBreakerApp extends StatelessWidget {
  const DoomBreakerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Doom Breaker',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF11131A),
      ),
      home: const MainGate(),
    );
  }
}

class MainGate extends StatefulWidget {
  const MainGate({super.key});

  @override
  State<MainGate> createState() => _MainGateState();
}

class _MainGateState extends State<MainGate> with WidgetsBindingObserver {
  static const platform = MethodChannel('com.doombreaker.app/bridge');

  String? _blockedAppPackage;

  bool? _hasOverlayPermission; // null = still checking
  bool? _hasAccessibilityPermission;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);

    // 1. Figyeljük a Kotlin-tól érkező utasításokat
    platform.setMethodCallHandler((call) async {
      if (call.method == 'showWaitingScreen') {
        setState(() {
          _blockedAppPackage = call.arguments as String;
        });
      }
    });

    // 2. Amikor az app elindul, megkérdezzük a natív oldalt, hogy egy blokkolás miatt nyíltunk-e meg
    _checkInitialIntent();

    // 3. Az ÉLES állapot lekérdezése — ez váltotta ki a hardkódolt
    // "A Kém működik!" üzenetet, ami a tényleges engedélyektől függetlenül
    // mindig zöld pipát mutatott.
    _refreshPermissionStatus();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  // Amikor a user visszajön a Settings képernyőről (akár Accessibility,
  // akár Overlay), az app "resumed" állapotba kerül — ekkor automatikusan
  // újra lekérdezzük a státuszt, nem kell külön "Frissítés" gombot nyomnia.
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _refreshPermissionStatus();
    }
  }

  Future<void> _refreshPermissionStatus() async {
    try {
      final overlay =
          await platform.invokeMethod('checkOverlayPermission') as bool;
      final accessibility =
          await platform.invokeMethod('checkAccessibilityPermission') as bool;
      if (!mounted) return;
      setState(() {
        _hasOverlayPermission = overlay;
        _hasAccessibilityPermission = accessibility;
      });
    } on PlatformException catch (e) {
      debugPrint('Permission check failed: ${e.message}');
    }
  }

  Future<void> _checkInitialIntent() async {
    try {
      await platform.invokeMethod('checkInitialIntent');
    } on PlatformException catch (e) {
      debugPrint("Hiba a hídnál: ${e.message}");
    }
  }

  // Segédfüggvény a csomagnevek szép nevéhez
  String _getAppDisplayName(String packageName) {
    if (packageName.contains('instagram')) return 'Instagram';
    if (packageName.contains('musically')) return 'TikTok';
    if (packageName.contains('facebook')) return 'Facebook';
    return packageName;
  }

  bool get _isFullySetUp =>
      _hasOverlayPermission == true && _hasAccessibilityPermission == true;

  @override
  Widget build(BuildContext context) {
    // Ha a Kotlin Kém átküldött egy csomagnevet, megjelenítjük az animációt
    if (_blockedAppPackage != null) {
      return WaitingScreen(
        appDisplayName: _getAppDisplayName(_blockedAppPackage!),
        weeklyTimeSpent: const Duration(hours: 12, minutes: 45), // Egyelőre mockolt
        onProceed: () async {
          final pkg = _blockedAppPackage;
          setState(() => _blockedAppPackage = null);
          // Átadjuk a csomagnevet, hogy a service tudja: ezt most NE
          // blokkolja újra azonnal, amint az ablaka előtérbe kerül.
          await platform.invokeMethod('proceedToApp', pkg);
        },
        onStayFocused: () async {
          setState(() => _blockedAppPackage = null);
          await platform.invokeMethod('dismissOverlay');
        },
      );
    }

    // Még tart az első lekérdezés
    if (_hasOverlayPermission == null || _hasAccessibilityPermission == null) {
      return const Scaffold(
        body: Center(child: CircularProgressIndicator(color: Color(0xFF8FE3C0))),
      );
    }

    // Csak akkor mutatjuk a "minden zöld" képernyőt, ha TÉNYLEG minden zöld
    if (_isFullySetUp) {
      return const _AllSetScreen();
    }

    // Különben: valódi, élő setup képernyő a hiányzó engedélyekkel
    return _PermissionSetupScreen(
      hasOverlayPermission: _hasOverlayPermission!,
      hasAccessibilityPermission: _hasAccessibilityPermission!,
      onRequestOverlay: () => platform.invokeMethod('requestOverlayPermission'),
      onOpenAccessibilitySettings: () =>
          platform.invokeMethod('openAccessibilitySettings'),
      onRefresh: _refreshPermissionStatus,
    );
  }
}

class _AllSetScreen extends StatelessWidget {
  const _AllSetScreen();

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.check_circle_outline, color: Color(0xFF8FE3C0), size: 100),
            SizedBox(height: 30),
            Text(
              "A Kém működik!",
              style: TextStyle(
                color: Colors.white,
                fontSize: 24,
                fontWeight: FontWeight.bold,
              ),
            ),
            SizedBox(height: 12),
            Text(
              "Tedd le az appot a háttérbe,\nés nyisd meg a TikTokot vagy az Instát!",
              textAlign: TextAlign.center,
              style: TextStyle(
                color: Colors.white70,
                fontSize: 16,
                height: 1.5,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _PermissionSetupScreen extends StatelessWidget {
  final bool hasOverlayPermission;
  final bool hasAccessibilityPermission;
  final VoidCallback onRequestOverlay;
  final VoidCallback onOpenAccessibilitySettings;
  final VoidCallback onRefresh;

  const _PermissionSetupScreen({
    required this.hasOverlayPermission,
    required this.hasAccessibilityPermission,
    required this.onRequestOverlay,
    required this.onOpenAccessibilitySettings,
    required this.onRefresh,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: 16),
              const Text(
                'Két lépés van hátra',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 24,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 8),
              const Text(
                'Ezeket Android nem engedi automatikusan kérni — '
                'mindkettőt neked kell bekapcsolnod a Beállításokban.',
                style: TextStyle(color: Colors.white60, fontSize: 14, height: 1.4),
              ),
              const SizedBox(height: 28),
              _PermissionRow(
                granted: hasAccessibilityPermission,
                title: 'Kisegítő lehetőségek (Accessibility)',
                description:
                    'Ez észleli, amikor megnyitod a TikTokot vagy az Instát. '
                    'Keresd meg a "Doom Breaker Guard" szolgáltatást a listában, '
                    'és kapcsold BE.',
                buttonLabel: 'Beállítások megnyitása',
                onPressed: onOpenAccessibilitySettings,
              ),
              const SizedBox(height: 16),
              _PermissionRow(
                granted: hasOverlayPermission,
                title: 'Más alkalmazások felett megjelenés',
                description:
                    'Ez kell ahhoz, hogy a várakozó képernyő tényleg '
                    'előtérbe ugorjon, ne csak a háttérben próbálkozzon.',
                buttonLabel: 'Engedély kérése',
                onPressed: onRequestOverlay,
              ),
              const Spacer(),
              SizedBox(
                width: double.infinity,
                child: OutlinedButton(
                  onPressed: onRefresh,
                  style: OutlinedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    side: const BorderSide(color: Colors.white24),
                  ),
                  child: const Text(
                    'Frissítés',
                    style: TextStyle(color: Colors.white70),
                  ),
                ),
              ),
              const SizedBox(height: 12),
            ],
          ),
        ),
      ),
    );
  }
}

class _PermissionRow extends StatelessWidget {
  final bool granted;
  final String title;
  final String description;
  final String buttonLabel;
  final VoidCallback onPressed;

  const _PermissionRow({
    required this.granted,
    required this.title,
    required this.description,
    required this.buttonLabel,
    required this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white.withAlpha(13),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: granted ? const Color(0xFF8FE3C0) : Colors.white24,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                granted ? Icons.check_circle : Icons.radio_button_unchecked,
                color: granted ? const Color(0xFF8FE3C0) : Colors.white38,
                size: 20,
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  title,
                  style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.w600,
                    fontSize: 15,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            description,
            style: const TextStyle(color: Colors.white60, fontSize: 13, height: 1.4),
          ),
          if (!granted) ...[
            const SizedBox(height: 12),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: onPressed,
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF8FE3C0),
                  foregroundColor: const Color(0xFF11131A),
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
                child: Text(buttonLabel, style: const TextStyle(fontWeight: FontWeight.w600)),
              ),
            ),
          ],
        ],
      ),
    );
  }
}