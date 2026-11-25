package com.edith.asistan

import android.Manifest
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val IPUCLARI = listOf(
    "“Hey edit” diyerek asistanı uyandırabilirsin; uygulama açıkken dinleme kendiliğinden başlar.",
    "Mikrofon düğmesine dokunursan beklemeden seni dinler; 1–2 saniye susunca mesaj gider.",
    "Soldaki menüden eski sohbetlerine dönebilirsin; cevap hazırlanırken başka sohbete geçebilirsin.",
    "İnternet yokken yazdığın mesaj saklanır, bağlantı gelince kendiliğinden gönderilir.",
    "Cevap hazır olduğunda uygulamada değilsen küçük bir bildirimle haber verilir.",
    "Ayarlar → Uyandırma bölümünden “hey edit” kelimesini sesine alıştırabilirsin.",
    "Varsayılan dijital asistan yaparsan güç tuşuyla da açabilirsin."
)

/** İlk yüklemede bir kez görünür: adım adım karşılama; her izin ve ayarın neden gerektiği açıklanır. */
@Composable
fun KurulumEkrani(onIzin: (Array<String>) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val bildirimAdimi = Build.VERSION.SDK_INT >= 33
    val adimlar = remember { listOfNotNull("hos", "mik", if (bildirimAdimi) "bildirim" else null, "anahtar", "model") }
    var adim by remember { mutableIntStateOf(0) }
    var ileri by remember { mutableStateOf(true) }
    var anahtar by remember { mutableStateOf(Motor.ayarlar.apiKey) }
    fun git(i: Int) { ileri = i > adim; adim = i.coerceIn(0, adimlar.lastIndex) }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp)) {
        // Üstte adım noktaları
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            adimlar.indices.forEach { i ->
                val w by animateDpAsState(if (i == adim) 26.dp else 8.dp, tween(250), label = "nokta")
                Box(
                    Modifier.padding(horizontal = 4.dp).height(8.dp).width(w).clip(RoundedCornerShape(50))
                        .background(if (i <= adim) Krem else Yuzey)
                )
            }
        }
        // Ses modelleri ilk açılıştan beri arka planda iner; her adımda ince bir çubukla görünür
        if (adimlar[adim] != "model") {
            val o by animateFloatAsState(if (Motor.modellerHazir) 1f else Motor.kurulumOran.coerceIn(0f, 1f), tween(300), label = "arka")
            Column(Modifier.fillMaxWidth().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Yuzey)) {
                    Box(Modifier.fillMaxWidth(o).fillMaxHeight().background(Krem))
                }
                Text(if (Motor.modellerHazir) "Ses modelleri hazır" else "Ses modelleri arka planda iniyor · %${(o * 100).toInt()}",
                    color = SolukKrem, fontSize = 11.sp)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = adim,
                transitionSpec = {
                    val yon = if (ileri) 1 else -1
                    (fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 6 * yon }) togetherWith
                        (fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { -it / 6 * yon })
                },
                label = "adim"
            ) { i ->
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (adimlar[i]) {
                        "hos" -> {
                            DusunenIkon(Krem, 64.dp)
                            Text("Hoş geldiniz", color = MetinKrem, fontFamily = FontFamily.Serif, fontSize = 34.sp)
                            Aciklama("Birkaç kısa adımda E.D.I.T.H.'i hazırlayalım. Her adımda neye ihtiyaç duyduğumuzu ve nedenini söyleyeceğiz; istediğin adımı atlayabilirsin.")
                        }
                        "mik" -> {
                            Simge("mik", Krem, Modifier.size(56.dp))
                            Text("Mikrofon", color = MetinKrem, fontFamily = FontFamily.Serif, fontSize = 30.sp)
                            Aciklama("Sesli komutları ve “hey edit” çağrısını duyabilmek için mikrofon izni gereklidir. Ses tanıma telefonda çalışır; konuşman kaydedilip bir yere gönderilmez.")
                            if (Motor.izinMikrofon) Durum("Mikrofon izni verildi")
                            else KurulumDugme("Mikrofon iznini ver", true) { onIzin(arrayOf(Manifest.permission.RECORD_AUDIO)) }
                        }
                        "bildirim" -> {
                            Simge("uyan", Krem, Modifier.size(56.dp))
                            Text("Bildirimler", color = MetinKrem, fontFamily = FontFamily.Serif, fontSize = 30.sp)
                            Aciklama("Uygulamadan çıktığında cevabın hazır olduğunu haber verebilmem için bildirim izni gereklidir. Yalnızca cevap geldiğinde bildirim gösterilir; başka bildirim gönderilmez.")
                            if (Motor.izinBildirim) Durum("Bildirim izni verildi")
                            else KurulumDugme("Bildirimlere izin ver", true) { onIzin(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }
                        }
                        "anahtar" -> {
                            Simge("ai", Krem, Modifier.size(56.dp))
                            Text("Yapay zeka anahtarı", color = MetinKrem, fontFamily = FontFamily.Serif, fontSize = 30.sp)
                            Aciklama("Cevap üretebilmem için bir Gemini API anahtarı gereklidir. Anahtar yalnızca bu telefonda saklanır. Groq, Mistral ve Cerebras gibi diğer sağlayıcıları sonradan Ayarlar → Yapay zeka bölümünden ekleyebilirsin.")
                            Alan(anahtar, { anahtar = it; Motor.ayarlar.apiKey = it }, "Gemini API anahtarı", gizli = true)
                            Text(
                                "Anahtarı nereden alırım?",
                                Modifier.clickable {
                                    runCatching {
                                        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://aistudio.google.com/apikey")))
                                    }
                                }.padding(8.dp),
                                color = Krem, fontSize = 14.sp, fontWeight = FontWeight.Medium
                            )
                            if (anahtar.isNotBlank()) Durum("Anahtar kaydedildi")
                        }
                        else -> ModelAdimi()
                    }
                }
            }
        }
        // Alt düğmeler
        val son = adim == adimlar.lastIndex
        val hazir = Motor.modellerHazir
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (son) KurulumDugme(if (hazir) "Başla" else "Yüklenirken bekle", true, etkin = hazir) { Motor.kurulumBitti() }
            else KurulumDugme(if (adim == 0) "Başlayalım" else "Devam", true) { git(adim + 1) }
            Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (adim > 0) Text("Geri", Modifier.clickable { git(adim - 1) }.padding(10.dp), color = SolukKrem, fontSize = 14.sp) else Spacer(Modifier.width(1.dp))
                if (son && !hazir) Text("Şimdilik atla", Modifier.clickable { Motor.kurulumBitti() }.padding(10.dp), color = SolukKrem, fontSize = 14.sp)
                else if (!son && adim > 0) Text("Atla", Modifier.clickable { git(adim + 1) }.padding(10.dp), color = SolukKrem, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun Aciklama(t: String) =
    Text(t, color = SolukKrem, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)

@Composable
private fun Durum(t: String) =
    Text("✓  $t", color = YesilAcik, fontSize = 15.sp, fontWeight = FontWeight.Medium)

/** Son adım: ses modelleri tek ilerleme çubuğuyla iner; bu sırada ipuçları döner. */
@Composable
private fun ModelAdimi() {
    var ipucu by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(6000)
            ipucu = (ipucu + 1) % IPUCLARI.size
        }
    }
    val oran by animateFloatAsState(Motor.kurulumOran.coerceIn(0f, 1f), tween(300), label = "oran")
    val hazir = Motor.modellerHazir
    Simge("ses", Krem, Modifier.size(56.dp))
    Text("Ses modelleri", color = MetinKrem, fontFamily = FontFamily.Serif, fontSize = 30.sp)
    Aciklama("Sizi internetsiz, telefonda anlayabilmem için iki küçük ses modeli (Türkçe ve İngilizce, toplam yaklaşık 75 MB) indirilmesi gereklidir. Bu yalnızca bir kez olur.")
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50)).background(Yuzey)) {
            Box(Modifier.fillMaxWidth(if (hazir) 1f else oran).fillMaxHeight().clip(RoundedCornerShape(50)).background(Krem))
        }
        val durum = when {
            hazir -> "Hazır"
            Motor.hata != null && Motor.indirme == null -> "Bir sorun oluştu"
            else -> Motor.indirme ?: "Başlıyor…"
        }
        Text("$durum  ·  %${if (hazir) 100 else (oran * 100).toInt()}", color = MetinKrem, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
    if (!hazir && Motor.hata != null && Motor.indirme == null) {
        Text(Motor.hata.orEmpty(), color = HataAcik, fontSize = 13.sp, textAlign = TextAlign.Center)
        KurulumDugme("Tekrar dene", true) { Motor.hata = null; Motor.acilistaYukle() }
    }
    Box(Modifier.fillMaxWidth().cam(RoundedCornerShape(22.dp)).padding(18.dp).height(64.dp), contentAlignment = Alignment.CenterStart) {
        AnimatedContent(
            targetState = ipucu,
            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(300)) },
            label = "ipucu"
        ) { i -> Text(IPUCLARI[i], color = MetinKrem, fontSize = 15.sp, lineHeight = 21.sp) }
    }
}

@Composable
private fun KurulumDugme(yazi: String, birincil: Boolean, etkin: Boolean = true, tikla: () -> Unit) {
    val sekil = RoundedCornerShape(50)
    Box(
        Modifier.fillMaxWidth().height(50.dp).alpha(if (etkin) 1f else 0.5f).clip(sekil)
            .background(if (birincil) Krem else Yuzey)
            .clickable(enabled = etkin, onClick = tikla),
        contentAlignment = Alignment.Center
    ) {
        Text(yazi, color = if (birincil) MetinKoyu else MetinKrem, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
