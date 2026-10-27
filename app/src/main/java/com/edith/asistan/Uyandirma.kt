package com.edith.asistan

/** PC sürümündeki uyandırma kelimesi mantığının birebir karşılığı ("hey edit" / "edit"). */
object Uyandirma {
    val WAKE = listOf("hey edit", "edit")
    val TOHUM = mapOf("hey edit" to listOf("hey edith", "hey edits"), "edit" to listOf("edith", "edits"))
    private val TR_ARTIK = setOf("hey", "hay", "ey", "edit", "edith", "edici", "edi", "edis", "idit", "adit")
    private val DURAK = setOf(
        "a", "an", "the", "i", "it", "is", "in", "on", "at", "of", "to", "and", "or", "uh", "um", "oh", "ah",
        "huh", "hmm", "yes", "no", "yeah", "you", "he", "she", "we", "they", "so", "but", "that", "this", "for"
    )
    private val SELAM = setOf("hey", "hi", "hello")
    private val bosluk = Regex("\\s+")
    private val noktalama = Regex("[^\\p{L}\\p{N}_\\s]")

    private var anahtar: List<String>? = null
    private var rxBas = Regex("(?!)")
    private var rxHer = Regex("(?!)")

    fun varyasyonlar(): List<String> {
        val s = mutableSetOf<String>()
        for (w in WAKE) {
            s.add(w)
            s.addAll(TOHUM[w].orEmpty())
            s.addAll(Motor.ayarlar.ogrenilen(w))
        }
        return s.sortedByDescending { it.length }
    }

    /** (temiz metin, bitiş indeksi) ya da null. basta=true: kelime cümlenin başında olmalı. */
    @Synchronized
    fun ara(metin: String, basta: Boolean = true): Pair<String, Int>? {
        val m = metin.lowercase().replace("[unk]", " ").replace(bosluk, " ").trim()
        val v = varyasyonlar()
        if (v != anahtar) {
            anahtar = v
            val alt = v.joinToString("|") { x -> x.split(" ").joinToString("\\s+") { Regex.escape(it) } }
            rxBas = Regex("^(?:(?:a|the|uh|oh|and|hi|um)\\s+)?(?:$alt)(?![\\p{L}\\p{N}_])")
            rxHer = Regex("(?<![\\p{L}\\p{N}_])(?:$alt)(?![\\p{L}\\p{N}_])")
        }
        val r = (if (basta) rxBas else rxHer).find(m) ?: return null
        return m to (r.range.last + 1)
    }

    /** Kelimeden sonra en fazla bir kelime varsa (hoparlör modunda kesme için). */
    fun tam(metin: String): Boolean {
        val r = ara(metin) ?: return false
        return r.first.substring(r.second).trim().split(bosluk).filter { it.isNotEmpty() }.size <= 1
    }

    /** Türkçe çözümün başına sızan uyandırma kalıntılarını (en fazla 2 kelime) atar. */
    fun basTemizle(metin: String): String {
        val k = metin.split(" ").filter { it.isNotEmpty() }.toMutableList()
        val j = TR_ARTIK + varyasyonlar().flatMap { it.split(" ") }.filter { it.length > 2 }
        var n = 0
        while (k.isNotEmpty() && n < 2 && k[0].lowercase() in j) {
            k.removeAt(0)
            n++
        }
        return k.joinToString(" ")
    }

    fun varyasyonUygun(v: String): Boolean {
        val t = v.split(" ").filter { it.isNotEmpty() }
        if (t.size !in 1..4) return false
        if (t.any { it !in DURAK && it !in SELAM && it.length >= 3 }) return true
        return t.size >= 2 && (t[0] in SELAM || t[0] == "a")
    }

    fun ayarKomutuMu(metin: String): Boolean {
        val k = metin.lowercase().replace(noktalama, "").split(bosluk).filter { it.isNotEmpty() }
        return k.any { it.startsWith("ayar") } && k.size <= 3
    }
}
