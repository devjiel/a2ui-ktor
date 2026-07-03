import 'dart:async';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:genui/genui.dart';
import 'package:genui_a2a/genui_a2a.dart';

/// Space flight booking screen with a sci-fi hero landing page.
///
/// Flow:
/// 1. Hero page with "Book your flight" CTA
/// 2. On tap → sends initial message to IntentAgent
/// 3. Agent generates A2UI surfaces (search form, results, boarding pass)
/// 4. Surfaces rendered inline via GenUI SDK
class BookingScreen extends StatefulWidget {
  const BookingScreen({super.key});

  @override
  State<BookingScreen> createState() => _BookingScreenState();
}

class _BookingScreenState extends State<BookingScreen>
    with TickerProviderStateMixin {
  // GenUI components
  late final A2uiAgentConnector _connector;
  late final SurfaceController _controller;
  StreamSubscription<SurfaceUpdate>? _surfaceSubscription;

  // UI state
  final List<String> _activeSurfaceIds = [];
  bool _isLoading = false;
  bool _showHero = true;
  String? _errorMessage;

  // Animations
  late final AnimationController _pulseController;
  late final AnimationController _fadeController;
  late final Animation<double> _fadeAnimation;

  @override
  void initState() {
    super.initState();

    // Pulse animation for the CTA button
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 2000),
    )..repeat(reverse: true);

    // Fade-in animation for the hero page
    _fadeController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    );
    _fadeAnimation = CurvedAnimation(
      parent: _fadeController,
      curve: Curves.easeOutCubic,
    );
    _fadeController.forward();

    _initGenUI();
  }

  void _initGenUI() {
    _controller = SurfaceController(
      catalogs: [BasicCatalogItems.asCatalog()],
    );

    _connector = A2uiAgentConnector(
      url: Uri.parse('http://localhost:9998/intent'),
    );

    // Pipe A2UI messages to the controller
    _connector.stream.listen(
      (a2uiMessage) {
        debugPrint('🟢 A2UI message received: ${a2uiMessage.runtimeType}');
        debugPrint('   message: $a2uiMessage');
        _controller.handleMessage(a2uiMessage);
      },
      onError: (Object error) {
        debugPrint('🔴 A2UI stream error: $error');
        setState(() {
          _errorMessage = 'Connexion perdue : $error';
          _isLoading = false;
        });
      },
      onDone: () {
        debugPrint('🔵 A2UI stream done');
      },
    );

    // Track surface lifecycle
    _surfaceSubscription = _controller.surfaceUpdates.listen((update) {
      debugPrint('🟡 Surface update: ${update.runtimeType} - ${update.surfaceId}');
      setState(() {
        switch (update) {
          case SurfaceAdded(:final surfaceId):
            if (!_activeSurfaceIds.contains(surfaceId)) {
              _activeSurfaceIds.add(surfaceId);
            }
          case SurfaceRemoved(:final surfaceId):
            _activeSurfaceIds.remove(surfaceId);
          case ComponentsUpdated():
            break;
        }
      });
    });

    // Listen for text responses
    _connector.textStream.listen((text) {
      debugPrint('💬 Agent text: $text');
    });

    // Listen for errors
    _connector.errorStream.listen((error) {
      debugPrint('❌ Agent error: $error');
      setState(() {
        _errorMessage = 'Erreur agent : $error';
        _isLoading = false;
      });
    });

    // Listen for user actions from surfaces (e.g., button clicks)
    _controller.onSubmit.listen((chatMessage) {
      _sendToAgent(chatMessage);
    });
  }

  /// Sends the initial booking request to the agent.
  Future<void> _startBooking() async {
    setState(() {
      _isLoading = true;
      _showHero = false;
      _errorMessage = null;
    });

    final chatMessage = ChatMessage.user(
      'Je veux réserver un vol spatial',
    );
    await _sendToAgent(chatMessage);
  }

  /// Sends a ChatMessage to the A2A agent.
  Future<void> _sendToAgent(ChatMessage chatMessage) async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final capabilities = _controller.clientCapabilities;
      await _connector.connectAndSend(
        chatMessage,
        clientCapabilities: capabilities,
      );
      setState(() => _isLoading = false);
    } catch (e) {
      setState(() {
        _isLoading = false;
        _errorMessage = 'Erreur de communication : $e';
      });
    }
  }

  @override
  void dispose() {
    _pulseController.dispose();
    _fadeController.dispose();
    _surfaceSubscription?.cancel();
    _connector.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        children: [
          // Animated star field background
          const _StarFieldBackground(),

          // Main content
          SafeArea(
            child: _showHero ? _buildHeroPage() : _buildAgentView(),
          ),
        ],
      ),
    );
  }

  // ─────────────────────────────────────────────
  // Hero landing page
  // ─────────────────────────────────────────────

  Widget _buildHeroPage() {
    return FadeTransition(
      opacity: _fadeAnimation,
      child: Center(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              // Logo / icon
              Container(
                width: 80,
                height: 80,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: const LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [Color(0xFF00E5FF), Color(0xFFBB86FC)],
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: const Color(0xFF00E5FF).withValues(alpha: 0.4),
                      blurRadius: 30,
                      spreadRadius: 5,
                    ),
                  ],
                ),
                child: const Icon(
                  Icons.rocket_launch_rounded,
                  size: 40,
                  color: Colors.white,
                ),
              ),
              const SizedBox(height: 32),

              // Title
              ShaderMask(
                shaderCallback: (bounds) => const LinearGradient(
                  colors: [Color(0xFF00E5FF), Color(0xFF64FFDA)],
                ).createShader(bounds),
                child: const Text(
                  'STELLAR LINES',
                  style: TextStyle(
                    fontSize: 36,
                    fontWeight: FontWeight.w900,
                    letterSpacing: 8,
                    color: Colors.white,
                  ),
                ),
              ),
              const SizedBox(height: 8),
              Text(
                'INTERPLANETARY TRAVEL',
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w300,
                  letterSpacing: 6,
                  color: Colors.white.withValues(alpha: 0.5),
                ),
              ),
              const SizedBox(height: 48),

              // Tagline
              Text(
                'Explorez les confins du système solaire.\nRéservez votre vol vers les étoiles.',
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 16,
                  height: 1.6,
                  color: Colors.white.withValues(alpha: 0.7),
                ),
              ),
              const SizedBox(height: 56),

              // CTA Button — animated glow
              AnimatedBuilder(
                animation: _pulseController,
                builder: (context, child) {
                  final glowIntensity =
                      0.2 + (_pulseController.value * 0.3);
                  return Container(
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(30),
                      boxShadow: [
                        BoxShadow(
                          color: const Color(0xFF00E5FF)
                              .withValues(alpha: glowIntensity),
                          blurRadius: 24,
                          spreadRadius: 2,
                        ),
                      ],
                    ),
                    child: child,
                  );
                },
                child: ElevatedButton(
                  onPressed: _startBooking,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.transparent,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(
                      horizontal: 40,
                      vertical: 18,
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(30),
                      side: const BorderSide(
                        color: Color(0xFF00E5FF),
                        width: 1.5,
                      ),
                    ),
                    elevation: 0,
                  ),
                  child: const Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(Icons.flight_takeoff_rounded, size: 20),
                      SizedBox(width: 12),
                      Text(
                        'RÉSERVER VOTRE BILLET',
                        style: TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w600,
                          letterSpacing: 2,
                        ),
                      ),
                    ],
                  ),
                ),
              ),

              const SizedBox(height: 64),

              // Destinations preview
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  _DestinationChip(name: 'Mars', icon: '🔴'),
                  const SizedBox(width: 12),
                  _DestinationChip(name: 'Luna', icon: '🌙'),
                  const SizedBox(width: 12),
                  _DestinationChip(name: 'Europa', icon: '🪐'),
                  const SizedBox(width: 12),
                  _DestinationChip(name: 'Titan', icon: '⭐'),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ─────────────────────────────────────────────
  // Agent-generated view (after clicking "Book")
  // ─────────────────────────────────────────────

  Widget _buildAgentView() {
    return Column(
      children: [
        // Top bar
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
          child: Row(
            children: [
              // Back button
              IconButton(
                icon: const Icon(
                  Icons.arrow_back_ios_rounded,
                  color: Color(0xFF00E5FF),
                  size: 20,
                ),
                onPressed: () {
                  setState(() {
                    _showHero = true;
                    _activeSurfaceIds.clear();
                    _errorMessage = null;
                  });
                },
              ),
              const SizedBox(width: 8),
              ShaderMask(
                shaderCallback: (bounds) => const LinearGradient(
                  colors: [Color(0xFF00E5FF), Color(0xFF64FFDA)],
                ).createShader(bounds),
                child: const Text(
                  'STELLAR LINES',
                  style: TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 4,
                    color: Colors.white,
                  ),
                ),
              ),
              const Spacer(),
              if (_isLoading)
                const SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: Color(0xFF00E5FF),
                  ),
                ),
            ],
          ),
        ),

        const SizedBox(height: 8),

        // Content area
        Expanded(
          child: _buildContent(),
        ),
      ],
    );
  }

  Widget _buildContent() {
    // Error state
    if (_errorMessage != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.error_outline_rounded,
                size: 48,
                color: Theme.of(context).colorScheme.error,
              ),
              const SizedBox(height: 16),
              Text(
                _errorMessage!,
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: Colors.white.withValues(alpha: 0.7),
                ),
              ),
              const SizedBox(height: 24),
              OutlinedButton.icon(
                onPressed: _startBooking,
                icon: const Icon(Icons.refresh_rounded),
                label: const Text('Réessayer'),
                style: OutlinedButton.styleFrom(
                  foregroundColor: const Color(0xFF00E5FF),
                  side: const BorderSide(color: Color(0xFF00E5FF)),
                ),
              ),
            ],
          ),
        ),
      );
    }

    // Loading state (no surfaces yet)
    if (_activeSurfaceIds.isEmpty) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const SizedBox(
              width: 40,
              height: 40,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                color: Color(0xFF00E5FF),
              ),
            ),
            const SizedBox(height: 24),
            Text(
              'Recherche des destinations...',
              style: TextStyle(
                color: Colors.white.withValues(alpha: 0.6),
                fontSize: 14,
                letterSpacing: 1,
              ),
            ),
          ],
        ),
      );
    }

    // GenUI surfaces
    return ListView.builder(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      itemCount: _activeSurfaceIds.length,
      itemBuilder: (context, index) {
        final surfaceId = _activeSurfaceIds[index];
        return Container(
          margin: const EdgeInsets.only(bottom: 16),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color: const Color(0xFF00E5FF).withValues(alpha: 0.15),
              width: 1,
            ),
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [
                const Color(0xFF0A0E21).withValues(alpha: 0.9),
                const Color(0xFF1A1E36).withValues(alpha: 0.9),
              ],
            ),
            boxShadow: [
              BoxShadow(
                color: const Color(0xFF00E5FF).withValues(alpha: 0.05),
                blurRadius: 20,
                spreadRadius: 1,
              ),
            ],
          ),
          clipBehavior: Clip.antiAlias,
          child: Surface(
            surfaceContext: _controller.contextFor(surfaceId),
          ),
        );
      },
    );
  }
}

// ─────────────────────────────────────────────
// Destination chip for the hero page
// ─────────────────────────────────────────────

class _DestinationChip extends StatelessWidget {
  const _DestinationChip({required this.name, required this.icon});

  final String name;
  final String icon;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: Colors.white.withValues(alpha: 0.1),
        ),
        color: Colors.white.withValues(alpha: 0.05),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(icon, style: const TextStyle(fontSize: 14)),
          const SizedBox(width: 6),
          Text(
            name,
            style: TextStyle(
              fontSize: 12,
              color: Colors.white.withValues(alpha: 0.6),
              letterSpacing: 1,
            ),
          ),
        ],
      ),
    );
  }
}

// ─────────────────────────────────────────────
// Animated star field background
// ─────────────────────────────────────────────

class _StarFieldBackground extends StatefulWidget {
  const _StarFieldBackground();

  @override
  State<_StarFieldBackground> createState() => _StarFieldBackgroundState();
}

class _StarFieldBackgroundState extends State<_StarFieldBackground>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  final List<_Star> _stars = [];
  final _random = Random(42);

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 60),
    )..repeat();

    // Generate random stars
    for (int i = 0; i < 120; i++) {
      _stars.add(_Star(
        x: _random.nextDouble(),
        y: _random.nextDouble(),
        size: _random.nextDouble() * 2.5 + 0.5,
        opacity: _random.nextDouble() * 0.6 + 0.2,
        speed: _random.nextDouble() * 0.3 + 0.05,
      ));
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, child) {
        return CustomPaint(
          painter: _StarFieldPainter(_stars, _controller.value),
          size: Size.infinite,
        );
      },
    );
  }
}

class _Star {
  final double x;
  final double y;
  final double size;
  final double opacity;
  final double speed;

  _Star({
    required this.x,
    required this.y,
    required this.size,
    required this.opacity,
    required this.speed,
  });
}

class _StarFieldPainter extends CustomPainter {
  final List<_Star> stars;
  final double time;

  _StarFieldPainter(this.stars, this.time);

  @override
  void paint(Canvas canvas, Size size) {
    // Deep space gradient background
    final bgPaint = Paint()
      ..shader = const LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: [
          Color(0xFF050816),
          Color(0xFF0A0E21),
          Color(0xFF0F1332),
        ],
      ).createShader(Rect.fromLTWH(0, 0, size.width, size.height));
    canvas.drawRect(Rect.fromLTWH(0, 0, size.width, size.height), bgPaint);

    // Stars
    for (final star in stars) {
      final twinkle = (sin((time * star.speed * 20) + star.x * 10) + 1) / 2;
      final alpha = star.opacity * (0.5 + twinkle * 0.5);
      final paint = Paint()
        ..color = Colors.white.withValues(alpha: alpha)
        ..maskFilter =
            star.size > 1.5 ? const MaskFilter.blur(BlurStyle.normal, 1) : null;

      canvas.drawCircle(
        Offset(star.x * size.width, star.y * size.height),
        star.size,
        paint,
      );
    }

    // Subtle nebula glow (bottom-right)
    final nebulaPaint = Paint()
      ..shader = RadialGradient(
        center: const Alignment(0.7, 0.8),
        radius: 0.6,
        colors: [
          const Color(0xFFBB86FC).withValues(alpha: 0.03),
          Colors.transparent,
        ],
      ).createShader(Rect.fromLTWH(0, 0, size.width, size.height));
    canvas.drawRect(Rect.fromLTWH(0, 0, size.width, size.height), nebulaPaint);

    // Subtle nebula glow (top-left)
    final nebulaPaint2 = Paint()
      ..shader = RadialGradient(
        center: const Alignment(-0.5, -0.3),
        radius: 0.5,
        colors: [
          const Color(0xFF00E5FF).withValues(alpha: 0.02),
          Colors.transparent,
        ],
      ).createShader(Rect.fromLTWH(0, 0, size.width, size.height));
    canvas.drawRect(
        Rect.fromLTWH(0, 0, size.width, size.height), nebulaPaint2);
  }

  @override
  bool shouldRepaint(_StarFieldPainter oldDelegate) =>
      oldDelegate.time != time;
}
