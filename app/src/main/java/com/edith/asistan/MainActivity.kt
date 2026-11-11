package com.edith.asistan

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private var guncellemeAlcisiKayitli = false
    private val guncellemeAlcisi = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id >= 0L) runCatching { Guncelleme.yukle(this@MainActivity, id) }
            }
        }
    }

    private val izinIstegi = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Motor.izinYenile()
        if (verildi(Manifest.permission.RECORD_AUDIO)) Motor.sesliKomut()
        else Motor.hata = "Mikrofon izni verilmedi; Ayarlar → İzinler bölümünden açabilirsin."
    }

    /** İlk kurulum ekranındaki izin düğmeleri: sonucu yalnızca durumu yeniler, dinlemeyi başlatmaz. */
    private val kurulumIzni = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Motor.izinYenile()
    }

    private fun verildi(izin: String) =
        ContextCompat.checkSelfPermission(this, izin) == PackageManager.PERMISSION_GRANTED

    /** Sohbetteki mikrofon düğmesi: izin varsa hemen dinlemeye başlar. */
    private fun mikrofonDugmesi() {
        if (verildi(Manifest.permission.RECORD_AUDIO)) Motor.sesliKomut()
        else izinIstegi.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
    }

    override fun onStart() {
        super.onStart()
        if (!guncellemeAlcisiKayitli) {
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(guncellemeAlcisi, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(guncellemeAlcisi, filter)
            }
            guncellemeAlcisiKayitli = true
        }
    }

    override fun onStop() {
        if (guncellemeAlcisiKayitli) {
            unregisterReceiver(guncellemeAlcisi)
            guncellemeAlcisiKayitli = false
        }
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Motor.init(applicationContext)
        Motor.acilistaYukle()   // modeller her açılışta arka planda hazırlanır
        sohbetIntenti(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            EdithTema { Uygulama(onMik = ::mikrofonDugmesi, onIzin = { kurulumIzni.launch(it) }) }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sohbetIntenti(intent)
    }

    /** Cevap bildirimine dokunulduysa o sohbeti açar. */
    private fun sohbetIntenti(i: android.content.Intent?) {
        i?.getStringExtra("sohbet")?.let {
            Motor.sohbetAcSonra(it)
            i.removeExtra("sohbet")
        }
    }

    override fun onResume() {
        super.onResume()
        Guncelleme.bekleyenKurulumuAc(this)
        Motor.onde = true
        Motor.izinYenile()
        Panel.gizle() // uygulama öndeyken ekran üstü panel gerekmez
        // Uygulama açıkken "hey edit" bildirimsiz dinlenir
        if (Motor.izinMikrofon && Motor.modellerHazir && !Motor.mikAcik && !Motor.kurulumGoster) Motor.mikrofonAc()
        else if (!Motor.modellerHazir) Motor.acilistaYukle()
    }

    override fun onPause() {
        Motor.onde = false
        // Sürekli dinleme kapalıysa uygulamadan çıkınca mikrofon da kapanır
        if (!Motor.ayarlar.surekliDinleme && Motor.mikAcik) Motor.mikrofonKapat()
        super.onPause()
    }
}
