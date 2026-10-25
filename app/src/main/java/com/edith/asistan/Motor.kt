package com.edith.asistan

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

enum class Faz { BOS, KOMUT, ISLE, KONUS }

/**
 * Uygulamanın beyni (tek örnek): sohbet, Vosk ile dinleme, ses, MCP. Ön plan servisi yalnızca süreci canlı tutar;
 * arayüz buradaki durumları izler.
 */
object Motor {
    private const val TR_MODEL = "vosk-model-small-tr-0.3"
    private const val EN_MODEL = "vosk-model-small-en-us-0.15"
    private const val HIZ = 16000f
    private const val UYANDIRMA = "[\"hey edit\", \"edit\", \"[unk]\"]"
    private const val SESSIZLIK_MS = 1500L
    private const val BOS_BEKLEME_MS = 9000L

    private var basladi = false
    lateinit var ctx: Context
        private set
    lateinit var ayarlar: Ayarlar
        private set

    val mesajlar = mutableStateListOf<Mesaj>()
    val mcpDurum = mutableStateMapOf<String, String>()

    var faz by mutableStateOf(Faz.BOS)
    var mikAcik by mutableStateOf(false)
    var canli by mutableStateOf("")
    var hata by mutableStateOf<String?>(null)
    var ayarAcik by mutableStateOf(false)
    var indirme by mutableStateOf<String?>(null)
    var yukleniyorModel by mutableStateOf(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var modelTr: Model? = null
    private var modelEn: Model? = null
    private var servis: SpeechService? = null
    private var nesil = 0
    private var islemJob: Job? = null
    private var zamanJob: Job? = null
    private val tampon = StringBuilder()
    private val mcp = mutableMapOf<String, McpIstemci>()
    private val mcpDenendi = mutableSetOf<String>()

    fun init(c: Context) {
        if (basladi) return
        basladi = true
        ctx = c.applicationContext
        ayarlar = Ayarlar(ctx)
    }

    // ───────────── Mikrofon / dinleme ─────────────

    fun mikrofonAc() {
        if (mikAcik) return
        mikAcik = true
        hata = null
        runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, EdithServisi::class.java)) }
        scope.launch {
            if (!modelleriHazirla()) {
                mikrofonKapat()
                return@launch
            }
            if (mikAcik && faz != Faz.KOMUT) baslatBekle()
        }
    }

    fun mikrofonKapat() {
        mikAcik = false
        nesil++
        zamanJob?.cancel()
        durdurDinleme()
        canli = ""
        if (faz == Faz.KOMUT) faz = Faz.BOS
        runCatching { ctx.stopService(Intent(ctx, EdithServisi::class.java)) }
    }

    private fun durdurDinleme() {
        val s = servis
        servis = null
        if (s != null) runCatching { s.stop(); s.shutdown() }
    }

    private fun json(h: String?, alan: String): String =
        runCatching { JSONObject(h ?: "").optString(alan) }.getOrDefault("").trim()

    /** İngilizce modelle yalnızca "hey edit" / "edit" dinler. */
    private fun baslatBekle() {
        val m = modelEn ?: return
        durdurDinleme()
        val benim = ++nesil
        try {
            val ss = SpeechService(Recognizer(m, HIZ, UYANDIRMA), HIZ)
            servis = ss
            ss.startListening(object : RecognitionListener {
                private fun kontrol(h: String?) {
                    if (benim != nesil) return
                    val t = json(h, "partial").ifBlank { json(h, "text") }
                    if (t.split(" ").any { it == "edit" }) uyan()
                }
                override fun onPartialResult(h: String?) = kontrol(h)
                override fun onResult(h: String?) = kontrol(h)
                override fun onFinalResult(h: String?) {}
                override fun onError(e: Exception?) { if (benim == nesil) hata = "Mikrofon hatası: ${e?.message}" }
                override fun onTimeout() {}
            })
        } catch (e: Exception) {
            hata = "Dinleme başlatılamadı: ${e.message}"
        }
    }

    /** Uyandırma kelimesi duyuldu: konuşma/işlem kesilir, Türkçe komut dinlemesi başlar. */
    private fun uyan() {
        islemJob?.cancel()
        Ses.kes()
        komutDinle()
    }

    private fun komutDinle() {
        val m = modelTr ?: return
        durdurDinleme()
        val benim = ++nesil
        faz = Faz.KOMUT
        canli = ""
        tampon.setLength(0)
        zamanJob?.cancel()
        try {
            val ss = SpeechService(Recognizer(m, HIZ), HIZ)
            servis = ss
            ss.startListening(object : RecognitionListener {
                override fun onPartialResult(h: String?) {
                    if (benim != nesil) return
                    val p = json(h, "partial")
                    if (p.isNotBlank()) {
                        zamanJob?.cancel()
                        canli = (tampon.toString() + p).trim()
                    }
                }
                override fun onResult(h: String?) {
                    if (benim != nesil) return
                    val t = json(h, "text")
                    if (t.isBlank()) return
                    tampon.append(t).append(' ')
                    canli = tampon.toString().trim()
                    zamanJob?.cancel()
                    zamanJob = scope.launch {
                        delay(SESSIZLIK_MS)
                        if (benim == nesil) komutuGonder()
                    }
                }
                override fun onFinalResult(h: String?) {}
                override fun onError(e: Exception?) { if (benim == nesil) hata = "Mikrofon hatası: ${e?.message}" }
                override fun onTimeout() {}
            })
            zamanJob = scope.launch {
                delay(BOS_BEKLEME_MS)
                if (benim == nesil && tampon.isBlank() && canli.isBlank()) {
                    faz = Faz.BOS
                    if (mikAcik) baslatBekle()
                }
            }
        } catch (e: Exception) {
            hata = "Dinleme başlatılamadı: ${e.message}"
            faz = Faz.BOS
        }
    }

    private fun komutuGonder() {
        val metin = tampon.toString().trim()
        zamanJob?.cancel()
        durdurDinleme()
        nesil++
        canli = ""
        tampon.setLength(0)
        if (metin.isBlank()) {
            faz = Faz.BOS
            if (mikAcik) baslatBekle()
            return
        }
        gonder(metin, sesli = true)
    }

    // ───────────── Sohbet ─────────────

    private val ayarKomutu = Regex("^(ayarlar|ayarları aç|ayarlar aç|ayarlar paneli|ayarlar paneli aç)[.!?\\s]*$")

    fun gonder(metin: String, sesli: Boolean) {
        val m = metin.trim()
        if (m.isEmpty()) return
        if (ayarKomutu.matches(m.lowercase())) {
            ayarAcik = true
            if (sesli) { faz = Faz.BOS; if (mikAcik) baslatBekle() }
            return
        }
        if (faz == Faz.ISLE || faz == Faz.KONUS) { islemJob?.cancel(); Ses.kes() }
        val gecmis = mesajlar.toList()
        mesajlar.add(Mesaj(true, m))
        hata = null
        faz = Faz.ISLE
        if (mikAcik) baslatBekle() // işlem/konuşma sırasında "edit" diyerek kesebilsin
        islemJob = scope.launch {
            var soru = false
            try {
                mcpHazirla()
                val (araclar, esle) = mcpAraclari()
                val cevap = Gemini.sor(ayarlar.apiKey, gecmis, m, araclar) { ad, args ->
                    val (istemci, gercekAd) = esle[ad] ?: return@sor "Bilinmeyen araç: $ad"
                    istemci.cagir(gercekAd, args)
                }
                soru = cevap.soru
                val konus = sesli || !ayarlar.sadeceSesliKomutta
                var pcm: ByteArray? = null
                if (konus) {
                    try { pcm = Ses.sentez(ayarlar, cevap.metin) } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { hata = "Ses üretilemedi: ${e.message}" }
                }
                mesajlar.add(Mesaj(false, cevap.metin)) // metin ve ses birlikte hazır olunca
                if (pcm != null) {
                    faz = Faz.KONUS
                    Ses.cal(pcm)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                hata = e.message ?: "Bilinmeyen hata"
            }
            faz = Faz.BOS
            if (mikAcik) {
                if (soru || ayarlar.herCevaptaMikrofon) komutDinle() else baslatBekle()
            }
        }
    }

    // ───────────── Ses denemeleri ─────────────

    fun sesDene(motor: String) {
        scope.launch {
            hata = null
            try {
                val a = ayarlar
                val cumle = "Merhaba, ben E.D.I.T.H. Ses denemesi."
                val pcm = if (motor == "gemini") Ses.geminiSentez(a.apiKey, a.geminiSes, cumle)
                else Ses.elevenSentez(a.elevenKey, a.elevenSes, a.elevenModel, cumle)
                Ses.cal(pcm)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                hata = "Ses denemesi: ${e.message}"
            }
        }
    }

    // ───────────── MCP ─────────────

    suspend fun mcpBaglan(b: Baglanti) {
        mcpDurum[b.url] = "Bağlanıyor…"
        try {
            val i = McpIstemci(b)
            val n = i.baglan()
            mcp[b.url] = i
            mcpDurum[b.url] = "Bağlı · $n araç"
        } catch (e: Exception) {
            mcp.remove(b.url)
            mcpDurum[b.url] = "Hata: ${e.message?.take(80)}"
        }
    }

    private suspend fun mcpHazirla() {
        for (b in ayarlar.baglantilar) {
            if (b.url in mcp || b.url in mcpDenendi) continue
            mcpDenendi.add(b.url)
            mcpBaglan(b)
        }
    }

    fun mcpSil(b: Baglanti) {
        mcp.remove(b.url)
        mcpDurum.remove(b.url)
        mcpDenendi.remove(b.url)
    }

    private fun mcpAraclari(): Pair<List<GeminiArac>, Map<String, Pair<McpIstemci, String>>> {
        val liste = mutableListOf<GeminiArac>()
        val esle = mutableMapOf<String, Pair<McpIstemci, String>>()
        ayarlar.baglantilar.forEachIndexed { i, b ->
            val ist = mcp[b.url] ?: return@forEachIndexed
            for (a in ist.araclar) {
                val ad = "m${i}_" + a.ad.replace(Regex("[^A-Za-z0-9_]"), "_")
                val kisa = ad.take(64)
                liste.add(GeminiArac(kisa, a.aciklama, a.sema))
                esle[kisa] = ist to a.ad
            }
        }
        return liste to esle
    }

    // ───────────── Vosk modelleri ─────────────

    private suspend fun modelleriHazirla(): Boolean {
        if (modelTr != null && modelEn != null) return true
        yukleniyorModel = true
        return try {
            withContext(Dispatchers.IO) {
                val tr = modelDizini(TR_MODEL, "Türkçe")
                val en = modelDizini(EN_MODEL, "İngilizce")
                indirme = "Modeller yükleniyor…"
                if (modelTr == null) modelTr = Model(tr)
                if (modelEn == null) modelEn = Model(en)
            }
            indirme = null
            true
        } catch (e: Exception) {
            indirme = null
            hata = "Ses modeli hazırlanamadı: ${e.message}"
            false
        } finally {
            yukleniyorModel = false
        }
    }

    private fun modelDizini(ad: String, etiket: String): String {
        val hedef = File(File(ctx.filesDir, "vosk"), ad)
        if (File(hedef, ".hazir").exists()) return hedef.absolutePath
        hedef.deleteRecursively()
        hedef.mkdirs()

        val zip = File(ctx.cacheDir, "$ad.zip")
        val bag = URL("https://alphacephei.com/vosk/models/$ad.zip").openConnection() as HttpURLConnection
        bag.connectTimeout = 20000
        bag.readTimeout = 30000
        try {
            if (bag.responseCode != 200) throw RuntimeException("İndirme hatası (HTTP ${bag.responseCode})")
            val toplam = bag.contentLengthLong
            var alinan = 0L
            var sonYuzde = -1
            bag.inputStream.use { girdi ->
                zip.outputStream().use { cikti ->
                    val b = ByteArray(64 * 1024)
                    while (true) {
                        val n = girdi.read(b)
                        if (n < 0) break
                        cikti.write(b, 0, n)
                        alinan += n
                        if (toplam > 0) {
                            val y = (alinan * 100 / toplam).toInt()
                            if (y != sonYuzde) { sonYuzde = y; indirme = "$etiket model indiriliyor %$y" }
                        }
                    }
                }
            }
        } finally {
            bag.disconnect()
        }

        indirme = "$etiket model açılıyor…"
        val kok = hedef.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val yol = e.name.substringAfter('/', "")
                if (yol.isEmpty()) continue
                val dosya = File(hedef, yol)
                if (!dosya.canonicalPath.startsWith(kok)) continue // zip-slip koruması
                if (e.isDirectory) dosya.mkdirs() else {
                    dosya.parentFile?.mkdirs()
                    dosya.outputStream().use { z.copyTo(it) }
                }
            }
        }
        zip.delete()
        File(hedef, ".hazir").writeText("ok")
        return hedef.absolutePath
    }
}
