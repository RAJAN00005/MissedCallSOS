package com.example.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class GlassTier {
    TIER_A, // API 33+, High GPU, blur + shader + specular shimmer
    TIER_B, // API 31-32 or power-saver Tier A: blur + static highlight
    TIER_C  // API 26-30 or reduced transparency: opaque tinted frosted glass
}

enum class ProtectionVisualStatus {
    ACTIVE,    // Protection running normally (green tint)
    ATTENTION, // Paused or permission missing (amber tint)
    CRITICAL   // Emergency alarm firing (red tint)
}

@Immutable
data class GlassTokens(
    val tier: GlassTier,
    val isDark: Boolean,
    val backgroundBase: Color,
    val surfaceFill: Color,
    val surfaceFillGradient: Brush,
    val borderBrush: Brush,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val activeGreen: Color,
    val attentionAmber: Color,
    val criticalRed: Color,
    val infoBlue: Color,
    val cardRadius: Dp = 24.dp,
    val sheetRadius: Dp = 32.dp,
    val buttonRadius: Dp = 20.dp,
    val chipRadius: Dp = 999.dp,
    val inputRadius: Dp = 18.dp
)

object DesignTokens {
    // Base palette
    val DeepNavy = Color(0xFF0B1020)
    val NavySurface = Color(0xFF131B33)
    val NavyTintGreen = Color(0xFF052319)
    val NavyTintAmber = Color(0xFF2E1906)
    val NavyTintRed = Color(0xFF330914)

    // Blobs
    val BlobIndigo = Color(0xFF4F46E5)
    val BlobTeal = Color(0xFF14B8A6)
    val BlobViolet = Color(0xFF8B5CF6)
    val BlobMagenta = Color(0xFFEC4899)

    // Semantic Colors (WCAG AAA contrast compliant)
    val StatusActiveGreen = Color(0xFF34D399)
    val StatusActiveGreenContainer = Color(0xFF064E3B)
    val StatusAttentionAmber = Color(0xFFFBBF24)
    val StatusAttentionAmberContainer = Color(0xFF78350F)
    val StatusCriticalRed = Color(0xFFF43F5E)
    val StatusCriticalRedContainer = Color(0xFF881337)
    val StatusInfoBlue = Color(0xFF60A5FA)

    // Specular border gradients
    val SpecularBorderDark = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.55f),
            Color.White.copy(alpha = 0.20f),
            Color.White.copy(alpha = 0.05f)
        )
    )

    val SpecularBorderLight = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.85f),
            Color.White.copy(alpha = 0.40f),
            Color.White.copy(alpha = 0.15f)
        )
    )

    fun createTokens(tier: GlassTier, isDark: Boolean = true): GlassTokens {
        val baseColor = if (isDark) DeepNavy else Color(0xFFEEF2FF)
        val surfaceFill = when (tier) {
            GlassTier.TIER_A -> if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.60f)
            GlassTier.TIER_B -> if (isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.70f)
            GlassTier.TIER_C -> if (isDark) Color(0xFF151D33) else Color(0xFFE2E8F0)
        }

        val surfaceGradient = Brush.verticalGradient(
            colors = if (isDark) {
                listOf(
                    surfaceFill.copy(alpha = surfaceFill.alpha * 1.15f),
                    surfaceFill.copy(alpha = surfaceFill.alpha * 0.85f)
                )
            } else {
                listOf(
                    Color.White.copy(alpha = 0.85f),
                    Color.White.copy(alpha = 0.60f)
                )
            }
        )

        return GlassTokens(
            tier = tier,
            isDark = isDark,
            backgroundBase = baseColor,
            surfaceFill = surfaceFill,
            surfaceFillGradient = surfaceGradient,
            borderBrush = if (isDark) SpecularBorderDark else SpecularBorderLight,
            textPrimary = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A),
            textSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
            textMuted = if (isDark) Color(0xFF64748B) else Color(0xFF64748B),
            activeGreen = StatusActiveGreen,
            attentionAmber = StatusAttentionAmber,
            criticalRed = StatusCriticalRed,
            infoBlue = StatusInfoBlue
        )
    }

    fun detectTier(context: Context, userQualitySetting: String, reduceTransparency: Boolean): GlassTier {
        if (reduceTransparency) return GlassTier.TIER_C

        when (userQualitySetting) {
            "minimal" -> return GlassTier.TIER_C
            "balanced" -> return GlassTier.TIER_B
            "high" -> return GlassTier.TIER_A
        }

        // Auto detection
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

        val isLowRam = am?.isLowRamDevice == true
        val isPowerSave = pm?.isPowerSaveMode == true

        val animScale = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f)
        } catch (e: Exception) {
            1.0f
        }
        if (animScale == 0f || isLowRam) return GlassTier.TIER_C

        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isPowerSave -> GlassTier.TIER_A
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> GlassTier.TIER_B
            else -> GlassTier.TIER_C
        }
    }
}
