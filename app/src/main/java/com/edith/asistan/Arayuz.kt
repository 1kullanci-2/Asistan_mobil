package com.edith.asistan

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
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

/** Düz kart yüzeyi: dolgu + ince kenar. */
fun Modifier.cam(sekil: Shape = RoundedCornerShape(22.dp), guc: Float = 1f): Modifier = this
    .clip(sekil)
    .background(Yuzey)
    .border(1.dp, YuzeyKenar, sekil)

private fun karsilama(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Günaydın"
    in 12..17 -> "İyi günler"
    in 18..22 -> "İyi akşamlar"
    else -> "İyi geceler"
}

@Composable
fun Uygulama(onMik: () -> Unit, onIzin: (Array<String>) -> Unit = {}) {
    Box(
        Modifier.fillMaxSize().background(Visne)
    ) {
        if (Motor.kurulumGoster) {
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) { KurulumEkrani(onIzin) }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                UstCubuk { Motor.menuAcik = true }
                Sohbet(Modifier.weight(1f))
                GirisCubugu(onMik)
            }
            YanPanel()
            if (Motor.ayarAcik) AyarlarEkrani { Motor.ayarAcik = false }
            OnayDiyalogu()
            ModelPaneli()
            IslemPaneli()
            MesajMenusu()
            GuncellemePaneli()
        }
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
        Modifier.size(46.dp).clip(CircleShape).background(Yuzey)
            .border(1.dp, YuzeyKenar, CircleShape).clickable(onClick = tikla),
        contentAlignment = Alignment.Center
    ) { Simge(simge, MetinKrem, Modifier.size(22.dp)) }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun YanPanel() {
    BackHandler(enabled = Motor.menuAcik) { Motor.menuAcik = false }
    var silinecek by remember { mutableStateOf<SohbetBaslik?>(null) }
    var menuId by remember { mutableStateOf<String?>(null) }
    var adDegisecek by remember { mutableStateOf<SohbetBaslik?>(null) }
    var yeniAd by remember { mutableStateOf("") }
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
                .border(1.dp, YuzeyKenar, sekil)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .statusBarsPadding().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CamDugme("ayar") { Motor.menuAcik = false; Motor.ayarAcik = true }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Yuzey)
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
                    Box {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(if (secili) YuzeyAcik else Color.Transparent)
                                .combinedClickable(onClick = { Motor.sohbetAc(sb.id) }, onLongClick = { menuId = sb.id })
                                .padding(start = 14.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                sb.baslik, Modifier.weight(1f).padding(vertical = 12.dp), color = MetinKrem, fontSize = 15.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).clickable { menuId = sb.id },
                                contentAlignment = Alignment.Center
                            ) { Simge("daha", SolukKrem, Modifier.size(20.dp)) }
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = menuId == sb.id,
                            onDismissRequest = { menuId = null },
                            offset = androidx.compose.ui.unit.DpOffset(120.dp, 0.dp),
                            containerColor = Krem
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Ad değiştir", color = MetinKoyu) },
                                onClick = { menuId = null; adDegisecek = sb; yeniAd = sb.baslik }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Sil", color = Hata) },
                                onClick = { menuId = null; silinecek = sb }
                            )
                        }
                    }
                }
            }
        }
    }
    adDegisecek?.let { sb ->
        AlertDialog(
            onDismissRequest = { adDegisecek = null },
            containerColor = Krem,
            title = { Text("Sohbet adı", color = MetinKoyu) },
            text = {
                androidx.compose.material3.TextField(
                    value = yeniAd, onValueChange = { yeniAd = it.take(60) }, singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = KremAcik, unfocusedContainerColor = KremAcik,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Vurgu, focusedTextColor = MetinKoyu, unfocusedTextColor = MetinKoyu
                    )
                )
            },
            confirmButton = {
                Text("Kaydet", Modifier.clickable { Motor.sohbetAdDegistir(sb.id, yeniAd); adDegisecek = null }.padding(12.dp),
                    color = Vurgu, fontWeight = FontWeight.SemiBold)
            },
            dismissButton = { Text("Vazgeç", Modifier.clickable { adDegisecek = null }.padding(12.dp), color = SolukKoyu) }
        )
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
    val canliVar = false   // sesli mesaj artık sohbette değil, yazı kutusunda canlı yazılır
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
        val sonAsistan = Motor.mesajlar.indexOfLast { !it.kullanici && !it.not }
        itemsIndexed(Motor.mesajlar) { i, m ->
            if (m.not) NotSatiri(m.metin) else if (m.kullanici) KullaniciBalonu(m.metin, 1f) { Motor.mesajMenusu = i }
            else AsistanMesaji(m, tikla = { Motor.mesajMenusu = i }, hoparlor = i == sonAsistan && (Motor.faz == Faz.KONUS || Motor.sesKapaliMi()))
        }
        if (akan) item { AsistanMetni(Motor.akanMetin, hoparlor = Motor.faz == Faz.KONUS) } else if (Motor.faz == Faz.ISLE) item { Dusunuyor() }
        Motor.indirme?.let { t -> item { Text(t, color = VurguAcik, fontSize = 13.sp) } }
        Motor.hata?.let { h -> item { Text(h, color = HataAcik, fontSize = 13.sp) } }
    }
}

@Composable
fun NotSatiri(metin: String) {
    Text(metin, Modifier.fillMaxWidth(), color = SolukKrem, fontSize = 12.sp, textAlign = TextAlign.Center)
}

@Composable
fun KullaniciBalonu(metin: String, alfa: Float, tikla: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            metin,
            Modifier.widthIn(max = 300.dp).alpha(alfa)
                .cam(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                .clickable(enabled = tikla != null) { tikla?.invoke() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            color = MetinKoyu, fontSize = 16.sp
        )
    }
}

@Composable
fun AsistanMetni(metin: String, hoparlor: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SelectionContainer {
            Text(metin, color = MetinKrem, fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 27.sp)
        }
        if (hoparlor) HoparlorDugmesi()
    }
}

/** Sesli cevaplarda görünen hoparlör: varsayılan açık; kapatılırsa bu sohbette tekrar açılana kadar kapalı kalır. */
@Composable
fun HoparlorDugmesi() {
    val kapali = Motor.sesKapaliMi()
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(Yuzey).border(1.dp, YuzeyKenar, CircleShape)
            .clickable { Motor.sesDegistir() },
        contentAlignment = Alignment.Center
    ) { Simge(if (kapali) "hoparlor_kapali" else "hoparlor", MetinKrem, Modifier.size(18.dp)) }
}

/** Düşünürken parlayan yazı; araç çalışırken hangi araç olduğu da görünür. */
@Composable
fun Dusunuyor() {
    val t = rememberInfiniteTransition(label = "dusunuyor")
    val kay by t.animateFloat(-260f, 700f, infiniteRepeatable(tween(1700, easing = LinearEasing)), label = "kay")
    val soluk = MetinKrem.copy(alpha = 0.5f)
    val firca = Brush.linearGradient(listOf(soluk, MetinKrem, soluk), start = Offset(kay, 0f), end = Offset(kay + 260f, 0f))
    val yazi = if (Motor.adim.isNotBlank()) Motor.adim + "…" else "Düşünüyor…"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DusunenIkon(MetinKrem)
        Text(yazi, style = TextStyle(brush = firca, fontFamily = FontFamily.Serif, fontSize = 16.sp))
    }
}

@Composable
fun AsistanMesaji(m: Mesaj, hoparlor: Boolean = false, tikla: () -> Unit = {}) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = tikla), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (m.araclar.isNotEmpty()) AracSatiri(m.araclar)
        AsistanMetni(m.metin, hoparlor)
    }
}

/** "İşlemler ⌄" satırı: dokununca kullanılan işlemler alttan açılan bir panelde listelenir. */
@Composable
fun AracSatiri(araclar: List<String>) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable { Motor.islemler = araclar }.padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("İşlemler", color = SolukKrem, fontSize = 13.sp)
        Spacer(Modifier.width(4.dp))
        Simge("ok_asagi", SolukKrem, Modifier.size(16.dp))
    }
}

private fun islemAdi(s: String): String =
    s.substringAfterLast("__").replace('_', ' ').trim().replaceFirstChar { it.uppercase() }

/** İşlemler listesi: alttan açılır, her işlem ayrı bir satırda. */
@Composable
fun IslemPaneli() {
    val liste = Motor.islemler
    BackHandler(liste != null) { Motor.islemler = null }
    AnimatedVisibility(visible = liste != null, enter = fadeIn(tween(150)), exit = fadeOut(tween(120))) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { Motor.islemler = null },
            contentAlignment = Alignment.BottomCenter
        ) {
            val sekil = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            Column(
                Modifier.fillMaxWidth().clip(sekil).background(Krem).border(1.dp, KremKoyu, sekil)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding().padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("İşlemler", color = MetinKoyu, fontFamily = FontFamily.Serif, fontSize = 20.sp)
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (liste ?: emptyList()).forEach { ad ->
                        val k = RoundedCornerShape(16.dp)
                        Row(
                            Modifier.fillMaxWidth().clip(k).background(Color.White).border(1.dp, KremKoyu, k).padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Vurgu))
                            Spacer(Modifier.width(12.dp))
                            Text(islemAdi(ad), color = MetinKoyu, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GirisCubugu(onMik: () -> Unit) {
    var metin by remember { mutableStateOf("") }
    val dinliyor = Motor.faz == Faz.KOMUT
    val bekliyor = Motor.faz == Faz.ISLE
    val duzenle = Motor.duzenleIndex >= 0
    LaunchedEffect(Motor.duzenleIndex) { if (Motor.duzenleIndex >= 0) metin = Motor.duzenleMetin }
    // Sesle konuşurken söylenenler mesaj olarak değil, bu kutuya canlı yazılır
    val gosterilen = if (dinliyor) Motor.canli else metin
    val yaziVar = metin.isNotBlank()
    val klavyeAcik = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    val genis = klavyeAcik || gosterilen.isNotBlank()
    val sekil = RoundedCornerShape(if (genis) 30.dp else 34.dp)

    /** Dinlemeyi bırakır; o ana kadar söylenen kutuda kalır, devamı elle yazılabilir. */
    fun sesiDurdur() {
        val t = Motor.canli.trim()
        Motor.dinlemeyiIptal()
        if (t.isNotEmpty()) metin = t
    }

    Column {
        if (duzenle) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Mesaj düzenleniyor", Modifier.weight(1f), color = SolukKrem, fontSize = 13.sp)
                Text("Vazgeç", Modifier.clip(RoundedCornerShape(8.dp)).clickable { Motor.duzenleIptal(); metin = "" }.padding(6.dp), color = MetinKrem, fontSize = 13.sp)
            }
        }
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
                .cam(sekil)
                .animateContentSize(tween(180))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            val sagGenislik = if (dinliyor) 100.dp else 54.dp
            TextField(
                value = gosterilen,
                onValueChange = { if (!dinliyor) metin = it },
                readOnly = dinliyor,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp),
                modifier = Modifier.fillMaxWidth()
                    .padding(start = if (genis) 0.dp else 92.dp, end = if (genis) 0.dp else sagGenislik, bottom = if (genis) 54.dp else 0.dp)
                    .heightIn(min = if (genis) 64.dp else 52.dp)
                    .onFocusChanged { if (it.isFocused && Motor.faz == Faz.KOMUT) sesiDurdur() },
                placeholder = { Text(if (dinliyor) "Dinleniyor…" else "Mesaj yaz…", color = MetinKoyu.copy(alpha = 0.6f), fontSize = 17.sp) },
                maxLines = if (genis) 6 else 1,
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
            ModelKutusu(Modifier.align(if (genis) Alignment.BottomStart else Alignment.CenterStart).padding(start = 4.dp, bottom = if (genis) 8.dp else 0.dp))
            Row(
                Modifier.align(if (genis) Alignment.BottomEnd else Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mikrofon düğmesi bir damla gibi ikiye bölünür: [durdur] [gönder]
                val yay = androidx.compose.animation.core.spring<androidx.compose.ui.unit.Dp>(dampingRatio = 0.5f, stiffness = 260f)
                val durdurGen by androidx.compose.animation.core.animateDpAsState(if (dinliyor) 46.dp else 0.dp, yay, label = "bol")
                val aralik by androidx.compose.animation.core.animateDpAsState(if (dinliyor) 8.dp else 0.dp, yay, label = "aralik")
                if (durdurGen > 1.dp) {
                    Box(
                        Modifier.width(durdurGen.coerceAtLeast(0.dp)).height(46.dp).clip(CircleShape).background(Vurgu)
                            .clickable { sesiDurdur() },
                        contentAlignment = Alignment.Center
                    ) { if (durdurGen > 30.dp) Simge("durdur", KremAcik, Modifier.size(18.dp)) }
                    Spacer(Modifier.width(aralik.coerceAtLeast(0.dp)))
                }
                val durum = if (bekliyor) 2 else if (dinliyor || yaziVar) 1 else 0
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(if (dinliyor) Yesil else Vurgu).clickable {
                        when {
                            bekliyor -> Motor.durdur()
                            dinliyor -> {
                                val t = Motor.canli.trim()
                                Motor.dinlemeyiIptal()
                                if (t.isNotEmpty()) Motor.gonder(t, sesli = true)
                            }
                            duzenle -> if (yaziVar) { Motor.duzenleGonder(metin.trim()); metin = "" }
                            yaziVar -> { Motor.gonder(metin, sesli = false); metin = "" }
                            else -> onMik()
                        }
                    },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = durum,
                        transitionSpec = {
                            (fadeIn(tween(160)) + scaleIn(initialScale = 0.6f)) togetherWith
                                (fadeOut(tween(120)) + scaleOut(targetScale = 0.6f))
                        },
                        label = "gonder"
                    ) { d -> Simge(when (d) { 2 -> "durdur"; 1 -> "gonder"; else -> "mik" }, KremAcik, Modifier.size(if (d == 2) 20.dp else 24.dp)) }
                }
            }
        }
    }
}

/** Mesaja dokununca alttan açılan eylem paneli: kopyala, düzenle (yalnızca kendi mesajların), sil. */
@Composable
fun MesajMenusu() {
    val i = Motor.mesajMenusu
    val m = Motor.mesajlar.getOrNull(i)
    BackHandler(m != null) { Motor.mesajMenusu = -1 }
    val pano = androidx.compose.ui.platform.LocalClipboardManager.current
    AnimatedVisibility(visible = m != null, enter = fadeIn(tween(150)), exit = fadeOut(tween(120))) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { Motor.mesajMenusu = -1 },
            contentAlignment = Alignment.BottomCenter
        ) {
            val sekil = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            Column(
                Modifier.fillMaxWidth().clip(sekil).background(Krem).border(1.dp, KremKoyu, sekil)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding().padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                @Composable
                fun Satir(yazi: String, renk: Color, tikla: () -> Unit) {
                    val k = RoundedCornerShape(16.dp)
                    Text(
                        yazi, Modifier.fillMaxWidth().clip(k).background(Color.White).border(1.dp, KremKoyu, k)
                            .clickable { Motor.mesajMenusu = -1; tikla() }.padding(horizontal = 16.dp, vertical = 14.dp),
                        color = renk, fontSize = 16.sp
                    )
                }
                Satir("Kopyala", MetinKoyu) { m?.let { pano.setText(androidx.compose.ui.text.AnnotatedString(it.metin)) } }
                if (m?.kullanici == true) Satir("Düzenle", MetinKoyu) { Motor.duzenleBaslat(i) }
                Satir("Sil", Hata) { Motor.mesajSil(i) }
            }
        }
    }
}

/** Seçimi ("servis|model") kutuya sığacak kısa bir ada çevirir. */
private fun kisaModel(secim: String): String {
    val m = secim.substringAfter("|", "").substringAfterLast("/").removePrefix("gemini-")
    return m.ifBlank { "model" }
}

/** Giriş çubuğunun solundaki küçük kutucuk: seçili modeli gösterir, dokununca alt paneli açar. */
@Composable
fun ModelKutusu(mod: Modifier = Modifier) {
    val sekil = RoundedCornerShape(14.dp)
    Text(
        kisaModel(Motor.secim),
        mod.widthIn(max = 92.dp).clip(sekil).background(YuzeyAcik)
            .border(1.dp, YuzeyKenar, sekil)
            .clickable { Motor.modelPaneli = true }
            .padding(horizontal = 9.dp, vertical = 6.dp),
        color = MetinKoyu, fontSize = 11.sp, maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
    )
}

/** Alttan açılan panel: anahtarı girilmiş bütün servislerin seçili modelleri; birine dokunarak geçilir. */
@Composable
fun ModelPaneli() {
    BackHandler(Motor.modelPaneli) { Motor.modelPaneli = false }
    val a = Motor.ayarlar
    AnimatedVisibility(
        visible = Motor.modelPaneli,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(120))
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { Motor.modelPaneli = false },
            contentAlignment = Alignment.BottomCenter
        ) {
            val sekil = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            Column(
                Modifier.fillMaxWidth().clip(sekil).background(Krem)
                    .border(1.dp, KremKoyu, sekil)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding().padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Model seç", color = MetinKoyu, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 20.sp)
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val servisler = Ayarlar.YAPAY_ZEKA_SERVISLER.filter { a.servisAnahtar(it).isNotBlank() }
                if (servisler.isEmpty()) {
                    Text("Henüz API anahtarı girilmedi. Ayarlar → Yapay zeka bölümünden bir servis ekle.", color = SolukKoyu, fontSize = 14.sp)
                }
                servisler.forEach { servis ->
                    Text(Ayarlar.YAPAY_ZEKA_AD[servis] ?: servis, color = SolukKoyu, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    val modeller = a.servisModelleri(servis)
                    if (modeller.isEmpty()) Text("Model seçilmedi (Ayarlar → Yapay zeka).", color = SolukKoyu, fontSize = 13.sp)
                    modeller.forEach { m ->
                        val deger = "$servis|$m"
                        val secili = Motor.secim == deger
                        val s2 = RoundedCornerShape(16.dp)
                        Row(
                            Modifier.fillMaxWidth().clip(s2)
                                .background(if (secili) Vurgu.copy(alpha = 0.16f) else Color.White)
                                .border(1.dp, if (secili) Vurgu else KremKoyu, s2)
                                .clickable { Motor.modelSec(deger) }
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(m, Modifier.weight(1f), color = MetinKoyu, fontSize = 15.sp)
                            if (secili) Text("✓", color = Vurgu, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                }
            }
        }
    }
}
