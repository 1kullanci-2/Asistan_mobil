package com.edith.asistan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ───────────────────────── Yapay zeka ─────────────────────────

@Composable
fun SayfaYapayZeka(geri: () -> Unit) {
    val a = Motor.ayarlar
    var anahtar by remember { mutableStateOf(a.apiKey) }
    var goster by remember { mutableStateOf(false) }
    var hizli by remember { mutableStateOf(a.modelHizli) }
    var kaliteli by remember { mutableStateOf(a.modelKaliteli) }
    var yonlendirme by remember { mutableStateOf(a.modelYonlendirme) }
    var liste by remember { mutableStateOf<List<String>>(emptyList()) }
    var yukleniyor by remember { mutableStateOf(false) }
    var listeHata by remember { mutableStateOf<String?>(null) }
    val sonuc = remember { mutableStateMapOf<String, String>() }
    val kapsam = rememberCoroutineScope()
    SayfaCerceve("Yapay zeka", geri) {
        Bolum("Gemini API anahtarı", "aistudio.google.com üzerinden ücretsiz alınır. Yalnızca bu telefonda saklanır.") {
            Alan(anahtar, { anahtar = it; a.apiKey = it }, "API anahtarı", gizli = !goster)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Dugme(if (goster) "Gizle" else "Göster") { goster = !goster }
                Dugme("Sil", DugmeTuru.TEHLIKE) { anahtar = ""; a.apiKey = "" }
                if (anahtar.isBlank()) Etiket("girilmedi", "hata") else Etiket("kayıtlı", "ok")
            }
        }
        Bolum(
            "Modeller",
            "Her istek önce hızlı modele gider; o gerekli görürse kaliteli modele devreder. Bildiğim kadarıyla Gemini'nin ücretsiz " +
                "planında Flash modelleri günde yaklaşık 20 istekle sınırlı, Flash-Lite ise ~500. Kaliteli modeli de Flash-Lite yaparsan " +
                "yönlendirme kapanır ve her şey tek modelle yanıtlanır."
        ) {
            Anahtarli("Otomatik yönlendirme", "Kapalıysa her istek kaliteli modelle yanıtlanır.", yonlendirme) {
                yonlendirme = it; a.modelYonlendirme = it
            }
            Text("Hızlı model", color = MetinKoyu, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Alan(hizli, { hizli = it; a.modelHizli = it }, "gemini-flash-lite-latest")
            Text("Kaliteli model", color = MetinKoyu, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Alan(kaliteli, { kaliteli = it; a.modelKaliteli = it }, "gemini-flash-latest")
        }
        Bolum(
            "Kullanılabilir modeller",
            "Anahtarının görebildiği modelleri getirir. “Dene” tek bir kısa istek harcar; kotası olmayan modeli hemen görürsün. " +
                "Yönlendirme için hızlı ve kaliteli modeli birbirinden farklı seç."
        ) {
            Dugme(if (yukleniyor) "Getiriliyor…" else "Modelleri getir", DugmeTuru.BIRINCIL, Modifier.fillMaxWidth()) {
                if (!yukleniyor && a.apiKey.isNotBlank()) {
                    yukleniyor = true
                    listeHata = null
                    kapsam.launch {
                        try {
                            liste = Gemini.modeller(a.apiKey)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            listeHata = e.message
                        }
                        yukleniyor = false
                    }
                } else if (a.apiKey.isBlank()) listeHata = "Önce API anahtarını gir."
            }
            listeHata?.let { Text(it, color = Hata, fontSize = 12.sp) }
            liste.forEach { m ->
                AltKutu {
                    Text(m, color = MetinKoyu, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (hizli == m) Etiket("hızlı", "bilgi")
                        if (kaliteli == m) Etiket("kaliteli", "bilgi")
                        val d = sonuc[m]
                        if (d == "ok") Etiket("çalışıyor ✓", "ok")
                        else if (d != null) Text(d, color = Hata, fontSize = 12.sp, lineHeight = 15.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Dugme("Dene", DugmeTuru.IKINCIL, Modifier.weight(1f)) {
                            sonuc[m] = "deneniyor…"
                            kapsam.launch { sonuc[m] = Gemini.modelDene(a.apiKey, m) }
                        }
                        Dugme("Hızlı", DugmeTuru.IKINCIL, Modifier.weight(1f)) { hizli = m; a.modelHizli = m }
                        Dugme("Kaliteli", DugmeTuru.IKINCIL, Modifier.weight(1f)) { kaliteli = m; a.modelKaliteli = m }
                    }
                }
            }
        }
    }
}

// ───────────────────────── Uyandırma ─────────────────────────

@Composable
fun SayfaUyandirma(geri: () -> Unit) {
    val a = Motor.ayarlar
    var cumleBasi by remember { mutableStateOf(a.cumleBasi) }
    var sesleKes by remember { mutableStateOf(a.sesleKes) }
    var hoparlor by remember { mutableStateOf(a.hoparlorModu) }
    SayfaCerceve("Uyandırma", geri) {
        HataKutusu()
        Bolum(
            "Uyandırma kelimeleri",
            "Kelime İngilizce dinlenir. Telaffuzun sistemce farklı duyuluyorsa “Öğren” ile telefona tanıt."
        ) {
            Uyandirma.WAKE.forEach { w ->
                val adet = Motor.ogrenSurum.let { a.ogrenilen(w).size }
                AltKutu {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("“$w”", Modifier.weight(1f), color = MetinKoyu, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Etiket(if (adet == 0) "öğrenilen yok" else "$adet varyasyon", if (adet == 0) "gri" else "ok")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Dugme("Öğren", DugmeTuru.BIRINCIL, Modifier.weight(1f)) { Motor.ogrenBaslat(w) }
                        Dugme("Sıfırla", DugmeTuru.TEHLIKE, Modifier.weight(1f)) { Motor.ogrenSifirla(w) }
                    }
                }
            }
            if (Motor.ogrenKelime != null || Motor.ogrenMesaj != null) {
                AltKutu {
                    Text(Motor.ogrenMesaj ?: "", color = MetinKoyu, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    if (Motor.ogrenCanli.isNotBlank()) Text(Motor.ogrenCanli, color = SolukKoyu, fontSize = 12.sp)
                    if (Motor.ogrenKelime != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Dugme("Bitir", DugmeTuru.BIRINCIL, Modifier.weight(1f)) { Motor.ogrenBitir(false) }
                            Dugme("İptal", DugmeTuru.IKINCIL, Modifier.weight(1f)) { Motor.ogrenBitir(true) }
                        }
                    }
                }
            }
        }
        Bolum("Algılama") {
            Anahtarli("Sadece cümle başında", "Kelime cümlenin ortasında geçerse uyanmaz.", cumleBasi) {
                cumleBasi = it; a.cumleBasi = it
            }
            Anahtarli("Sesle kes", "Edith konuşurken uyandırma kelimesi söylenirse sus ve dinle.", sesleKes) {
                sesleKes = it; a.sesleKes = it
            }
            Anahtarli(
                "Hoparlör modu", "Edith'in kendi sesindeki uyandırma kelimesinin kendini tetiklemesini engeller.", hoparlor
            ) { hoparlor = it; a.hoparlorModu = it }
        }
    }
}

// ───────────────────────── Mikrofon ─────────────────────────

@Composable
fun SayfaMikrofon(geri: () -> Unit) {
    val a = Motor.ayarlar
    var soru by remember { mutableStateOf(a.soruSorunca) }
    var her by remember { mutableStateOf(a.herCevapta) }
    var titresim by remember { mutableStateOf(a.titresim) }
    var takip by remember { mutableStateOf(a.takipZamanAsimi) }
    var sessizlik by remember { mutableStateOf(a.sessizlikSuresi) }
    SayfaCerceve("Mikrofon", geri) {
        HataKutusu()
        Bolum("Mikrofon testi", "Konuş ve iki dilin ne duyduğunu gör. Test bitince mikrofon kendiliğinden kapanır.") {
            Dugme(if (Motor.testAktif) "Testi durdur" else "Testi başlat", DugmeTuru.BIRINCIL) { Motor.testDegistir() }
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(KremKoyu)) {
                Box(
                    Modifier.fillMaxWidth(Motor.seviye.coerceIn(0f, 1f)).fillMaxHeight().background(Vurgu)
                )
            }
            AltKutu {
                Text("İngilizce", color = SolukKoyu, fontSize = 12.sp)
                Text(Motor.testEnMetni, color = MetinKoyu, fontSize = 14.sp)
                Text("Türkçe", color = SolukKoyu, fontSize = 12.sp)
                Text(Motor.testTrMetni, color = MetinKoyu, fontSize = 14.sp)
            }
        }
        Bolum("Konuşma akışı") {
            Anahtarli("Soru sorunca mikrofonu aç", "Edith soru sorarsa cevabını beklemek için dinlemeye geçer.", soru) {
                soru = it; a.soruSorunca = it
            }
            Anahtarli("Her cevaptan sonra mikrofonu aç", "Uyandırma kelimesi söylemeden sohbet etmeni sağlar.", her) {
                her = it; a.herCevapta = it
            }
            Anahtarli("Titreşim", "Dinlemeye başlarken kısa titreşim.", titresim) { titresim = it; a.titresim = it }
        }
        Bolum("Süreler") {
            Satir("Takip zaman aşımı", "Cevaptan sonra konuşmanı bekleme süresi.") {
                Adim(takip, 3f..20f, 1f, " sn") { takip = it; a.takipZamanAsimi = it }
            }
            Satir("Sessizlik süresi", "Bu kadar sessizlikten sonra cümle bitmiş sayılır.") {
                Adim(sessizlik, 0.5f..3f, 0.1f, " sn") { sessizlik = it; a.sessizlikSuresi = it }
            }
        }
        Bolum("Ses tanıma modelleri", "Türkçe ve İngilizce modeller telefonda çalışır; yalnızca ilk kurulumda internet gerekir.") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (Motor.modellerHazir) Etiket("hazır ✓", "ok") else Etiket("indirilmedi", "hata")
                Motor.indirme?.let { Text(it, color = SolukKoyu, fontSize = 12.sp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!Motor.modellerHazir) Dugme("İndir", DugmeTuru.BIRINCIL) { Motor.modelleriIndir() }
                Dugme("Yeniden indir") { Motor.modelleriYenidenIndir() }
            }
        }
    }
}

// ───────────────────────── Bağlantılar (MCP) ─────────────────────────

@Composable
fun SayfaBaglanti(geri: () -> Unit) {
    val a = Motor.ayarlar
    var liste by remember { mutableStateOf(a.baglantilar) }
    var duzenle by remember { mutableStateOf<Baglanti?>(null) }
    var yeni by remember { mutableStateOf(false) }
    var silOnay by remember { mutableStateOf(a.mcpSilmeOnayi) }
    var acilan by remember { mutableStateOf<String?>(null) }
    val kapsam = rememberCoroutineScope()

    fun kaydet(l: List<Baglanti>) { liste = l; a.baglantilar = l }

    SayfaCerceve("Bağlantılar", geri) {
        Bolum("MCP sunucuları", "Edith'in kullanabileceği dış araçlar. Adres genellikle https://…/mcp biçimindedir.") {
            if (liste.isEmpty()) Text("Henüz bağlantı yok.", color = SolukKoyu, fontSize = 13.sp)
            Dugme("Bağlantı ekle", DugmeTuru.BIRINCIL) { yeni = true }
        }
        liste.forEach { b ->
            Bolum(b.ad, b.url) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val d = Motor.mcpDurum[b.id]
                    Etiket(d ?: if (b.acik) "bağlanmadı" else "kapalı", if (d?.startsWith("Bağlı") == true) "ok" else if (d?.startsWith("Hata") == true) "hata" else "gri")
                }
                Anahtarli("Açık", null, b.acik) { v -> kaydet(liste.map { if (it.id == b.id) it.copy(acik = v) else it }) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dugme("Dene", DugmeTuru.IKINCIL, Modifier.weight(1f)) { kapsam.launch { Motor.mcpBaglan(b) } }
                    Dugme("Araçlar", DugmeTuru.IKINCIL, Modifier.weight(1f)) { acilan = if (acilan == b.id) null else b.id }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dugme("Düzenle", DugmeTuru.IKINCIL, Modifier.weight(1f)) { duzenle = b }
                    Dugme("Sil", DugmeTuru.TEHLIKE, Modifier.weight(1f)) { Motor.mcpSil(b); kaydet(liste.filter { it.id != b.id }) }
                }
                if (acilan == b.id) {
                    val araclar = Motor.mcpAraclarListesi(b)
                    if (araclar.isEmpty()) Text("Araç listesi için önce “Dene”ye bas.", color = SolukKoyu, fontSize = 12.sp)
                    araclar.forEach { ar ->
                        Anahtarli(ar.ad, ar.aciklama.take(90).ifBlank { null }, ar.ad !in b.kapaliAraclar) { v ->
                            kaydet(liste.map {
                                if (it.id == b.id) it.copy(
                                    kapaliAraclar = if (v) it.kapaliAraclar - ar.ad else it.kapaliAraclar + ar.ad
                                ) else it
                            })
                        }
                    }
                }
            }
        }
        Bolum("Güvenlik") {
            Anahtarli("Silme işlemlerinde onay iste", "Adında “sil” geçen araçlar çalışmadan önce sorulur.", silOnay) {
                silOnay = it; a.mcpSilmeOnayi = it
            }
        }
    }

    if (yeni || duzenle != null) {
        BaglantiDiyalogu(duzenle, kapat = { yeni = false; duzenle = null }) { b ->
            val varMi = liste.any { it.id == b.id }
            kaydet(if (varMi) liste.map { if (it.id == b.id) b else it } else liste + b)
            Motor.mcpSil(b)
            yeni = false
            duzenle = null
        }
    }
}

@Composable
private fun BaglantiDiyalogu(mevcut: Baglanti?, kapat: () -> Unit, kaydet: (Baglanti) -> Unit) {
    var ad by remember { mutableStateOf(mevcut?.ad ?: "") }
    var url by remember { mutableStateOf(mevcut?.url ?: "") }
    var baslik by remember { mutableStateOf(mevcut?.baslik ?: "") }
    var sonuc by remember { mutableStateOf<String?>(null) }
    val kapsam = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = kapat,
        containerColor = Krem,
        title = { Text(if (mevcut == null) "Bağlantı ekle" else "Bağlantıyı düzenle", color = MetinKoyu) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Alan(ad, { ad = it }, "Ad")
                Alan(url, { url = it }, "https://…/mcp")
                Alan(baslik, { baslik = it }, "Başlık (ör. Authorization: Bearer …)")
                sonuc?.let { Text(it, color = if (it.startsWith("Bağlandı")) Yesil else Hata, fontSize = 12.sp) }
                Dugme("Bağlantıyı dene") {
                    sonuc = "Deneniyor…"
                    kapsam.launch {
                        sonuc = try {
                            "Bağlandı · ${Motor.mcpDene(url.trim(), baslik.trim())} araç"
                        } catch (e: Exception) {
                            "Hata: ${e.message?.take(100)}"
                        }
                    }
                }
            }
        },
        confirmButton = {
            Dugme("Kaydet", DugmeTuru.BIRINCIL) {
                if (url.isNotBlank()) {
                    val b = mevcut?.copy(ad = ad.ifBlank { url }.trim(), url = url.trim(), baslik = baslik.trim())
                        ?: Baglanti(ad = ad.ifBlank { url }.trim(), url = url.trim(), baslik = baslik.trim())
                    kaydet(b)
                }
            }
        },
        dismissButton = { Dugme("Vazgeç", tikla = kapat) }
    )
}

// ───────────────────────── İzinler ─────────────────────────

@Composable
fun SayfaPanel(geri: () -> Unit) {
    val a = Motor.ayarlar
    var panel by remember { mutableStateOf(a.panelGoster) }
    var surekli by remember { mutableStateOf(a.surekliDinleme) }
    SayfaCerceve("İzinler", geri) {
        Bolum(
            "Sürekli dinleme",
            "Açıksa uygulama kapalıyken de “hey edit” dinlenir; bildirimde mikrofon göstergesi görünür ve pil daha çok harcanır. " +
                "Kapalıysa yalnızca uygulama açıkken ve güç tuşuyla (varsayılan asistan) dinlenir."
        ) {
            Anahtarli("Arka planda dinle", null, surekli) { surekli = it; Motor.surekliAyarla(it) }
        }
        Bolum("Dinleme paneli", "Uyandırma kelimesi söylendiğinde uygulama açık değilse diğer uygulamaların üstünde küçük bir panel açılır.") {
            Anahtarli("Paneli göster", null, panel) { panel = it; a.panelGoster = it }
            Dugme("Paneli şimdi dene", DugmeTuru.BIRINCIL) { Panel.dene() }
        }
        Bolum(
            "Varsayılan dijital asistan",
            "Telefonda asistan olarak seçilirse güç tuşuna ya da ana ekran hareketine uzun basınca panel sistem tarafından açılır."
        ) {
            Etiket(if (Motor.asistanSecili) "E.D.I.T.H. seçili ✓" else "Seçili değil", if (Motor.asistanSecili) "ok" else "gri")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dugme("Asistan ayarını aç", DugmeTuru.BIRINCIL, Modifier.weight(1f)) { Motor.ayarAc("asistan") }
                Dugme("Varsayılan uygulamalar", DugmeTuru.IKINCIL, Modifier.weight(1f)) { Motor.ayarAc("varsayilan") }
            }
            Text(
                "Huawei/EMUI: Ayarlar → Uygulamalar → Varsayılan uygulamalar → Dijital asistan uygulaması.",
                color = SolukKoyu, fontSize = 12.sp, lineHeight = 16.sp
            )
        }
        Bolum(
            "Diğer uygulamaların üzerinde göster",
            "Varsayılan asistan olarak çalışmazsa yedek yol. Uygulama bu izni kendiliğinden istemez; yalnızca buradan açılır."
        ) {
            Etiket(if (Motor.izinOverlay) "izin verildi ✓" else "izin verilmedi", if (Motor.izinOverlay) "ok" else "gri")
            Dugme(if (Motor.izinOverlay) "İzni yönet" else "İzni aç") { Motor.ayarAc("overlay") }
        }
        Bolum("Diğer izinler") {
            Satir("Mikrofon", "Dinleme için gerekli.") {
                Etiket(if (Motor.izinMikrofon) "verildi ✓" else "yok", if (Motor.izinMikrofon) "ok" else "hata")
            }
            Satir("Bildirim", "Mikrofon açıkken kalıcı bildirim için.") {
                Etiket(if (Motor.izinBildirim) "verildi ✓" else "yok", if (Motor.izinBildirim) "ok" else "hata")
            }
            Satir("Pil kısıtlaması yok", "Arka planda dinlemenin kesilmemesi için önerilir.") {
                Etiket(if (Motor.pilMuaf) "muaf ✓" else "kısıtlı", if (Motor.pilMuaf) "ok" else "gri")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dugme("Pil ayarı", DugmeTuru.IKINCIL, Modifier.weight(1f)) { Motor.ayarAc("pil") }
                Dugme("Uygulama izinleri", DugmeTuru.IKINCIL, Modifier.weight(1f)) { Motor.ayarAc("uygulama") }
            }
            Text(
                "Huawei'de ayrıca Pil → Uygulama başlatma → E.D.I.T.H. → “Elle yönet” içinde üç seçeneği de açman gerekebilir.",
                color = SolukKoyu, fontSize = 12.sp, lineHeight = 16.sp
            )
        }
    }
}
