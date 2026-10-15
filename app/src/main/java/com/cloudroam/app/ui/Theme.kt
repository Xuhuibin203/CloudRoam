package com.cloudroam.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BrandPrimary = Color(0xFF02743B)
private val BrandOnPrimary = Color(0xFFFFFFFF)
private val BrandContainer = Color(0xFFE5FFF2)

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = BrandOnPrimary,
    primaryContainer = BrandContainer,
    onPrimaryContainer = Color(0xFF004D26),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF32F08C),
    onPrimary = Color(0xFF004D26),
    primaryContainer = Color(0xFF004D26),
    onPrimaryContainer = Color(0xFFE5FFF2),
)

@Composable
fun CloudRoamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
