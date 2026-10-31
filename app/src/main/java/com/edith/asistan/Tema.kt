package com.edith.asistan

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palet: gül kurusu (orta ton, tek tema). İsimler eski paletten kaldı; ana zemin artık toz gül/mor-pembe tonu.
val Visne = Color(0xFFA76C80)        // ana zemin
val VisneKoyu = Color(0xFF86506A)    // derin alanlar (yan panel vb.)
val VisneAcik = Color(0xFFBC8396)    // zemin üstünde ikincil yüzey
val Krem = Color(0xFFF7ECF0)         // kartlar
val KremKoyu = Color(0xFFEBD9E0)     // kart içi ikincil alan, kenarlar
val KremAcik = Color(0xFFFCF6F8)     // giriş alanları
val MetinKoyu = Color(0xFF3B2230)    // açık yüzey üstü yazı
val MetinKrem = Color(0xFFFFF8FA)    // zemin üstü yazı
val SolukKoyu = Color(0xFF8E6A78)    // açık yüzey üstü soluk yazı
val SolukKrem = Color(0xFFF3DCE5)    // zemin üstü soluk yazı
val Vurgu = Color(0xFF8C4A63)        // açık yüzey üstü vurgu
val VurguAcik = Color(0xFFCFDDC0)    // zemin üstü vurgu (fıstık yeşili)
val Yesil = Color(0xFF3F8F5F)
val YesilAcik = Color(0xFFCFDDC0)
val Hata = Color(0xFFB3261E)
val HataAcik = Color(0xFFFFE3DC)

@Composable
fun EdithTema(icerik: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Vurgu, onPrimary = KremAcik, background = Visne, surface = Krem,
            onSurface = MetinKoyu, onBackground = MetinKrem, surfaceVariant = KremKoyu, outline = KremKoyu
        ),
        content = icerik
    )
}
