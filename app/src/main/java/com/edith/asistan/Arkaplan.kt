package com.edith.asistan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject

/** İnternet bağlantısı var mı? */
object Ag {
    fun bagliMi(c: Context): Boolean = runCatching {
        val cm = c.getSystemService(ConnectivityManager::class.java)
        val n = cm.activeNetwork ?: return false
        cm.getNetworkCapabilities(n)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)
}

/**
 * Çevrim dışıyken yazılan mesajlar: sohbetin kimliği ve metni telefonda saklanır; internet gelince
 * (uygulama kapalı olsa bile) WorkManager gönderimi başlatır.
 */
object Kuyruk {
    private fun sp(c: Context) = c.getSharedPreferences("edith_kuyruk", Context.MODE_PRIVATE)

    private fun oku(c: Context) = runCatching { JSONArray(sp(c).getString("q", "[]")) }.getOrDefault(JSONArray())

    @Synchronized
    fun ekle(c: Context, id: String, metin: String) {
        val a = oku(c)
        a.put(JSONObject().put("id", id).put("m", metin))
        sp(c).edit().putString("q", a.toString()).apply()
    }

    /** Bekleyen her şeyi sohbete göre gruplayıp döndürür ve kuyruğu boşaltır. */
    @Synchronized
    fun al(c: Context): Map<String, List<String>> {
        val a = oku(c)
        val s = LinkedHashMap<String, MutableList<String>>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            s.getOrPut(o.getString("id")) { mutableListOf() }.add(o.getString("m"))
        }
        sp(c).edit().remove("q").apply()
        return s
    }

    @Synchronized
    fun bosMu(c: Context) = oku(c).length() == 0

    @Synchronized
    fun sohbetiSil(c: Context, id: String) {
        val a = oku(c)
        val y = JSONArray()
        for (i in 0 until a.length()) if (a.getJSONObject(i).getString("id") != id) y.put(a.getJSONObject(i))
        sp(c).edit().putString("q", y.toString()).apply()
    }

    fun planla(c: Context) {
        val is_ = OneTimeWorkRequestBuilder<KuyrukIsi>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(c).enqueueUniqueWork("edith_kuyruk", ExistingWorkPolicy.REPLACE, is_)
    }
}

/** İnternet geldiğinde bekleyen mesajları gönderir ve cevapları bekler. */
class KuyrukIsi(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        Motor.init(applicationContext)
        Motor.kuyrukBosalt()
        return Result.success()
    }
}

/** Cevap beklenirken süreci canlı tutan, sönük bir ön plan bildirimi (uygulamadan çıkınca cevap kaybolmasın). */
class GorevServisi : Service() {
    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(KANAL, "Cevap hazırlanıyor", NotificationManager.IMPORTANCE_MIN))
        val b = Notification.Builder(this, KANAL)
            .setContentTitle("E.D.I.T.H.")
            .setContentText("Cevap hazırlanıyor…")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(2, b, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(2, b)
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val KANAL = "edith_gorev"
    }
}

/** Cevap geldiğinde (uygulama açık değilken ya da başka sohbetteyken) küçük bir ön izlemeyle haber verir. */
object Bildirimler {
    private const val KANAL = "edith_cevap"

    fun cevap(c: Context, sohbetId: String, baslik: String, metin: String) {
        runCatching {
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(KANAL, "Cevaplar", NotificationManager.IMPORTANCE_DEFAULT))
            val ac = PendingIntent.getActivity(
                c, sohbetId.hashCode(),
                Intent(c, MainActivity::class.java).putExtra("sohbet", sohbetId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val onizleme = metin.replace(Regex("[*#`>_]"), "").replace(Regex("\\s+"), " ").trim().take(110)
            val b = Notification.Builder(c, KANAL)
                .setContentTitle(baslik.ifBlank { "E.D.I.T.H." })
                .setContentText(onizleme)
                .setStyle(Notification.BigTextStyle().bigText(onizleme))
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentIntent(ac)
                .setAutoCancel(true)
                .build()
            nm.notify(sohbetId.hashCode(), b)
        }
    }

    fun temizle(c: Context, sohbetId: String) {
        runCatching { c.getSystemService(NotificationManager::class.java).cancel(sohbetId.hashCode()) }
    }
}
