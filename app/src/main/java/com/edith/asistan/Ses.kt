package com.edith.asistan

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
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
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** Seslendirme: önce Gemini TTS (tek deneme), olmazsa ElevenLabs. Çıktı 24 kHz 16-bit mono PCM. */
object Ses {
    const val GEMINI_TTS_MODEL = "gemini-3.1-flash-tts-preview"
    private const val HIZ = 24000

    val geminiSesleri = listOf(
        "Zephyr", "Puck", "Charon", "Kore", "Fenrir", "Leda", "Orus", "Aoede", "Callirrhoe", "Autonoe",
        "Enceladus", "Iapetus", "Umbriel", "Algieba", "Despina", "Erinome", "Algenib", "Rasalgethi",
        "Laomedeia", "Achernar", "Alnilam", "Schedar", "Gacrux", "Pulcherrima", "Achird",
        "Zubenelgenubi", "Vindemiatrix", "Sadachbia", "Sadaltager", "Sulafat"
    )

    private val http = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    private val kodBlogu = Regex("```[\\s\\S]*?```")
    private val baglantiRe = Regex("https?://\\S+")

    @Volatile private var track: AudioTrack? = null
    @Volatile private var iptal = false

    /** Kod bloklarını, bağlantıları ve biçim işaretlerini atar; geriye okunacak düz metin kalır. */
    fun okunacak(metin: String): String = metin
        .replace(kodBlogu, " ")
        .replace(baglantiRe, " ")
        .replace(Regex("[*_#`>|~]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(2500)

    /** Ayarlardaki sıraya göre sentezler: Gemini → ElevenLabs. İkisi de olmazsa istisna fırlatır. */
    suspend fun sentez(a: Ayarlar, metin: String): ByteArray? {
        val temiz = okunacak(metin)
        if (temiz.isEmpty()) return null
        var hata = ""
        if (a.apiKey.isNotBlank()) {
            try { return geminiSentez(a.apiKey, a.geminiSes, temiz) } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                hata = "Gemini ses: ${e.message}"
            }
        }
        if (a.elevenKey.isNotBlank() && a.elevenSes.isNotBlank()) {
            try { return elevenSentez(a.elevenKey, a.elevenSes, a.elevenModel, temiz) } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                hata += " | ElevenLabs: ${e.message}"
            }
        }
        throw RuntimeException(hata.ifBlank { "Ses için Gemini anahtarı ya da ElevenLabs anahtarı + ses seçimi gerekli." })
    }

    suspend fun geminiSentez(key: String, ses: String, metin: String): ByteArray = withContext(Dispatchers.IO) {
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
            if (!r.isSuccessful) throw RuntimeException(hata(r.code, s))
            val veri = JSONObject(s).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                .getJSONObject("inlineData").getString("data")
            Base64.decode(veri, Base64.DEFAULT)
        }
    }

    suspend fun elevenSentez(key: String, ses: String, model: String, metin: String): ByteArray =
        withContext(Dispatchers.IO) {
            val govde = JSONObject().put("text", metin).put("model_id", model.ifBlank { "eleven_multilingual_v2" })
            val istek = Request.Builder()
                .url("https://api.elevenlabs.io/v1/text-to-speech/$ses?output_format=pcm_24000")
                .header("xi-api-key", key)
                .post(govde.toString().toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(istek).execute().use { r ->
                if (!r.isSuccessful) throw RuntimeException(hata(r.code, r.body?.string().orEmpty()))
                r.body!!.bytes()
            }
        }

    /** ElevenLabs hesabındaki sesler: (id, ad). Anahtarda "voices read" izni gerekir. */
    suspend fun elevenSesler(key: String): List<Pair<String, String>> = liste(key, "voices", "voices", "voice_id", "name")

    /** ElevenLabs modelleri: (id, ad). Yalnızca metinden sese çevirebilenler. */
    suspend fun elevenModeller(key: String): List<Pair<String, String>> =
        liste(key, "models", null, "model_id", "name") { it.optBoolean("can_do_text_to_speech", true) }

    suspend fun elevenKredi(key: String): String = withContext(Dispatchers.IO) {
        val istek = Request.Builder().url("https://api.elevenlabs.io/v1/user/subscription").header("xi-api-key", key).build()
        http.newCall(istek).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw RuntimeException(hata(r.code, s))
            val o = JSONObject(s)
            val kalan = o.optLong("character_limit") - o.optLong("character_count")
            "Kalan: $kalan / ${o.optLong("character_limit")} karakter"
        }
    }

    private suspend fun liste(
        key: String, yol: String, kok: String?, idAlani: String, adAlani: String,
        sinama: (JSONObject) -> Boolean = { true }
    ): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val istek = Request.Builder().url("https://api.elevenlabs.io/v1/$yol").header("xi-api-key", key).build()
        http.newCall(istek).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw RuntimeException(hata(r.code, s))
            val dizi = if (kok != null) JSONObject(s).getJSONArray(kok) else JSONArray(s)
            (0 until dizi.length()).map { dizi.getJSONObject(it) }.filter(sinama)
                .map { it.getString(idAlani) to it.optString(adAlani, it.getString(idAlani)) }
        }
    }

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
