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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class Mesaj(val kullanici: Boolean, val metin: String)
data class Cevap(val metin: String, val soru: Boolean)
data class GeminiArac(val ad: String, val aciklama: String, val sema: JSONObject?)

object Gemini {
    private val BASIT = listOf("saat", "hava durumu", "merhaba", "selam", "nasılsın", "teşekkür", "günaydın")

    private fun sistem(): String {
        val an = SimpleDateFormat("d MMMM yyyy EEEE HH:mm", Locale("tr", "TR")).format(Date())
        return "Adın E.D.I.T.H. Kullanıcının telefonundaki sesli asistansın; yalnızca adın bu. " +
            "Kurgusal bir kimliğe bürünme: Stark Industries, Tony Stark, gözlük, savunma sistemi, uydu bağlantısı " +
            "gibi bir geçmişin ya da yeteneğin yok, bunlardan bahsetme ve rol yapma. Sıradan, yardımsever, " +
            "dürüst bir yapay zeka asistanı gibi davran; yapamadığın şeyi yapamam de. Kullanıcının diliyle, kısa ve " +
            "doğal cevap ver (sesli okunacak). Kod veya dosya içeriğini her zaman ``` işaretli bloklar içinde ver. " +
            "Kullanıcıdan bir cevap, onay ya da seçim bekliyorsan (ör. 'bunu yapayım mı?') cevabının EN SONUNA " +
            "tek başına [SORU] yaz; beklemiyorsan yazma. Şu an: $an (Türkiye saati)."
    }

    private val istemci = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS).retryOnConnectionFailure(true).build()
    private val soruIsareti = Regex("""\[\s*SORU\s*]""", RegexOption.IGNORE_CASE)
    private val soruSonu = Regex("""\?[\s"'”)»*_]*$""")
    private val kodBlogu = Regex("```[\\s\\S]*?```")
    private val izinliAnahtar = setOf("type", "properties", "required", "description", "enum", "items", "format", "nullable")

    suspend fun sor(
        ayar: Ayarlar,
        gecmis: List<Mesaj>,
        mesaj: String,
        araclar: List<GeminiArac> = emptyList(),
        cagir: suspend (String, JSONObject) -> String = { _, _ -> "" }
    ): Cevap = withContext(Dispatchers.IO) {
        val apiKey = ayar.apiKey
        require(apiKey.isNotBlank()) { "Gemini API anahtarı girilmemiş (Ayarlar → Yapay zeka)." }
        val basit = ayar.modelYonlendirme && araclar.isEmpty() &&
            (BASIT.any { mesaj.lowercase().contains(it) } || mesaj.trim().split(" ").size <= 6)
        val model = if (basit) ayar.modelHizli else ayar.modelKaliteli

        val icerik = JSONArray()
        (gecmis + Mesaj(true, mesaj)).forEach {
            icerik.put(
                JSONObject()
                    .put("role", if (it.kullanici) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", it.metin)))
            )
        }
        val araclarJson = if (araclar.isEmpty()) null else JSONArray().put(
            JSONObject().put("functionDeclarations", JSONArray().apply { araclar.forEach { put(bildirim(it)) } })
        )

        repeat(6) {
            val govde = JSONObject()
                .put("contents", icerik)
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sistem()))))
            if (araclarJson != null) govde.put("tools", araclarJson)

            val yanit = istekAt(apiKey, model, govde.toString())
            val adayIcerik = yanit.getJSONArray("candidates").getJSONObject(0).optJSONObject("content")
                ?: return@withContext Cevap("(Boş cevap geldi)", false)
            val parts = adayIcerik.optJSONArray("parts") ?: return@withContext Cevap("(Boş cevap geldi)", false)

            val cagrilar = (0 until parts.length()).map { parts.getJSONObject(it) }.filter { it.has("functionCall") }
            if (cagrilar.isEmpty()) return@withContext ayristir(parts)

            // Modelin ham içeriğini olduğu gibi geri ekle (düşünme imzaları korunmalı)
            icerik.put(adayIcerik)
            val cevaplar = JSONArray()
            for (p in cagrilar) {
                val fc = p.getJSONObject("functionCall")
                val ad = fc.getString("name")
                val sonuc = try {
                    cagir(ad, fc.optJSONObject("args") ?: JSONObject())
                } catch (e: Exception) {
                    "Hata: ${e.message}"
                }
                cevaplar.put(
                    JSONObject().put(
                        "functionResponse",
                        JSONObject().put("name", ad).put("response", JSONObject().put("result", sonuc))
                    )
                )
            }
            icerik.put(JSONObject().put("role", "user").put("parts", cevaplar))
        }
        Cevap("(Araç çağrıları çok uzadı, işlem yarıda kesildi.)", false)
    }

    private suspend fun istekAt(apiKey: String, model: String, govde: String): JSONObject {
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
                    if (r.isSuccessful) return JSONObject(s)
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

    private fun bildirim(a: GeminiArac): JSONObject {
        val o = JSONObject().put("name", a.ad).put("description", a.aciklama.ifBlank { a.ad }.take(900))
        val sema = a.sema?.let { temizle(it) }
        if (sema != null && (sema.optJSONObject("properties")?.length() ?: 0) > 0) o.put("parameters", sema)
        return o
    }

    /** MCP şemasını Gemini'nin kabul ettiği alt kümeye indirger. */
    private fun temizle(s: JSONObject): JSONObject {
        val o = JSONObject()
        for (k in s.keys()) {
            if (k !in izinliAnahtar) continue
            val v = s.get(k)
            when (k) {
                "type" -> o.put(
                    k,
                    if (v is JSONArray) (0 until v.length()).map { v.getString(it) }.firstOrNull { it != "null" } ?: "string"
                    else v.toString()
                )
                "properties" -> if (v is JSONObject) {
                    val p = JSONObject()
                    for (n in v.keys()) (v.optJSONObject(n))?.let { p.put(n, temizle(it)) }
                    o.put(k, p)
                }
                "items" -> if (v is JSONObject) o.put(k, temizle(v))
                else -> o.put(k, v)
            }
        }
        if (!o.has("type")) o.put("type", "string")
        return o
    }

    private fun ayristir(parts: JSONArray): Cevap {
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
