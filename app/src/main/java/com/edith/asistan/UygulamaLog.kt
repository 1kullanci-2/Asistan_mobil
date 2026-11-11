package com.edith.asistan

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.SSLException

/** Ayrıntılı hata kaydı Android/data/<paket>/files/uygulama.log dosyasına yazılır. */
object UygulamaLog {
    private const val DOSYA = "uygulama.log"
    private const val MAKS_BOYUT = 1024L * 1024L
    private val kilit = Any()
    private val tarih = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    fun kaydet(context: Context, alan: String, hata: Throwable) {
        runCatching {
            val kok = context.getExternalFilesDir(null) ?: context.filesDir
            val dosya = File(kok, DOSYA)
            synchronized(kilit) {
                if (dosya.exists() && dosya.length() > MAKS_BOYUT) {
                    val onceki = File(kok, "$DOSYA.1")
                    if (onceki.exists()) onceki.delete()
                    dosya.renameTo(onceki)
                }
                val yigin = StringWriter()
                hata.printStackTrace(PrintWriter(yigin))
                val icerik = buildString {
                    append(tarih.get().format(Date()))
                    append(" [").append(alan).append("] ")
                    append(hata.javaClass.simpleName).append(": ")
                    append(hata.message.orEmpty()).append('\n')
                    append(yigin)
                    append("\n----------------------------------------\n")
                }
                val gizli = Regex("""(?i)(api[_ -]?key|authorization|bearer)(\s*[:=]?\s*)[^\s,;]+""")
                val guvenliIcerik = gizli.replace(icerik) { eslesme ->
                    eslesme.groupValues[1] + eslesme.groupValues[2] + "[REDACTED]"
                }
                dosya.appendText(guvenliIcerik, Charsets.UTF_8)
            }
        }
    }

    fun kullaniciMesaji(hata: Throwable): String {
        var neden: Throwable = hata
        while (neden.cause != null && neden.cause !== neden) neden = neden.cause!!
        val mesaj = (hata.message.orEmpty() + " " + neden.message.orEmpty()).lowercase(Locale.ROOT)
        return when {
            neden is SocketTimeoutException || "timeout" in mesaj || "timed out" in mesaj -> "Zaman aşımı"
            neden is UnknownHostException || neden is ConnectException || "unable to resolve host" in mesaj -> "Bağlantı hatası"
            neden is SSLException || "ssl" in mesaj || "certificate" in mesaj -> "Güvenli bağlantı hatası"
            Regex("""\b429\b""").containsMatchIn(mesaj) || "quota" in mesaj || "rate limit" in mesaj -> "Kullanım limiti doldu"
            Regex("""\b40[13]\b""").containsMatchIn(mesaj) || "unauthorized" in mesaj || "api key" in mesaj -> "API anahtarı veya erişim hatası"
            Regex("""\b404\b""").containsMatchIn(mesaj) -> "Sunucu veya model bulunamadı"
            Regex("""\b50[023]\b""").containsMatchIn(mesaj) || "server error" in mesaj -> "Sunucu hatası"
            else -> "İşlem başarısız"
        }
    }
}
