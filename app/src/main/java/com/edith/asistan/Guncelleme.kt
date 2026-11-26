package com.edith.asistan

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SurumNotu(val surum: String, val maddeler: List<String>)

/** [yeni]: yüklü sürümden daha yeni bir sürüm var mı. [notlar]: yüklü sürümden 2 önceki sürümden başlayarak en yeniye kadar (yeniden eskiye). */
data class GuncellemeBilgisi(
    val surum: String,
    val apkUrl: String,
    val yeni: Boolean,
    val notlar: List<SurumNotu>
)

object Guncelleme {
    private const val PREFS = "edith_update"
    private const val PENDING_ID = "pending_download_id"
    const val INDIRME_TAMAMLANDI = DownloadManager.ACTION_DOWNLOAD_COMPLETE
    private const val RELEASE_API =
        "https://api.github.com/repos/1kullanci-2/Asistan_mobil/releases/tags/son-surum"

    private val istemci = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun kontrol(): GuncellemeBilgisi? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        istemci.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    error("Güncelleme deposuna erişilemedi (depo herkese açık olmalı).")
                }
                error("Güncelleme sunucusuna ulaşılamadı (${response.code}).")
            }
            val json = JSONObject(raw)
            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apk: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name") == "app-debug.apk") {
                    apk = asset.optString("browser_download_url").takeIf { it.startsWith("https://") }
                    break
                }
            }
            val body = json.optString("body")
            val version = Regex("""Sürüm:\s*([0-9]+(?:[.][0-9]+){1,3})""", RegexOption.IGNORE_CASE)
                .find(body)?.groupValues?.get(1)
            if (apk == null || version == null) return@withContext null
            GuncellemeBilgisi(version, apk, yeniSurum(version, BuildConfig.VERSION_NAME), notlariAyir(body))
        }
    }

    /** Sürüm gövdesindeki "## 0.3.N" başlıklarını ve altındaki "- madde" satırlarını okur; yüklü sürümün 2 öncesinden itibaren olanları döndürür. */
    private fun notlariAyir(body: String): List<SurumNotu> {
        fun no(s: String) = s.substringAfterLast('.').toIntOrNull() ?: 0
        val liste = mutableListOf<SurumNotu>()
        var ad: String? = null
        var maddeler = mutableListOf<String>()
        fun bitir() { ad?.let { liste.add(SurumNotu(it, maddeler)) } }
        for (satir in body.lines()) {
            if (satir.startsWith("## ")) {
                bitir()
                ad = satir.removePrefix("## ").trim()
                maddeler = mutableListOf()
            } else if (ad != null && satir.startsWith("- ")) maddeler.add(satir.removePrefix("- ").trim())
        }
        bitir()
        return liste.sortedByDescending { no(it.surum) }   // sürüm geçmişi: yayındaki tüm kayıtlar
    }

    private fun yeniSurum(uzak: String, yerel: String): Boolean {
        fun sayilar(s: String) = s.split(".").map { it.toIntOrNull() ?: 0 }
        val a = sayilar(uzak)
        val b = sayilar(yerel)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun indir(context: Context, url: String): Long {
        require(url.startsWith("https://")) { "Güvenli olmayan indirme adresi." }
        runCatching {
            java.io.File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "edith-guncelleme.apk").delete()
        }
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("E.D.I.T.H. güncellemesi")
            .setDescription("Yeni APK indiriliyor…")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "edith-guncelleme.apk")
        return (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }

    /** İndirme durumu: (indirilen bayt, toplam bayt, DownloadManager durumu). */
    fun ilerleme(context: Context, id: Long): Triple<Long, Long, Int>? {
        val m = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        m.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) return null
            return Triple(
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            )
        }
        return null
    }

    fun bekleyenKurulumuAc(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getLong(PENDING_ID, -1L)
        if (id >= 0L && (android.os.Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls())) {
            prefs.edit().remove(PENDING_ID).apply()
            yukle(context, id)
        }
    }

    fun yukle(context: Context, indirmeId: Long) {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = manager.getUriForDownloadedFile(indirmeId) ?: return
        if (android.os.Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(PENDING_ID, indirmeId).apply()
            val izin = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(izin)
            return
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(PENDING_ID).apply()
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
