package com.edith.asistan

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView

/** Telefonun "Varsayılan dijital asistan" listesinde E.D.I.T.H.'in görünmesini sağlar. */
class EdithAsistanServisi : VoiceInteractionService() {
    override fun onCreate() {
        super.onCreate()
        Motor.init(applicationContext)
    }

    override fun onReady() {
        super.onReady()
        instance = this
        Motor.izinYenile()
    }

    override fun onShutdown() {
        instance = null
        super.onShutdown()
    }

    companion object {
        @Volatile var instance: EdithAsistanServisi? = null
    }
}

class EdithOturumServisi : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = EdithOturum(this)
}

/** Asistan çağrıldığında (uzun basma, kenar kaydırma ya da uyandırma kelimesi) açılan şeffaf pencere. */
class EdithOturum(baglam: Context) : VoiceInteractionSession(baglam) {
    private val sahip = GorunumSahibi()

    override fun onCreate() {
        setTheme(R.style.Theme_EdithOturum)
        super.onCreate()
        kilitUstu()
    }

    /** Pencere kilit ekranının üstünde görünsün (altında kalmasın). */
    @Suppress("DEPRECATION")
    private fun kilitUstu() {
        runCatching {
            window?.window?.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    override fun onCreateContentView(): View {
        val kok = Kok(context, sahip)
        val cv = ComposeView(context).apply {
            setContent { EdithTema { DinlemePaneli(tamEkran = true) { Motor.panelKapat(); Panel.gizle() } } }
        }
        kok.addView(cv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return kok
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        kilitUstu()
        aktif = this
        Motor.init(context.applicationContext)
        if (Panel.kendimAcildi) {
            Panel.kendimAcildi = false      // uyandırma kelimesi zaten dinlemeyi başlattı
            Motor.panelGorunur = true
        } else {
            Motor.asistanAcildi()           // kullanıcı sistem kısayoluyla çağırdı
        }
    }

    override fun onHide() {
        super.onHide()
        if (aktif === this) aktif = null
        Motor.panelGorunur = false
    }

    override fun onDestroy() {
        sahip.bitir()
        super.onDestroy()
    }

    companion object {
        @Volatile var aktif: EdithOturum? = null
    }
}

/** Sistemin asistan olarak kaydetmek için zorunlu tuttuğu (kullanılmayan) tanıma servisi. */
class EdithTanimaServisi : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        runCatching { listener?.error(SpeechRecognizer.ERROR_CLIENT) }
    }

    override fun onCancel(listener: Callback?) {}
    override fun onStopListening(listener: Callback?) {}
}
