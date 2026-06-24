import 'package:flutter/material.dart';

/// A single phase of the breathing cycle (e.g. inhale, hold, exhale).
///
/// [targetScale] is a 0.0–1.0 fraction interpolated between [BreathingCircle.minSize]
/// and [BreathingCircle.maxSize] — not a literal pixel scale.
class BreathPhase {
  final String label;
  final Duration duration;
  final double targetScale;

  const BreathPhase({
    required this.label,
    required this.duration,
    required this.targetScale,
  });
}

/// A minimalist, pulsing circle that visually guides the user through a slow
/// breathing pattern (default: 4s inhale, 4s hold, 4s exhale).
///
/// Designed to sit at the center of the 20-second friction screen. Pure
/// Flutter — no animation packages required, so it drops into any project
/// with zero added dependencies.
class BreathingCircle extends StatefulWidget {
  final List<BreathPhase> phases;
  final double minSize;
  final double maxSize;
  final Color coreColor;
  final Color glowColor;
  final bool showLabel;

  const BreathingCircle({
    super.key,
    this.phases = const [
      BreathPhase(
        label: 'Breathe in',
        duration: Duration(seconds: 4),
        targetScale: 1.0,
      ),
      BreathPhase(
        label: 'Hold',
        duration: Duration(seconds: 4),
        targetScale: 1.0,
      ),
      BreathPhase(
        label: 'Breathe out',
        duration: Duration(seconds: 4),
        targetScale: 0.55,
      ),
    ],
    this.minSize = 120,
    this.maxSize = 220,
    this.coreColor = const Color(0xFFB8C0FF), // soft pastel lavender
    this.glowColor = const Color(0xFF6E7BD6),
    this.showLabel = true,
  });

  @override
  State<BreathingCircle> createState() => _BreathingCircleState();
}

class _BreathingCircleState extends State<BreathingCircle>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late Animation<double> _scale;
  int _phaseIndex = 0;
  double _lastScale = 0.55;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this)
      ..addStatusListener(_onStatusChange);
    _playPhase(0);
  }

  void _onStatusChange(AnimationStatus status) {
    if (status == AnimationStatus.completed && mounted) {
      final next = (_phaseIndex + 1) % widget.phases.length;
      _playPhase(next);
    }
  }

  void _playPhase(int index) {
    final phase = widget.phases[index];
    setState(() {
      _phaseIndex = index;
      _controller.duration = phase.duration;
      _scale = Tween<double>(begin: _lastScale, end: phase.targetScale).animate(
        CurvedAnimation(parent: _controller, curve: Curves.easeInOut),
      );
      _lastScale = phase.targetScale;
    });
    _controller.forward(from: 0);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final phase = widget.phases[_phaseIndex];
    return AnimatedBuilder(
      animation: _scale,
      builder: (context, _) {
        final size =
            widget.minSize + (widget.maxSize - widget.minSize) * _scale.value;
        return Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: size,
              height: size,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: RadialGradient(
                  colors: [
                    widget.coreColor.withAlpha(230), // 0.9 * 255
                    widget.glowColor.withAlpha(38),  // 0.15 * 255
                  ],
                  stops: const [0.4, 1.0],
                ),
                boxShadow: [
                  BoxShadow(
                    color: widget.glowColor.withAlpha(89), // 0.35 * 255
                    blurRadius: 40,
                    spreadRadius: 6,
                  ),
                ],
              ),
            ),
            if (widget.showLabel) ...[
              const SizedBox(height: 28),
              Text(
                phase.label,
                style: const TextStyle(
                  color: Colors.white70,
                  fontSize: 16,
                  fontWeight: FontWeight.w400,
                  letterSpacing: 1.2,
                ),
              ),
            ],
          ],
        );
      },
    );
  }
}