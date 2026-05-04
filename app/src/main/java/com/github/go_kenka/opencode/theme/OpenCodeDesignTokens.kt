package com.github.go_kenka.opencode.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class OpenCodePaletteTokens(
    val primary: Color,
    val accent: Color,
    val secondary: Color,
    val success: Color,
    val error: Color,
    val warning: Color,
    val background: Color,
    val surface: Color,
    val surfaceInteractive: Color,
    val toolbarBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val onAccent: Color,
    val borderTertiary: Color,
    val borderSecondary: Color,
    val buttonPrimary: Color,
    val buttonSecondary: Color,
    val diffAdd: Color,
    val diffDelete: Color,
    val diffHiddenStrong: Color,
)

object OpenCodePalette {
    fun light() = OpenCodePaletteTokens(
        primary = Color(0xFF111111),
        accent = Color(0xFF111111),
        secondary = Color(0xFF2B2B2B),
        success = Color(0xFF111111),
        error = Color(0xFF111111),
        warning = Color(0xFF111111),
        background = Color(0xFFFFFFFF),
        surface = Color(0xFFFFFFFF),
        surfaceInteractive = Color(0xFFF7F7F7),
        toolbarBackground = Color(0xFFF3F3F3),
        textPrimary = Color(0xFF111111),
        textSecondary = Color(0xFF666666),
        onAccent = Color(0xFFFFFFFF),
        borderTertiary = Color(0xFFDDDDDD),
        borderSecondary = Color(0xFFBBBBBB),
        buttonPrimary = Color(0xFF111111),
        buttonSecondary = Color(0xFFF3F3F3),
        diffAdd = Color(0xFFF5F5F5),
        diffDelete = Color(0xFFF1F1F1),
        diffHiddenStrong = Color(0xFF111111),
    )

    fun dark() = OpenCodePaletteTokens(
        primary = Color(0xFFEDEDED),
        accent = Color(0xFFEDEDED),
        secondary = Color(0xFFC8C8C8),
        success = Color(0xFFEDEDED),
        error = Color(0xFFFFB4A9),
        warning = Color(0xFFFFDE9C),
        background = Color(0xFF111111),
        surface = Color(0xFF171717),
        surfaceInteractive = Color(0xFF212121),
        toolbarBackground = Color(0xFF151515),
        textPrimary = Color(0xFFEDEDED),
        textSecondary = Color(0xFFAFAFAF),
        onAccent = Color(0xFF111111),
        borderTertiary = Color(0xFF323232),
        borderSecondary = Color(0xFF4A4A4A),
        buttonPrimary = Color(0xFFEDEDED),
        buttonSecondary = Color(0xFF222222),
        diffAdd = Color(0xFF1A1A1A),
        diffDelete = Color(0xFF202020),
        diffHiddenStrong = Color(0xFFEDEDED),
    )
}

data class OpenCodeShapeTokens(
    val large: Dp,
    val medium: Dp,
    val small: Dp,
)

object OpenCodeShapeScale {
    fun default() = OpenCodeShapeTokens(
        large = 12.dp,
        medium = 6.dp,
        small = 4.dp,
    )
}

data class OpenCodeSpacingTokens(
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
)

object OpenCodeSpacingScale {
    fun default() = OpenCodeSpacingTokens(
        xs = 4.dp,
        sm = 8.dp,
        md = 12.dp,
        lg = 16.dp,
        xl = 24.dp,
    )
}

data class OpenCodeTypographyTokens(
    val small: TextUnit,
    val base: TextUnit,
    val large: TextUnit,
    val smallLineHeight: TextUnit,
    val baseLineHeight: TextUnit,
    val largeLineHeight: TextUnit,
)

object OpenCodeTypographyScale {
    fun default() = OpenCodeTypographyTokens(
        small = 13.sp,
        base = 14.sp,
        large = 16.sp,
        smallLineHeight = 20.sp,
        baseLineHeight = 18.sp,
        largeLineHeight = 24.sp,
    )
}

data class OpenCodeBorderTokens(
    val default: Dp,
    val emphasis: Dp,
    val featured: Dp,
)

object OpenCodeBorderScale {
    fun default() = OpenCodeBorderTokens(
        default = 0.5.dp,
        emphasis = 0.5.dp,
        featured = 2.dp,
    )
}

data class OpenCodeDesignTokens(
    val palette: OpenCodePaletteTokens,
    val shapes: OpenCodeShapeTokens,
    val spacing: OpenCodeSpacingTokens,
    val typography: OpenCodeTypographyTokens,
    val borders: OpenCodeBorderTokens,
)

private val LocalOpenCodeDesignTokens = staticCompositionLocalOf {
    OpenCodeDesignTokens(
        palette = OpenCodePalette.light(),
        shapes = OpenCodeShapeScale.default(),
        spacing = OpenCodeSpacingScale.default(),
        typography = OpenCodeTypographyScale.default(),
        borders = OpenCodeBorderScale.default(),
    )
}

@Composable
fun opencodeTokens(): OpenCodeDesignTokens = LocalOpenCodeDesignTokens.current

@Composable
fun ProvideOpenCodeTokens(
    tokens: OpenCodeDesignTokens = OpenCodeDesignTokens(
        palette = OpenCodePalette.light(),
        shapes = OpenCodeShapeScale.default(),
        spacing = OpenCodeSpacingScale.default(),
        typography = OpenCodeTypographyScale.default(),
        borders = OpenCodeBorderScale.default(),
    ),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalOpenCodeDesignTokens provides tokens) {
        content()
    }
}
