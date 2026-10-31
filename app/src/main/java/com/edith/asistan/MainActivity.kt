package com.edith.asistan

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

    private val izinIstegi = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Motor.izinYenile()
        if (verildi(Manifest.permission.RECORD_AUDIO)) Motor.sesliKomut()
        else Motor.hata = "Mikrofon izni verilmedi; Ayarlar → İzinler bölümünden açabilirsin."
    }

    private fun verildi(izin: String) =
        ContextCompat.checkSelfPermission(this, izin) == PackageManager.PERMISSION_GRANTED

    /** Sohbetteki mikrofon düğmesi: izin varsa hemen dinlemeye başlar. */
    private fun mikrofonDugmesi() {
        if (verildi(Manifest.permission.RECORD_AUDIO)) Motor.sesliKomut()
        else izinIstegi.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Motor.init(applicationContext)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            EdithTema { Uygulama(onMik = ::mikrofonDugmesi) }
        }
    }

    override fun onResume() {
        super.onResume()
        Motor.onde = true
        Motor.izinYenile()
        Panel.gizle() // uygulama öndeyken ekran üstü panel gerekmez
        // Uygulama açıkken "hey edit" bildirimsiz dinlenir
        if (Motor.izinMikrofon && Motor.modellerHazir && !Motor.mikAcik) Motor.mikrofonAc()
    }

    override fun onPause() {
        Motor.onde = false
        // Sürekli dinleme kapalıysa uygulamadan çıkınca mikrofon da kapanır
        if (!Motor.ayarlar.surekliDinleme && Motor.mikAcik) Motor.mikrofonKapat()
        super.onPause()
    }
}
