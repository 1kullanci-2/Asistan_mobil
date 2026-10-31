package com.edith.asistan

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palet: gül kurusu (orta ton, tek tema). İsimler eski paletten kaldı; ana zemin artık soluk gül tonu.
val Visne = Color(0xFF9C6670)        // ana zemin
val VisneKoyu = Color(0xFF7E4D57)    // derin alanlar (yan panel vb.)
val VisneAcik = Color(0xFFB27F88)    // zemin üstünde ikincil yüzey
val Krem = Color(0xFFF5EBEC)         // kartlar
val KremKoyu = Color(0xFFE8D8DB)     // kart içi ikincil alan, kenarlar
val KremAcik = Color(0xFFFBF5F5)     // giriş alanları
val MetinKoyu = Color(0xFF3B2226)    // açık yüzey üstü yazı
val MetinKrem = Color(0xFFFFF7F5)    // zemin üstü yazı
val SolukKoyu = Color(0xFF8C6A70)    // açık yüzey üstü soluk yazı
val SolukKrem = Color(0xFFF0D9DC)    // zemin üstü soluk yazı
val Vurgu = Color(0xFF8A4655)        // açık yüzey üstü vurgu
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
