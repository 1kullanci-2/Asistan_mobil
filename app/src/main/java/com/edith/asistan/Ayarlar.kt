package com.edith.asistan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Baglanti(val ad: String, val url: String, val anahtar: String = "")

/** Ayarlar yalnızca bu telefonda saklanır (uygulamaya özel alan), repoya gitmez. */
class Ayarlar(ctx: Context) {
    private val sp = ctx.getSharedPreferences("edith", Context.MODE_PRIVATE)

    private fun oku(k: String, v: String = "") = sp.getString(k, v) ?: v
    private fun yaz(k: String, v: String) = sp.edit().putString(k, v.trim()).apply()

    var apiKey: String
        get() = oku("api_key")
        set(v) = yaz("api_key", v)

    var elevenKey: String
        get() = oku("eleven_key")
        set(v) = yaz("eleven_key", v)

    var elevenSes: String
        get() = oku("eleven_ses")
        set(v) = yaz("eleven_ses", v)

    var elevenModel: String
        get() = oku("eleven_model", "eleven_multilingual_v2")
        set(v) = yaz("eleven_model", v)

    var geminiSes: String
        get() = oku("gemini_ses", "Kore")
        set(v) = yaz("gemini_ses", v)

    /** Açıkken yazılı mesajlara sesli cevap verilmez (istek/token tasarrufu). */
    var sadeceSesliKomutta: Boolean
        get() = sp.getBoolean("sadece_sesli", true)
        set(v) = sp.edit().putBoolean("sadece_sesli", v).apply()

    /** Açıkken her cevaptan sonra (soru olsun olmasın) mikrofon tekrar açılır. */
    var herCevaptaMikrofon: Boolean
        get() = sp.getBoolean("her_cevapta_mik", false)
        set(v) = sp.edit().putBoolean("her_cevapta_mik", v).apply()

    var baglantilar: List<Baglanti>
        get() = runCatching {
            val a = JSONArray(oku("mcp", "[]"))
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Baglanti(o.getString("ad"), o.getString("url"), o.optString("anahtar"))
            }
        }.getOrDefault(emptyList())
        set(l) {
            val a = JSONArray()
            l.forEach { a.put(JSONObject().put("ad", it.ad).put("url", it.url).put("anahtar", it.anahtar)) }
            sp.edit().putString("mcp", a.toString()).apply()
        }
}
