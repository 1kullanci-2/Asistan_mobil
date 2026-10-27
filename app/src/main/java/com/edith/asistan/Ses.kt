package com.edith.asistan

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class TtsHata(mesaj: String, val bekleme: Int = 0) : RuntimeException(mesaj)

data class GeminiSes(val ad: String, val tarif: String)
data class ElevenSes(val id: String, val ad: String, val aciklama: String, val onizleme: String)
data class ElevenModel(val id: String, val ad: String, val aciklama: String, val turkce: Boolean)
data class ElevenKredi(val kalan: Long, val limit: Long, val yenilenme: String)

/** Seslendirme: ayarlardaki sıraya göre Gemini TTS / ElevenLabs. Çıktı 24 kHz 16-bit mono PCM. */
object Ses {
    const val GEMINI_TTS_MODEL = "gemini-3.1-flash-tts-preview"
    private const val HIZ = 24000
    const val DENEME_CUMLESI = "Merhaba, ben E.D.I.T.H. Sesim böyle."

    val geminiSesleri = listOf(
        GeminiSes("Zephyr", "Parlak"), GeminiSes("Puck", "Neşeli"), GeminiSes("Charon", "Bilgilendirici"),
        GeminiSes("Kore", "Kararlı"), GeminiSes("Fenrir", "Heyecanlı"), GeminiSes("Leda", "Genç"),
        GeminiSes("Orus", "Kararlı"), GeminiSes("Aoede", "Havadar"), GeminiSes("Callirrhoe", "Rahat"),
        GeminiSes("Autonoe", "Parlak"), GeminiSes("Enceladus", "Nefesli"), GeminiSes("Iapetus", "Berrak"),
        GeminiSes("Umbriel", "Rahat"), GeminiSes("Algieba", "Yumuşak"), GeminiSes("Despina", "Yumuşak"),
        GeminiSes("Erinome", "Berrak"), GeminiSes("Algenib", "Hırıltılı"), GeminiSes("Rasalgethi", "Bilgilendirici"),
        GeminiSes("Laomedeia", "Neşeli"), GeminiSes("Achernar", "Yumuşak"), GeminiSes("Alnilam", "Kararlı"),
        GeminiSes("Schedar", "Dengeli"), GeminiSes("Gacrux", "Olgun"), GeminiSes("Pulcherrima", "Atılgan"),
        GeminiSes("Achird", "Samimi"), GeminiSes("Zubenelgenubi", "Gündelik"), GeminiSes("Vindemiatrix", "Nazik"),
        GeminiSes("Sadachbia", "Canlı"), GeminiSes("Sadaltager", "Bilgili"), GeminiSes("Sulafat", "Sıcak")
    )

    /** "ok" | "hata" — motor durum etiketleri için. */
    val durum = mutableStateMapOf<String, String>()
    /** Hata veren motorun yeniden deneneceği an (ms). */
    val cooldown = mutableStateMapOf<String, Long>()

    private val http = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    private val kodBlogu = Regex("```[\\s\\S]*?```")
    private val baglantiRe = Regex("https?://\\S+")

    @Volatile private var track: AudioTrack? = null
    @Volatile private var iptal = false

    /** Kod bloklarını, bağlantıları ve biçim işaretlerini atar; geriye okunacak düz metin kalır. */
    fun okunacak(metin: String): String = metin
        .replace(kodBlogu, " İlgili kod bloğunu ekledim. ")
        .replace(baglantiRe, " ")
        .replace(Regex("[*_#`>|~]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(2500)

    fun hazir(a: Ayarlar, m: String) = when (m) {
        "gemini" -> a.apiKey.isNotBlank()
        else -> a.elevenKey.isNotBlank() && a.elevenSes.isNotBlank()
    }

    /** Bekleme süresini yok sayar, durumu kaydeder; hata olursa fırlatır. */
    suspend fun motorDene(a: Ayarlar, m: String, metin: String): ByteArray {
        try {
            val d = if (m == "gemini") geminiSentez(a.apiKey, a.geminiSes, metin) else elevenSentez(a, metin)
            durum[m] = "ok"
            cooldown.remove(m)
            return d
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            durum[m] = "hata"
            if (e is TtsHata && e.bekleme > 0) cooldown[m] = System.currentTimeMillis() + e.bekleme * 1000L
            throw e
        }
    }

    /** Sırayla dene ayarına göre sentezler. Hiçbiri olmazsa nedenleriyle birlikte istisna fırlatır; boş metinde null. */
    suspend fun sentez(a: Ayarlar, metin: String): ByteArray? {
        val temiz = okunacak(metin)
        if (temiz.isEmpty()) return null
        val sira = a.motorSirasi.filter { a.motorAktif(it) && hazir(a, it) }
        if (sira.isEmpty()) throw RuntimeException("Açık ve hazır bir ses motoru yok (Ayarlar → Ses).")
        val notlar = mutableListOf<String>()
        for (m in if (a.siraylaDene) sira else sira.take(1)) {
            val kalan = (cooldown[m] ?: 0L) - System.currentTimeMillis()
            if (kalan > 0) {
                notlar.add("${Ayarlar.MOTOR_AD[m]}: ${kalan / 1000 + 1} sn beklemede")
                continue
            }
            try {
                return motorDene(a, m, temiz)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notlar.add("${Ayarlar.MOTOR_AD[m]}: ${e.message?.take(160)}")
            }
        }
        throw RuntimeException(notlar.joinToString(" | "))
    }

    suspend fun geminiSentez(key: String, ses: String, metin: String): ByteArray = withContext(Dispatchers.IO) {
        if (key.isBlank()) throw TtsHata("Gemini API anahtarı girilmemiş.")
        val govde = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", metin)))))
            .put(
                "generationConfig", JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put(
                        "speechConfig", JSONObject().put(
                            "voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", ses))
                        )
                    )
            )
        val istek = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_TTS_MODEL:generateContent")
            .header("x-goog-api-key", key)
            .post(govde.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(istek).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw TtsHata(hata(r.code, s), if (r.code == 429) 60 else 0)
            val veri = JSONObject(s).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                .getJSONObject("inlineData").getString("data")
            Base64.decode(veri, Base64.DEFAULT)
        }
    }

    suspend fun elevenSentez(a: Ayarlar, metin: String): ByteArray = withContext(Dispatchers.IO) {
        if (a.elevenKey.isBlank() || a.elevenSes.isBlank()) throw TtsHata("ElevenLabs anahtarı ya da sesi seçilmemiş.")
        val govde = JSONObject().put("text", metin).put("model_id", a.elevenModel)
            .put(
                "voice_settings", JSONObject()
                    .put("stability", a.elevenKararlilik.toDouble())
                    .put("similarity_boost", a.elevenBenzerlik.toDouble())
            )
        val istek = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/${a.elevenSes}?output_format=pcm_24000")
            .header("xi-api-key", a.elevenKey)
            .post(govde.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(istek).execute().use { r ->
            if (!r.isSuccessful) {
                throw TtsHata(hata(r.code, r.body?.string().orEmpty()), if (r.code == 429) 60 else 0)
            }
            r.body!!.bytes()
        }
    }

    /** Gemini ses önizlemesi: ilk üretimde telefona kaydedilir, sonraki dokunuşlarda kota harcanmaz. */
    suspend fun geminiOnizleme(ctx: Context, key: String, ses: String): ByteArray = withContext(Dispatchers.IO) {
        val dosya = File(File(ctx.cacheDir, "onizleme").apply { mkdirs() }, "$ses.pcm")
        if (dosya.exists() && dosya.length() > 0) return@withContext dosya.readBytes()
        val pcm = geminiSentez(key, ses, DENEME_CUMLESI)
        dosya.writeBytes(pcm)
        pcm
    }

    // ── ElevenLabs hesap verileri ──

    suspend fun elevenSesler(key: String): List<ElevenSes> = withContext(Dispatchers.IO) {
        val dizi = JSONObject(elevenGet(key, "voices")).getJSONArray("voices")
        (0 until dizi.length()).map { dizi.getJSONObject(it) }.map { o ->
            val e = o.optJSONObject("labels")
            val etiket = listOf("gender", "age", "accent", "descriptive", "use_case")
                .mapNotNull { k -> e?.optString(k)?.takeIf { it.isNotBlank() && it != "null" } }
            ElevenSes(
                o.getString("voice_id"), o.optString("name", "Ses"),
                etiket.joinToString(" · ").ifBlank { o.optString("category") },
                o.optString("preview_url").takeIf { it != "null" }.orEmpty()
            )
        }
    }

    suspend fun elevenModeller(key: String): List<ElevenModel> = withContext(Dispatchers.IO) {
        val dizi = JSONArray(elevenGet(key, "models"))
        (0 until dizi.length()).map { dizi.getJSONObject(it) }.filter { it.optBoolean("can_do_text_to_speech", true) }
            .map { o ->
                val diller = o.optJSONArray("languages") ?: JSONArray()
                val tr = (0 until diller.length()).any { diller.getJSONObject(it).optString("language_id") == "tr" }
                ElevenModel(o.getString("model_id"), o.optString("name", o.getString("model_id")), o.optString("description"), tr)
            }
    }

    suspend fun elevenKredi(key: String): ElevenKredi = withContext(Dispatchers.IO) {
        val o = JSONObject(elevenGet(key, "user/subscription"))
        val sn = o.optLong("next_character_count_reset_unix", 0L)
        val yenile = if (sn > 0) SimpleDateFormat("d MMM HH:mm", Locale("tr", "TR")).format(Date(sn * 1000)) else "—"
        ElevenKredi(o.optLong("character_limit") - o.optLong("character_count"), o.optLong("character_limit"), yenile)
    }

    private fun elevenGet(key: String, yol: String): String {
        val istek = Request.Builder().url("https://api.elevenlabs.io/v1/$yol").header("xi-api-key", key.trim()).build()
        http.newCall(istek).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw RuntimeException(hata(r.code, s) + " (anahtarda bu bilgiyi okuma izni gerekir)")
            return s
        }
    }

    // ── Oynatma ──

    /** PCM'i çalar; bitene ya da kes() çağrılana kadar bekler. */
    suspend fun cal(pcm: ByteArray) = withContext(Dispatchers.IO) {
        iptal = false
        val tampon = maxOf(AudioTrack.getMinBufferSize(HIZ, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), 8192)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(HIZ).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setBufferSizeInBytes(tampon)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        try {
            t.play()
            var i = 0
            while (i < pcm.size && !iptal && coroutineContext.isActive) {
                val n = minOf(4096, pcm.size - i)
                val yazilan = t.write(pcm, i, n)
                if (yazilan <= 0) break
                i += yazilan
            }
            val toplamKare = pcm.size / 2
            while (!iptal && coroutineContext.isActive && t.playbackHeadPosition < toplamKare) delay(50)
        } finally {
            runCatching { t.stop() }
            t.release()
            if (track === t) track = null
        }
    }

    fun kes() {
        iptal = true
        runCatching { track?.pause(); track?.flush() }
    }

    private fun hata(kod: Int, s: String): String {
        val m = runCatching {
            val o = JSONObject(s)
            o.optJSONObject("error")?.optString("message")
                ?: o.optJSONObject("detail")?.optString("message")
                ?: o.optString("detail")
        }.getOrNull().orEmpty().ifBlank { s }
        return "HTTP $kod: ${m.replace("\n", " ").take(140)}"
    }
}
