package com.edith.asistan

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.sqrt

/** Dinleyicinin ana iş parçacığına bildirimleri (hepsi ana iş parçacığında çağrılır). */
interface DinleyiciGeri {
    fun uyandi(kes: Boolean)
    fun takipBasladi()
    fun canli(metin: String)
    fun komut(metin: String)
    fun komutBos()
    fun seviye(v: Float)
    fun testEn(metin: String, kesin: Boolean)
    fun testTr(metin: String, kesin: Boolean)
    fun hata(mesaj: String)
}

/**
 * PC sürümündeki VoskDinleyici'nin karşılığı. Tek bir mikrofon akışı; bekleme sırasında yalnızca İngilizce model,
 * uyandırma sonrası Türkçe model (son ~5 sn'lik ses halkadan Türkçe tanıyıcıya geri beslenir).
 */
class Dinleyici(
    private val trModel: Model,
    private val enModel: Model,
    private val geri: DinleyiciGeri
) : Thread("edith-dinleyici") {

    companion object {
        const val SR = 16000
        const val BLOK = 2000          // örnek sayısı (125 ms)
        const val STABIL = 3
        const val UYANMA_ASIMI = 8.0
    }

    private val ana = Handler(Looper.getMainLooper())
    @Volatile var calisiyor = true
    @Volatile var testModu = false
    @Volatile var kendiWake = false     // asistanın okuduğu metinde "edit" geçiyor mu
    private val takipIstegi = AtomicBoolean(false)

    private val halka = ArrayDeque<Pair<Int, ByteArray>>()
    private var no = 0
    private var enR: Recognizer? = null
    private var trR: Recognizer? = null
    private var oz: Pair<Recognizer, Recognizer>? = null
    private var komut = false
    private var kesinti = false
    private var devre = false
    private var say = 0
    private var ilk = 0
    private var biriken = ""
    private var gor = ""
    private var sonSoz = 0.0
    private var bas = 0.0
    private var asim = UYANMA_ASIMI

    fun takipIste() = takipIstegi.set(true)

    private fun post(f: () -> Unit) { ana.post { if (calisiyor) f() } }
    private fun simdi() = System.nanoTime() / 1e9

    private fun yeniEn() = Recognizer(enModel, SR.toFloat())
    private fun yeniTr() = Recognizer(trModel, SR.toFloat())

    override fun run() {
        val minBoyut = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, SR, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBoyut, BLOK * 8)
            )
        } catch (e: Exception) {
            ana.post { geri.hata("Mikrofon açılamadı: ${e.message}") }
            return
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            ana.post { geri.hata("Mikrofon başlatılamadı (izin ya da başka uygulama kullanıyor olabilir).") }
            return
        }
        try {
            rec.startRecording()
            val tampon = ByteArray(BLOK * 2)
            while (calisiyor) {
                var o = 0
                while (o < tampon.size && calisiyor) {
                    val n = rec.read(tampon, o, tampon.size - o)
                    if (n < 0) {
                        ana.post { geri.hata("Mikrofon okunamadı (kod $n).") }
                        calisiyor = false
                        break
                    }
                    o += n
                }
                if (o == tampon.size && calisiyor) {
                    try {
                        adim(tampon.copyOf())
                    } catch (e: Exception) {
                        ana.post { geri.hata("Dinleyici hatası: ${e.message}") }
                    }
                }
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
            sifirla()
            oz?.let { runCatching { it.first.close(); it.second.close() } }
            oz = null
        }
    }

    private fun sifirla() {
        runCatching { enR?.close() }
        runCatching { trR?.close() }
        enR = null
        trR = null
        komut = false
        say = 0
    }

    private fun oku(r: Recognizer, son: Boolean): String = try {
        if (son) JSONObject(r.result).optString("text") else JSONObject(r.partialResult).optString("partial")
    } catch (e: Exception) {
        ""
    }

    private fun adim(veri: ByteArray) {
        no++
        halka.addLast(no to veri)
        if (halka.size > 40) halka.removeFirst()
        seviye(veri)

        if (testModu) return ozel(veri)
        oz?.let {
            runCatching { it.first.close(); it.second.close() }
            oz = null
            sifirla()
        }
        val faz = Motor.faz
        val mesgul = faz == Faz.ISLE || faz == Faz.KONUS
        if (devre) {
            if (faz == Faz.KOMUT) return sifirla()
            devre = false
        }
        if (!mesgul) kesinti = false
        if (mesgul && !kesinti) {
            if (!(faz == Faz.KONUS && Motor.ayarlar.sesleKes)) return sifirla()
            return bekle(veri, Motor.ayarlar.hoparlorModu || kendiWake, true)
        }
        if (komut) return komutAdimi(veri)
        if (takipIstegi.compareAndSet(true, false)) {
            post { geri.takipBasladi() }
            halka.clear()
            return komuta(0, Motor.ayarlar.takipZamanAsimi.toDouble())
        }
        bekle(veri, false, false)
    }

    private fun seviye(veri: ByteArray) {
        var s = 0.0
        val n = veri.size / 2
        for (i in 0 until n) {
            val v = ((veri[2 * i + 1].toInt() shl 8) or (veri[2 * i].toInt() and 0xFF)).toShort().toInt()
            s += v.toDouble() * v
        }
        val d = min(1f, (sqrt(s / n) / 4000.0).toFloat())
        post { geri.seviye(d) }
    }

    /** Mikrofon testi ve uyandırma öğrenme: iki tanıyıcı da ne duyduğunu gösterir. */
    private fun ozel(veri: ByteArray) {
        val (t, e) = oz ?: Pair(yeniTr(), yeniEn()).also { oz = it }
        val sT = t.acceptWaveForm(veri, veri.size)
        val mT = oku(t, sT)
        if (mT.isNotEmpty() || sT) post { geri.testTr(mT, sT) }
        val sE = e.acceptWaveForm(veri, veri.size)
        val mE = oku(e, sE)
        if (mE.isNotEmpty() || sE) post { geri.testEn(mE, sE) }
    }

    private fun bekle(veri: ByteArray, siki: Boolean, kes: Boolean) {
        if (enR == null) {
            enR = yeniEn()
            say = 0
        }
        val r = enR ?: return
        val son = r.acceptWaveForm(veri, veri.size)
        val m = oku(r, son)
        val tetik: Boolean
        val ilkNo: Int
        if (siki) { // hoparlör: yalnızca biten, kısa ve temiz "hey edit/edit" kabul edilir
            tetik = son && Uyandirma.tam(m)
            ilkNo = no
        } else {
            if (Uyandirma.ara(m, Motor.ayarlar.cumleBasi) != null) {
                ilk = if (say > 0) ilk else no
                say++
                tetik = son || say >= STABIL
            } else {
                say = 0
                tetik = false
            }
            ilkNo = ilk
        }
        if (tetik) {
            if (kes) kesinti = true
            post { geri.uyandi(kes) }
            komuta(ilkNo, UYANMA_ASIMI)
        }
    }

    private fun komuta(ilkNo: Int, asimSn: Double) {
        runCatching { trR?.close() }
        runCatching { enR?.close() }
        trR = yeniTr()
        enR = null
        say = 0
        komut = true
        biriken = ""
        gor = ""
        sonSoz = simdi()
        bas = sonSoz
        asim = asimSn
        val tr = trR ?: return
        for ((n, b) in halka.toList()) {
            if (n >= ilkNo && tr.acceptWaveForm(b, b.size)) biriken = (biriken + " " + oku(tr, true)).trim()
        }
        post { geri.canli("") }
    }

    private fun komutAdimi(veri: ByteArray) {
        val r = trR ?: return
        val son = r.acceptWaveForm(veri, veri.size)
        val m = oku(r, son)
        val ham: String
        if (son) {
            biriken = (biriken + " " + m).trim()
            ham = biriken
        } else {
            ham = (biriken + " " + m).trim()
        }
        val g = Uyandirma.basTemizle(ham)
        val t = simdi()
        if (g != gor) {
            gor = g
            sonSoz = t
            post { geri.canli(g) }
        }
        if (g.isNotEmpty() && t - sonSoz > Motor.ayarlar.sessizlikSuresi) {
            val kalan = if (son) "" else runCatching { JSONObject(r.finalResult).optString("text") }.getOrDefault("")
            val k = Uyandirma.basTemizle((biriken + " " + kalan).trim())
            sifirla()
            devre = true
            if (k.isNotEmpty()) post { geri.komut(k) } else post { geri.komutBos() }
        } else if (g.isEmpty() && t - bas > asim) {
            sifirla()
            post { geri.komutBos() }
        }
    }
}
