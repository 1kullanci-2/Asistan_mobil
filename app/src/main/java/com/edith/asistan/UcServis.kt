package com.edith.asistan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Groq, Cerebras ve Mistral OpenAI uyumlu Chat Completions API'lerini kullanır.
 * Araç çağrıları da aynı döngüde yürütülür; API anahtarları yalnızca cihazın SharedPreferences alanında kalır.
 */
object UcServis {
    private data class Uc(val url: String, val varsayilanModel: String)

    private val servisler = mapOf(
        "groq" to Uc("https://api.groq.com/openai/v1/chat/completions", "openai/gpt-oss-120b"),
        "cerebras" to Uc("https://api.cerebras.ai/v1/chat/completions", "zai-glm-4.7"),
        "mistral" to Uc("https://api.mistral.ai/v1/chat/completions", "mistral-small-latest")
    )

    private val istemci = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun sor(
        servis: String,
        modelAdi: String,
        ayar: Ayarlar,
        ozet: String,
        gecmis: List<Mesaj>,
        mesaj: String,
        ilkMesaj: Boolean,
        araclar: List<GeminiArac>,
        akis: (String) -> Unit,
        baslik: (String) -> Unit,
        cagir: suspend (String, JSONObject) -> String
    ): Cevap = withContext(Dispatchers.IO) {
        val cfg = servisler[servis] ?: error("Desteklenmeyen yapay zekâ servisi: $servis")
        val apiKey = ayar.servisAnahtar(servis)
        require(apiKey.isNotBlank()) { "${Ayarlar.YAPAY_ZEKA_AD[servis] ?: servis} API anahtarı girilmemiş." }
        val model = modelAdi.ifBlank { cfg.varsayilanModel }

        val messages = JSONArray()
        val system = Gemini.sistem(ozet, ilkMesaj, false)
        messages.put(JSONObject().put("role", "system").put("content", system))
        gecmis.forEach {
            messages.put(
                JSONObject()
                    .put("role", if (it.kullanici) "user" else "assistant")
                    .put("content", it.metin)
            )
        }
        messages.put(JSONObject().put("role", "user").put("content", mesaj))

        val tools = if (araclar.isEmpty()) null else JSONArray().apply {
            araclar.forEach { ar ->
                val fn = JSONObject()
                    .put("name", ar.ad)
                    .put("description", ar.aciklama)
                    .put("parameters", ar.sema ?: JSONObject().put("type", "object").put("properties", JSONObject()))
                put(JSONObject().put("type", "function").put("function", fn))
            }
        }

        var tur = 0
        while (tur++ < 8) {
            val body = JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("temperature", 0.7)
            if (tools != null) body.put("tools", tools).put("tool_choice", "auto")

            val request = Request.Builder()
                .url(cfg.url)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val raw = istemci.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull()
                    error("${Ayarlar.YAPAY_ZEKA_AD[servis]} API (${response.code}): ${detail?.takeIf { it.isNotBlank() } ?: text.take(240)}")
                }
                text
            }

            val choice = JSONObject(raw).optJSONArray("choices")?.optJSONObject(0)
                ?: error("API geçerli bir yanıt döndürmedi.")
            val assistant = choice.optJSONObject("message") ?: error("API yanıtında mesaj bulunamadı.")
            val calls = assistant.optJSONArray("tool_calls")
            if (calls != null && calls.length() > 0) {
                messages.put(assistant)
                for (i in 0 until calls.length()) {
                    val call = calls.getJSONObject(i)
                    val fn = call.optJSONObject("function") ?: continue
                    val name = fn.optString("name")
                    val args = runCatching { JSONObject(fn.optString("arguments", "{}")) }.getOrDefault(JSONObject())
                    val result = cagir(name, args)
                    messages.put(
                        JSONObject()
                            .put("role", "tool")
                            .put("tool_call_id", call.optString("id"))
                            .put("content", result)
                    )
                }
                continue
            }

            var answer = assistant.optString("content").trim()
            val title = Regex("^\\s*\\[BASLIK\\s*:\\s*([^]]+)]", RegexOption.IGNORE_CASE).find(answer)
            if (title != null) {
                baslik(title.groupValues[1].trim())
                answer = answer.removeRange(title.range).trimStart()
            }
            val question = Regex("\\[\\s*SORU\\s*]", RegexOption.IGNORE_CASE).containsMatchIn(answer) ||
                answer.trimEnd().endsWith("?")
            answer = answer.replace(Regex("\\[\\s*SORU\\s*]", RegexOption.IGNORE_CASE), "").trim()
            if (answer.isNotEmpty()) akis(answer)
            return@withContext Cevap(answer.ifBlank { "(Boş cevap geldi)" }, question)
        }
        error("Araç çağrıları çok uzadı, işlem yarıda kesildi.")
    }

    /** Servisin hesabında kullanılabilir sohbet modellerini listeler. */
    suspend fun modeller(servis: String, ayar: Ayarlar): List<String> = withContext(Dispatchers.IO) {
        val cfg = servisler[servis] ?: error("Desteklenmeyen servis")
        val key = ayar.servisAnahtar(servis)
        require(key.isNotBlank()) { "API anahtarı girilmemiş" }
        val url = when (servis) {
            "groq" -> "https://api.groq.com/openai/v1/models"
            "cerebras" -> "https://api.cerebras.ai/v1/models"
            "mistral" -> "https://api.mistral.ai/v1/models"
            else -> error("Desteklenmeyen servis")
        }
        val request = Request.Builder().url(url).header("Authorization", "Bearer $key").get().build()
        istemci.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("${servis} HTTP ${response.code}: $raw")
            val data = JSONObject(raw).optJSONArray("data") ?: JSONArray()
            (0 until data.length()).mapNotNull { i ->
                data.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }
            }.distinct().sorted()
        }
    }

    /** Seçili modelle kısa bir sohbet isteği göndererek API anahtarını ve modeli doğrular. */
    suspend fun modelDene(servis: String, ayar: Ayarlar, modelAdi: String) = withContext(Dispatchers.IO) {
        val cfg = servisler[servis] ?: error("Desteklenmeyen servis")
        val key = ayar.servisAnahtar(servis)
        require(key.isNotBlank()) { "API anahtarı girilmemiş" }
        val model = modelAdi.ifBlank { cfg.varsayilanModel }
        val body = JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "Merhaba")))
            .put("max_tokens", 8)
        val request = Request.Builder()
            .url(cfg.url)
            .header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        istemci.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("${servis} HTTP ${response.code}: $raw")
            val content = JSONObject(raw).optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content").orEmpty()
            if (content.isBlank()) error("Model boş yanıt verdi")
            "ok"
        }
    }
}
