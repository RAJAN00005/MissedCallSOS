# Liquid Glass Design System

The Liquid Glass design system provides a tactile, calm, and reassuring interface for emergency safety applications. Translucent layers blur and slightly bend what is behind them, with bright specular edges, soft depth, and fluid spring-based motion.

## Design Philosophy
1. **Content First**: Glass is the container, never the content. Text and icons maintain WCAG AAA contrast (7:1 on Alarm Screen, 4.5:1 on normal screens).
2. **Calm by Default, Loud Only in Emergencies**: Normal screens are deep navy and soft; the SOS alarm screen is bold, high-contrast, and almost opaque red.
3. **Multi-Sensory Status**: Status is communicated by color + icon + text (never color alone).
4. **Safety Independent of Visuals**: All background telephony, alarm, and SMS logic run independently of UI rendering.

## Capability Tiers
- **Tier A (API 33+, High Performance)**:
  - Ambient drifting mesh gradient blobs.
  - Multi-stop radial gradients and specular shimmer.
  - Spring-based motion and subtle tilt response.
- **Tier B (API 31-32, or Tier A in Power Saver)**:
  - Static highlight lines and flat translucent surfaces.
  - Smooth spring transitions.
- **Tier C (API 26-30, Low-RAM, or 'Reduce Transparency')**:
  - Semi-opaque tinted dark navy surfaces (`#151D33`).
  - Specular gradient stroke + soft drop shadow.
  - Zero backdrop blur cost.
  - **The SOS Alarm Screen always enforces Tier C** for instant lockscreen rendering without GPU overhead.

## Tokens
- **Background Base**: Deep Navy `#0B1020`
- **Surface Fill**: White 10-16% alpha (dark) / 55-65% (light)
- **Specular Edge**: 1dp linear gradient (White 55% -> White 5%)
- **Active Green**: `#34D399`
- **Attention Amber**: `#FBBF24`
- **Critical Red**: `#F43F5E`
- **Info Blue**: `#60A5FA`

## Motion Rules
- Default spring: `spring(dampingRatio = 0.75f, stiffness = 450f)`
- Press feedback: `scale(0.96f)`
- Respects system `ANIMATOR_DURATION_SCALE` and in-app "Reduce Motion" setting.

## Adding a New Component
1. Wrap root with `GlassTheme.tokens` colors and borders.
2. Ensure touch target >= 48dp (`defaultMinSize(minHeight = 48.dp)`).
3. Add accessible `contentDescription` on all icons.
4. Support dynamic text scaling up to 200%.
