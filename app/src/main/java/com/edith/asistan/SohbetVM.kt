package com.edith.asistan

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class SohbetVM(app: Application) : AndroidViewModel(app) {
    val ayarlar = Ayarlar(app)
    val mesajlar = mutableStateListOf<Mesaj>()

    var yukleniyor by mutableStateOf(false)
        private set
    var hata by mutableStateOf<String?>(null)
        private set

    fun gonder(metin: String) {
        val m = metin.trim()
        if (m.isEmpty() || yukleniyor) return
        val gecmis = mesajlar.toList()
        mesajlar.add(Mesaj(true, m))
        yukleniyor = true
        hata = null
        viewModelScope.launch {
            try {
                mesajlar.add(Mesaj(false, Gemini.sor(ayarlar.apiKey, gecmis, m).metin))
            } catch (e: Exception) {
                hata = e.message ?: "Bilinmeyen hata"
            } finally {
                yukleniyor = false
            }
        }
    }
}
