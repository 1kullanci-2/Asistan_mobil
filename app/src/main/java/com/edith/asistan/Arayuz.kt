package com.edith.asistan

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

/** Düz, yarı koyu yüzey: degrade, gölge ya da parıltı yok. */
fun Modifier.yuzey(sekil: Shape = RoundedCornerShape(20.dp), renk: Color = Yuzey): Modifier = this
    .clip(sekil)
    .background(renk)
    .border(1.dp, Kenar, sekil)

private fun karsilama(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Günaydın"
    in 12..17 -> "İyi günler"
    in 18..22 -> "İyi akşamlar"
    else -> "İyi geceler"
}

@Composable
fun Uygulama(onMik: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Zemin)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            UstCubuk { Motor.ayarAcik = true }
            Sohbet(Modifier.weight(1f))
            DurumSatiri()
            GirisCubugu(onMik)
        }
        if (Motor.ayarAcik) AyarlarEkrani { Motor.ayarAcik = false }
    }
}

@Composable
fun UstCubuk(ayarlar: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("E.D.I.T.H", color = Metin, fontFamily = FontFamily.Serif, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f))
        Text(
            "Ayarlar",
            Modifier.yuzey(RoundedCornerShape(50)).clickable(onClick = ayarlar).padding(horizontal = 16.dp, vertical = 9.dp),
            color = Metin, fontSize = 14.sp
        )
    }
}

@Composable
fun Sohbet(mod: Modifier) {
    val liste = rememberLazyListState()
    val canliVar = Motor.faz == Faz.KOMUT && Motor.canli.isNotBlank()
    val adet = Motor.mesajlar.size + (if (Motor.faz == Faz.ISLE) 1 else 0) + (if (canliVar) 1 else 0) +
        (if (Motor.hata != null) 1 else 0)
    LaunchedEffect(adet, Motor.canli) { if (adet > 0) liste.animateScrollToItem(adet - 1) }

    if (Motor.mesajlar.isEmpty() && Motor.faz != Faz.ISLE && !canliVar) {
        Box(mod.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(karsilama(), color = Metin, fontFamily = FontFamily.Serif, fontSize = 32.sp)
                Text("E.D.I.T.H.'e hoş geldiniz", color = Soluk, fontFamily = FontFamily.Serif, fontSize = 17.sp)
                Motor.indirme?.let { Text(it, color = Turuncu, fontSize = 13.sp) }
                Motor.hata?.let { Text(it, color = Kirmizi, fontSize = 13.sp) }
            }
        }
        return
    }
    LazyColumn(
        mod.fillMaxWidth(),
        state = liste,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        items(Motor.mesajlar) { m -> if (m.kullanici) KullaniciBalonu(m.metin, 1f) else AsistanMetni(m.metin) }
        if (canliVar) item { KullaniciBalonu(Motor.canli, 0.6f) }
        if (Motor.faz == Faz.ISLE) item { Dusunuyor() }
        Motor.indirme?.let { t -> item { Text(t, color = Turuncu, fontSize = 13.sp) } }
        Motor.hata?.let { h -> item { Text(h, color = Kirmizi, fontSize = 13.sp) } }
    }
}

@Composable
fun KullaniciBalonu(metin: String, alfa: Float) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            metin,
            Modifier.widthIn(max = 300.dp).alpha(alfa)
                .yuzey(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            color = Metin, fontSize = 16.sp
        )
    }
}

@Composable
fun AsistanMetni(metin: String) {
    SelectionContainer {
        Text(metin, color = Metin, fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 27.sp)
    }
}

@Composable
fun Dusunuyor() {
    val t = rememberInfiniteTransition(label = "dusunuyor")
    val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "alfa")
    Text("Düşünüyor…", Modifier.alpha(a), color = Turuncu, fontFamily = FontFamily.Serif, fontSize = 16.sp)
}

@Composable
fun DurumSatiri() {
    val (yazi, renk) = when (Motor.faz) {
        Faz.KOMUT -> "Dinleniyor" to Yesil
        Faz.ISLE -> "Düşünüyor…" to Turuncu
        Faz.KONUS -> "Konuşuyor" to Turuncu
        Faz.BOS -> "Bekleniyor" to Soluk
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(yazi, color = renk, fontSize = 13.sp)
        if (Motor.mikAcik && Motor.faz == Faz.BOS) Text("  ·  \"hey edit\" de", color = Soluk, fontSize = 13.sp)
    }
}

@Composable
fun GirisCubugu(onMik: () -> Unit) {
    var metin by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
            .yuzey(RoundedCornerShape(32.dp))
            .padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = metin,
            onValueChange = { metin = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Mesaj yaz…", color = Soluk) },
            maxLines = 4,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Turuncu,
                focusedTextColor = Metin,
                unfocusedTextColor = Metin
            )
        )
        Box(
            Modifier.height(42.dp).clip(RoundedCornerShape(50))
                .background(if (Motor.mikAcik) Yesil else Kenar)
                .clickable(onClick = onMik).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (Motor.mikAcik) "Mik açık" else "Mikrofon",
                color = if (Motor.mikAcik) Zemin else Metin, fontSize = 13.sp, fontWeight = FontWeight.Medium
            )
        }
        Box(Modifier.padding(start = 8.dp))
        Box(
            Modifier.height(42.dp).widthIn(min = 42.dp).clip(CircleShape).background(Turuncu).clickable {
                if (metin.isNotBlank()) {
                    Motor.gonder(metin, sesli = false)
                    metin = ""
                }
            },
            contentAlignment = Alignment.Center
        ) { Text("↑", color = Zemin, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
    }
}
