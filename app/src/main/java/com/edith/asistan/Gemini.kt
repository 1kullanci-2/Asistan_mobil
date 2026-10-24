package com.edith.asistan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class Mesaj(val kullanici: Boolean, val metin: String)
data class Cevap(val metin: String, val soru: Boolean)

object Gemini {
    private const val HIZLI = "gemini-flash-lite-latest"
    private const val KALITELI = "gemini-flash-latest"
    private val BASIT = listOf("saat", "hava durumu", "merhaba", "selam", "nasılsın", "teşekkür", "günaydın")
    private const val SISTEM =
        "Sen E.D.I.T.H. adında bir sesli asistansın; kullanıcının diliyle cevap ver. Kod veya dosya içeriğini " +
            "her zaman ``` işaretli bloklar içinde ver. Kullanıcıdan bir cevap, onay ya da seçim bekliyorsan " +
            "(ör. 'bunu yapayım mı?') cevabının EN SONUNA tek başına [SORU] yaz; beklemiyorsan yazma."

    private val istemci = OkHttpClient.Builder().callTimeout(90, TimeUnit.SECONDS).build()
    private val soruIsareti = Regex("""\[\s*SORU\s*]""", RegexOption.IGNORE_CASE)
    private val soruSonu = Regex("""\?[\s"'”)»*_]*$""")
    private val kodBlogu = Regex("```[\\s\\S]*?```")

    suspend fun sor(apiKey: String, gecmis: List<Mesaj>, mesaj: String): Cevap = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Gemini API anahtarı girilmemiş (Ayarlar'dan ekleyin)." }
        val basit = BASIT.any { mesaj.lowercase().contains(it) } || mesaj.trim().split(" ").size <= 6
        val model = if (basit) HIZLI else KALITELI

        val icerik = JSONArray()
        (gecmis + Mesaj(true, mesaj)).forEach {
            icerik.put(
                JSONObject()
                    .put("role", if (it.kullanici) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", it.metin)))
            )
        }
        val govde = JSONObject()
            .put("contents", icerik)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SISTEM))))
            .toString()

        var son = "Sunucudan cevap alınamadı."
        for (deneme in 0 until 3) {
            val istek = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                .header("x-goog-api-key", apiKey)
                .post(govde.toRequestBody("application/json".toMediaType()))
                .build()
            try {
                istemci.newCall(istek).execute().use { r ->
                    val s = r.body?.string().orEmpty()
                    if (r.isSuccessful) return@withContext ayristir(s)
                    son = hataMetni(r.code, s)
                    if (r.code != 503 && r.code != 429) throw RuntimeException(son)
                }
            } catch (e: IOException) {
                son = "Bağlantı hatası: ${e.message}"
            }
            if (deneme < 2) delay(1200L * (deneme + 1))
        }
        throw RuntimeException(son)
    }

    private fun ayristir(s: String): Cevap {
        val parts = JSONObject(s).getJSONArray("candidates").getJSONObject(0)
            .optJSONObject("content")?.optJSONArray("parts") ?: return Cevap("(Boş cevap geldi)", false)
        val ham = buildString {
            for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text"))
        }.trim()
        if (ham.isEmpty()) return Cevap("(Boş cevap geldi)", false)
        val soru = soruIsareti.containsMatchIn(ham) ||
            soruSonu.containsMatchIn(ham.replace(kodBlogu, "").trim())
        return Cevap(ham.replace(soruIsareti, "").trim(), soru)
    }

    private fun hataMetni(kod: Int, s: String): String {
        val m = runCatching { JSONObject(s).getJSONObject("error").getString("message") }.getOrDefault(s)
        return "HTTP $kod: ${m.replace("\n", " ").take(160)}"
    }
}
