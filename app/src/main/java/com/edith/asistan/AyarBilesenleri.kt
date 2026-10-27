package com.edith.asistan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

enum class DugmeTuru { BIRINCIL, IKINCIL, TEHLIKE }

/** Ayar sayfalarının ortak çerçevesi: geri oku, başlık ve kayan içerik. */
@Composable
fun SayfaCerceve(baslik: String, geri: () -> Unit, icerik: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Visne).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).clickable(onClick = geri), contentAlignment = Alignment.Center) {
                Simge("geri", MetinKrem, Modifier.size(24.dp))
            }
            Text(baslik, Modifier.padding(start = 4.dp), color = MetinKrem, fontFamily = FontFamily.Serif, fontSize = 26.sp)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            icerik()
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** Krem kart bölümü: başlık, isteğe bağlı açıklama ve içerik. */
@Composable
fun Bolum(baslik: String, aciklama: String? = null, icerik: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).kart().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(baslik, color = MetinKoyu, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        if (aciklama != null) Text(aciklama, color = SolukKoyu, fontSize = 13.sp, lineHeight = 18.sp)
        icerik()
    }
}

/** Bölüm içinde, bir ton koyu krem alt kutu. */
@Composable
fun AltKutu(icerik: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KremKoyu).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { icerik() }
}

@Composable
fun Satir(baslik: String, aciklama: String? = null, sag: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(baslik, color = MetinKoyu, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (aciklama != null) Text(aciklama, color = SolukKoyu, fontSize = 12.sp, lineHeight = 16.sp)
        }
        sag()
    }
}

@Composable
fun Anahtarli(baslik: String, aciklama: String?, acik: Boolean, degisti: (Boolean) -> Unit) {
    Satir(baslik, aciklama) {
        Switch(
            checked = acik, onCheckedChange = degisti,
            colors = SwitchDefaults.colors(
                checkedThumbColor = KremAcik, checkedTrackColor = Vurgu, checkedBorderColor = Vurgu,
                uncheckedThumbColor = SolukKoyu, uncheckedTrackColor = KremKoyu, uncheckedBorderColor = SolukKoyu
            )
        )
    }
}

@Composable
fun Dugme(yazi: String, tur: DugmeTuru = DugmeTuru.IKINCIL, mod: Modifier = Modifier, tikla: () -> Unit) {
    val (zemin, yaziRenk) = when (tur) {
        DugmeTuru.BIRINCIL -> Visne to MetinKrem
        DugmeTuru.IKINCIL -> KremKoyu to MetinKoyu
        DugmeTuru.TEHLIKE -> KremKoyu to Hata
    }
    Box(
        mod.clip(RoundedCornerShape(50)).background(zemin).clickable(onClick = tikla)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(yazi, color = yaziRenk, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}

@Composable
fun Etiket(yazi: String, tur: String) {
    val (zemin, renk) = when (tur) {
        "ok" -> Color(0xFFDDEFE3) to Yesil
        "hata" -> Color(0xFFF6D9D5) to Hata
        "bilgi" -> Color(0xFFEBD9DC) to Vurgu
        else -> KremKoyu to SolukKoyu
    }
    Text(
        yazi, Modifier.clip(RoundedCornerShape(50)).background(zemin).padding(horizontal = 10.dp, vertical = 4.dp),
        color = renk, fontSize = 12.sp, fontWeight = FontWeight.Medium
    )
}

@Composable
fun Alan(deger: String, degisti: (String) -> Unit, ipucu: String, gizli: Boolean = false) {
    OutlinedTextField(
        value = deger, onValueChange = degisti, modifier = Modifier.fillMaxWidth(), singleLine = true,
        visualTransformation = if (gizli) PasswordVisualTransformation() else VisualTransformation.None,
        placeholder = { Text(ipucu, color = SolukKoyu) },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Vurgu, unfocusedBorderColor = KremKoyu,
            focusedContainerColor = KremAcik, unfocusedContainerColor = KremAcik, cursorColor = Vurgu,
            focusedTextColor = MetinKoyu, unfocusedTextColor = MetinKoyu
        )
    )
}

/** − değer + biçiminde sayı ayarlayıcı. */
@Composable
fun Adim(deger: Float, aralik: ClosedFloatingPointRange<Float>, adim: Float, birim: String, degisti: (Float) -> Unit) {
    fun yeni(d: Float) = ((d * 100).roundToInt() / 100f).coerceIn(aralik.start, aralik.endInclusive)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Dugme("−") { degisti(yeni(deger - adim)) }
        Text("${"%.1f".format(deger)}$birim", color = MetinKoyu, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Dugme("+") { degisti(yeni(deger + adim)) }
    }
}

@Composable
fun Kaydirici(baslik: String, aciklama: String, deger: Float, degisti: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(baslik, Modifier.weight(1f), color = MetinKoyu, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("%.2f".format(deger), color = Vurgu, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(aciklama, color = SolukKoyu, fontSize = 12.sp, lineHeight = 16.sp)
        Slider(
            value = deger, onValueChange = { degisti((it * 100).roundToInt() / 100f) }, valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = Visne, activeTrackColor = Vurgu, inactiveTrackColor = KremKoyu)
        )
    }
}

@Composable
fun KategoriKarti(simge: String, baslik: String, alt: String, mod: Modifier, tikla: () -> Unit) {
    Column(
        mod.kart(RoundedCornerShape(24.dp)).clickable(onClick = tikla).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(Visne), contentAlignment = Alignment.Center) {
            Simge(simge, KremAcik, Modifier.size(24.dp))
        }
        Text(baslik, color = MetinKoyu, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Text(alt, color = SolukKoyu, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

/** Hata mesajı kutusu (Motor.hata); kapatılabilir. */
@Composable
fun HataKutusu() {
    val h = Motor.hata ?: return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF6D9D5)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(h, Modifier.weight(1f), color = Hata, fontSize = 13.sp)
        Text("Kapat", Modifier.clickable { Motor.hata = null }.padding(start = 12.dp), color = Hata, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

fun Modifier.kartKenar(sekil: androidx.compose.ui.graphics.Shape, renk: Color, kalinlik: Int) =
    this.border(kalinlik.dp, renk, sekil)
