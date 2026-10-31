package com.edith.asistan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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

/** [not] true ise sistem notudur (ör. "Gemini limiti doldu"): ekranda küçük görünür, modele gönderilmez. */
data class Mesaj(
    val kullanici: Boolean,
    val metin: String,
    val araclar: List<String> = emptyList(),
    val not: Boolean = false
)

data class Cevap(val metin: String, val soru: Boolean)
data class GeminiArac(val ad: String, val aciklama: String, val sema: JSONObject?)
class KotaHatasi(mesaj: String) : RuntimeException(mesaj)

/**
 * Akıştaki metni ayıklar: baştaki [BASLIK: …] ve [GUCLU] etiketlerini ayırır, sondaki [SORU] işaretini
 * gizler ve geri kalanı geciktirmeden iletir.
 */
class AkisSuzgeci(private val ilet: (String) -> Unit, private val baslik: (String) -> Unit) {
    private val tampon = StringBuilder()
    private var basDurum = true
    var yonlendirmeAcik = false
    var guclu = false
        private set
    val ham = StringBuilder()    // alınan her şey
    val tumu = StringBuilder()   // kullanıcıya iletilen temiz metin

    private val etiket = Regex("""^\s*\[\s*(GUCLU|BASLIK\s*:[^\]]*)]\s*""", RegexOption.IGNORE_CASE)
    private val soruRe = Regex("""\[\s*SORU\s*]""", RegexOption.IGNORE_CASE)

    fun ekle(parca: String) {
        ham.append(parca)
        if (guclu) return
        tampon.append(parca)
        if (basDurum) {
            while (true) {
                val s = tampon.toString()
                val m = etiket.find(s)
                if (m != null) {
                    val ic = m.groupValues[1]
                    if (ic.startsWith("G", ignoreCase = true)) {
                        if (yonlendirmeAcik) guclu = true
                    } else baslik(ic.substringAfter(":").trim())
                    tampon.delete(0, m.range.last + 1)
                    if (guclu) { tampon.setLength(0); return }
                    continue
                }
                val t = s.trimStart()
                if (t.isEmpty()) return
                if (t.startsWith("[") && !t.contains("]") && t.length < 160) return // etiket olabilir, bekle
                basDurum = false
                break
            }
        }
        bosalt(false)
    }

    private fun bosalt(son: Boolean) {
        var s = tampon.toString().replace(soruRe, "")
        var tut = ""
        if (!son) {
            val i = s.lastIndexOf('[')
            if (i >= 0 && !s.substring(i).contains(']') && s.length - i < 10) {
                tut = s.substring(i)
                s = s.substring(0, i)
            }
        }
        tampon.setLength(0)
        tampon.append(tut)
        if (s.isNotEmpty()) {
            tumu.append(s)
            ilet(s)
        }
    }

    /** Araç turundan sonra gelen metin öncekine yapışmasın. */
    fun ayirac() {
        bosalt(true)
        if (tumu.isNotEmpty() && !tumu.endsWith("\n")) {
            tumu.append("\n\n")
            ilet("\n\n")
        }
    }

    /** Hızlı model devredince güçlü modelin cevabı için sıfırlar. */
    fun sifirla() {
        tampon.setLength(0)
        guclu = false
        basDurum = false
    }

    fun bitir() {
        if (!guclu) bosalt(true)
        tampon.setLength(0)
    }
}

object Gemini {
    private fun sistem(ozet: String, baslikIste: Boolean, yonlendir: Boolean): String {
        val an = SimpleDateFormat("d MMMM yyyy EEEE HH:mm", Locale("tr", "TR")).format(Date())
        val s = StringBuilder(
            "Adın E.D.I.T.H. Kullanıcının telefonundaki sesli asistansın; yalnızca adın bu. " +
                "Kurgusal bir kimliğe bürünme: Stark Industries, Tony Stark, gözlük, savunma sistemi, uydu bağlantısı " +
                "gibi bir geçmişin ya da yeteneğin yok, bunlardan bahsetme ve rol yapma. Sıradan, yardımsever, " +
                "dürüst bir yapay zeka asistanı gibi davran; yapamadığın şeyi yapamam de. Kullanıcının diliyle, kısa ve " +
                "doğal cevap ver (sesli okunacak). Kod veya dosya içeriğini her zaman ``` işaretli bloklar içinde ver. " +
                "Kullanıcıdan bir cevap, onay ya da seçim bekliyorsan (ör. 'bunu yapayım mı?') cevabının EN SONUNA " +
                "tek başına [SORU] yaz; beklemiyorsan yazma. Şu an: $an (Türkiye saati)."
        )
        if (yonlendir) {
            s.append(
                " YÖNLENDİRME: Sohbet, kısa soru-cevap, saat/tarih ve araç çağırma gibi basit işleri kendin yap. " +
                    "Uzun metin yazma, kod yazma, derin analiz, çok adımlı akıl yürütme ya da özenli yazım gerektiren " +
                    "isteklerde araç çağırmadan ve başka hiçbir şey yazmadan cevabın yalnızca [GUCLU] olsun."
            )
        }
        if (baslikIste) {
            s.append(
                " ÖNEMLİ: Bu sohbetin ilk mesajı. Cevabının en başına tek satır olarak " +
                    "[BASLIK: en fazla 4 kelimelik kısa konu başlığı] yaz, satır sonu bırak, sonra cevabı yaz. " +
                    "Bu satırdan kullanıcıya asla söz etme."
            )
        }
        if (ozet.isNotBlank()) {
            s.append("\n\nÖnceki konuşmanın özeti (bağlam için; kullanıcıya özetten söz etme):\n").append(ozet)
        }
        return s.toString()
    }

    private val istemci = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS).retryOnConnectionFailure(true).build()
    private val soruIsareti = Regex("""\[\s*SORU\s*]""", RegexOption.IGNORE_CASE)
    private val soruSonu = Regex("""\?[\s"'”)»*_]*$""")
    private val kodBlogu = Regex("```[\\s\\S]*?```")
    private val izinliAnahtar = setOf("type", "properties", "required", "description", "enum", "items", "format", "nullable")

    /**
     * Önce hızlı model dener (araçlarla birlikte); o "[GUCLU]" derse aynı istek güçlü modele gider. Metin geldikçe
     * [akis] ile iletilir. Bir modelin kotası dolarsa öbürüne geçilir ve [bildir] ile not düşülür.
     */
    suspend fun sor(
        ayar: Ayarlar,
        ozet: String,
        gecmis: List<Mesaj>,
        mesaj: String,
        ilkMesaj: Boolean,
        araclar: List<GeminiArac>,
        akis: (String) -> Unit,
        bildir: (String) -> Unit,
        baslik: (String) -> Unit,
        cagir: suspend (String, JSONObject) -> String
    ): Cevap = withContext(Dispatchers.IO) {
        val apiKey = ayar.apiKey
        require(apiKey.isNotBlank()) { "Gemini API anahtarı girilmemiş (Ayarlar → Yapay zeka)." }
        val hizli = ayar.modelHizli
        val kaliteli = ayar.modelKaliteli
        val yonlendirme = ayar.modelYonlendirme && hizli != kaliteli
        var model = if (yonlendirme) hizli else kaliteli
        val kota = mutableSetOf<String>()
        var baslikIstendi = ilkMesaj

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
        val suzgec = AkisSuzgeci(akis, baslik)

        var tur = 0
        while (tur < 8) {
            tur++
            val yonlendirici = yonlendirme && model == hizli && kaliteli !in kota
            suzgec.yonlendirmeAcik = yonlendirici
            val govde = JSONObject()
                .put("contents", icerik)
                .put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sistem(ozet, baslikIstendi, yonlendirici))))
                )
            if (araclarJson != null) govde.put("tools", araclarJson)

            val parcalar = try {
                akisliIstek(apiKey, model, govde.toString(), suzgec)
            } catch (e: KotaHatasi) {
                kota.add(model)
                val yedek = listOf(kaliteli, hizli).firstOrNull { it !in kota }
                    ?: throw RuntimeException(e.message)
                bildir(
                    if (model == hizli) "Hızlı modelin limiti doldu, güçlü modele geçildi."
                    else "Güçlü modelin limiti doldu, hızlı modele geçildi."
                )
                model = yedek
                tur--
                continue
            }

            val cagrilar = (0 until parcalar.length()).map { parcalar.getJSONObject(it) }.filter { it.has("functionCall") }
            if (cagrilar.isEmpty()) {
                if (suzgec.guclu && yonlendirici) {      // hızlı model işi devretti
                    suzgec.sifirla()
                    model = kaliteli
                    baslikIstendi = false
                    continue
                }
                suzgec.bitir()
                val temiz = suzgec.tumu.toString().trim()
                if (temiz.isEmpty()) return@withContext Cevap("(Boş cevap geldi)", false)
                val soru = soruIsareti.containsMatchIn(suzgec.ham) ||
                    soruSonu.containsMatchIn(temiz.replace(kodBlogu, "").trim())
                return@withContext Cevap(temiz, soru)
            }

            // Modelin ham içeriğini olduğu gibi geri ekle (düşünme imzaları korunmalı)
            icerik.put(JSONObject().put("role", "model").put("parts", parcalar))
            suzgec.ayirac()
            baslikIstendi = false
            val cevaplar = JSONArray()
            for (p in cagrilar) {
                val fc = p.getJSONObject("functionCall")
                val ad = fc.getString("name")
                val sonuc = try {
                    cagir(ad, fc.optJSONObject("args") ?: JSONObject())
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
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

    /** Uzun konuşmaların eski kısmını (varsa mevcut özetle birlikte) tek bir kısa özete çevirir. */
    suspend fun ozetle(ayar: Ayarlar, eski: String, mesajlar: List<Mesaj>): String = withContext(Dispatchers.IO) {
        val metin = buildString {
            if (eski.isNotBlank()) append("Mevcut özet:\n").append(eski).append("\n\n")
            append("Yeni konuşma parçası:\n")
            mesajlar.forEach {
                append(if (it.kullanici) "Kullanıcı: " else "Asistan: ").append(it.metin.take(900)).append('\n')
            }
        }
        val govde = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", metin)))))
            .put(
                "systemInstruction",
                JSONObject().put(
                    "parts", JSONArray().put(
                        JSONObject().put(
                            "text",
                            "Sen bir konuşma özetleyicisisin. Mevcut özetle yeni parçayı birleştirip Türkçe, en fazla 1000 " +
                                "karakterlik tek bir özet yaz: kullanıcıyla ilgili bilgiler, tercihler, istekler, alınan kararlar, " +
                                "yarım kalan işler ve konuşmanın akışı. Yalnızca özeti yaz."
                        )
                    )
                )
            )
        val yanit = istekAt(ayar.apiKey, ayar.modelHizli, govde.toString())
        val p = yanit.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
        buildString {
            if (p != null) for (i in 0 until p.length()) append(p.getJSONObject(i).optString("text"))
        }.trim().take(1500)
    }

    /** Anahtarın görebildiği, metin üretebilen Gemini modelleri. */
    suspend fun modeller(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val istek = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200")
            .header("x-goog-api-key", apiKey).build()
        istemci.newCall(istek).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw RuntimeException(hataMetni(r.code, s))
            val dizi = JSONObject(s).optJSONArray("models") ?: JSONArray()
            val dislanan = listOf("tts", "image", "live", "embedding", "audio", "robotics", "computer", "veo")
            (0 until dizi.length()).map { dizi.getJSONObject(it) }
                .filter { m -> (m.optJSONArray("supportedGenerationMethods")?.toString() ?: "").contains("generateContent") }
                .map { it.optString("name").removePrefix("models/") }
                .filter { ad -> ad.startsWith("gemini") && dislanan.none { x -> x in ad } }
                .sorted()
        }
    }

    /** Modele tek kısa istek atar; "ok" ya da kısa hata metni döner. */
    suspend fun modelDene(apiKey: String, model: String): String = withContext(Dispatchers.IO) {
        val govde = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", "Merhaba")))))
            .put("generationConfig", JSONObject().put("maxOutputTokens", 8))
        try {
            istekAt(apiKey, model, govde.toString())
            "ok"
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: KotaHatasi) {
            "kota dolu"
        } catch (e: Exception) {
            e.message?.take(90) ?: "hata"
        }
    }

    /** Akışlı (SSE) istek. Gelen metin [suzgec]e verilir; modelin ham parçaları döndürülür. */
    private suspend fun akisliIstek(apiKey: String, model: String, govde: String, suzgec: AkisSuzgeci): JSONArray {
        var son = "Sunucudan cevap alınamadı."
        for (deneme in 0 until 3) {
            currentCoroutineContext().ensureActive()
            val istek = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse")
                .header("x-goog-api-key", apiKey)
                .post(govde.toRequestBody("application/json".toMediaType()))
                .build()
            val parcalar = JSONArray()
            var basarili = false
            var geldi = false
            try {
                istemci.newCall(istek).execute().use { r ->
                    if (!r.isSuccessful) {
                        val s = r.body?.string().orEmpty()
                        son = hataMetni(r.code, s)
                        if (r.code == 429) throw KotaHatasi(kotaMetni(model, s))
                        if (r.code != 503) throw RuntimeException(son)
                    } else {
                        val kaynak = r.body!!.source()
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val satir = kaynak.readUtf8Line() ?: break
                            if (!satir.startsWith("data:")) continue
                            val j = runCatching { JSONObject(satir.removePrefix("data:").trim()) }.getOrNull() ?: continue
                            val p = j.optJSONArray("candidates")?.optJSONObject(0)
                                ?.optJSONObject("content")?.optJSONArray("parts") ?: continue
                            for (i in 0 until p.length()) {
                                val o = p.getJSONObject(i)
                                parcalar.put(o)
                                val t = o.optString("text")
                                if (t.isNotEmpty() && !o.optBoolean("thought", false)) {
                                    geldi = true
                                    suzgec.ekle(t)
                                }
                            }
                        }
                        basarili = true
                    }
                }
            } catch (e: IOException) {
                son = "Bağlantı hatası: ${e.message}"
                if (geldi) throw RuntimeException(son) // yarım cevap geldiyse tekrar deneme: metin ikilenir
            }
            if (basarili) return parcalar
            if (deneme < 2) delay(1200L * (deneme + 1))
        }
        throw RuntimeException(son)
    }

    /** Akışsız istek (özet gibi arka plan işleri). */
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
                    if (r.code == 429) throw KotaHatasi(kotaMetni(model, s))
                    if (r.code != 503) throw RuntimeException(son)
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

    private fun kotaMetni(model: String, s: String): String {
        val gunluk = s.contains("PerDay", true)
        val bekle = Regex(""""retryDelay"\s*:\s*"(\d+)""").find(s)?.groupValues?.get(1)?.toIntOrNull()
        return "Gemini kotası doldu ($model). " +
            if (gunluk) "Bu günlük limit; yarın sıfırlanır. Ayarlar → Yapay zeka'dan başka bir model deneyebilir ya da ücretli plana geçebilirsin."
            else "Dakikalık limit; ${bekle ?: 60} sn sonra tekrar dene."
    }

    private fun hataMetni(kod: Int, s: String): String {
        val m = runCatching { JSONObject(s).getJSONObject("error").getString("message") }.getOrDefault(s)
        return "HTTP $kod: ${m.replace("\n", " ").take(160)}"
    }
}
