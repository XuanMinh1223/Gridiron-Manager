package com.xuan.gridironmanager.ui.match.components

import androidx.compose.ui.graphics.Color

data class TeamPalette(
    val primary: Color,
    val secondary: Color,
)

private val DefaultPrimary = Color(0xFF0D47A1)
private val DefaultSecondary = Color.White

fun teamPalette(primaryHex: String, secondaryHex: String): TeamPalette =
    TeamPalette(
        primary = parseHexColor(primaryHex) ?: DefaultPrimary,
        secondary = parseHexColor(secondaryHex) ?: DefaultSecondary,
    )

private fun parseHexColor(value: String): Color? {
    val hex = value.trim().removePrefix("#")
    if (hex.length != 6 && hex.length != 8) return null
    return hex.toLongOrNull(16)?.let { parsed ->
        if (hex.length == 6) Color((0xFF000000L or parsed).toInt()) else Color(parsed.toInt())
    }
}
