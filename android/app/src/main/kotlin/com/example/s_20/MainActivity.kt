import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'waiting_screen.dart';

// GLOBÁLIS ÁLLAPOTOK (Ezek mentik el, hogy a gombok működjenek)
int globalFrictionTime = 20;
Map<String, bool> globalBlockedApps = {
  'TikTok': true,
  'Instagram': true,
  'Facebook': true,
  'X (Twitter)': false,
  'YouTube': false,
};

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
  Duration _weeklyTimeSpent = Duration.zero; 

  bool? _hasOverlayPermission; 
  bool? _hasAccessibilityPermission;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);

    platform.setMethodCallHandler((call) async {
      if (call.method == 'showWaitingScreen') {
        final pkg = call.arguments as String;
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
      // EZ TAKARÍT, MIUTÁN AZ APP BIZTONSÁGOSAN A HÁTTÉRBE KERÜLT.
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

  bool get _isFullySetUp => _hasOverlayPermission == true && _hasAccessibilityPermission == true;

  @override
  Widget build(BuildContext context) {
    if (_blockedAppPackage != null) {
      return WaitingScreen(
        appDisplayName: _getAppDisplayName(_blockedAppPackage!),
        weeklyTimeSpent: _weeklyTimeSpent, 
        onProceed: () async {
          final pkg = _blockedAppPackage;
          // KIVETTÜK a setState nullázást innen, hogy ne villanjon be a Dashboard!
          await platform.invokeMethod('proceedToApp', pkg);
        },
        onStayFocused: () async {
          // KIVETTÜK a setState nullázást innen, csak hazaküldjük a usert!
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
      onOpenAccessibilitySettings: () => platform.invokeMethod('openAccessibilitySettings'),
      onRefresh: _refreshPermissionStatus,
    );
  }
}

// =========================================================================
// MŰKÖDŐ DASHBOARD (IRÁNYÍTÓPULT)
// =========================================================================
class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF11131A),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        title: const Text('S-20', style: TextStyle(fontWeight: FontWeight.w800, letterSpacing: 2.0)),
        centerTitle: true,
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
        children: [
          Container(
            padding: const EdgeInsets.all(28),
            decoration: BoxDecoration(
              gradient: const LinearGradient(colors: [Color(0xFF1B212D), Color(0xFF13161F)], begin: Alignment.topLeft, end: Alignment.bottomRight),
              borderRadius: BorderRadius.circular(24),
              border: Border.all(color: const Color(0xFF8FE3C0).withAlpha(50), width: 1),
              boxShadow: [BoxShadow(color: const Color(0xFF8FE3C0).withAlpha(15), blurRadius: 30, spreadRadius: -5, offset: const Offset(0, 10))],
            ),
            child: Column(
              children: [
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(color: const Color(0xFF8FE3C0).withAlpha(26), shape: BoxShape.circle),
                  child: const Icon(Icons.shield_rounded, color: Color(0xFF8FE3C0), size: 48),
                ),
                const SizedBox(height: 20),
                const Text('Védelem Aktív', style: TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                Text('A háttérben figyeljük a megadott alkalmazásokat. Nincs több végtelen pörgetés!', textAlign: TextAlign.center, style: TextStyle(color: Colors.white.withAlpha(150), fontSize: 14, height: 1.4)),
              ],
            ),
          ),
          const SizedBox(height: 36),

          _buildSectionHeader('BEÁLLÍTÁSOK'),
          _buildMenuItem(
            icon: Icons.apps_rounded,
            title: 'Blokkolt alkalmazások',
            subtitle: 'Kezeld a figyelőlistát',
            onTap: () async {
              await Navigator.push(context, MaterialPageRoute(builder: (context) => const _BlockedAppsScreen()));
              setState(() {}); // Frissítés visszatéréskor
            },
          ),
          _buildMenuItem(
            icon: Icons.timer_rounded,
            title: 'Friction idő',
            subtitle: '$globalFrictionTime másodperc beállítva',
            onTap: () async {
              await Navigator.push(context, MaterialPageRoute(builder: (context) => const _FrictionTimeScreen()));
              setState(() {}); // Frissítés visszatéréskor
            },
          ),

          const SizedBox(height: 28),

          _buildSectionHeader('INFORMÁCIÓ & TÁMOGATÁS'),
          _buildMenuItem(
            icon: Icons.help_outline_rounded,
            title: 'Gyakori Kérdések (FAQ)',
            subtitle: 'Hogyan működik az S-20?',
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (context) => const _FaqScreen())),
          ),
          _buildMenuItem(
            icon: Icons.info_outline_rounded,
            title: 'Rólunk',
            subtitle: 'A projekt története',
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (context) => const _AboutUsScreen())),
          ),
          const SizedBox(height: 20),
        ],
      ),
    );
  }

  Widget _buildSectionHeader(String title) {
    return Padding(
      padding: const EdgeInsets.only(left: 12, bottom: 12),
      child: Text(title, style: TextStyle(color: Colors.white.withAlpha(100), fontSize: 12, fontWeight: FontWeight.bold, letterSpacing: 1.2)),
    );
  }

  Widget _buildMenuItem({required IconData icon, required String title, required String subtitle, required VoidCallback onTap}) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(color: Colors.white.withAlpha(8), borderRadius: BorderRadius.circular(18), border: Border.all(color: Colors.white.withAlpha(15))),
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        leading: Container(padding: const EdgeInsets.all(12), decoration: BoxDecoration(color: const Color(0xFF8FE3C0).withAlpha(26), borderRadius: BorderRadius.circular(14)), child: Icon(icon, color: const Color(0xFF8FE3C0), size: 24)),
        title: Text(title, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600, fontSize: 15)),
        subtitle: Padding(padding: const EdgeInsets.only(top: 4), child: Text(subtitle, style: TextStyle(color: Colors.white.withAlpha(120), fontSize: 13))),
        trailing: Icon(Icons.arrow_forward_ios_rounded, color: Colors.white.withAlpha(50), size: 16),
        onTap: onTap,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
      ),
    );
  }
}

// =========================================================================
// ALOLDAL: BLOKKOLT ALKALMAZÁSOK
// =========================================================================
class _BlockedAppsScreen extends StatefulWidget {
  const _BlockedAppsScreen();
  @override
  State<_BlockedAppsScreen> createState() => _BlockedAppsScreenState();
}

class _BlockedAppsScreenState extends State<_BlockedAppsScreen> {
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
          const Text('Ezeknél az alkalmazásoknál fog megjelenni a visszaszámláló megnyitáskor.', style: TextStyle(color: Colors.white60, height: 1.5)),
          const SizedBox(height: 24),
          ...globalBlockedApps.keys.map((appName) => _buildAppSwitch(appName)),
        ],
      ),
    );
  }

  Widget _buildAppSwitch(String name) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(color: Colors.white.withAlpha(8), borderRadius: BorderRadius.circular(16), border: Border.all(color: Colors.white.withAlpha(15))),
      child: SwitchListTile(
        title: Text(name, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w500)),
        value: globalBlockedApps[name]!,
        activeColor: const Color(0xFF8FE3C0),
        onChanged: (bool value) {
          setState(() {
            globalBlockedApps[name] = value;
          });
        },
      ),
    );
  }
}

// =========================================================================
// ALOLDAL: FRICTION IDŐ (Custom Slider + Gyorsgombok)
// =========================================================================
class _FrictionTimeScreen extends StatefulWidget {
  const _FrictionTimeScreen();
  @override
  State<_FrictionTimeScreen> createState() => _FrictionTimeScreenState();
}

class _FrictionTimeScreenState extends State<_FrictionTimeScreen> {
  late double _sliderValue;

  @override
  void initState() {
    super.initState();
    _sliderValue = globalFrictionTime.toDouble();
  }

  void _updateTime(int time) {
    setState(() {
      globalFrictionTime = time;
      _sliderValue = time.toDouble();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF11131A),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        title: const Text('Friction idő', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 18)),
      ),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          const Text('Válaszd ki, hány másodpercet szeretnél várni egy blokkolt app megnyitásakor.', style: TextStyle(color: Colors.white60, height: 1.5)),
          const SizedBox(height: 24),
          _buildPresetOption(20, '20 másodperc (Könnyű)'),
          _buildPresetOption(30, '30 másodperc (Közepes)'),
          _buildPresetOption(50, '50 másodperc (Nehéz)'),
          const SizedBox(height: 30),
          Text('Egyéni idő: ${globalFrictionTime} mp', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16)),
          const SizedBox(height: 10),
          Slider(
            value: _sliderValue,
            min: 1,
            max: 100,
            divisions: 99,
            activeColor: const Color(0xFF8FE3C0),
            inactiveColor: Colors.white24,
            label: _sliderValue.toInt().toString(),
            onChanged: (value) {
              setState(() {
                _sliderValue = value;
                globalFrictionTime = value.toInt();
              });
            },
          ),
        ],
      ),
    );
  }

  Widget _buildPresetOption(int time, String title) {
    final isSelected = globalFrictionTime == time;
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(color: Colors.white.withAlpha(8), borderRadius: BorderRadius.circular(16), border: Border.all(color: isSelected ? const Color(0xFF8FE3C0) : Colors.white.withAlpha(15))),
      child: RadioListTile<int>(
        title: Text(title, style: TextStyle(color: isSelected ? Colors.white : Colors.white70, fontWeight: FontWeight.w500)),
        value: time,
        groupValue: globalFrictionTime,
        activeColor: const Color(0xFF8FE3C0),
        onChanged: (val) => _updateTime(val!),
      ),
    );
  }
}

// =========================================================================
// ALOLDAL: FAQ
// =========================================================================
class _FaqScreen extends StatelessWidget {
  const _FaqScreen();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF11131A),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        title: const Text('Gyakori Kérdések', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 18)),
      ),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          _buildFaqItem('Miért pont ennyi másodperc?', 'Az emberi agy dopamin kereső ciklusa könnyen megszakítható egy mesterséges várakozási idővel. Még egy rövid 20 másodperces fék is tudatosítja a döntést, és gyakran meg is hiúsítja a reflexszerű megnyitást.'),
          _buildFaqItem('Lassítja a telefonom az app?', 'Egyáltalán nem. Az S-20 natív Android folyamatként, szinte nulla memória- és akkumulátorhasználattal dolgozik a háttérben.'),
          _buildFaqItem('Miért kér Accessibility engedélyt?', 'A modern Android rendszereken ez a legbiztonságosabb és legenergiatakarékosabb módja annak, hogy az applikációnk tudja, mikor nyitsz meg egy tiltólistás appot, anélkül, hogy folyamatosan vizsgálná a képernyődet.'),
        ],
      ),
    );
  }

  Widget _buildFaqItem(String question, String answer) {
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      decoration: BoxDecoration(color: Colors.white.withAlpha(8), borderRadius: BorderRadius.circular(16)),
      child: ExpansionTile(
        iconColor: const Color(0xFF8FE3C0),
        collapsedIconColor: Colors.white54,
        title: Text(question, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
        children: [
          Padding(
            padding: const EdgeInsets.only(left: 16, right: 16, bottom: 16),
            child: Text(answer, style: const TextStyle(color: Colors.white60, height: 1.5)),
          )
        ],
      ),
    );
  }
}

// =========================================================================
// ALOLDAL: RÓLUNK
// =========================================================================
class _AboutUsScreen extends StatelessWidget {
  const _AboutUsScreen();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF11131A),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        title: const Text('Rólunk', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 18)),
      ),
      body: const Padding(
        padding: EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('S-20: Vissza a jelenbe', style: TextStyle(color: Colors.white, fontSize: 24, fontWeight: FontWeight.bold)),
            SizedBox(height: 16),
            Text(
              'A közösségi média platformok algoritmusai arra lettek kifejlesztve, hogy minél tovább a képernyő előtt tartsanak minket. '
              'Az S-20 azért született meg, hogy pajzsként álljon közéd és az algoritmikus "doomscrolling" közé.\n\n'
              'Nem tiltjuk le az alkalmazásaidat teljesen, csupán egy kis "súrlódást" (friction) teszünk a folyamatba, '
              'ami lehetőséget ad rá, hogy tudatosan dönts: valóban erre akarod-e szánni az idődet.',
              style: TextStyle(color: Colors.white60, fontSize: 15, height: 1.6),
            ),
            Spacer(),
            Center(
              child: Text('Verzió: 1.0.0 (MVP)', style: TextStyle(color: Colors.white24, fontWeight: FontWeight.w500)),
            ),
            SizedBox(height: 20),
          ],
        ),
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