package com.edith.asistan

import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.sin

/** Servis/pencere içinde Compose çalıştırmak için gereken yaşam döngüsü sahibi. */
class GorunumSahibi : LifecycleOwner, SavedStateRegistryOwner {
    private val kayit = LifecycleRegistry(this)
    private val kontrol = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = kayit
    override val savedStateRegistry: SavedStateRegistry get() = kontrol.savedStateRegistry

    init {
        kontrol.performAttach()
        kontrol.performRestore(null)
        kayit.currentState = Lifecycle.State.RESUMED
    }

    fun bitir() { kayit.currentState = Lifecycle.State.DESTROYED }
}

/** Compose görünümünün, pencerenin kök görünümünde yaşam döngüsü sahibini bulabilmesi için sarmalayıcı. */
class Kok(c: Context, private val sahip: GorunumSahibi) : FrameLayout(c) {
    init {
        setViewTreeLifecycleOwner(sahip)
        setViewTreeSavedStateRegistryOwner(sahip)
    }

    override fun onAttachedToWindow() {
        rootView.setViewTreeLifecycleOwner(sahip)
        rootView.setViewTreeSavedStateRegistryOwner(sahip)
        super.onAttachedToWindow()
    }
}

/**
 * Dinleme panelinin ne zaman, nerede görüneceğini yönetir: varsayılan asistansak asistan oturum penceresi,
 * değilse (ve "üstte gösterme" izni verilmişse) WindowManager katmanı.
 */
object Panel {
    private val ana = Handler(Looper.getMainLooper())
    @Volatile var zorla = false
    @Volatile var kendimAcildi = false

    fun guncelle() {
        val gerek = (Motor.faz != Faz.BOS || Motor.kapatSorusu) && (zorla || (Motor.ayarlar.panelGoster && !Motor.onde))
        if (gerek) {
            goster()
        } else if (Motor.panelGorunur) {
            ana.postDelayed({ if (Motor.faz == Faz.BOS && !zorla && !Motor.kapatSorusu) gizle() }, 1200)
        }
    }

    fun goster() {
        if (Motor.panelGorunur) return
        Motor.panelGorunur = true
        val servis = EdithAsistanServisi.instance
        if (servis != null && Motor.asistanMi()) {
            try {
                kendimAcildi = true
                servis.showSession(Bundle(), 0)
                return
            } catch (e: Exception) {
                kendimAcildi = false
            }
        }
        if (!OverlayHost.goster(Motor.ctx)) Motor.panelGorunur = false
    }

    fun gizle() {
        if (!Motor.panelGorunur) return
        Motor.panelGorunur = false
        Motor.kapatSorusu = false
        OverlayHost.gizle(Motor.ctx)
        runCatching { EdithOturum.aktif?.hide() }
    }

    /** Ayarlardan "Paneli şimdi dene". */
    fun dene() {
        if (Motor.faz != Faz.BOS) return
        zorla = true
        Motor.canli = "Panel böyle görünür…"
        Motor.fazAyarla(Faz.KOMUT)
        ana.postDelayed({
            zorla = false
            Motor.canli = ""
            Motor.fazAyarla(Faz.BOS)
        }, 4000)
    }
}

object OverlayHost {
    private var gorunum: Kok? = null
    private var sahip: GorunumSahibi? = null

    fun goster(c: Context): Boolean {
        if (!Settings.canDrawOverlays(c)) return false
        if (gorunum != null) return true
        return try {
            val wm = c.getSystemService(WindowManager::class.java)
            val s = GorunumSahibi()
            val kok = Kok(c, s)
            val cv = ComposeView(c).apply {
                setContent { EdithTema { DinlemePaneli(tamEkran = false) { Motor.panelKapat() } } }
            }
            kok.addView(cv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val lp = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.BOTTOM }
            wm.addView(kok, lp)
            gorunum = kok
            sahip = s
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Yazı modunda klavye açılabilsin diye pencere odaklanabilir yapılır; dinlerken tekrar odaksız olur. */
    fun yaziModu(c: Context, yazi: Boolean) {
        val g: View = gorunum ?: return
        runCatching {
            val lp = g.layoutParams as WindowManager.LayoutParams
            lp.flags = if (yazi) WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            c.getSystemService(WindowManager::class.java).updateViewLayout(g, lp)
        }
    }

    fun gizle(c: Context) {
        val g: View = gorunum ?: return
        runCatching { c.getSystemService(WindowManager::class.java).removeView(g) }
        gorunum = null
        sahip?.bitir()
        sahip = null
    }
}

/** Dinleme paneli: krem kapsül; üstte sesle hareket eden çizgi, altında canlı yazı. */
@Composable
fun DinlemePaneli(tamEkran: Boolean, kapat: () -> Unit) {
    val etkilesim = remember { MutableInteractionSource() }
    // Panelin dışına dokunmak hemen kapatmaz; önce "Kapatılsın mı?" diye sorar.
    val dis = if (tamEkran) Modifier.fillMaxSize().clickable(interactionSource = etkilesim, indication = null) { Motor.disaDokunuldu() }
    else Modifier.fillMaxWidth()
    Column(dis, verticalArrangement = Arrangement.Bottom) {
        val sekil = RoundedCornerShape(32.dp)
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val yazi = Motor.panelYazi
        var metin by remember { mutableStateOf("") }
        val odak = remember { androidx.compose.ui.focus.FocusRequester() }
        val klavye = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        LaunchedEffect(yazi) {
            if (!tamEkran) OverlayHost.yaziModu(ctx, yazi)
            if (yazi) {
                kotlinx.coroutines.delay(150)
                runCatching { odak.requestFocus(); klavye?.show() }
            }
        }
        if (tamEkran && Motor.kapatSorusu) KapatSorusu(kapat)
        // İnce kapsül: solda yuvarlak klavye/mikrofon düğmesi; ortada dalga (ya da yazı kutusu)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp)
                .clip(sekil).background(Krem.copy(alpha = 0.94f))
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.9f), KremKoyu)), sekil)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(KremKoyu)
                    .clickable {
                        if (yazi) { Motor.panelYazi = false; Motor.sesliKomut() }
                        else { Motor.dinlemeyiIptal(); Motor.panelYazi = true }
                    },
                contentAlignment = Alignment.Center
            ) { Simge(if (yazi) "mik" else "klavye", MetinKoyu, Modifier.size(22.dp)) }

            Column(Modifier.weight(1f)) {
                val durum = when (Motor.faz) {
                    Faz.KOMUT -> "Dinleniyor"
                    Faz.ISLE -> "Düşünüyor…"
                    Faz.KONUS -> "Konuşuyor"
                    Faz.BOS -> if (yazi) "Yaz" else "Hazır"
                }
                val yaziRengi = if (Motor.faz == Faz.KOMUT) MetinKoyu else SolukKoyu
                if (!yazi) {
                    Dalga(Modifier.fillMaxWidth().height(30.dp))   // şeridin tüm genişliğini kaplar
                }
                Text(durum, color = if (Motor.faz == Faz.KOMUT) Yesil else SolukKoyu, fontSize = 11.sp)
                when (Motor.faz) {
                    Faz.KOMUT -> if (!yazi) {
                        if (Motor.canli.isBlank()) Text("Konuş, seni dinliyorum", color = SolukKoyu, fontFamily = FontFamily.Serif, fontSize = 16.sp, maxLines = 1)
                        else Text(Motor.canli, color = yaziRengi, fontFamily = FontFamily.Serif, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Faz.ISLE -> Text(
                        Motor.mesajlar.lastOrNull { it.kullanici }?.metin.orEmpty(),
                        color = SolukKoyu, fontFamily = FontFamily.Serif, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Faz.KONUS -> Text(
                        Motor.mesajlar.lastOrNull { !it.kullanici }?.metin.orEmpty(),
                        color = MetinKoyu, fontFamily = FontFamily.Serif, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis
                    )
                    Faz.BOS -> Text(
                        Motor.mesajlar.lastOrNull { !it.kullanici }?.metin.orEmpty(),
                        color = SolukKoyu, fontFamily = FontFamily.Serif, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                if (yazi) {
                    androidx.compose.material3.TextField(
                        value = metin,
                        onValueChange = { metin = it },
                        modifier = Modifier.fillMaxWidth().focusRequester(odak),
                        placeholder = { Text("Mesaj yaz…", color = MetinKoyu.copy(alpha = 0.6f)) },
                        maxLines = 3,
                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = Vurgu,
                            focusedTextColor = MetinKoyu,
                            unfocusedTextColor = MetinKoyu
                        )
                    )
                }
            }
            if (yazi) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(if (metin.isBlank()) Vurgu.copy(alpha = 0.45f) else Vurgu)
                        .clickable {
                            if (metin.isNotBlank()) { Motor.gonder(metin, sesli = false); metin = "" }
                        },
                    contentAlignment = Alignment.Center
                ) { Simge("gonder", KremAcik, Modifier.size(22.dp)) }
            }
        }
    }
}

/** Panelin üstünde beliren soru: iki düğme, "Evet" paneli kapatır, "Hayır" dinlemeye devam eder. */
@Composable
fun KapatSorusu(evet: () -> Unit) {
    val sekil = RoundedCornerShape(26.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(sekil).background(Visne.copy(alpha = 0.94f))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.18f))), sekil)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Kapatılsın mı?", color = MetinKrem, fontFamily = FontFamily.Serif, fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PanelDugme("Evet", Modifier.weight(1f), true, evet)
            PanelDugme("Hayır, dinle", Modifier.weight(1f), false) { Motor.kapatCevap(false) }
        }
    }
}

@Composable
private fun PanelDugme(yazi: String, mod: Modifier, vurgulu: Boolean, tikla: () -> Unit) {
    val sekil = RoundedCornerShape(50)
    Box(
        mod.height(44.dp).clip(sekil)
            .background(if (vurgulu) Krem else Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = if (vurgulu) 0.9f else 0.32f), sekil)
            .clickable(onClick = tikla),
        contentAlignment = Alignment.Center
    ) { Text(yazi, color = if (vurgulu) MetinKoyu else MetinKrem, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
}

/** İki üst üste binen ince çizgi; genlik mikrofon seviyesini (dinlerken) ya da konuşma ritmini izler. */
@Composable
fun Dalga(mod: Modifier) {
    val gecis = rememberInfiniteTransition(label = "dalga")
    val ikiPi = (2 * Math.PI).toFloat()
    val evre by gecis.animateFloat(0f, ikiPi, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "evre")
    val nabiz by gecis.animateFloat(0.45f, 0.75f, infiniteRepeatable(tween(520, easing = LinearEasing)), label = "nabiz")
    val hedef = when (Motor.faz) {
        Faz.KOMUT -> 0.12f + Motor.seviye * 0.88f
        Faz.KONUS -> nabiz
        Faz.ISLE -> 0.14f
        Faz.BOS -> 0.05f
    }
    val genlik by animateFloatAsState(hedef, tween(120), label = "genlik")
    Canvas(mod) {
        val w = size.width
        val h = size.height
        val orta = h / 2
        val pi = Math.PI.toFloat()
        fun yol(ofset: Float, olcek: Float, dongu: Float): Path {
            val p = Path()
            val n = 96
            for (i in 0..n) {
                val x = w * i / n
                val zarf = sin(pi * i / n)
                val y = orta + (h / 2) * 0.9f * genlik * olcek * zarf * sin(dongu * 2f * pi * i / n + evre + ofset)
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            return p
        }
        drawPath(yol(0f, 1f, 2.2f), Visne, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        drawPath(yol(1.6f, 0.65f, 3.1f), Vurgu.copy(alpha = 0.65f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}
