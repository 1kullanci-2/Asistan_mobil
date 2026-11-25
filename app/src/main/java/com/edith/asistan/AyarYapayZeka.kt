package com.edith.asistan

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File

/** Servislerin model listesi dosyada önbelleklenir; sonraki yenilemelerde yalnızca fark uygulanır. */
object ModelKatalog {
    private fun dosya(c: android.content.Context, servis: String) = File(c.filesDir, "model_katalog_$servis.json")

    fun oku(c: android.content.Context, servis: String): List<String> = runCatching {
        val d = JSONArray(dosya(c, servis).readText())
        (0 until d.length()).map { d.getString(it) }
    }.getOrDefault(emptyList())

    private fun yaz(c: android.content.Context, servis: String, liste: List<String>) {
        dosya(c, servis).writeText(JSONArray(liste).toString())
    }

    /** Getirir, önbellekle karşılaştırır: (eklenen, kalkan). Kalkan modeller seçili listeden de çıkarılır. */
    suspend fun yenile(c: android.content.Context, a: Ayarlar, servis: String): Pair<List<String>, List<String>> {
        val yeni = if (servis == "gemini") Gemini.modeller(a.apiKey) else UcServis.modeller(servis, a)
        val eski = oku(c, servis)
        val eklenen = if (eski.isEmpty()) yeni else yeni - eski.toSet()
        val kalkan = eski - yeni.toSet()
        yaz(c, servis, yeni)
        if (kalkan.isNotEmpty()) {
            a.servisModelleri(servis, a.servisModelleri(servis) - kalkan.toSet())
            if (a.varsayilanServis == servis && a.varsayilanModel in kalkan) { a.varsayilanServis = ""; a.varsayilanModel = "" }
        }
        return eklenen to kalkan
    }
}

@Composable
fun SayfaYapayZeka(geri: () -> Unit, git: (String) -> Unit) {
    val a = Motor.ayarlar
    SayfaCerceve("Yapay zeka", geri) {
        val vs = a.varsayilanSecim()
        Bolum("Yeni sohbetlerin modeli", "Yeni bir sohbet bu modelle başlar. Değiştirmek için sağlayıcı sayfasında bir modelin yanındaki “Varsayılan yap”a dokun.") {
            val (s, m) = vs.split("|", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            Text(
                if (m.isBlank()) "Henüz model seçilmedi" else "${Ayarlar.YAPAY_ZEKA_AD[s] ?: s} · $m",
                color = MetinKoyu, fontSize = 15.sp, fontWeight = FontWeight.Medium
            )
        }
        Ayarlar.YAPAY_ZEKA_SERVISLER.chunked(2).forEach { cift ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                cift.forEach { s ->
                    val anahtarVar = a.servisAnahtar(s).isNotBlank()
                    val alt = if (!anahtarVar) "Anahtar girilmedi" else "${a.servisModelleri(s).size} model seçili"
                    KategoriKarti("ai", Ayarlar.YAPAY_ZEKA_AD[s] ?: s, alt, Modifier.weight(1f).fillMaxHeight()) { git("ai_$s") }
                }
            }
        }
        Text(
            "Her sağlayıcıda istediğin kadar model seçebilirsin. Seçtiklerin sohbet ekranındaki model kutusunda listelenir.",
            Modifier.padding(horizontal = 24.dp), color = SolukKrem, fontSize = 12.sp, lineHeight = 17.sp
        )
    }
}

@Composable
fun SayfaServis(servis: String, geri: () -> Unit) {
    val a = Motor.ayarlar
    val ctx = Motor.ctx
    val ad = Ayarlar.YAPAY_ZEKA_AD[servis] ?: servis
    var anahtar by remember { mutableStateOf(a.servisAnahtar(servis)) }
    var goster by remember { mutableStateOf(false) }
    var secili by remember { mutableStateOf(a.servisModelleri(servis)) }
    var katalog by remember { mutableStateOf(ModelKatalog.oku(ctx, servis)) }
    var varsayilan by remember { mutableStateOf(if (a.varsayilanServis == servis) a.varsayilanModel else "") }
    var yukleniyor by remember { mutableStateOf(false) }
    var durum by remember { mutableStateOf<String?>(null) }
    val sonuc = remember { mutableStateMapOf<String, String>() }
    val kapsam = rememberCoroutineScope()

    fun kaydet(yeni: List<String>) { secili = yeni; a.servisModelleri(servis, yeni) }
    fun sec(m: String) {
        if (m in secili) {
            kaydet(secili - m)
            if (varsayilan == m) { varsayilan = ""; a.varsayilanServis = ""; a.varsayilanModel = "" }
        } else kaydet(secili + m)
    }
    fun yenile() {
        if (anahtar.isBlank()) { durum = "Önce API anahtarını gir."; return }
        if (yukleniyor) return
        yukleniyor = true
        durum = null
        kapsam.launch {
            try {
                val (eklenen, kalkan) = ModelKatalog.yenile(ctx, a, servis)
                katalog = ModelKatalog.oku(ctx, servis)
                secili = a.servisModelleri(servis)
                if (varsayilan !in secili) varsayilan = ""
                durum = "${katalog.size} model · ${eklenen.size} yeni, ${kalkan.size} kalkan"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                UygulamaLog.kaydet(ctx, "$ad model listesi", e)
                durum = UygulamaLog.kullaniciMesaji(e)
            }
            yukleniyor = false
        }
    }

    SayfaCerceve(ad, geri) {
        Bolum(
            "API anahtarı",
            if (servis == "gemini") "Google AI Studio'dan alabilirsin. Anahtar yalnızca bu telefonda saklanır."
            else "OpenAI uyumlu API. Anahtar yalnızca bu telefonda saklanır."
        ) {
            Alan(anahtar, { anahtar = it; a.servisAnahtar(servis, it) }, "$ad API anahtarı", gizli = !goster)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Dugme(if (goster) "Gizle" else "Göster") { goster = !goster }
                Dugme("Sil", DugmeTuru.TEHLIKE) { anahtar = ""; a.servisAnahtar(servis, "") }
                if (anahtar.isBlank()) Etiket("girilmedi", "hata") else Etiket("kayıtlı", "ok")
            }
            Dugme(if (yukleniyor) "Modeller getiriliyor…" else "Modelleri yenile", DugmeTuru.BIRINCIL, Modifier.fillMaxWidth()) { yenile() }
            durum?.let { Text(it, color = if (it.startsWith("Önce") || it.contains("HTTP") || it.contains("anahtar")) Hata else Yesil, fontSize = 12.sp) }
        }
        Bolum("Seçili modeller", "Sohbet ekranında bu modeller arasında geçiş yaparsın. Sınır yok.") {
            if (secili.isEmpty()) Text("Henüz model seçilmedi.", color = SolukKoyu, fontSize = 12.sp)
            secili.forEach { m ->
                AltKutu {
                    Text(m, color = MetinKoyu, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (varsayilan == m) Etiket("varsayılan", "bilgi")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (varsayilan != m) Dugme("Varsayılan yap", DugmeTuru.BIRINCIL, Modifier.weight(1f)) {
                            varsayilan = m; a.varsayilanServis = servis; a.varsayilanModel = m
                        }
                        Dugme("Kaldır", DugmeTuru.IKINCIL, Modifier.weight(1f)) { sec(m) }
                    }
                }
            }
            Dugme("Sıfırla", DugmeTuru.IKINCIL, Modifier.fillMaxWidth()) {
                a.servisSifirla(servis)
                secili = a.servisModelleri(servis)
                varsayilan = ""
            }
        }
        Bolum("Tüm modeller", if (katalog.isEmpty()) "Liste boş. Anahtarı girip “Modelleri yenile”ye dokun." else "${katalog.size} model · önbellekten; açılışta otomatik güncellenir.") {
            katalog.forEach { m ->
                AltKutu {
                    Text(m, color = MetinKoyu, fontSize = 14.sp)
                    val d = sonuc[m]
                    if (d == "ok") Etiket("çalışıyor ✓", "ok")
                    else if (d != null) Text(d, color = if (d == "deneniyor…") SolukKoyu else Hata, fontSize = 12.sp, lineHeight = 15.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Dugme("Dene", DugmeTuru.IKINCIL, Modifier.weight(1f)) {
                            sonuc[m] = "deneniyor…"
                            kapsam.launch {
                                sonuc[m] = try {
                                    if (servis == "gemini") Gemini.modelDene(a.apiKey, m) else UcServis.modelDene(servis, a, m)
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    UygulamaLog.kaydet(ctx, "$ad model testi", e)
                                    UygulamaLog.kullaniciMesaji(e)
                                }
                            }
                        }
                        Dugme(if (m in secili) "Seçildi ✓" else "Seç", if (m in secili) DugmeTuru.BIRINCIL else DugmeTuru.IKINCIL, Modifier.weight(1f)) { sec(m) }
                    }
                }
            }
        }
    }
}
