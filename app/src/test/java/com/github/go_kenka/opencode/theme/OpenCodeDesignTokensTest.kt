package com.github.go_kenka.opencode.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenCodeDesignTokensTest {

    @Test
    fun lightPalette_hasExpectedKeyValues() {
        val palette = OpenCodePalette.light()

        assertEquals(Color(0xFF111111), palette.primary)
        assertEquals(Color(0xFF111111), palette.accent)
        assertEquals(Color(0xFFFFFFFF), palette.background)
        assertEquals(Color(0xFFFFFFFF), palette.surface)
        assertEquals(Color(0xFFF3F3F3), palette.toolbarBackground)
        assertEquals(Color(0xFF111111), palette.textPrimary)
        assertEquals(Color(0xFF666666), palette.textSecondary)
        assertEquals(Color(0xFFDDDDDD), palette.borderTertiary)
        assertEquals(Color(0xFFBBBBBB), palette.borderSecondary)
    }

    @Test
    fun defaultShapeScale_hasExpectedRadii() {
        val shapes = OpenCodeShapeScale.default()

        assertEquals(12.dp, shapes.large)
        assertEquals(6.dp, shapes.medium)
        assertEquals(4.dp, shapes.small)
    }
}
