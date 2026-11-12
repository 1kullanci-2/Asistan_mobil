package com.edith.asistan

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.vosk.Model
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.zip.ZipInputStream

enum class Faz { BOS, KOMUT, ISLE, KONUS }

class OnayIstegi(val soru: String, val sonuc: CompletableDeferred<Boolean>)

/**
 * Uygulamanın beyni (tek örnek): sohbet, dinleme, ses, MCP ve panel durumu. Ön plan servisi yalnızca süreci canlı
 * tutar; arayüz buradaki durumları izler.
 */
object Motor : DinleyiciGeri {
    private const val TR_MODEL = "vosk-model-small-tr-0.3"
    private const val EN_MODEL = "vosk-model-small-en-us-0.15"
    const val OGREN_HEDEF = 8

    private var basladi = false
    lateinit var ctx: Context
        private set
    lateinit var ayarlar: Ayarlar
        private set

    // ── Sohbet ve genel durum
    val mesajlar = mutableStateListOf<Mesaj>()
    var adim by mutableStateOf("")           // "Araç çalışıyor · …" gibi anlık durum
    var akanMetin by mutableStateOf("")      // cevap yazılırken canlı görünen metin
    var menuAcik by mutableStateOf(false)
    val sohbetler = mutableStateListOf<SohbetBaslik>()
    var aktifId by mutableStateOf<String?>(null)
    private val kayitlar = mutableMapOf<String, SohbetKaydi>()
    @Volatile private var aktif: SohbetKaydi? = null
    var faz by mutableStateOf(Faz.BOS)
        private set
    var mikAcik by mutableStateOf(false)
    var canli by mutableStateOf("")
    var seviye by mutableStateOf(0f)
    var hata by mutableStateOf<String?>(null)
    var ayarAcik by mutableStateOf(false)
    var indirme by mutableStateOf<String?>(null)
    var panelGorunur by mutableStateOf(false)
    var panelYazi by mutableStateOf(false)          // panelde dinleme yerine yazı kutusu
    var secim by mutableStateOf("gemini|")          // etkin sohbetin "servis|model" seçimi
    var modelPaneli by mutableStateOf(false)
    var kapatSorusu by mutableStateOf(false)      // panelde "Kapatılsın mı?" sorusu
    private var sorudaDinliyordu = false
    var kurulumGoster by mutableStateOf(false)    // ilk kurulum ekranı
    var kurulumOran by mutableStateOf(0f)
    @Volatile var onde = false

    // ── Mikrofon testi ve uyandırma öğrenme
    var testAktif by mutableStateOf(false)
    var testEnMetni by mutableStateOf("—")
    var testTrMetni by mutableStateOf("—")
    var ogrenKelime by mutableStateOf<String?>(null)
    val ogrenOrnekler = mutableStateListOf<String>()
    var ogrenMesaj by mutableStateOf<String?>(null)
    var ogrenCanli by mutableStateOf("")
    var ogrenSurum by mutableStateOf(0)
    private var testIcinAcildi = false

    // ── ElevenLabs verileri
    var elevenSesler by mutableStateOf<List<ElevenSes>>(emptyList())
    var elevenModeller by mutableStateOf<List<ElevenModel>>(emptyList())
    var elevenKredi by mutableStateOf<ElevenKredi?>(null)
    var elevenMesaj by mutableStateOf<String?>(null)
    var elevenYukleniyor by mutableStateOf(false)
    var onizleme by mutableStateOf<String?>(null)

    // ── MCP
    val mcpDurum = mutableStateMapOf<String, String>()
    var onay by mutableStateOf<OnayIstegi?>(null)

    // ── İzinler
    var izinOverlay by mutableStateOf(false)
    var izinMikrofon by mutableStateOf(false)
    var izinBildirim by mutableStateOf(false)
    var asistanSecili by mutableStateOf(false)
    var pilMuaf by mutableStateOf(false)
    var modellerHazir by mutableStateOf(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var modelTr: Model? = null
    private var modelEn: Model? = null
    private var dinleyici: Dinleyici? = null
    private val isler = java.util.concurrent.ConcurrentHashMap<String, Job>()   // sohbet → çalışan istek
    private val kismi = java.util.concurrent.ConcurrentHashMap<String, String>() // sohbet → yazılmakta olan cevap
    private val sessiz = java.util.concurrent.ConcurrentHashMap.newKeySet<String>() // sesi kapatılan sohbetler
    private var gorevSayisi = 0
    private val yuklendi = CompletableDeferred<Unit>()
    private val modelKilit = Mutex()
    private var onizleJob: Job? = null
    private var oynatici: MediaPlayer? = null
    private var bekleyenTakip = false
    private val mcp = java.util.concurrent.ConcurrentHashMap<String, McpIstemci>()

    fun init(c: Context) {
        if (basladi) return
        basladi = true
        ctx = c.applicationContext
        ayarlar = Ayarlar(ctx)
        secim = ayarlar.varsayilanSecim()
        izinYenile()
        // İlk kurulum yalnızca ilk yüklemede gösterilir; modelleri zaten olan eski kurulumlar atlar.
        if (modellerHazir) ayarlar.kurulumTamam = true
        kurulumGoster = !ayarlar.kurulumTamam
        scope.launch {
            val l = withContext(Dispatchers.IO) { Depo.yukle(ctx) }
            l.forEach { kayitlar[it.id] = it }
            listeYenile()
            yuklendi.complete(Unit)
        }
        scope.launch(Dispatchers.IO) { Gemini.isin() }
        if (!Kuyruk.bosMu(ctx)) Kuyruk.planla(ctx)   // önceki oturumdan kalan, gönderilmemiş mesajlar var
        scope.launch { // MCP bağlantıları arka planda hazır tutulur
            while (true) {
                mcpTazele()
                delay(60_000)
            }
        }
    }

    fun fazAyarla(f: Faz) {
        faz = f
        Panel.guncelle()
    }

    // ───────────── İzinler ve sistem durumu ─────────────

    fun izinYenile() {
        izinOverlay = Settings.canDrawOverlays(ctx)
        izinMikrofon = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        izinBildirim = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            ctx, android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        asistanSecili = asistanMi()
        pilMuaf = runCatching {
            ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
        }.getOrDefault(false)
        modellerHazir = modelHazirMi(TR_MODEL) && modelHazirMi(EN_MODEL)
    }

    fun asistanMi(): Boolean = runCatching {
        Settings.Secure.getString(ctx.contentResolver, "assistant").orEmpty().startsWith(ctx.packageName + "/")
    }.getOrDefault(false)

    fun ayarIntent(tur: String): Intent = when (tur) {
        "overlay" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
        "asistan" -> Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
        "varsayilan" -> Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        "pil" -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun ayarAc(tur: String) {
        try {
            ctx.startActivity(ayarIntent(tur))
        } catch (e: Exception) {
            runCatching { ctx.startActivity(ayarIntent("uygulama")) }
        }
    }

    fun uygulamayiAc() {
        runCatching { ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    // ───────────── Mikrofon / dinleme ─────────────

    fun mikrofonAc() {
        if (mikAcik) return
        mikAcik = true
        hata = null
        if (ayarlar.surekliDinleme) {
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, EdithServisi::class.java)) }
        }
        scope.launch {
            if (!modelleriHazirla()) {
                mikrofonKapat()
                return@launch
            }
            if (mikAcik && dinleyici == null) dinleyiciBaslat()
        }
    }

    private fun dinleyiciBaslat() {
        val tr = modelTr ?: return
        val en = modelEn ?: return
        val d = Dinleyici(tr, en, this)
        d.testModu = testAktif || ogrenKelime != null
        dinleyici = d
        d.start()
        if (bekleyenTakip) {
            bekleyenTakip = false
            d.takipIste()
        }
    }

    fun mikrofonKapat() {
        mikAcik = false
        dinleyici?.calisiyor = false
        dinleyici = null
        canli = ""
        seviye = 0f
        testAktif = false
        ogrenKelime = null
        if (faz == Faz.KOMUT) fazAyarla(Faz.BOS)
        runCatching { ctx.stopService(Intent(ctx, EdithServisi::class.java)) }
    }

    /** Sohbet ekranındaki mikrofon düğmesi: "hey edit" beklemeden hemen komut dinler. */
    fun sesliKomut() {
        if (faz == Faz.ISLE || faz == Faz.KONUS) {
            aktifIsiDurdur()
            Ses.kes()
            akanMetin = ""
            fazAyarla(Faz.BOS)
        }
        if (!mikAcik) {
            bekleyenTakip = true
            mikrofonAc()
        } else dinleyici?.takipIste()
    }

    fun dinlemeyiIptal() {
        bekleyenTakip = false
        dinleyici?.iptalIste()
        if (faz == Faz.KOMUT) {
            canli = ""
            fazAyarla(Faz.BOS)
        }
    }

    /** İzinler → Sürekli dinleme anahtarı. */
    fun surekliAyarla(v: Boolean) {
        ayarlar.surekliDinleme = v
        if (v) {
            if (mikAcik) runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, EdithServisi::class.java)) }
            else mikrofonAc()
        } else if (!onde) mikrofonKapat()
        else runCatching { ctx.stopService(Intent(ctx, EdithServisi::class.java)) }
    }

    /** Sistem, varsayılan asistan olarak bizi açtığında (uzun basma vb.): hemen dinlemeye başla. */
    fun asistanAcildi() {
        panelGorunur = true
        if (!mikAcik) {
            bekleyenTakip = true
            mikrofonAc()
        } else dinleyici?.takipIste()
    }

    /** Panel kapanınca konuşma susar ama düşünen/yazılan cevap iptal edilmez; bitince bildirim gelir. */
    fun panelKapat() {
        panelYazi = false
        kapatSorusu = false
        aktifId?.let { sessiz.add(it) }
        Ses.kes()
        canli = ""
        akanMetin = ""
        fazAyarla(Faz.BOS)
    }

    /** Panelin dışına dokunuldu: dinlemeyi durdur, "Kapatılsın mı?" diye sor. */
    fun disaDokunuldu() {
        if (kapatSorusu) return
        kapatSorusu = true
        sorudaDinliyordu = faz == Faz.KOMUT
        if (sorudaDinliyordu) dinlemeyiIptal()
        Panel.guncelle()
    }

    fun kapatCevap(evet: Boolean) {
        if (evet) {
            panelKapat()
            Panel.gizle()
        } else {
            kapatSorusu = false
            if (sorudaDinliyordu) sesliKomut()
            sorudaDinliyordu = false
        }
    }

    private fun aktifIsiDurdur() {
        aktifId?.let { isler[it]?.cancel() }
    }

    private fun titret() {
        if (!ayarlar.titresim) return
        runCatching {
            ctx.getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    // ── Dinleyici geri bildirimleri (ana iş parçacığı)

    override fun uyandi(kes: Boolean) {
        if (kes) {
            aktifIsiDurdur()
            Ses.kes()
            akanMetin = ""
        }
        canli = ""
        fazAyarla(Faz.KOMUT)
    }

    override fun takipBasladi() {
        titret()
        canli = ""
        fazAyarla(Faz.KOMUT)
    }

    override fun canli(metin: String) { canli = metin }
    override fun seviye(v: Float) { seviye = v }

    override fun komut(metin: String) {
        canli = ""
        gonder(metin, sesli = true)
    }

    override fun komutBos() {
        canli = ""
        fazAyarla(Faz.BOS)
    }

    override fun hata(mesaj: String) {
        hata = mesaj
        mikrofonKapat()
    }

    // ───────────── Sohbet geçmişi ─────────────

    private fun listeYenile() {
        val l = kayitlar.values.filter { it.mesajlar.isNotEmpty() }
            .sortedByDescending { it.guncelleme }.map { SohbetBaslik(it.id, it.baslik, it.guncelleme) }
        sohbetler.clear()
        sohbetler.addAll(l)
    }

    private fun kaydetKayit(k: SohbetKaydi) {
        val ic = Depo.json(k)
        listeYenile()
        scope.launch(Dispatchers.IO) { Depo.yaz(ctx, k.id, ic) }
    }

    /** Mesajı sohbet kaydına ekler; sohbet ekrandaysa ekrandaki listeye de yansıtır. Yalnızca ana iş parçacığında. */
    private fun ekle(k: SohbetKaydi, m: Mesaj) {
        k.mesajlar.add(m)
        if (aktif === k) mesajlar.add(m)
        k.guncelleme = System.currentTimeMillis()
        kaydetKayit(k)
    }

    private fun notlariSil(k: SohbetKaydi, metin: String) {
        k.mesajlar.removeAll { it.not && it.metin == metin }
        if (aktif === k) mesajlar.removeAll { it.not && it.metin == metin }
    }

    /** Ekrandaki sohbetin durumuna göre faz: istek sürüyorsa "düşünüyor", yoksa boş. */
    private fun fazSenkron() {
        val calisiyor = aktifId?.let { isler[it]?.isActive == true } == true
        when {
            faz == Faz.KOMUT -> {}
            calisiyor -> if (faz != Faz.ISLE) fazAyarla(Faz.ISLE)
            faz == Faz.ISLE || faz == Faz.KONUS -> fazAyarla(Faz.BOS)
        }
    }

    fun yeniSohbet() {
        Ses.kes()
        aktif = null
        aktifId = null
        secim = ayarlar.varsayilanSecim()   // her yeni sohbet Gemini'nin en hızlı modeliyle başlar
        mesajlar.clear()
        akanMetin = ""
        adim = ""
        hata = null
        menuAcik = false
        fazSenkron()
    }

    /** Alt panelden seçim: etkin sohbet (varsa) kaydedilir, yoksa sonraki mesajla başlayacak sohbete uygulanır. */
    fun modelSec(s: String) {
        secim = s
        modelPaneli = false
        aktif?.let { it.model = s; kaydetKayit(it) }
    }

    /** Seçili servis/model artık yoksa (anahtar silindi, model kaldırıldı) varsayılana döner. */
    fun gecerliSecim(s: String): String {
        val (servis, model) = s.split("|", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val ok = ayarlar.servisAnahtar(servis).isNotBlank() && model.isNotBlank()
        return if (ok) s else ayarlar.varsayilanSecim()
    }

    fun sohbetAc(id: String) {
        val k = kayitlar[id] ?: return
        Ses.kes()
        aktif = k
        aktifId = id
        secim = gecerliSecim(k.model.ifBlank { ayarlar.varsayilanSecim() })
        mesajlar.clear()
        mesajlar.addAll(k.mesajlar)
        akanMetin = kismi[id].orEmpty()
        adim = ""
        hata = null
        menuAcik = false
        Bildirimler.temizle(ctx, id)
        fazSenkron()
    }

    /** Bildirimden gelindiğinde sohbet listesi henüz yüklenmemiş olabilir; yüklenince açar. */
    fun sohbetAcSonra(id: String) {
        scope.launch {
            yuklendi.await()
            sohbetAc(id)
        }
    }

    fun sohbetSil(id: String) {
        isler.remove(id)?.cancel()
        kismi.remove(id)
        Kuyruk.sohbetiSil(ctx, id)
        if (aktifId == id) yeniSohbet()
        kayitlar.remove(id)
        listeYenile()
        scope.launch(Dispatchers.IO) { Depo.sil(ctx, id) }
    }

    private fun baslikAyarla(k: SohbetKaydi, b: String) {
        scope.launch {
            k.baslik = b.take(40)
            kaydetKayit(k)
        }
    }

    /** Konuşma uzadıkça eski kısmı arka planda özetler; sohbetler arasında gezmek istek harcamaz. */
    private fun ozetGuncelle(k: SohbetKaydi) {
        if (ayarlar.apiKey.isBlank()) return   // özet Gemini ile çıkarılır; anahtar yoksa mekanik kısaltma kullanılır
        val tum = k.mesajlar.filter { !it.not }
        val hedef = tum.size - 6
        if (hedef - k.ozetKapsam < 6) return
        val parca = tum.subList(k.ozetKapsam, hedef).toList()
        scope.launch {
            val y = runCatching { Gemini.ozetle(ayarlar, k.ozet, parca) }.getOrNull() ?: return@launch
            if (y.isNotBlank()) {
                k.ozet = y
                k.ozetKapsam = hedef
                kaydetKayit(k)
            }
        }
    }

    // ───────────── Arka plan görevi ─────────────

    private fun gorevBasla() {
        if (gorevSayisi++ == 0) {
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, GorevServisi::class.java)) }
        }
    }

    private fun gorevBitir() {
        if (--gorevSayisi <= 0) {
            gorevSayisi = 0
            runCatching { ctx.stopService(Intent(ctx, GorevServisi::class.java)) }
        }
    }

    // ───────────── Sohbet ─────────────

    private val cumleSonu = Regex("""[.!?…]["”')»]*\s+|\n+""")
    private const val CEVRIMDISI = "Çevrim dışısınız — mesaj kaydedildi, internet gelince otomatik gönderilecek."

    fun gonder(metin: String, sesli: Boolean) {
        val m = metin.trim()
        if (m.isEmpty()) return
        if (Uyandirma.ayarKomutuMu(m)) {
            ayarAcik = true
            if (!onde) uygulamayiAc()
            if (sesli) fazAyarla(Faz.BOS)
            return
        }
        if (faz == Faz.ISLE || faz == Faz.KONUS) {
            aktifIsiDurdur()
            Ses.kes()
        }
        akanMetin = ""
        adim = ""
        var k = aktif
        if (k == null) {
            k = SohbetKaydi(UUID.randomUUID().toString().take(12), m.take(32), System.currentTimeMillis())
            aktif = k
            aktifId = k.id
            kayitlar[k.id] = k
        }
        if (k.model.isBlank()) k.model = secim.ifBlank { ayarlar.varsayilanSecim() }
        val kayit: SohbetKaydi = k
        val ilk = kayit.mesajlar.none { !it.kullanici && !it.not }
        ekle(kayit, Mesaj(true, m))
        hata = null
        if (!Ag.bagliMi(ctx)) {
            kuyruga(kayit, listOf(m))
            return
        }
        calistir(kayit, listOf(m), sesli, ilk)
    }

    /** İnternet yok: mesaj zaten sohbette görünüyor; telefona kaydedilir, bağlantı gelince gönderilir. */
    private fun kuyruga(k: SohbetKaydi, parcalar: List<String>) {
        parcalar.forEach { Kuyruk.ekle(ctx, k.id, it) }
        notlariSil(k, CEVRIMDISI)
        ekle(k, Mesaj(false, CEVRIMDISI, not = true))
        Kuyruk.planla(ctx)
        if (aktif === k && faz == Faz.ISLE) fazAyarla(Faz.BOS)
    }

    /** İnternet geldiğinde (uygulama kapalı olsa da) bekleyen mesajları sohbetlerine göre gönderir; cevapları bekler. */
    suspend fun kuyrukBosalt() {
        yuklendi.await()
        if (!Ag.bagliMi(ctx)) return
        val bekleyen = Kuyruk.al(ctx)
        val isListesi = bekleyen.mapNotNull { (id, liste) ->
            withContext(Dispatchers.Main) {
                val k = kayitlar[id] ?: return@withContext null
                notlariSil(k, CEVRIMDISI)
                kaydetKayit(k)
                val ilk = k.mesajlar.none { !it.kullanici && !it.not }
                calistir(k, liste, false, ilk)
            }
        }
        isListesi.forEach { it.join() }
    }

    /**
     * Bir sohbet için isteği başlatır. Kullanıcının mesajları sohbet kaydında zaten vardır; cevap, kullanıcı başka
     * sohbete geçse ya da uygulamadan çıksa bile bu sohbete yazılır, sonunda gerekirse bildirim gösterilir.
     */
    private fun calistir(kayit: SohbetKaydi, parcalar: List<String>, sesli: Boolean, ilk: Boolean): Job {
        isler.remove(kayit.id)?.cancel()
        sessiz.remove(kayit.id)
        val m = parcalar.joinToString("\n")
        val (ozet, yakin) = Baglam.hazirla(kayit, kayit.mesajlar.filter { !it.not }.dropLast(parcalar.size))
        kismi[kayit.id] = ""
        if (aktif === kayit) {
            akanMetin = ""
            adim = ""
            fazAyarla(Faz.ISLE)
        }
        gorevBasla()

        val job = scope.launch {
            val bu = coroutineContext[Job]
            var soru = false
            var bitti = false
            var cevrimDisiKaldi = false
            var sonCevap = ""
            val kullanilan = linkedSetOf<String>()
            val konus = sesli || !ayarlar.sadeceSesliKomutta
            val cumleler = Channel<String>(Channel.UNLIMITED)
            val sesler = Channel<ByteArray>(2)
            val tampon = StringBuilder()
            var ilkGitti = false
            var sesHatasi = false
            dinleyici?.kendiWake = false

            // Konuşma yalnızca sohbet ekrandayken ve kullanıcı o an uygulamadaysa / panel açıksa sürer.
            fun sesAcik() = konus && aktif === kayit && kayit.id !in sessiz && (onde || panelGorunur || sesli)
            fun adimYaz(s: String) { if (aktif === kayit) adim = s }

            fun cumleGonder(c: String) {
                if (c.isBlank()) return
                if (Uyandirma.ara(c, false) != null) dinleyici?.kendiWake = true
                cumleler.trySend(c)
            }

            // İlk parça kısa (hemen konuşmaya başlasın), sonrakiler uzun (ses motoru istek sayısı az kalsın).
            fun cumleCikar(son: Boolean) {
                while (true) {
                    val s = tampon.toString()
                    if (!son && Regex("```").findAll(s).count() % 2 == 1) return
                    val esik = if (ilkGitti) 250 else 25
                    val bul = if (s.length > esik) cumleSonu.find(s, esik) else null
                    if (bul != null) {
                        val c = s.substring(0, bul.range.last + 1)
                        tampon.delete(0, bul.range.last + 1)
                        ilkGitti = true
                        cumleGonder(c)
                    } else {
                        if (son && s.isNotBlank()) {
                            cumleGonder(s)
                            tampon.setLength(0)
                        }
                        return
                    }
                }
            }

            try {
                try {
                    coroutineScope {
                        if (konus) {
                            launch(Dispatchers.IO) {
                                try {
                                    for (c in cumleler) {
                                        if (!sesAcik()) continue
                                        val pcm = try {
                                            Ses.sentez(ayarlar, c)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            if (hata == null) { UygulamaLog.kaydet(ctx, "Ses sentezi", e); hata = "Ses üretilemedi: ${UygulamaLog.kullaniciMesaji(e)}" }
                                            sesHatasi = true
                                            null
                                        }
                                        if (sesHatasi) break
                                        if (pcm != null) sesler.send(pcm)
                                    }
                                } finally {
                                    sesler.close()
                                }
                            }
                            launch {
                                for (pcm in sesler) {
                                    if (!sesAcik()) continue
                                    if (faz != Faz.KONUS) fazAyarla(Faz.KONUS)
                                    Ses.cal(pcm)
                                }
                            }
                        }

                        mcpBekle()
                        val (araclar, esle) = mcpAraclari()
                        val cevap = Gemini.sor(
                            ayarlar, gecerliSecim(kayit.model.ifBlank { secim }), ozet, yakin, m, ilk, araclar,
                            akis = { d ->
                                val t = (kismi[kayit.id] ?: "") + d
                                kismi[kayit.id] = t
                                if (aktif === kayit) akanMetin = t
                                if (sesAcik()) {
                                    tampon.append(d)
                                    cumleCikar(false)
                                }
                            },
                            bildir = { n -> scope.launch { ekle(kayit, Mesaj(false, n, not = true)) } },
                            baslik = { b -> if (ilk && b.isNotBlank()) baslikAyarla(kayit, b) },
                            cagir = cagir@{ ad, args ->
                                val (ist, gercek, bag) = esle[ad] ?: return@cagir "Bilinmeyen araç: $ad"
                                if (ayarlar.mcpSilmeOnayi && "sil" in gercek.lowercase().split("_")) {
                                    if (!onayIste("“$gercek” aracı çalıştırılsın mı? (silme işlemi)")) {
                                        return@cagir "Kullanıcı bu işlemi onaylamadı."
                                    }
                                }
                                kullanilan.add(gercek)
                                adimYaz("Araç çalışıyor · $gercek")
                                try {
                                    ist.cagir(gercek, args)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    // oturum düşmüş olabilir: bir kez yeniden bağlan, tekrar dene
                                    mcpBaglan(bag)
                                    val yeni = mcp[bag.id] ?: throw e
                                    yeni.cagir(gercek, args)
                                } finally {
                                    adimYaz("")
                                }
                            }
                        )
                        soru = cevap.soru
                        sonCevap = cevap.metin
                        bitti = true
                        ekle(kayit, Mesaj(false, cevap.metin, kullanilan.toList()))
                        kismi.remove(kayit.id)
                        if (aktif === kayit) akanMetin = ""
                        if (konus) {
                            cumleCikar(true)
                            cumleler.close()
                        }
                    } // ses bitene kadar bekler
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val yarim = kismi[kayit.id].orEmpty().trim()
                    if (yarim.isEmpty() && !Ag.bagliMi(ctx)) {
                        cevrimDisiKaldi = true   // bağlantı koptu: mesaj kuyruğa alınır, hata gösterilmez
                    } else {
                        UygulamaLog.kaydet(ctx, "Asistan isteği", e)
                        val h = UygulamaLog.kullaniciMesaji(e)
                        if (aktif === kayit) hata = h else ekle(kayit, Mesaj(false, h, not = true))
                        if (yarim.isNotEmpty()) ekle(kayit, Mesaj(false, yarim, kullanilan.toList()))
                    }
                }
                if (cevrimDisiKaldi) kuyruga(kayit, parcalar)
                kismi.remove(kayit.id)
                val aktifMi = aktif === kayit
                if (aktifMi) {
                    akanMetin = ""
                    adim = ""
                    if (faz != Faz.KOMUT) fazAyarla(Faz.BOS)
                }
                if (bitti && sonCevap.isNotBlank() && ((!onde && !panelGorunur) || !aktifMi)) {
                    Bildirimler.cevap(ctx, kayit.id, kayit.baslik, sonCevap)
                }
                if (aktifMi && mikAcik && hata == null && ((soru && ayarlar.soruSorunca) || ayarlar.herCevapta)) {
                    dinleyici?.takipIste()
                }
                ozetGuncelle(kayit)
            } finally {
                gorevBitir()
                if (bu != null) isler.remove(kayit.id, bu)
            }
        }
        isler[kayit.id] = job
        return job
    }

    // ───────────── Ses: deneme, önizleme, ElevenLabs verileri ─────────────

    fun sesDene(motor: String) {
        scope.launch {
            hata = null
            try {
                Ses.cal(Ses.motorDene(ayarlar, motor, Ses.DENEME_CUMLESI))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UygulamaLog.kaydet(ctx, "Ses motoru/$motor", e)
                hata = "${Ayarlar.MOTOR_AD[motor]}: ${UygulamaLog.kullaniciMesaji(e)}"
            }
        }
    }

    private fun onizlemeDurdur() {
        onizleJob?.cancel()
        Ses.kes()
        runCatching { oynatici?.release() }
        oynatici = null
    }

    fun geminiOnizle(ses: String) {
        ayarlar.geminiSes = ses
        onizlemeDurdur()
        onizleme = ses
        onizleJob = scope.launch {
            try {
                Ses.cal(Ses.geminiOnizleme(ctx, ayarlar.apiKey, ses))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UygulamaLog.kaydet(ctx, "Ses önizleme", e)
                hata = "Önizleme: ${UygulamaLog.kullaniciMesaji(e)}"
            } finally {
                if (onizleme == ses) onizleme = null
            }
        }
    }

    fun elevenOnizle(s: ElevenSes) {
        ayarlar.elevenSes = s.id
        ayarlar.elevenSesAdi = s.ad
        onizlemeDurdur()
        if (s.onizleme.isBlank()) return
        onizleme = s.id
        try {
            val p = MediaPlayer()
            oynatici = p
            p.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            p.setDataSource(s.onizleme)
            p.setOnPreparedListener { it.start() }
            p.setOnCompletionListener { onizlemeDurdur(); if (onizleme == s.id) onizleme = null }
            p.setOnErrorListener { _, _, _ -> onizlemeDurdur(); onizleme = null; true }
            p.prepareAsync()
        } catch (e: Exception) {
            onizleme = null
            UygulamaLog.kaydet(ctx, "ElevenLabs önizleme", e)
            hata = "Önizleme: ${UygulamaLog.kullaniciMesaji(e)}"
        }
    }

    fun elevenCek() {
        val k = ayarlar.elevenKey
        if (k.isBlank()) {
            elevenMesaj = "Önce ElevenLabs API anahtarını gir."
            return
        }
        elevenYukleniyor = true
        elevenMesaj = null
        scope.launch {
            val h = mutableListOf<String>()
            runCatching { Ses.elevenSesler(k) }.onSuccess { elevenSesler = it }.onFailure { h.add("Sesler: ${it.message}") }
            runCatching { Ses.elevenModeller(k) }.onSuccess { elevenModeller = it }.onFailure { h.add("Modeller: ${it.message}") }
            runCatching { Ses.elevenKredi(k) }.onSuccess { elevenKredi = it }.onFailure { h.add("Kredi: ${it.message}") }
            elevenMesaj = if (h.isEmpty()) null else h.joinToString("\n")
            elevenYukleniyor = false
        }
    }

    fun elevenTemizle() {
        ayarlar.elevenKey = ""
        elevenSesler = emptyList()
        elevenModeller = emptyList()
        elevenKredi = null
        elevenMesaj = null
    }

    // ───────────── Mikrofon testi ve uyandırma öğrenme ─────────────

    private fun testModuGuncelle() { dinleyici?.testModu = testAktif || ogrenKelime != null }

    fun testDegistir() {
        testAktif = !testAktif
        if (testAktif && !mikAcik) {
            testIcinAcildi = true
            mikrofonAc()
        }
        testModuGuncelle()
    }

    fun testiBitir() {
        testAktif = false
        ogrenKelime = null
        testModuGuncelle()
        if (testIcinAcildi) {
            testIcinAcildi = false
            mikrofonKapat()
        }
    }

    fun ogrenBaslat(w: String) {
        if (ogrenKelime != null) return
        ogrenKelime = w
        ogrenOrnekler.clear()
        ogrenMesaj = "“$w” de, yarım saniye bekle — 0 / $OGREN_HEDEF"
        ogrenCanli = "Sessiz bir ortamda sadece kelimeyi söyleyin."
        if (!mikAcik) {
            testIcinAcildi = true
            mikrofonAc()
        }
        testModuGuncelle()
    }

    fun ogrenSifirla(w: String) {
        ayarlar.ogrenilen(w, emptyList())
        ogrenSurum++
    }

    fun ogrenBitir(iptal: Boolean) {
        val w = ogrenKelime ?: return
        val say = ogrenOrnekler.groupingBy { it }.eachCount()
        ogrenKelime = null
        testModuGuncelle()
        val yeni = if (iptal) emptyList() else say.filter { (t, c) ->
            c >= 2 && Uyandirma.varyasyonUygun(t) && t != w && t !in Uyandirma.TOHUM[w].orEmpty()
        }.keys.toList()
        if (yeni.isNotEmpty()) {
            ayarlar.ogrenilen(w, (ayarlar.ogrenilen(w) + yeni).toSortedSet().take(40))
            ogrenMesaj = "Öğrenildi: " + yeni.joinToString(", ")
        } else {
            ogrenMesaj = if (iptal) "İptal edildi." else "Yeterince tutarlı örnek çıkmadı; tekrar deneyin."
        }
        ogrenCanli = ""
        ogrenSurum++
        scope.launch {
            delay(4000)
            if (ogrenKelime == null) ogrenMesaj = null
        }
        if (testIcinAcildi && !testAktif) {
            testIcinAcildi = false
            mikrofonKapat()
        }
    }

    override fun testEn(metin: String, kesin: Boolean) {
        if (testAktif && metin.isNotEmpty()) {
            testEnMetni = metin + if (Uyandirma.ara(metin, false) != null) "   ✓ uyandırma kelimesi" else ""
        }
        val w = ogrenKelime ?: return
        if (!kesin) {
            ogrenCanli = "Duyuyorum: $metin"
            return
        }
        if (metin.isNotEmpty() && metin.split(" ").size <= 4) ogrenOrnekler.add(metin)
        val n = ogrenOrnekler.size
        ogrenMesaj = "“$w” de, yarım saniye bekle — $n / $OGREN_HEDEF"
        ogrenCanli = "Duyulan: " + ogrenOrnekler.joinToString(", ")
        if (n >= OGREN_HEDEF) ogrenBitir(false)
    }

    override fun testTr(metin: String, kesin: Boolean) {
        if (testAktif && metin.isNotEmpty()) testTrMetni = metin
    }

    // ───────────── MCP ─────────────

    suspend fun mcpBaglan(b: Baglanti) {
        mcpDurum[b.id] = "Bağlanıyor…"
        try {
            val i = McpIstemci(b.url, b.baslik)
            val n = i.baglan()
            mcp[b.id] = i
            mcpDurum[b.id] = "Bağlı · $n araç"
        } catch (e: Exception) {
            mcp.remove(b.id)
            UygulamaLog.kaydet(ctx, "MCP/${b.ad}", e)
            mcpDurum[b.id] = UygulamaLog.kullaniciMesaji(e)
        }
    }

    suspend fun mcpDene(url: String, baslik: String): Int = try {
        McpIstemci(url, baslik).baglan()
    } catch (e: Exception) {
        UygulamaLog.kaydet(ctx, "MCP bağlantı testi", e)
        throw IllegalStateException(UygulamaLog.kullaniciMesaji(e), e)
    }

    fun mcpSil(b: Baglanti) {
        mcp.remove(b.id)
        mcpDurum.remove(b.id)
        mcpSon.remove(b.id)
    }

    fun mcpAraclarListesi(b: Baglanti): List<McpArac> = mcp[b.id]?.araclar.orEmpty()

    private var mcpIs: Job? = null
    private val mcpSon = mutableMapOf<String, Long>()

    /** Açık bağlantıları arka planda kurar/tazeler: bağlı olanı 10 dk'da bir, düşmüş olanı 20 sn'de bir dener. */
    private fun mcpTazele() {
        if (mcpIs?.isActive == true) return
        mcpIs = scope.launch {
            for (b in ayarlar.baglantilar) {
                if (!b.acik) continue
                val yas = System.currentTimeMillis() - (mcpSon[b.id] ?: 0L)
                val limit = if (mcp.containsKey(b.id)) 10 * 60_000L else 20_000L
                if (yas < limit) continue
                mcpSon[b.id] = System.currentTimeMillis()
                mcpBaglan(b)
            }
        }
    }

    private suspend fun mcpBekle() {
        mcpTazele()
        withTimeoutOrNull(10_000) { mcpIs?.join() }
    }

    private fun mcpAraclari(): Pair<List<GeminiArac>, Map<String, Triple<McpIstemci, String, Baglanti>>> {
        val liste = mutableListOf<GeminiArac>()
        val esle = mutableMapOf<String, Triple<McpIstemci, String, Baglanti>>()
        ayarlar.baglantilar.forEachIndexed { i, b ->
            if (!b.acik) return@forEachIndexed
            val ist = mcp[b.id] ?: return@forEachIndexed
            for (a in ist.araclar) {
                if (a.ad in b.kapaliAraclar) continue
                val ad = ("m${i}_" + a.ad.replace(Regex("[^A-Za-z0-9_]"), "_")).take(64)
                liste.add(GeminiArac(ad, a.aciklama, a.sema))
                esle[ad] = Triple(ist, a.ad, b)
            }
        }
        return liste to esle
    }

    suspend fun onayIste(soru: String): Boolean {
        val d = CompletableDeferred<Boolean>()
        onay = OnayIstegi(soru, d)
        if (!onde) uygulamayiAc()
        val sonuc = withTimeoutOrNull(60_000) { d.await() } ?: false
        onay = null
        return sonuc
    }

    fun onayCevap(evet: Boolean) { onay?.sonuc?.complete(evet) }

    // ───────────── Vosk modelleri ─────────────

    private fun modelHazirMi(ad: String) = File(File(File(ctx.filesDir, "vosk"), ad), ".hazir").exists()

    fun modelleriYenidenIndir() {
        if (mikAcik) mikrofonKapat()
        modelTr = null
        modelEn = null
        File(ctx.filesDir, "vosk").deleteRecursively()
        izinYenile()
        scope.launch {
            modelleriHazirla()
            izinYenile()
        }
    }

    fun modelleriIndir() {
        scope.launch {
            modelleriHazirla()
            izinYenile()
        }
    }

    /** Uygulama her açıldığında: modeller yoksa indirir, varsa belleğe arka planda yükler. */
    fun acilistaYukle() {
        if (!::ctx.isInitialized || (modelTr != null && modelEn != null)) return
        scope.launch {
            modelleriHazirla()
            izinYenile()
        }
    }

    /** İlk kurulum ekranındaki "Başla". */
    fun kurulumBitti() {
        ayarlar.kurulumTamam = true
        kurulumGoster = false
        if (izinMikrofon && onde && modellerHazir && !mikAcik) mikrofonAc()
    }

    // Tek ilerleme çubuğu: iki modelin boyutuna göre ağırlıklı (TR ≈ 35 MB, EN ≈ 40 MB); son %5 belleğe yükleme.
    private const val AG_TR = 0.45f
    private const val AG_EN = 0.50f

    private suspend fun modelleriHazirla(): Boolean = modelKilit.withLock {
        if (modelTr != null && modelEn != null) return@withLock true
        try {
            kurulumOran = 0f
            withContext(Dispatchers.IO) {
                val tr = modelDizini(TR_MODEL, "Türkçe", 0f, AG_TR)
                val en = modelDizini(EN_MODEL, "İngilizce", AG_TR, AG_EN)
                indirme = "Modeller belleğe yükleniyor…"
                kurulumOran = AG_TR + AG_EN
                if (modelTr == null) modelTr = Model(tr)
                if (modelEn == null) modelEn = Model(en)
            }
            kurulumOran = 1f
            indirme = null
            modellerHazir = true
            true
        } catch (e: Exception) {
            indirme = null
            UygulamaLog.kaydet(ctx, "Ses modeli", e)
            hata = "Ses modeli hazırlanamadı: ${UygulamaLog.kullaniciMesaji(e)}"
            false
        }
    }

    private fun modelDizini(ad: String, etiket: String, taban: Float, agirlik: Float): String {
        val hedef = File(File(ctx.filesDir, "vosk"), ad)
        if (File(hedef, ".hazir").exists()) {
            kurulumOran = taban + agirlik
            return hedef.absolutePath
        }
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
                            if (y != sonYuzde) {
                                sonYuzde = y
                                indirme = "$etiket model indiriliyor %$y"
                                kurulumOran = taban + agirlik * 0.85f * y / 100f
                            }
                        }
                    }
                }
            }
        } finally {
            bag.disconnect()
        }

        indirme = "$etiket model açılıyor…"
        kurulumOran = taban + agirlik * 0.9f
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
        kurulumOran = taban + agirlik
        return hedef.absolutePath
    }
}
