import 'dart:async';
import 'package:flutter/material.dart';
import 'breathing_circle.dart';
import 'main.dart'; // <--- EZ A SOR HIÁNYZOTT!

class WaitingScreen extends StatefulWidget {
  final String appDisplayName;
  final Duration weeklyTimeSpent;
  final int weeklyAttemptCount;
  final bool useAttemptFallback;
  final VoidCallback onProceed;
  final VoidCallback onStayFocused;

  const WaitingScreen({
    super.key,
    required this.appDisplayName,
    this.weeklyTimeSpent = Duration.zero,
    this.weeklyAttemptCount = 0,
    this.useAttemptFallback = false,
    required this.onProceed,
    required this.onStayFocused,
  });

  @override
  State<WaitingScreen> createState() => _WaitingScreenState();
}

class _WaitingScreenState extends State<WaitingScreen> {
  late int _secondsLeft;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    // A globális beállításokból olvassuk ki a beállított időt
    _secondsLeft = globalFrictionTime;
    
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      if (_secondsLeft <= 1) {
        _timer?.cancel();
        setState(() => _secondsLeft = 0);
      } else {
        setState(() => _secondsLeft--);
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  String get _costMessage {
    if (widget.useAttemptFallback) {
      final n = widget.weeklyAttemptCount;
      return "You've already tried to open ${widget.appDisplayName} "
          '$n time${n == 1 ? '' : 's'} this week.';
    }
    final hours = widget.weeklyTimeSpent.inMinutes / 60;
    return "You've already spent ${hours.toStringAsFixed(1)} hours on "
        '${widget.appDisplayName} this week.';
  }

  bool get _canProceed => _secondsLeft == 0;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF11131A),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 24),
          child: Column(
            children: [
              const SizedBox(height: 12),
              Text(
                _secondsLeft > 0 ? '$_secondsLeft' : 'Done',
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 22,
                  fontWeight: FontWeight.w300,
                  letterSpacing: 2,
                ),
              ),
              const Spacer(),
              const BreathingCircle(),
              const Spacer(),
              Container(
                padding: const EdgeInsets.all(18),
                decoration: BoxDecoration(
                  color: Colors.white.withAlpha(13),
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Text(
                  '$_costMessage\nDo you really want to proceed?',
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    color: Colors.white60,
                    fontSize: 14.5,
                    height: 1.5,
                  ),
                ),
              ),
              const SizedBox(height: 22),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: widget.onStayFocused,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF8FE3C0),
                    foregroundColor: const Color(0xFF11131A),
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                  ),
                  child: const Text(
                    'Stay focused',
                    style: TextStyle(fontWeight: FontWeight.w600),
                  ),
                ),
              ),
              const SizedBox(height: 10),
              SizedBox(
                width: double.infinity,
                child: TextButton(
                  onPressed: _canProceed ? widget.onProceed : null,
                  style: TextButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 14),
                  ),
                  child: Text(
                    _canProceed
                        ? 'Open ${widget.appDisplayName} anyway'
                        : 'Wait $_secondsLeft more second${_secondsLeft == 1 ? '' : 's'}...',
                    style: TextStyle(
                      color: _canProceed ? Colors.white70 : Colors.white24,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}