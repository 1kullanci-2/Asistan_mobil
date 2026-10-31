package com.edith.asistan

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

/** Düz krem yüzey: degrade, gölge ya da parıltı yok. */
fun Modifier.kart(sekil: Shape = RoundedCornerShape(22.dp), renk: Color = Krem): Modifier = this
    .clip(sekil)
    .background(renk)
    .border(1.dp, KremKoyu, sekil)

private fun karsilama(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Günaydın"
    in 12..17 -> "İyi günler"
    in 18..22 -> "İyi akşamlar"
    else -> "İyi geceler"
}

@Composable
fun Uygulama(onMik: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Visne)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            UstCubuk { Motor.menuAcik = true }
            Sohbet(Modifier.weight(1f))
            DurumSatiri()
            GirisCubugu(onMik)
        }
        YanPanel()
        if (Motor.ayarAcik) AyarlarEkrani { Motor.ayarAcik = false }
        OnayDiyalogu()
    }
}

@Composable
fun OnayDiyalogu() {
    val o = Motor.onay ?: return
    AlertDialog(
        onDismissRequest = { Motor.onayCevap(false) },
        containerColor = Krem,
        title = { Text("Onay gerekiyor", color = MetinKoyu) },
        text = { Text(o.soru, color = MetinKoyu) },
        confirmButton = {
            Text("Evet", Modifier.clickable { Motor.onayCevap(true) }.padding(12.dp), color = Vurgu, fontWeight = FontWeight.SemiBold)
        },
        dismissButton = {
            Text("Hayır", Modifier.clickable { Motor.onayCevap(false) }.padding(12.dp), color = SolukKoyu)
        }
    )
}

@Composable
fun UstCubuk(menu: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        CamDugme("menu", menu)
    }
}

/** Yarı saydam film + ince parlak kenar: bulanıklık olmadan cam hissi verir. */
@Composable
fun CamDugme(simge: String, tikla: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.32f), CircleShape).clickable(onClick = tikla),
        contentAlignment = Alignment.Center
    ) { Simge(simge, MetinKrem, Modifier.size(22.dp)) }
}

@Composable
fun YanPanel() {
    BackHandler(enabled = Motor.menuAcik) { Motor.menuAcik = false }
    var silinecek by remember { mutableStateOf<SohbetBaslik?>(null) }
    AnimatedVisibility(visible = Motor.menuAcik, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(Color(0x59000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { Motor.menuAcik = false }
        )
    }
    AnimatedVisibility(
        visible = Motor.menuAcik,
        enter = slideInHorizontally { -it },
        exit = slideOutHorizontally { -it }
    ) {
        val sekil = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
        Column(
            Modifier.fillMaxHeight().width(304.dp).clip(sekil).background(VisneKoyu)
                .border(1.dp, Color.White.copy(alpha = 0.22f), sekil)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .statusBarsPadding().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CamDugme("ayar") { Motor.menuAcik = false; Motor.ayarAcik = true }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.14f))
                    .clickable { Motor.yeniSohbet() }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Simge("yeni", MetinKrem, Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Yeni sohbet", color = MetinKrem, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Text("Sohbetler", Modifier.padding(start = 6.dp), color = SolukKrem, fontSize = 12.sp)
            if (Motor.sohbetler.isEmpty()) {
                Text("Henüz sohbet yok.", Modifier.padding(start = 6.dp), color = SolukKrem, fontSize = 13.sp)
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(Motor.sohbetler, key = { it.id }) { sb ->
                    val secili = sb.id == Motor.aktifId
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(if (secili) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                            .clickable { Motor.sohbetAc(sb.id) }.padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            sb.baslik, Modifier.weight(1f).padding(vertical = 12.dp), color = MetinKrem, fontSize = 15.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).clickable { silinecek = sb },
                            contentAlignment = Alignment.Center
                        ) { Simge("cop", SolukKrem, Modifier.size(18.dp)) }
                    }
                }
            }
        }
    }
    silinecek?.let { sb ->
        AlertDialog(
            onDismissRequest = { silinecek = null },
            containerColor = Krem,
            title = { Text("Sohbet silinsin mi?", color = MetinKoyu) },
            text = { Text("“${sb.baslik}” bu telefondan kalıcı olarak silinir.", color = MetinKoyu) },
            confirmButton = {
                Text(
                    "Sil", Modifier.clickable { Motor.sohbetSil(sb.id); silinecek = null }.padding(12.dp),
                    color = Hata, fontWeight = FontWeight.SemiBold
                )
            },
            dismissButton = { Text("Vazgeç", Modifier.clickable { silinecek = null }.padding(12.dp), color = SolukKoyu) }
        )
    }
}

@Composable
fun Sohbet(mod: Modifier) {
    val liste = rememberLazyListState()
    val alt = remember {
        listOf("Bugün ne yapıyoruz?", "Nasılsınız?", "E.D.I.T.H.'e hoş geldiniz", "Size nasıl yardımcı olabilirim?").random()
    }
    val canliVar = Motor.faz == Faz.KOMUT && Motor.canli.isNotBlank()
    val akan = Motor.akanMetin.isNotBlank() && (Motor.faz == Faz.ISLE || Motor.faz == Faz.KONUS)
    val adet = Motor.mesajlar.size + (if (Motor.faz == Faz.ISLE || akan) 1 else 0) + (if (canliVar) 1 else 0) +
        (if (Motor.hata != null) 1 else 0)
    LaunchedEffect(adet, Motor.canli, Motor.akanMetin.length / 40) { if (adet > 0) liste.animateScrollToItem(adet - 1) }

    if (Motor.mesajlar.isEmpty() && Motor.faz != Faz.ISLE && !canliVar) {
        Box(mod.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(karsilama(), color = MetinKrem, fontFamily = FontFamily.Serif, fontSize = 32.sp)
                Text(alt, color = SolukKrem, fontFamily = FontFamily.Serif, fontSize = 17.sp, textAlign = TextAlign.Center)
                Motor.indirme?.let { Text(it, color = VurguAcik, fontSize = 13.sp) }
                Motor.hata?.let { Text(it, color = HataAcik, fontSize = 13.sp) }
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
        items(Motor.mesajlar) { m ->
            if (m.not) NotSatiri(m.metin) else if (m.kullanici) KullaniciBalonu(m.metin, 1f) else AsistanMetni(m.metin)
        }
        if (canliVar) item { KullaniciBalonu(Motor.canli, 0.65f) }
        if (akan) item { AsistanMetni(Motor.akanMetin) } else if (Motor.faz == Faz.ISLE) item { Dusunuyor() }
        Motor.indirme?.let { t -> item { Text(t, color = VurguAcik, fontSize = 13.sp) } }
        Motor.hata?.let { h -> item { Text(h, color = HataAcik, fontSize = 13.sp) } }
    }
}

@Composable
fun NotSatiri(metin: String) {
    Text(metin, Modifier.fillMaxWidth(), color = SolukKrem, fontSize = 12.sp, textAlign = TextAlign.Center)
}

@Composable
fun KullaniciBalonu(metin: String, alfa: Float) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            metin,
            Modifier.widthIn(max = 300.dp).alpha(alfa)
                .kart(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            color = MetinKoyu, fontSize = 16.sp
        )
    }
}

@Composable
fun AsistanMetni(metin: String) {
    SelectionContainer {
        Text(metin, color = MetinKrem, fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 27.sp)
    }
}

@Composable
fun Dusunuyor() {
    val t = rememberInfiniteTransition(label = "dusunuyor")
    val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "alfa")
    Text("Düşünüyor…", Modifier.alpha(a), color = VurguAcik, fontFamily = FontFamily.Serif, fontSize = 16.sp)
}

@Composable
fun DurumSatiri() {
    val (yazi, renk) = when (Motor.faz) {
        Faz.KOMUT -> "Dinleniyor" to YesilAcik
        Faz.ISLE -> "Düşünüyor…" to VurguAcik
        Faz.KONUS -> "Konuşuyor" to VurguAcik
        Faz.BOS -> "Bekleniyor" to SolukKrem
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(yazi, color = renk, fontSize = 13.sp)
        if (Motor.mikAcik && Motor.faz == Faz.BOS) Text("  ·  \"hey edit\" de", color = SolukKrem, fontSize = 13.sp)
    }
}

@Composable
fun GirisCubugu(onMik: () -> Unit) {
    var metin by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
            .kart(RoundedCornerShape(32.dp))
            .padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = metin,
            onValueChange = { metin = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Mesaj yaz…", color = SolukKoyu) },
            maxLines = 4,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Vurgu,
                focusedTextColor = MetinKoyu,
                unfocusedTextColor = MetinKoyu
            )
        )
        Box(
            Modifier.height(42.dp).clip(RoundedCornerShape(50))
                .background(if (Motor.mikAcik) Yesil else KremKoyu)
                .clickable(onClick = onMik).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (Motor.mikAcik) "Mik açık" else "Mikrofon",
                color = if (Motor.mikAcik) KremAcik else MetinKoyu, fontSize = 13.sp, fontWeight = FontWeight.Medium
            )
        }
        Box(Modifier.padding(start = 8.dp))
        Box(
            Modifier.height(42.dp).widthIn(min = 42.dp).clip(CircleShape).background(Vurgu).clickable {
                if (metin.isNotBlank()) {
                    Motor.gonder(metin, sesli = false)
                    metin = ""
                }
            },
            contentAlignment = Alignment.Center
        ) { Text("↑", color = KremAcik, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
    }
}
