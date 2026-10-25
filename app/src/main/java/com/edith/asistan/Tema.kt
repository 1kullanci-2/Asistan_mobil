package com.edith.asistan

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Tek tema: koyu, düz tonlar; parıltı/degrade yok.
val Zemin = Color(0xFF141414)
val Yuzey = Color(0xFF1E1E1E)
val Kenar = Color(0xFF2E2E2E)
val Metin = Color(0xFFE8E4DF)
val Soluk = Color(0xFF8C867F)
val Turuncu = Color(0xFFD9814A)
val Yesil = Color(0xFF5FBF7A)
val Kirmizi = Color(0xFFE06C5F)

@Composable
fun EdithTema(icerik: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Turuncu, onPrimary = Zemin, background = Zemin, surface = Yuzey,
            onSurface = Metin, onBackground = Metin, surfaceVariant = Yuzey, outline = Kenar
        ),
        content = icerik
    )
}
