package com.edith.asistan

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import kotlin.math.abs

private val PALET = listOf(
    Krem to MetinKoyu,
    Color(0xFF7A2A3E) to MetinKrem,
    Color(0xFFD9B8A8) to MetinKoyu,
    Color(0xFF8E3A4C) to MetinKrem,
    KremKoyu to MetinKoyu,
    Color(0xFFA64B5C) to KremAcik,
    Color(0xFF6B2336) to MetinKrem,
    Color(0xFFC98A93) to MetinKoyu
)

/** Yatay kayan listedeki kare ses kartı: kendi rengi, sese özgü çubuk deseni, ad ve tarif. */
@Composable
fun SesKarti(ad: String, tarif: String, secili: Boolean, caliyor: Boolean, tikla: () -> Unit) {
    val (zemin, yazi) = PALET[abs(ad.hashCode()) % PALET.size]
    val tohum = abs(ad.hashCode())
    val yukseklik = List(7) { 0.25f + ((tohum shr (it * 3)) and 7) / 7f * 0.75f }
    val gecis = rememberInfiniteTransition(label = "kart")
    val nabiz by gecis.animateFloat(0.55f, 1f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "nabiz")
    val sekil = RoundedCornerShape(24.dp)
    Column(
        Modifier.width(152.dp).height(180.dp).clip(sekil).background(zemin)
            .border(if (secili) 3.dp else 0.dp, if (secili) KremAcik else Color.Transparent, sekil)
            .clickable(onClick = tikla).padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Canvas(Modifier.fillMaxWidth().height(54.dp).alpha(if (caliyor) nabiz else 1f)) {
            val n = yukseklik.size
            val aralik = size.width / n
            yukseklik.forEachIndexed { i, y ->
                val h = size.height * y
                drawRoundRect(
                    yazi.copy(alpha = 0.9f), Offset(i * aralik + aralik * 0.2f, (size.height - h) / 2),
                    Size(aralik * 0.5f, h), CornerRadius(aralik * 0.25f)
                )
            }
        }
        Column {
            Text(ad, color = yazi, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tarif, color = yazi.copy(alpha = 0.78f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                if (caliyor) "çalıyor…" else if (secili) "seçili" else "dene ve seç",
                color = yazi.copy(alpha = 0.9f), fontSize = 11.sp, fontWeight = FontWeight.Medium
            )
        }
    }
}

// ───────────────────────── Ses ana sayfa ─────────────────────────

@Composable
fun SayfaSes(geri: () -> Unit, git: (String) -> Unit) {
    val a = Motor.ayarlar
    var siraylaDene by remember { mutableStateOf(a.siraylaDene) }
    var sadeceSesli by remember { mutableStateOf(a.sadeceSesliKomutta) }
    var sira by remember { mutableStateOf(a.motorSirasi) }
    val simdi by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }

    SayfaCerceve("Ses", geri) {
        HataKutusu()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SaglayiciKarti("Gemini sesleri", "Seçili: ${a.geminiSes}", Modifier.weight(1f)) { git("ses_gemini") }
            SaglayiciKarti(
                "ElevenLabs",
                if (a.elevenSes.isBlank()) "Ses seçilmedi" else "Seçili: ${a.elevenSesAdi.ifBlank { a.elevenSes.take(8) }}",
                Modifier.weight(1f)
            ) { git("ses_eleven") }
        }
        Bolum("Genel") {
            Anahtarli(
                "Sırayla dene (yedek motorlar)",
                "Bir motor hata verirse (ör. 429 limit) sıradaki açık motor denenir. Hata veren motor bir süre atlanır.",
                siraylaDene
            ) { siraylaDene = it; a.siraylaDene = it }
            Anahtarli(
                "Sadece sesli komutlarda sesli cevap ver",
                "Yazdığın mesajlara yalnızca yazılı cevap gelir; ses hakkın harcanmaz.",
                sadeceSesli
            ) { sadeceSesli = it; a.sadeceSesliKomutta = it }
        }
        Bolum("Ses motorları", "Yukarıdan aşağıya deneme sırası.") {
            sira.forEachIndexed { i, m ->
                var aktif by remember(m) { mutableStateOf(a.motorAktif(m)) }
                AltKutu {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}.  ${Ayarlar.MOTOR_AD[m]}", Modifier.weight(1f), color = MetinKoyu, fontWeight = FontWeight.SemiBold)
                        val kalan = (Ses.cooldown[m] ?: 0L) - simdi
                        when {
                            !Ses.hazir(a, m) -> Etiket("kurulmadı", "gri")
                            !aktif -> Etiket("kapalı", "gri")
                            kalan > 0 -> Etiket("bekliyor · ${kalan / 1000 + 1} sn", "hata")
                            Ses.durum[m] == "ok" -> Etiket("çalıştı ✓", "ok")
                            Ses.durum[m] == "hata" -> Etiket("hata", "hata")
                            else -> Etiket("hazır", "bilgi")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Dugme("Dene", DugmeTuru.BIRINCIL) { Motor.sesDene(m) }
                        Dugme("↑") {
                            if (i > 0) {
                                val y = sira.toMutableList().also { l -> l[i] = l[i - 1].also { l[i - 1] = l[i] } }
                                a.motorSirasi = y
                                sira = y
                            }
                        }
                        Dugme("↓") {
                            if (i < sira.lastIndex) {
                                val y = sira.toMutableList().also { l -> l[i] = l[i + 1].also { l[i + 1] = l[i] } }
                                a.motorSirasi = y
                                sira = y
                            }
                        }
                        Box(Modifier.weight(1f))
                        androidx.compose.material3.Switch(
                            checked = aktif, onCheckedChange = { aktif = it; a.motorAktif(m, it) },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = KremAcik, checkedTrackColor = Vurgu, checkedBorderColor = Vurgu,
                                uncheckedThumbColor = SolukKoyu, uncheckedTrackColor = KremKoyu, uncheckedBorderColor = SolukKoyu
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SaglayiciKarti(baslik: String, alt: String, mod: Modifier, tikla: () -> Unit) {
    Column(
        mod.kart(RoundedCornerShape(24.dp)).clickable(onClick = tikla).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Simge("ses", Visne, Modifier.height(26.dp).width(26.dp))
        Text(baslik, color = MetinKoyu, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Text(alt, color = SolukKoyu, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("Sesleri aç ›", color = Vurgu, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ───────────────────────── Gemini sesleri ─────────────────────────

@Composable
fun SayfaGemini(geri: () -> Unit) {
    val a = Motor.ayarlar
    var secili by remember { mutableStateOf(a.geminiSes) }
    SayfaCerceve("Gemini sesleri", geri) {
        HataKutusu()
        Bolum(
            "Google'ın hazır sesleri",
            "Bir karta dokununca ses seçilir ve kısa bir örnek çalınır. Örnek ilk seferde üretilip telefona kaydedilir; " +
                "sonraki dokunuşlarda kota harcanmaz."
        ) {
            if (a.apiKey.isBlank()) Etiket("Önce Ayarlar → Yapay zeka'dan Gemini anahtarı gir", "hata")
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(Ses.geminiSesleri) { s ->
                SesKarti(s.ad, s.tarif, secili == s.ad, Motor.onizleme == s.ad) {
                    secili = s.ad
                    Motor.geminiOnizle(s.ad)
                }
            }
        }
        Bolum("Seçili ses") {
            Satir(secili, "Yanıtların bu sesle okunur (Gemini TTS).") { Etiket("Gemini", "bilgi") }
            Dugme("Deneme cümlesini çal", DugmeTuru.BIRINCIL) { Motor.sesDene("gemini") }
        }
    }
}

// ───────────────────────── ElevenLabs ─────────────────────────

@Composable
fun SayfaEleven(geri: () -> Unit) {
    val a = Motor.ayarlar
    var sekme by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        if (a.elevenKey.isNotBlank() && Motor.elevenSesler.isEmpty() && !Motor.elevenYukleniyor) Motor.elevenCek()
    }
    SayfaCerceve("ElevenLabs", geri) {
        HataKutusu()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Hesap", "Sesler", "Modeller", "Karakter").forEachIndexed { i, ad ->
                Text(
                    ad,
                    Modifier.weight(1f).clip(RoundedCornerShape(50))
                        .background(if (sekme == i) Krem else VisneAcik)
                        .clickable { sekme = i }.padding(vertical = 10.dp),
                    color = if (sekme == i) MetinKoyu else MetinKrem, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        when (sekme) {
            0 -> ElevenHesap()
            1 -> ElevenSesler()
            2 -> ElevenModeller()
            else -> ElevenKarakter()
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ElevenHesap() {
    val a = Motor.ayarlar
    var anahtar by remember { mutableStateOf(a.elevenKey) }
    var goster by remember { mutableStateOf(false) }
    Bolum("API anahtarı", "elevenlabs.io → API Keys. Anahtarda ses, model ve kullanıcı okuma izinlerini aç.") {
        Alan(anahtar, { anahtar = it; a.elevenKey = it }, "ElevenLabs API anahtarı", gizli = !goster)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dugme(if (Motor.elevenYukleniyor) "Çekiliyor…" else "Bağlan ve çek", DugmeTuru.BIRINCIL) { Motor.elevenCek() }
            Dugme(if (goster) "Gizle" else "Göster") { goster = !goster }
            Dugme("Sil", DugmeTuru.TEHLIKE) { anahtar = ""; Motor.elevenTemizle() }
        }
        Motor.elevenMesaj?.let { Text(it, color = Hata, fontSize = 12.sp, lineHeight = 16.sp) }
    }
    Bolum("Kalan kredi") {
        val k = Motor.elevenKredi
        if (k == null) {
            Text("Henüz alınmadı. “Bağlan ve çek” ile yenile.", color = SolukKoyu, fontSize = 13.sp)
        } else {
            val oran = if (k.limit > 0) (k.kalan.toFloat() / k.limit).coerceIn(0f, 1f) else 0f
            Text("${k.kalan} / ${k.limit} karakter", color = MetinKoyu, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(KremKoyu)) {
                Box(Modifier.fillMaxWidth(oran).fillMaxHeight().background(Vurgu))
            }
            Text("Yenilenme: ${k.yenilenme}", color = SolukKoyu, fontSize = 12.sp)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ElevenSesler() {
    val a = Motor.ayarlar
    var secili by remember { mutableStateOf(a.elevenSes) }
    var elle by remember { mutableStateOf(a.elevenSes) }
    Bolum(
        "Hesabındaki sesler",
        "Karta dokununca ses seçilir ve ElevenLabs'in kendi örnek kaydı çalınır; kredi harcanmaz."
    ) {
        if (Motor.elevenSesler.isEmpty()) {
            Text(
                if (a.elevenKey.isBlank()) "Önce Hesap sekmesine anahtarını gir." else "Liste boş. Hesap sekmesinde “Bağlan ve çek”e bas.",
                color = SolukKoyu, fontSize = 13.sp
            )
        }
    }
    if (Motor.elevenSesler.isNotEmpty()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(Motor.elevenSesler) { s ->
                SesKarti(s.ad, s.aciklama.ifBlank { "ElevenLabs sesi" }, secili == s.id, Motor.onizleme == s.id) {
                    secili = s.id
                    elle = s.id
                    Motor.elevenOnizle(s)
                }
            }
        }
    }
    Bolum("Ses kimliği (Voice ID)", "Listede yoksa kimliği elle de girebilirsin.") {
        Alan(elle, { elle = it; secili = it; a.elevenSes = it }, "Voice ID")
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ElevenModeller() {
    val a = Motor.ayarlar
    var secili by remember { mutableStateOf(a.elevenModel) }
    Bolum(
        "Modeller",
        "Hızlı modeller (flash/turbo) gecikmeyi ve krediyi azaltır; çok dilli modeller Türkçede daha doğal okur."
    ) {
        if (Motor.elevenModeller.isEmpty()) {
            Text("Liste boş. Hesap sekmesinde “Bağlan ve çek”e bas.", color = SolukKoyu, fontSize = 13.sp)
        }
    }
    if (Motor.elevenModeller.isNotEmpty()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(Motor.elevenModeller) { m ->
                val sekil = RoundedCornerShape(22.dp)
                val s = secili == m.id
                Column(
                    Modifier.width(210.dp).height(170.dp).clip(sekil).background(if (s) Krem else VisneAcik)
                        .border(if (s) 3.dp else 0.dp, if (s) KremAcik else Color.Transparent, sekil)
                        .clickable { secili = m.id; a.elevenModel = m.id }.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(m.ad, color = if (s) MetinKoyu else MetinKrem, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 2)
                    Text(
                        m.aciklama.ifBlank { m.id }, color = if (s) SolukKoyu else SolukKrem, fontSize = 12.sp,
                        lineHeight = 16.sp, maxLines = 4, overflow = TextOverflow.Ellipsis
                    )
                    Box(Modifier.weight(1f))
                    Text(
                        (if (m.turkce) "Türkçe ✓  ·  " else "") + if (s) "seçili" else m.id,
                        color = if (s) Vurgu else SolukKrem, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
    Bolum("Model kimliği") {
        Alan(secili, { secili = it; a.elevenModel = it }, "ör. eleven_flash_v2_5")
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ElevenKarakter() {
    val a = Motor.ayarlar
    var kararlilik by remember { mutableStateOf(a.elevenKararlilik) }
    var benzerlik by remember { mutableStateOf(a.elevenBenzerlik) }
    Bolum("Ses karakteri", "Sesin nasıl okunacağını ince ayarlar. Değişiklik bir sonraki konuşmada geçerli olur.") {
        Kaydirici(
            "Kararlılık", "Düşük: daha duygulu ve değişken. Yüksek: daha düz ve tutarlı.",
            kararlilik
        ) { kararlilik = it; a.elevenKararlilik = it }
        Kaydirici(
            "Benzerlik", "Yüksek değer, seçili sesin özgün tınısına daha sıkı bağlı kalır.",
            benzerlik
        ) { benzerlik = it; a.elevenBenzerlik = it }
    }
    Bolum("Deneme") {
        val simdi by produceState(System.currentTimeMillis()) {
            while (true) {
                delay(1000)
                value = System.currentTimeMillis()
            }
        }
        val kalan = (Ses.cooldown["elevenlabs"] ?: 0L) - simdi
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Dugme("Deneme cümlesini çal", DugmeTuru.BIRINCIL) { Motor.sesDene("elevenlabs") }
            when {
                !Ses.hazir(a, "elevenlabs") -> Etiket("kurulmadı", "gri")
                kalan > 0 -> Etiket("bekliyor · ${kalan / 1000 + 1} sn", "hata")
                Ses.durum["elevenlabs"] == "ok" -> Etiket("çalıştı ✓", "ok")
                Ses.durum["elevenlabs"] == "hata" -> Etiket("hata", "hata")
                else -> Etiket("hazır", "bilgi")
            }
        }
    }
}
