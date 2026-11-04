package com.edith.asistan

import android.Manifest
import android.os.Build
import androidx.compose.animation.AnimatedContent
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

/** İlk yüklemede bir kez görünür: ses modellerini tek çubukla indirir, izinleri açıklar. */
@Composable
fun KurulumEkrani(onIzin: (Array<String>) -> Unit) {
    var ipucu by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(6000)
            ipucu = (ipucu + 1) % IPUCLARI.size
        }
    }
    val oran by animateFloatAsState(Motor.kurulumOran.coerceIn(0f, 1f), tween(300), label = "oran")
    val hazir = Motor.modellerHazir
    val bildirimGerek = Build.VERSION.SDK_INT >= 33 && !Motor.izinBildirim

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Hoş geldiniz", color = MetinKrem, fontFamily = FontFamily.Serif, fontSize = 34.sp)
        Text(
            "E.D.I.T.H. sizi duyabilmek için iki küçük ses modeli (Türkçe ve İngilizce, toplam yaklaşık 75 MB) indirir. " +
                "Bu yalnızca bir kez olur; sonrasında ses tanıma telefonda, internetsiz çalışır.",
            color = SolukKrem, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp
        )

        // Tek birleşik ilerleme çubuğu
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.16f))
            ) {
                Box(Modifier.fillMaxWidth(if (hazir) 1f else oran).fillMaxHeight().clip(RoundedCornerShape(50)).background(Krem))
            }
            val durum = when {
                hazir -> "Hazır"
                Motor.hata != null && Motor.indirme == null -> "Bir sorun oluştu"
                else -> Motor.indirme ?: "Başlıyor…"
            }
            Text(
                "$durum  ·  %${if (hazir) 100 else (oran * 100).toInt()}",
                color = MetinKrem, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
        }
        if (!hazir && Motor.hata != null && Motor.indirme == null) {
            Text(Motor.hata.orEmpty(), color = HataAcik, fontSize = 13.sp, textAlign = TextAlign.Center)
            KurulumDugme("Tekrar dene", true) { Motor.hata = null; Motor.acilistaYukle() }
        }

        // Bilgi ve ipuçları
        Box(
            Modifier.fillMaxWidth().cam(RoundedCornerShape(22.dp)).padding(18.dp).height(64.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            AnimatedContent(
                targetState = ipucu,
                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(300)) },
                label = "ipucu"
            ) { i -> Text(IPUCLARI[i], color = MetinKrem, fontSize = 15.sp, lineHeight = 21.sp) }
        }

        // İzinler (isteğe bağlı; kullanıcı dokunursa istenir)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!Motor.izinMikrofon) {
                Text("Sesinizi duyabilmem için mikrofon izni gerekir.", color = SolukKrem, fontSize = 13.sp)
                KurulumDugme("Mikrofon iznini ver", false) { onIzin(arrayOf(Manifest.permission.RECORD_AUDIO)) }
            }
            if (bildirimGerek) {
                Text("Cevap geldiğinde uygulama kapalıysa haber verebilmem için bildirim izni gerekir.", color = SolukKrem, fontSize = 13.sp)
                KurulumDugme("Bildirimlere izin ver", false) {
                    onIzin(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        KurulumDugme(if (hazir) "Başla" else "Yüklenirken bekle", true, etkin = hazir) { Motor.kurulumBitti() }
        if (!hazir) {
            Text(
                "Şimdilik atla", Modifier.clickable { Motor.kurulumBitti() }.padding(10.dp),
                color = SolukKrem, fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun KurulumDugme(yazi: String, birincil: Boolean, etkin: Boolean = true, tikla: () -> Unit) {
    val sekil = RoundedCornerShape(50)
    Box(
        Modifier.fillMaxWidth().height(50.dp).alpha(if (etkin) 1f else 0.5f).clip(sekil)
            .background(if (birincil) Krem else Color.White.copy(alpha = 0.16f))
            .clickable(enabled = etkin, onClick = tikla),
        contentAlignment = Alignment.Center
    ) {
        Text(yazi, color = if (birincil) MetinKoyu else MetinKrem, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
