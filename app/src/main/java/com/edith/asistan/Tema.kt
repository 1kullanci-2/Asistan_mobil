package com.edith.asistan

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Krem = Color(0xFFF6F1E9)
val Murekkep = Color(0xFF2B2320)
val Turuncu = Color(0xFFD97A3E)
val Soluk = Color(0xFF8A7A6E)

@Composable
fun EdithTema(icerik: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Turuncu, background = Krem, surface = Krem, onSurface = Murekkep),
        content = icerik
    )
}
