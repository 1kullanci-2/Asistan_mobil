package com.edith.asistan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
private fun alanRenkleri() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Turuncu, unfocusedBorderColor = Kenar, cursorColor = Turuncu,
    focusedTextColor = Metin, unfocusedTextColor = Metin
)

@Composable
private fun Kart(baslik: String, icerik: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().yuzey().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(baslik, color = Metin, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        icerik()
    }
}

@Composable
private fun Dugme(yazi: String, vurgulu: Boolean = false, onClick: () -> Unit) {
    Text(
        yazi,
        Modifier.clip(RoundedCornerShape(50)).background(if (vurgulu) Turuncu else Kenar).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        color = if (vurgulu) Zemin else Metin, fontSize = 13.sp, fontWeight = FontWeight.Medium
    )
}

@Composable
private fun Anahtarli(baslik: String, aciklama: String, acik: Boolean, degisti: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(baslik, color = Metin, fontSize = 15.sp)
            Text(aciklama, color = Soluk, fontSize = 12.sp)
        }
        Switch(
            checked = acik, onCheckedChange = degisti,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Zemin, checkedTrackColor = Turuncu,
                uncheckedThumbColor = Soluk, uncheckedTrackColor = Kenar, uncheckedBorderColor = Kenar
            )
        )
    }
}

/** (id, görünen ad) listesinden seçim; uzun listeler kendi içinde kayar. */
@Composable
private fun Secenekler(liste: List<Pair<String, String>>, secili: String, sec: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(max = 240.dp).yuzey(RoundedCornerShape(14.dp), Zemin)
            .verticalScroll(rememberScrollState())
    ) {
        liste.forEach { (id, ad) ->
            val s = id == secili
            Row(
                Modifier.fillMaxWidth().clickable { sec(id) }.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(ad, Modifier.weight(1f), color = if (s) Turuncu else Metin, fontSize = 14.sp,
                    fontWeight = if (s) FontWeight.SemiBold else FontWeight.Normal)
                if (s) Text("seçili", color = Turuncu, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun AyarlarEkrani(kapat: () -> Unit) {
    BackHandler(onBack = kapat)
    val ayar = Motor.ayarlar
    val scope = rememberCoroutineScope()

    var geminiKey by remember { mutableStateOf(ayar.apiKey) }
    var goster by remember { mutableStateOf(false) }
    var geminiSes by remember { mutableStateOf(ayar.geminiSes) }

    var elevenKey by remember { mutableStateOf(ayar.elevenKey) }
    var elevenSes by remember { mutableStateOf(ayar.elevenSes) }
    var elevenModel by remember { mutableStateOf(ayar.elevenModel) }
    var elevenSesler by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var elevenModeller by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var elevenKredi by remember { mutableStateOf<String?>(null) }
    var elevenMesaj by remember { mutableStateOf<String?>(null) }

    var sadeceSesli by remember { mutableStateOf(ayar.sadeceSesliKomutta) }
    var herCevapta by remember { mutableStateOf(ayar.herCevaptaMikrofon) }

    val baglantilar = remember { mutableStateListOf<Baglanti>().apply { addAll(ayar.baglantilar) } }
    var ekleAcik by remember { mutableStateOf(false) }

    fun elevenCek() {
        if (ayar.elevenKey.isBlank()) { elevenMesaj = "Önce ElevenLabs API anahtarını gir."; return }
        elevenMesaj = "Çekiliyor…"
        scope.launch {
            val hatalar = mutableListOf<String>()
            runCatching { Ses.elevenSesler(ayar.elevenKey) }.onSuccess { elevenSesler = it }
                .onFailure { hatalar.add("Sesler: ${it.message}") }
            runCatching { Ses.elevenModeller(ayar.elevenKey) }.onSuccess { elevenModeller = it }
                .onFailure { hatalar.add("Modeller: ${it.message}") }
            runCatching { Ses.elevenKredi(ayar.elevenKey) }.onSuccess { elevenKredi = it }
                .onFailure { hatalar.add("Kredi: ${it.message}") }
            elevenMesaj = if (hatalar.isEmpty()) null else hatalar.joinToString("\n") + "\n(Anahtarda ilgili okuma izni açık olmalı.)"
        }
    }
    LaunchedEffect(Unit) { if (ayar.elevenKey.isNotBlank()) elevenCek() }

    Column(
        Modifier.fillMaxSize().background(Zemin).statusBarsPadding().navigationBarsPadding().imePadding()
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", Modifier.clickable(onClick = kapat).padding(end = 14.dp), color = Turuncu, fontSize = 34.sp)
            Text("Ayarlar", color = Metin, fontFamily = FontFamily.Serif, fontSize = 26.sp)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Kart("Gemini API anahtarı") {
                OutlinedTextField(
                    value = geminiKey,
                    onValueChange = { geminiKey = it; ayar.apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (goster) VisualTransformation.None else PasswordVisualTransformation(),
                    placeholder = { Text("Google AI Studio anahtarı", color = Soluk) },
                    colors = alanRenkleri()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dugme(if (goster) "Gizle" else "Göster") { goster = !goster }
                    Dugme("Sil") { geminiKey = ""; ayar.apiKey = "" }
                }
                Text("Anahtar yalnızca bu telefonda saklanır, GitHub'a gitmez.", color = Soluk, fontSize = 12.sp)
            }

            Kart("Gemini sesi") {
                Secenekler(Ses.geminiSesleri.map { it to it }, geminiSes) { geminiSes = it; ayar.geminiSes = it }
                Dugme("Dene", vurgulu = true) { Motor.sesDene("gemini") }
            }

            Kart("ElevenLabs") {
                OutlinedTextField(
                    value = elevenKey,
                    onValueChange = { elevenKey = it; ayar.elevenKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    placeholder = { Text("ElevenLabs API anahtarı", color = Soluk) },
                    colors = alanRenkleri()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dugme("Ses, model ve krediyi çek", vurgulu = true) { elevenCek() }
                    Dugme("Sil") {
                        elevenKey = ""; ayar.elevenKey = ""
                        elevenSesler = emptyList(); elevenModeller = emptyList(); elevenKredi = null
                    }
                }
                elevenKredi?.let { Text(it, color = Yesil, fontSize = 13.sp) }
                elevenMesaj?.let { Text(it, color = if (it.startsWith("Çek")) Soluk else Kirmizi, fontSize = 12.sp) }
                if (elevenSesler.isNotEmpty()) {
                    Text("Ses", color = Soluk, fontSize = 12.sp)
                    Secenekler(elevenSesler, elevenSes) { elevenSes = it; ayar.elevenSes = it }
                } else if (elevenSes.isNotBlank()) {
                    Text("Seçili ses: $elevenSes", color = Soluk, fontSize = 12.sp)
                }
                if (elevenModeller.isNotEmpty()) {
                    Text("Model", color = Soluk, fontSize = 12.sp)
                    Secenekler(elevenModeller, elevenModel) { elevenModel = it; ayar.elevenModel = it }
                }
                Dugme("Dene", vurgulu = true) { Motor.sesDene("eleven") }
                Text(
                    "Ses sırası: önce Gemini bir kez denenir, hata (ör. 429) gelirse ElevenLabs'e geçilir.",
                    color = Soluk, fontSize = 12.sp
                )
            }

            Kart("Davranış") {
                Anahtarli(
                    "Sadece sesli komutlarda sesli cevap ver",
                    "Açıkken yazdığın mesajlara sesli cevap verilmez; istek ve kota tasarrufu sağlar.",
                    sadeceSesli
                ) { sadeceSesli = it; ayar.sadeceSesliKomutta = it }
                Anahtarli(
                    "Her cevaptan sonra mikrofonu tekrar aç",
                    "Cevapta soru olsun olmasın dinlemeye devam eder. Kapalıyken yalnızca cevap soru içeriyorsa açılır.",
                    herCevapta
                ) { herCevapta = it; ayar.herCevaptaMikrofon = it }
            }

            Kart("Özel bağlantılar (MCP)") {
                if (baglantilar.isEmpty()) Text("Henüz bağlantı yok.", color = Soluk, fontSize = 13.sp)
                baglantilar.toList().forEach { b ->
                    Column(Modifier.fillMaxWidth().yuzey(RoundedCornerShape(14.dp), Zemin).padding(14.dp)) {
                        Text(b.ad, color = Metin, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(b.url, color = Soluk, fontSize = 12.sp)
                        val d = Motor.mcpDurum[b.url]
                        Text(
                            d ?: "Henüz bağlanılmadı",
                            color = when {
                                d == null -> Soluk
                                d.startsWith("Bağlı") -> Yesil
                                d.startsWith("Hata") -> Kirmizi
                                else -> Turuncu
                            },
                            fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
                        )
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Dugme("Bağlan", vurgulu = true) { scope.launch { Motor.mcpBaglan(b) } }
                            Dugme("Kaldır") {
                                baglantilar.remove(b)
                                ayar.baglantilar = baglantilar.toList()
                                Motor.mcpSil(b)
                            }
                        }
                    }
                }
                Dugme("+ Bağlantı ekle", vurgulu = true) { ekleAcik = true }
            }
            Box(Modifier.padding(bottom = 24.dp))
        }
    }

    if (ekleAcik) {
        var ad by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var token by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { ekleAcik = false },
            containerColor = Yuzey,
            title = { Text("Yeni bağlantı", color = Metin) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(ad, { ad = it }, singleLine = true, label = { Text("Bağlantı adı") }, colors = alanRenkleri())
                    OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("Adres (https://…/mcp)") }, colors = alanRenkleri())
                    OutlinedTextField(token, { token = it }, singleLine = true, label = { Text("Erişim anahtarı (isteğe bağlı)") }, colors = alanRenkleri())
                }
            },
            confirmButton = {
                Text(
                    "Ekle", Modifier.clickable {
                        if (url.isNotBlank()) {
                            val b = Baglanti(ad.ifBlank { "Bağlantı" }.trim(), url.trim(), token.trim())
                            baglantilar.add(b)
                            ayar.baglantilar = baglantilar.toList()
                            ekleAcik = false
                            scope.launch { Motor.mcpBaglan(b) }
                        }
                    }.padding(12.dp),
                    color = Turuncu, fontWeight = FontWeight.SemiBold
                )
            },
            dismissButton = {
                Text("Vazgeç", Modifier.clickable { ekleAcik = false }.padding(12.dp), color = Soluk)
            }
        )
    }
}
