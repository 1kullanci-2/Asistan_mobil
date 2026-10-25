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

data class McpArac(val ad: String, val aciklama: String, val sema: JSONObject?)

/** Basit MCP istemcisi (Streamable HTTP, JSON-RPC): initialize, tools/list, tools/call. */
class McpIstemci(val baglanti: Baglanti) {
    private val http = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    private var oturum: String? = null
    private var sayac = 0
    var araclar: List<McpArac> = emptyList()
        private set

    suspend fun baglan(): Int = withContext(Dispatchers.IO) {
        oturum = null
        rpc(
            "initialize",
            JSONObject()
                .put("protocolVersion", "2025-03-26")
                .put("capabilities", JSONObject())
                .put("clientInfo", JSONObject().put("name", "edith-android").put("version", "0.2"))
        )
        rpc("notifications/initialized", null, bildirim = true)
        val sonuc = rpc("tools/list", JSONObject()) ?: JSONObject()
        val dizi = sonuc.optJSONArray("tools") ?: JSONArray()
        araclar = (0 until dizi.length()).map {
            val o = dizi.getJSONObject(it)
            McpArac(o.getString("name"), o.optString("description"), o.optJSONObject("inputSchema"))
        }
        araclar.size
    }

    suspend fun cagir(ad: String, args: JSONObject): String = withContext(Dispatchers.IO) {
        val s = rpc("tools/call", JSONObject().put("name", ad).put("arguments", args)) ?: return@withContext "(boş cevap)"
        val icerik = s.optJSONArray("content")
        val metin = buildString {
            if (icerik != null) for (i in 0 until icerik.length()) {
                val o = icerik.getJSONObject(i)
                if (o.optString("type") == "text") append(o.optString("text")).append('\n')
            }
        }.trim().ifEmpty { s.toString() }
        if (s.optBoolean("isError")) "Araç hatası: $metin" else metin
    }

    /** Başarıda `result` nesnesini döndürür; bildirimlerde null. Hata olursa istisna fırlatır. */
    private fun rpc(yontem: String, params: JSONObject?, bildirim: Boolean = false): JSONObject? {
        val id = if (bildirim) null else ++sayac
        val govde = JSONObject().put("jsonrpc", "2.0").put("method", yontem)
        if (id != null) govde.put("id", id)
        if (params != null) govde.put("params", params)

        val b = Request.Builder().url(baglanti.url)
            .header("Accept", "application/json, text/event-stream")
            .post(govde.toString().toRequestBody("application/json".toMediaType()))
        oturum?.let { b.header("Mcp-Session-Id", it) }
        if (baglanti.anahtar.isNotBlank()) b.header("Authorization", "Bearer ${baglanti.anahtar}")

        return http.newCall(b.build()).execute().use { r ->
            r.header("Mcp-Session-Id")?.let { oturum = it }
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw RuntimeException("MCP HTTP ${r.code}: ${s.take(120)}")

            val tur = r.header("Content-Type").orEmpty()
            val yanit: JSONObject? = if (bildirim || s.isBlank()) null
            else if (tur.contains("event-stream")) {
                s.lineSequence().filter { it.startsWith("data:") }
                    .mapNotNull { runCatching { JSONObject(it.removePrefix("data:").trim()) }.getOrNull() }
                    .lastOrNull { it.has("result") || it.has("error") }
            } else runCatching { JSONObject(s) }.getOrNull()

            yanit?.optJSONObject("error")?.let { throw RuntimeException("MCP: ${it.optString("message")}") }
            yanit?.optJSONObject("result")
        }
    }
}
