package com.edith.asistan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AyarlarEkrani(kapat: () -> Unit) {
    var sayfa by remember { mutableStateOf("ana") }
    val ust = mapOf("ses_gemini" to "ses", "ses_eleven" to "ses")
    BackHandler { if (sayfa == "ana") kapat() else sayfa = ust[sayfa] ?: "ana" }
    DisposableEffect(Unit) { onDispose { Motor.testiBitir() } }
    LaunchedEffect(sayfa) { Motor.izinYenile() }

    when (sayfa) {
        "ai" -> SayfaYapayZeka { sayfa = "ana" }
        "ses" -> SayfaSes(geri = { sayfa = "ana" }, git = { sayfa = it })
        "ses_gemini" -> SayfaGemini { sayfa = "ses" }
        "ses_eleven" -> SayfaEleven { sayfa = "ses" }
        "uyan" -> SayfaUyandirma { sayfa = "ana" }
        "mik" -> SayfaMikrofon { sayfa = "ana" }
        "bag" -> SayfaBaglanti { sayfa = "ana" }
        "panel" -> SayfaPanel { sayfa = "ana" }
        else -> AnaAyarlar(kapat) { sayfa = it }
    }
}

@Composable
private fun AnaAyarlar(kapat: () -> Unit, git: (String) -> Unit) {
    SayfaCerceve("Ayarlar", kapat) {
        val a = Motor.ayarlar
        val satirlar = listOf(
            listOf(
                Triple("ai", "Yapay zeka", "Gemini anahtarı, modeller, yönlendirme"),
                Triple("ses", "Ses", "Motor sırası, Gemini ve ElevenLabs sesleri")
            ),
            listOf(
                Triple("uyan", "Uyandırma", "Kelimeler, telaffuz öğrenme, algılama"),
                Triple("mik", "Mikrofon", "Test, konuşma akışı, süreler, modeller")
            ),
            listOf(
                Triple("baglanti", "Bağlantılar", "MCP araçları, onaylar"),
                Triple("panel", "Panel ve izinler", "Ekran üstü panel, asistan, pil")
            )
        )
        val hedef = mapOf("ai" to "ai", "ses" to "ses", "uyan" to "uyan", "mik" to "mik", "baglanti" to "bag", "panel" to "panel")
        for (satir in satirlar) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                for ((simge, baslik, alt) in satir) {
                    KategoriKarti(simge, baslik, alt, Modifier.weight(1f).fillMaxHeight()) { git(hedef.getValue(simge)) }
                }
            }
        }
        Text(
            "E.D.I.T.H. 0.3  ·  ayarlar yalnızca bu telefonda saklanır" +
                if (a.apiKey.isBlank()) "\nGemini API anahtarı henüz girilmemiş." else "",
            Modifier.padding(horizontal = 24.dp), color = SolukKrem, fontSize = 12.sp, lineHeight = 17.sp
        )
    }
}
