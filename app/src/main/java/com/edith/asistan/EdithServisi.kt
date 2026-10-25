package com.edith.asistan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/** Mikrofon açıkken Android'in zorunlu tuttuğu ön plan bildirimi; süreci canlı tutar. */
class EdithServisi : Service() {
    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Motor.init(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == KAPAT) {
            Motor.mikrofonKapat()
            stopSelf()
            return START_NOT_STICKY
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(KANAL, "Dinleme", NotificationManager.IMPORTANCE_LOW))

        val ac = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val kapat = PendingIntent.getService(
            this, 1, Intent(this, EdithServisi::class.java).setAction(KAPAT), PendingIntent.FLAG_IMMUTABLE
        )
        val bildirim = Notification.Builder(this, KANAL)
            .setContentTitle("E.D.I.T.H.")
            .setContentText("Dinliyor — \"hey edit\" de")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(ac)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Mikrofonu kapat", kapat).build())
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, bildirim, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, bildirim)
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val KANAL = "edith_dinleme"
        private const val KAPAT = "com.edith.asistan.KAPAT"
    }
}
