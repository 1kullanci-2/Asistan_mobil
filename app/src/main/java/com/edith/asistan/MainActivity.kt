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
        if (verildi(Manifest.permission.RECORD_AUDIO)) Motor.mikrofonAc()
        else Motor.hata = "Mikrofon izni verilmedi; telefon ayarlarından izin verebilirsin."
    }

    private fun verildi(izin: String) =
        ContextCompat.checkSelfPermission(this, izin) == PackageManager.PERMISSION_GRANTED

    private fun mikrofonDugmesi() {
        if (Motor.mikAcik) { Motor.mikrofonKapat(); return }
        val eksik = mutableListOf<String>()
        if (!verildi(Manifest.permission.RECORD_AUDIO)) eksik.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 && !verildi(Manifest.permission.POST_NOTIFICATIONS)) {
            eksik.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (eksik.isEmpty()) Motor.mikrofonAc() else izinIstegi.launch(eksik.toTypedArray())
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
}
