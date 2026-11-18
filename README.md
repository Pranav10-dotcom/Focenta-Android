# Focenta — Android MVP

A digital-wellbeing prototype where a living plant reacts to short-form video consumption.

## Current prototype
- Animated plant character drawn natively with Jetpack Compose Canvas.
- Idle sway + blinking.
- Health changes with Reel count.
- Character visually deteriorates as count increases.
- Milestone messages at 5/10/20/30/40 reels.
- Break/recovery interaction.
- Dashboard cards and Garden preview.
- Overlay/Accessibility permissions are declared for the future detection layer.

## Architecture target

Instagram / YouTube / Facebook
        ↓
Detection Engine
  UsageStats + Accessibility
        ↓
ReelCounter
        ↓
PlantStateManager
        ↓
Character Renderer / Rive
        ↓
Overlay

## Next implementation stages

1. Replace simulated Reel +1 with real Android detection.
2. Add foreground-app detection.
3. Add AccessibilityService-based scroll heuristics.
4. Build platform-specific detectors for Instagram / YouTube / Facebook.
5. Add the small always-on-top overlay.
6. Move the character into Rive for production-quality animation.
7. Add Room persistence and daily history.
8. Add recovery timer and Garden.
9. Add Play Store disclosure/consent flow for accessibility usage.

Important: the generated character artwork is a visual reference. For production, recreate the character as vector layers in Rive rather than using a screenshot.


## Focenta V2 architecture

The MVP now uses an event-driven detection pipeline: accessibility events are filtered, short-video candidates are scored and deduplicated, confirmed events feed a session/behavior model, and the overlay renders the resulting plant state. Daily count, recent scrolling load, recovery, session count, re-entry count, and longest break are derived centrally by `FocentaRepository`.

The detector is intentionally conservative and currently targets Instagram only. Raw accessibility nodes are not persisted.
