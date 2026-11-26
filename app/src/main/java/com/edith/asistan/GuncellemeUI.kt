package com.edith.asistan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tema renklerinde ilerleme çubuğu; yüzde çubuğun içinde değil, altında. */
@Composable
fun IlerlemeCubugu(oran: Float, zemin: Color = KremKoyu, dolgu: Color = Vurgu, yaziRengi: Color = SolukKoyu) {
    val o by animateFloatAsState(oran.coerceIn(0f, 1f), label = "indirme")
    val sekil = RoundedCornerShape(50)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(8.dp).clip(sekil).background(zemin)) {
            Box(Modifier.fillMaxWidth(o).fillMaxHeight().clip(sekil).background(dolgu))
        }
        Text("%${(o * 100).toInt()}", color = yaziRengi, fontSize = 12.sp)
    }
}

@Composable
fun SurumNotlari(g: GuncellemeBilgisi, yaziRengi: Color, soluk: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        g.notlar.forEach { n ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(n.surum, color = yaziRengi, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                n.maddeler.forEach { Text("•  $it", color = soluk, fontSize = 13.sp, lineHeight = 18.sp) }
            }
        }
    }
}

/** Yeni sürüm bulunduğunda (her sürüm için bir kez) açılan küçük panel. */
@Composable
fun GuncellemePaneli() {
    val g = Motor.guncelleme
    AnimatedVisibility(
        visible = Motor.guncellemePaneli && g != null,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.92f)
    ) {
        if (g == null) return@AnimatedVisibility
        val indiriyor = Motor.guncelOran >= 0f
        Box(
            Modifier.fillMaxSize().background(Color(0x66000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (!indiriyor) Motor.guncellemePaneli = false
                },
            contentAlignment = Alignment.Center
        ) {
            val sekil = RoundedCornerShape(26.dp)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 40.dp).clip(sekil).background(Krem)
                    .border(1.dp, KremKoyu, sekil)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Yeni sürüm var", color = MetinKoyu, fontFamily = FontFamily.Serif, fontSize = 20.sp)
                Text("Sürüm ${g.surum} hazır.", color = Vurgu, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                if (indiriyor) {
                    IlerlemeCubugu(Motor.guncelOran)
                    Text(Motor.guncelDurum, color = SolukKoyu, fontSize = 12.sp)
                } else {
                    if (Motor.guncelDurum.isNotBlank()) Text(Motor.guncelDurum, color = SolukKoyu, fontSize = 12.sp)
                    Text("İsterseniz daha sonra Ayarlar → Hakkında bölümünden de güncelleyebilirsiniz.", color = SolukKoyu, fontSize = 12.sp, lineHeight = 17.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Dugme("Daha sonra", DugmeTuru.IKINCIL, Modifier.weight(1f)) { Motor.guncellemePaneli = false }
                        Dugme("Güncelle", DugmeTuru.BIRINCIL, Modifier.weight(1f)) { Motor.guncelleIndir() }
                    }
                }
            }
        }
    }
}

@Composable
fun SayfaHakkinda(geri: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { Motor.guncellemeKontrol(false) }
    val g = Motor.guncelleme
    SayfaCerceve("Hakkında", geri) {
        Bolum("E.D.I.T.H.", "Yüklü sürüm: ${BuildConfig.VERSION_NAME}") {
            val indiriyor = Motor.guncelOran >= 0f
            when {
                Motor.guncelKontrolEdiliyor && g == null -> Text("Güncelleme kontrol ediliyor…", color = SolukKoyu, fontSize = 13.sp)
                Motor.guncelKontrolHata != null -> Text(Motor.guncelKontrolHata!!, color = Hata, fontSize = 13.sp)
                g != null && g.yeni -> Text("Yeni sürüm: ${g.surum}", color = MetinKoyu, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                g != null -> Text("Uygulama güncel.", color = SolukKoyu, fontSize = 13.sp)
            }
            if (indiriyor) {
                IlerlemeCubugu(Motor.guncelOran)
                Text(Motor.guncelDurum, color = SolukKoyu, fontSize = 12.sp)
            } else {
                if (Motor.guncelDurum.isNotBlank()) Text(Motor.guncelDurum, color = SolukKoyu, fontSize = 12.sp)
                if (g != null && g.yeni) Dugme("Güncelle", DugmeTuru.BIRINCIL, Modifier.fillMaxWidth()) { Motor.guncelleIndir() }
                Dugme(
                    if (Motor.guncelKontrolEdiliyor) "Kontrol ediliyor…" else "Güncellemeyi kontrol et",
                    if (g != null && g.yeni) DugmeTuru.IKINCIL else DugmeTuru.BIRINCIL,
                    Modifier.fillMaxWidth()
                ) { Motor.guncellemeKontrol(false) }
            }
        }
        if (g != null && g.notlar.isNotEmpty()) {
            Bolum("Değişiklikler") {
                g.notlar.forEach { n ->
                    AltKutu {
                        Text(n.surum, color = Vurgu, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        n.maddeler.forEach { Text("•  $it", color = MetinKoyu, fontSize = 13.sp, lineHeight = 18.sp) }
                    }
                }
            }
        }
    }
}
