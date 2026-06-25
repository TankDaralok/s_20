import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'waiting_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
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
      title: 'S-20',
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
  Duration _weeklyTimeSpent = Duration.zero; // ÚJ: Itt tároljuk a valós időt

  bool? _hasOverlayPermission; 
  bool? _hasAccessibilityPermission;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);

    platform.setMethodCallHandler((call) async {
      if (call.method == 'showWaitingScreen') {
        final pkg = call.arguments as String;
        // ÚJ: Lekérdezzük a TÉNYLEGES heti használati időt az Androidtól
        final num usageMs = await platform.invokeMethod('getWeeklyUsage', {'package': pkg}) ?? 0;
        
        setState(() {
          _blockedAppPackage = pkg;
          _weeklyTimeSpent = Duration(milliseconds: usageMs.toInt());
        });
      }
    });

    _checkInitialIntent();
    _refreshPermissionStatus();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _refreshPermissionStatus();
    } else if (state == AppLifecycleState.paused) {
      setState(() {
        _blockedAppPackage = null;
      });
    }
  }

  Future<void> _refreshPermissionStatus() async {
    try {
      final overlay = await platform.invokeMethod('checkOverlayPermission') as bool;
      final accessibility = await platform.invokeMethod('checkAccessibilityPermission') as bool;
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
    if (_blockedAppPackage != null) {
      return WaitingScreen(
        appDisplayName: _getAppDisplayName(_blockedAppPackage!),
        weeklyTimeSpent: _weeklyTimeSpent, // ÚJ: Valós adat bekötve
        onProceed: () async {
          final pkg = _blockedAppPackage;
          setState(() => _blockedAppPackage = null);
          await platform.invokeMethod('proceedToApp', pkg);
        },
        onStayFocused: () async {
          setState(() => _blockedAppPackage = null);
          await platform.invokeMethod('dismissOverlay');
        },
      );
    }

    if (_hasOverlayPermission == null || _hasAccessibilityPermission == null) {
      return const Scaffold(
        body: Center(child: CircularProgressIndicator(color: Color(0xFF8FE3C0))),
      );
    }

    if (_isFullySetUp) {
      return const DashboardScreen();
    }

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

// =========================================================================
// MŰKÖDŐ DASHBOARD (IRÁNYÍTÓPULT)
// =========================================================================
class DashboardScreen extends StatelessWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF11131A),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        title: const Text(
          'S-20',
          style: TextStyle(fontWeight: FontWeight.w800, letterSpacing: 2.0),
        ),
        centerTitle: true,
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
        children: [
          // BIZTONSÁGI PAJZS KÁRTYA
          Container(
            padding: const EdgeInsets.all(28),
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                colors: [Color(0xFF1B212D), Color(0xFF13161F)],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(24),
              border: Border.all(
                color: const Color(0xFF8FE3C0).withAlpha(50),
                width: 1,
              ),
              boxShadow: [
                BoxShadow(
                  color: const Color(0xFF8FE3C0).withAlpha(15),
                  blurRadius: 30,
                  spreadRadius: -5,
                  offset: const Offset(0, 10),
                ),
              ],
            ),
            child: Column(
              children: [
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: const Color(0xFF8FE3C0).withAlpha(26),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.shield_rounded, color: Color(0xFF8FE3C0), size: 48),
                ),
                const SizedBox(height: 20),
                const Text(
                  'Védelem Aktív',
                  style: TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 8),
                Text(
                  'A háttérben figyeljük a megadott alkalmazásokat. Nincs több végtelen pörgetés!',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: Colors.white.withAlpha(150), fontSize: 14, height: 1.4),
                ),
              ],
            ),
          ),
          const SizedBox(height: 36),

          // BEÁLLÍTÁSOK SZEKCIÓ
          _buildSectionHeader('BEÁLLÍTÁSOK'),
          _buildMenuItem(
            icon: Icons.apps_rounded,
            title: 'Blokkolt alkalmazások',
            subtitle: 'Kezeld a figyelőlistát',
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (context) => const _BlockedAppsScreen()),
              );
            },
          ),
          _buildMenuItem(
            icon: Icons.timer_rounded,
            title: 'Friction idő',
            subtitle: '20 másodperc (Alapértelmezett)',
            onTap: () => _showTimePicker(context),
          ),

          const SizedBox(height: 28),

          // TÁMOGATÁS SZEKCIÓ
          _buildSectionHeader('INFORMÁCIÓ & TÁMOGATÁS'),
          _buildMenuItem(
            icon: Icons.help_outline_rounded,
            title: 'Gyakori Kérdések (FAQ)',
            subtitle: 'Hogyan működik az S-20?',
            onTap: () => _showInfoDialog(context, 'Gyakori Kérdések', 'Itt fognak megjelenni a leggyakrabban ismételt kérdések. A 20 másodperces szabály tudományosan bizonyítottan megszakítja a dopamin-hurkot.'),
          ),
          _buildMenuItem(
            icon: Icons.info_outline_rounded,
            title: 'Rólunk',
            subtitle: 'A projekt története',
            onTap: () => _showInfoDialog(context, 'Rólunk', 'Az S-20 (korábban Doom Breaker) célja, hogy visszaadja az irányítást az időd és a figyelmed felett.'),
          ),
          const SizedBox(height: 20),
        ],
      ),
    );
  }

  void _showTimePicker(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: const Color(0xFF1B212D),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (context) => Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Friction idő beállítása', style: TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 20),
            ListTile(
              title: const Text('20 másodperc', style: TextStyle(color: Colors.white)),
              trailing: const Icon(Icons.check_circle, color: Color(0xFF8FE3C0)),
              onTap: () => Navigator.pop(context),
            ),
            ListTile(
              title: const Text('30 másodperc', style: TextStyle(color: Colors.white70)),
              onTap: () => Navigator.pop(context),
            ),
            ListTile(
              title: const Text('60 másodperc', style: TextStyle(color: Colors.white70)),
              onTap: () => Navigator.pop(context),
            ),
          ],
        ),
      ),
    );
  }

  void _showInfoDialog(BuildContext context, String title, String content) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: const Color(0xFF1B212D),
        title: Text(title, style: const TextStyle(color: Colors.white)),
        content: Text(content, style: const TextStyle(color: Colors.white70, height: 1.5)),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Bezárás', style: TextStyle(color: Color(0xFF8FE3C0))),
          ),
        ],
      ),
    );
  }

  Widget _buildSectionHeader(String title) {
    return Padding(
      padding: const EdgeInsets.only(left: 12, bottom: 12),
      child: Text(
        title,
        style: TextStyle(
          color: Colors.white.withAlpha(100),
          fontSize: 12,
          fontWeight: FontWeight.bold,
          letterSpacing: 1.2,
        ),
      ),
    );
  }

  Widget _buildMenuItem({
    required IconData icon,
    required String title,
    required String subtitle,
    required VoidCallback onTap,
  }) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: Colors.white.withAlpha(8),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: Colors.white.withAlpha(15)),
      ),
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        leading: Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: const Color(0xFF8FE3C0).withAlpha(26),
            borderRadius: BorderRadius.circular(14),
          ),
          child: Icon(icon, color: const Color(0xFF8FE3C0), size: 24),
        ),
        title: Text(title, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600, fontSize: 15)),
        subtitle: Padding(
          padding: const EdgeInsets.only(top: 4),
          child: Text(subtitle, style: TextStyle(color: Colors.white.withAlpha(120), fontSize: 13)),
        ),
        trailing: Icon(Icons.arrow_forward_ios_rounded, color: Colors.white.withAlpha(50), size: 16),
        onTap: onTap,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
      ),
    );
  }
}

// =========================================================================
// ÚJ: BLOKKOLT ALKALMAZÁSOK KÉPERNYŐ
// =========================================================================
class _BlockedAppsScreen extends StatelessWidget {
  const _BlockedAppsScreen();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF11131A),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        title: const Text('Blokkolt alkalmazások', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 18)),
      ),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          const Text(
            'Ezeknél az alkalmazásoknál fog megjelenni a 20 másodperces visszaszámláló megnyitáskor.',
            style: TextStyle(color: Colors.white60, height: 1.5),
          ),
          const SizedBox(height: 24),
          _buildAppSwitch('TikTok', true),
          _buildAppSwitch('Instagram', true),
          _buildAppSwitch('Facebook', true),
          _buildAppSwitch('X (Twitter)', false),
          _buildAppSwitch('YouTube', false),
        ],
      ),
    );
  }

  Widget _buildAppSwitch(String name, bool isActive) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: Colors.white.withAlpha(8),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.white.withAlpha(15)),
      ),
      child: SwitchListTile(
        title: Text(name, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w500)),
        value: isActive,
        activeColor: const Color(0xFF8FE3C0),
        onChanged: (bool value) {
          // TODO: Valós adatbázis mentés a jövőben
        },
      ),
    );
  }
}

// =========================================================================
// ENGEDÉLYEK KÉPERNYŐ (EZ VÁLTOZATLAN)
// =========================================================================
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
              const Text('Két lépés van hátra', style: TextStyle(color: Colors.white, fontSize: 24, fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              const Text('Ezeket Android nem engedi automatikusan kérni — mindkettőt neked kell bekapcsolnod a Beállításokban.', style: TextStyle(color: Colors.white60, fontSize: 14, height: 1.4)),
              const SizedBox(height: 28),
              _PermissionRow(
                granted: hasAccessibilityPermission,
                title: 'Kisegítő lehetőségek (Accessibility)',
                description: 'Ez észleli, amikor megnyitod a TikTokot vagy az Instát. Keresd meg a szolgáltatást a listában, és kapcsold BE.',
                buttonLabel: 'Beállítások megnyitása',
                onPressed: onOpenAccessibilitySettings,
              ),
              const SizedBox(height: 16),
              _PermissionRow(
                granted: hasOverlayPermission,
                title: 'Más alkalmazások felett megjelenés',
                description: 'Ez kell ahhoz, hogy a várakozó képernyő tényleg előtérbe ugorjon.',
                buttonLabel: 'Engedély kérése',
                onPressed: onRequestOverlay,
              ),
              const Spacer(),
              SizedBox(
                width: double.infinity,
                child: OutlinedButton(
                  onPressed: onRefresh,
                  style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 14), side: const BorderSide(color: Colors.white24)),
                  child: const Text('Frissítés', style: TextStyle(color: Colors.white70)),
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

  const _PermissionRow({required this.granted, required this.title, required this.description, required this.buttonLabel, required this.onPressed});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(color: Colors.white.withAlpha(13), borderRadius: BorderRadius.circular(16), border: Border.all(color: granted ? const Color(0xFF8FE3C0) : Colors.white24)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(granted ? Icons.check_circle : Icons.radio_button_unchecked, color: granted ? const Color(0xFF8FE3C0) : Colors.white38, size: 20),
              const SizedBox(width: 8),
              Expanded(child: Text(title, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600, fontSize: 15))),
            ],
          ),
          const SizedBox(height: 8),
          Text(description, style: const TextStyle(color: Colors.white60, fontSize: 13, height: 1.4)),
          if (!granted) ...[
            const SizedBox(height: 12),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: onPressed,
                style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF8FE3C0), foregroundColor: const Color(0xFF11131A), padding: const EdgeInsets.symmetric(vertical: 12), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12))),
                child: Text(buttonLabel, style: const TextStyle(fontWeight: FontWeight.w600)),
              ),
            ),
          ],
        ],
      ),
    );
  }
}