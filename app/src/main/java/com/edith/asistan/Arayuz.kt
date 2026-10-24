package com.edith.asistan

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

/** "Liquid glass" yüzeyi: yarı saydam dikey degrade + parlak ince kenar + yumuşak gölge. */
fun Modifier.cam(sekil: Shape = RoundedCornerShape(24.dp), opak: Float = 0.55f): Modifier = this
    .shadow(10.dp, sekil, clip = false, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
    .clip(sekil)
    .background(
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = opak + 0.2f), Color.White.copy(alpha = opak - 0.15f))
        )
    )
    .border(
        1.dp,
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.2f))),
        sekil
    )

@Composable
fun CamArkaPlan(icerik: @Composable BoxScope.() -> Unit) {
    fun orb(renk: Color) = Brush.radialGradient(listOf(renk, Color.Transparent))
    Box(Modifier.fillMaxSize().background(Krem)) {
        Box(Modifier.size(360.dp).offset((-100).dp, (-70).dp).background(orb(Turuncu.copy(alpha = 0.45f)), CircleShape))
        Box(
            Modifier.size(320.dp).align(Alignment.CenterEnd).offset(120.dp, 0.dp)
                .background(orb(Color(0xFFF2B27A).copy(alpha = 0.5f)), CircleShape)
        )
        Box(
            Modifier.size(340.dp).align(Alignment.BottomStart).offset((-90).dp, 100.dp)
                .background(orb(Color(0xFFE9C9A8).copy(alpha = 0.65f)), CircleShape)
        )
        icerik()
    }
}

private fun karsilama(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Günaydın"
    in 12..17 -> "İyi günler"
    in 18..22 -> "İyi akşamlar"
    else -> "İyi geceler"
}

@Composable
fun Uygulama(vm: SohbetVM) {
    var ayarAcik by remember { mutableStateOf(false) }
    CamArkaPlan {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            UstCubuk { ayarAcik = true }
            Sohbet(vm, Modifier.weight(1f))
            GirisCubugu(vm)
        }
        if (ayarAcik) AyarlarSayfasi(vm.ayarlar) { ayarAcik = false }
    }
}

@Composable
fun UstCubuk(ayarlar: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("E.D.I.T.H", color = Murekkep, fontFamily = FontFamily.Serif, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f))
        Box(
            Modifier.size(42.dp).cam(CircleShape, 0.45f).clickable(onClick = ayarlar),
            contentAlignment = Alignment.Center
        ) { Text("⚙", color = Murekkep, fontSize = 20.sp) }
    }
}

@Composable
fun Sohbet(vm: SohbetVM, mod: Modifier) {
    val liste = rememberLazyListState()
    val adet = vm.mesajlar.size + (if (vm.yukleniyor) 1 else 0) + (if (vm.hata != null) 1 else 0)
    LaunchedEffect(adet) { if (adet > 0) liste.animateScrollToItem(adet - 1) }

    if (vm.mesajlar.isEmpty() && !vm.yukleniyor) {
        Box(mod.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(karsilama(), color = Murekkep, fontFamily = FontFamily.Serif, fontSize = 32.sp)
                Text("E.D.I.T.H.'e hoş geldiniz", color = Soluk, fontFamily = FontFamily.Serif, fontSize = 17.sp)
            }
        }
        return
    }
    LazyColumn(
        mod.fillMaxWidth(),
        state = liste,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        items(vm.mesajlar) { m -> if (m.kullanici) KullaniciBalonu(m.metin) else AsistanMetni(m.metin) }
        if (vm.yukleniyor) item { Dusunuyor() }
        vm.hata?.let { h -> item { Text(h, color = Color(0xFFB3382C), fontSize = 13.sp) } }
    }
}

@Composable
fun KullaniciBalonu(metin: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            metin,
            Modifier.widthIn(max = 300.dp)
                .cam(RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp), 0.4f)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            color = Murekkep, fontSize = 16.sp
        )
    }
}

@Composable
fun AsistanMetni(metin: String) {
    SelectionContainer {
        Text(metin, color = Murekkep, fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 27.sp)
    }
}

@Composable
fun Dusunuyor() {
    val t = rememberInfiniteTransition(label = "dusunuyor")
    val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "alfa")
    Text("Düşünüyor…", Modifier.alpha(a), color = Turuncu, fontFamily = FontFamily.Serif, fontSize = 16.sp)
}

@Composable
fun GirisCubugu(vm: SohbetVM) {
    var metin by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth().padding(14.dp).cam(RoundedCornerShape(32.dp), 0.5f)
            .padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = metin,
            onValueChange = { metin = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Mesaj yaz…", color = Soluk) },
            maxLines = 4,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Turuncu,
                focusedTextColor = Murekkep,
                unfocusedTextColor = Murekkep
            )
        )
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(Turuncu).clickable {
                if (!vm.yukleniyor && metin.isNotBlank()) {
                    vm.gonder(metin)
                    metin = ""
                }
            },
            contentAlignment = Alignment.Center
        ) { Text("↑", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun AyarlarSayfasi(ayar: Ayarlar, kapat: () -> Unit) {
    BackHandler(onBack = kapat)
    var anahtar by remember { mutableStateOf(ayar.apiKey) }
    Column(
        Modifier.fillMaxSize().background(Krem.copy(alpha = 0.94f))
            .statusBarsPadding().navigationBarsPadding().imePadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", Modifier.clickable(onClick = kapat).padding(end = 14.dp), color = Turuncu, fontSize = 34.sp)
            Text("Ayarlar", color = Murekkep, fontFamily = FontFamily.Serif, fontSize = 26.sp)
        }
        Column(Modifier.fillMaxWidth().cam().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Gemini API anahtarı", color = Murekkep, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = anahtar,
                onValueChange = { anahtar = it; ayar.apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                placeholder = { Text("Google AI Studio anahtarı") },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Turuncu, cursorColor = Turuncu)
            )
            Text("Anahtar yalnızca bu telefonda saklanır, GitHub'a gitmez.", color = Soluk, fontSize = 12.sp)
        }
    }
}
