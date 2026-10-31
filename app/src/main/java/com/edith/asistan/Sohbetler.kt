package com.edith.asistan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Telefonda saklanan bir sohbet. [ozet], ilk [ozetKapsam] mesajın (notlar hariç) özetidir. */
class SohbetKaydi(
    val id: String,
    var baslik: String,
    var guncelleme: Long,
    var ozet: String = "",
    var ozetKapsam: Int = 0,
    val mesajlar: MutableList<Mesaj> = mutableListOf()
)

data class SohbetBaslik(val id: String, val baslik: String, val guncelleme: Long)

/** Sohbetleri uygulamaya özel alanda JSON dosyaları olarak saklar (her sohbet ayrı dosya). */
object Depo {
    private fun klasor(c: Context) = File(c.filesDir, "sohbetler").also { it.mkdirs() }

    fun json(k: SohbetKaydi): String {
        val m = JSONArray()
        k.mesajlar.forEach {
            m.put(
                JSONObject().put("k", it.kullanici).put("m", it.metin).put("a", JSONArray(it.araclar)).put("n", it.not)
            )
        }
        return JSONObject().put("id", k.id).put("baslik", k.baslik).put("guncelleme", k.guncelleme)
            .put("ozet", k.ozet).put("kapsam", k.ozetKapsam).put("mesajlar", m).toString()
    }

    @Synchronized
    fun yaz(c: Context, id: String, icerik: String) {
        val gecici = File(klasor(c), "$id.tmp")
        gecici.writeText(icerik)
        gecici.renameTo(File(klasor(c), "$id.json"))
    }

    @Synchronized
    fun sil(c: Context, id: String) {
        File(klasor(c), "$id.json").delete()
    }

    @Synchronized
    fun yukle(c: Context): List<SohbetKaydi> =
        klasor(c).listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { f ->
            runCatching {
                val o = JSONObject(f.readText())
                val dizi = o.optJSONArray("mesajlar") ?: JSONArray()
                val liste = (0 until dizi.length()).map {
                    val j = dizi.getJSONObject(it)
                    val a = j.optJSONArray("a") ?: JSONArray()
                    Mesaj(j.getBoolean("k"), j.getString("m"), (0 until a.length()).map { i -> a.getString(i) }, j.optBoolean("n"))
                }.toMutableList()
                SohbetKaydi(
                    o.getString("id"), o.optString("baslik", "Sohbet"), o.optLong("guncelleme"),
                    o.optString("ozet"), o.optInt("kapsam"), liste
                )
            }.getOrNull()
        }
}

/** Modele gidecek geçmişi hazırlar: eski kısım özet, yakın kısım olduğu gibi (kısaltılmış). */
object Baglam {
    private const val YAKIN = 8
    private const val TAVAN = 14

    private fun kisalt(s: String, n: Int) = if (s.length <= n) s else s.take(n) + "…"

    private fun mekanik(l: List<Mesaj>) = l.joinToString("\n") {
        (if (it.kullanici) "K: " else "A: ") + kisalt(it.metin.replace('\n', ' '), 140)
    }

    /** @return özet metni ve modele verilecek mesajlar (hep kullanıcı mesajıyla başlar). */
    fun hazirla(k: SohbetKaydi, tum: List<Mesaj>): Pair<String, List<Mesaj>> {
        val kapsam = k.ozetKapsam.coerceIn(0, tum.size)
        var yakin = tum.drop(kapsam)
        var ozet = k.ozet
        if (yakin.size > TAVAN) {   // özet henüz yetişmedi: fazlayı ağ kullanmadan sıkıştır
            val fazla = yakin.dropLast(YAKIN)
            yakin = yakin.takeLast(YAKIN)
            ozet = (ozet + "\n" + mekanik(fazla)).trim().takeLast(3500)
        }
        yakin = yakin.map { it.copy(metin = kisalt(it.metin, if (it.kullanici) 500 else 700), araclar = emptyList()) }
        while (yakin.isNotEmpty() && !yakin.first().kullanici) yakin = yakin.drop(1)
        return ozet to yakin
    }
}
