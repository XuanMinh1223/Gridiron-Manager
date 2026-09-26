package com.xuan.gridironmanager.ui.match.components

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class TeamPaletteTest {
    @Test
    fun parsesSixDigitAndEightDigitHexColors() {
        assertEquals(Color(0xFF123456), teamPalette("#123456", "#FFFFFF").primary)
        assertEquals(Color(0x80123456), teamPalette("#123456", "#80123456").secondary)
    }

    @Test
    fun malformedColorsUseSafeFallbacks() {
        assertEquals(Color(0xFF0D47A1), teamPalette("invalid", "#FFFFFF").primary)
        assertEquals(Color.White, teamPalette("#0D47A1", "invalid").secondary)
    }
}
