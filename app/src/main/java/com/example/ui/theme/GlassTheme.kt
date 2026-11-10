package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

val LocalGlassTokens = staticCompositionLocalOf {
    DesignTokens.createTokens(GlassTier.TIER_C, true)
}

val LocalGlassTier = staticCompositionLocalOf {
    GlassTier.TIER_C
}

val LocalReduceMotion = staticCompositionLocalOf {
    false
}

object GlassTheme {
    val tokens: GlassTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassTokens.current

    val tier: GlassTier
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassTier.current

    val reduceMotion: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalReduceMotion.current
}

@Composable
fun GlassTheme(
    tierOverride: GlassTier? = null,
    reduceTransparency: Boolean = false,
    reduceMotion: Boolean = false,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val tier = tierOverride ?: DesignTokens.detectTier(context, "auto", reduceTransparency)
    val tokens = DesignTokens.createTokens(tier, darkTheme)

    CompositionLocalProvider(
        LocalGlassTokens provides tokens,
        LocalGlassTier provides tier,
        LocalReduceMotion provides reduceMotion,
        content = content
    )
}
