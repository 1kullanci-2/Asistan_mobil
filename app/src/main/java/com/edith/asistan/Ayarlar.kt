package com.edith.asistan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Baglanti(
    val id: String = UUID.randomUUID().toString().take(8),
    val ad: String,
    val url: String,
    val baslik: String = "",
    val acik: Boolean = true,
    val kapaliAraclar: List<String> = emptyList()
)

/** Ayarlar yalnızca bu telefonda saklanır (uygulamaya özel alan), repoya gitmez. Varsayılanlar PC sürümüyle aynıdır. */
class Ayarlar(ctx: Context) {
    private val sp = ctx.getSharedPreferences("edith", Context.MODE_PRIVATE)

    private fun oku(k: String, v: String = "") = sp.getString(k, v) ?: v
    private fun yaz(k: String, v: String) = sp.edit().putString(k, v.trim()).apply()
    private fun bool(k: String, v: Boolean) = sp.getBoolean(k, v)
    private fun bool(k: String, v: Boolean, y: Boolean) = sp.edit().putBoolean(k, y).apply()
    private fun sayi(k: String, v: Float) = sp.getFloat(k, v)

    // ── Yapay zeka
    var apiKey: String
        get() = oku("api_key")
        set(v) = yaz("api_key", v)
    var modelHizli: String
        get() = oku("model_hizli", "gemini-flash-lite-latest").ifBlank { "gemini-flash-lite-latest" }
        set(v) = yaz("model_hizli", v)
    var modelKaliteli: String
        get() = oku("model_kaliteli", "gemini-flash-latest").ifBlank { "gemini-flash-latest" }
        set(v) = yaz("model_kaliteli", v)
    var modelYonlendirme: Boolean
        get() = bool("model_yonlendirme", true)
        set(v) = bool("model_yonlendirme", true, v)

    // ── Ses
    var siraylaDene: Boolean
        get() = bool("sirayla_dene", true)
        set(v) = bool("sirayla_dene", true, v)
    var sadeceSesliKomutta: Boolean
        get() = bool("sadece_sesli", false)
        set(v) = bool("sadece_sesli", false, v)
    var motorSirasi: List<String>
        get() {
            val kayitli = oku("motor_sirasi", "gemini,elevenlabs").split(",").filter { it in MOTORLAR }
            return kayitli + MOTORLAR.filter { it !in kayitli }
        }
        set(v) = yaz("motor_sirasi", v.joinToString(","))
    fun motorAktif(m: String) = bool("motor_aktif_$m", true)
    fun motorAktif(m: String, v: Boolean) = bool("motor_aktif_$m", true, v)

    var geminiSes: String
        get() = oku("gemini_ses", "Kore")
        set(v) = yaz("gemini_ses", v)
    var elevenKey: String
        get() = oku("eleven_key")
        set(v) = yaz("eleven_key", v)
    var elevenSes: String
        get() = oku("eleven_ses")
        set(v) = yaz("eleven_ses", v)
    var elevenSesAdi: String
        get() = oku("eleven_ses_adi")
        set(v) = yaz("eleven_ses_adi", v)
    var elevenModel: String
        get() = oku("eleven_model", "eleven_flash_v2_5").ifBlank { "eleven_flash_v2_5" }
        set(v) = yaz("eleven_model", v)
    var elevenKararlilik: Float
        get() = sayi("eleven_kararlilik", 0.5f)
        set(v) = sp.edit().putFloat("eleven_kararlilik", v).apply()
    var elevenBenzerlik: Float
        get() = sayi("eleven_benzerlik", 0.75f)
        set(v) = sp.edit().putFloat("eleven_benzerlik", v).apply()

    // ── Uyandırma
    var cumleBasi: Boolean
        get() = bool("cumle_basi", true)
        set(v) = bool("cumle_basi", true, v)
    var sesleKes: Boolean
        get() = bool("sesle_kes", true)
        set(v) = bool("sesle_kes", true, v)
    var hoparlorModu: Boolean
        get() = bool("hoparlor_modu", true)
        set(v) = bool("hoparlor_modu", true, v)

    fun ogrenilen(kelime: String): List<String> = runCatching {
        val a = JSONObject(oku("ogrenilen", "{}")).optJSONArray(kelime) ?: JSONArray()
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    fun ogrenilen(kelime: String, liste: List<String>) {
        val o = runCatching { JSONObject(oku("ogrenilen", "{}")) }.getOrDefault(JSONObject())
        o.put(kelime, JSONArray(liste))
        sp.edit().putString("ogrenilen", o.toString()).apply()
    }

    // ── Mikrofon
    var soruSorunca: Boolean
        get() = bool("soru_sorunca_mik", true)
        set(v) = bool("soru_sorunca_mik", true, v)
    var herCevapta: Boolean
        get() = bool("her_cevapta_mik", false)
        set(v) = bool("her_cevapta_mik", false, v)
    var titresim: Boolean
        get() = bool("titresim", false)
        set(v) = bool("titresim", false, v)
    var takipZamanAsimi: Float
        get() = sayi("takip_zaman_asimi", 8f)
        set(v) = sp.edit().putFloat("takip_zaman_asimi", v).apply()
    var sessizlikSuresi: Float
        get() = sayi("sessizlik_suresi", 1.2f)
        set(v) = sp.edit().putFloat("sessizlik_suresi", v).apply()

    // ── Panel
    var panelGoster: Boolean
        get() = bool("panel_goster", true)
        set(v) = bool("panel_goster", true, v)

    // ── Bağlantılar (MCP)
    var mcpSilmeOnayi: Boolean
        get() = bool("mcp_silme_onayi", true)
        set(v) = bool("mcp_silme_onayi", true, v)

    var baglantilar: List<Baglanti>
        get() = runCatching {
            if (!sp.contains("mcp2") && sp.contains("mcp")) { // 0.2 sürümünden taşıma
                val e = JSONArray(oku("mcp", "[]"))
                val yeni = (0 until e.length()).map {
                    val o = e.getJSONObject(it)
                    val t = o.optString("anahtar")
                    Baglanti(ad = o.getString("ad"), url = o.getString("url"),
                        baslik = if (t.isBlank()) "" else "Authorization: Bearer $t")
                }
                baglantilar = yeni
            }
            val a = JSONArray(oku("mcp2", "[]"))
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                val k = o.optJSONArray("kapali") ?: JSONArray()
                Baglanti(
                    o.getString("id"), o.getString("ad"), o.getString("url"), o.optString("baslik"),
                    o.optBoolean("acik", true), (0 until k.length()).map { i -> k.getString(i) }
                )
            }
        }.getOrDefault(emptyList())
        set(l) {
            val a = JSONArray()
            l.forEach {
                a.put(
                    JSONObject().put("id", it.id).put("ad", it.ad).put("url", it.url).put("baslik", it.baslik)
                        .put("acik", it.acik).put("kapali", JSONArray(it.kapaliAraclar))
                )
            }
            sp.edit().putString("mcp2", a.toString()).apply()
        }

    companion object {
        val MOTORLAR = listOf("gemini", "elevenlabs")
        val MOTOR_AD = mapOf("gemini" to "Gemini TTS", "elevenlabs" to "ElevenLabs")
    }
}
