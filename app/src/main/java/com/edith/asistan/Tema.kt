package com.edith.asistan

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palet: vişne ve krem. Yalnızca renk; hiçbir simge/şekil bu paletten türetilmez.
val Visne = Color(0xFF5B1A2B)        // ana zemin
val VisneKoyu = Color(0xFF47121F)    // derin alanlar
val VisneAcik = Color(0xFF7A2A3E)    // zemin üstünde ikincil yüzey
val Krem = Color(0xFFF2E8DA)         // kartlar
val KremKoyu = Color(0xFFE6D8C5)     // kart içi ikincil alan, kenarlar
val KremAcik = Color(0xFFFAF4EB)     // giriş alanları
val MetinKoyu = Color(0xFF3A1220)    // krem üstü yazı
val MetinKrem = Color(0xFFF5EBDD)    // vişne üstü yazı
val SolukKoyu = Color(0xFF8A6B6E)    // krem üstü soluk yazı
val SolukKrem = Color(0xFFD9BFB9)    // vişne üstü soluk yazı
val Vurgu = Color(0xFFB3344A)        // krem üstü vurgu
val VurguAcik = Color(0xFFE58A97)    // vişne üstü vurgu
val Yesil = Color(0xFF3F8F5F)
val YesilAcik = Color(0xFF8ED3A8)
val Hata = Color(0xFFB3261E)
val HataAcik = Color(0xFFF2A39C)

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
