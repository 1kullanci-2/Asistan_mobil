package com.edith.asistan

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

/** Sade, geometrik çizgi simgeleri (24 birimlik tuval). */
@Composable
fun Simge(tur: String, renk: Color, mod: Modifier = Modifier) {
    Canvas(mod) {
        val u = size.minDimension / 24f
        val kalem = Stroke(width = 1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (tur) {
            "ai" -> { // dört köşeli yıldız
                val p = Path()
                p.moveTo(12f * u, 3f * u)
                p.quadraticBezierTo(13f * u, 11f * u, 21f * u, 12f * u)
                p.quadraticBezierTo(13f * u, 13f * u, 12f * u, 21f * u)
                p.quadraticBezierTo(11f * u, 13f * u, 3f * u, 12f * u)
                p.quadraticBezierTo(11f * u, 11f * u, 12f * u, 3f * u)
                p.close()
                drawPath(p, renk)
            }
            "ses" -> { // ses çubukları
                val yuk = listOf(6f, 12f, 17f, 10f, 5f)
                yuk.forEachIndexed { i, h ->
                    val x = (4f + i * 4f) * u
                    drawLine(renk, Offset(x, (12f - h / 2) * u), Offset(x, (12f + h / 2) * u), 2.2f * u, StrokeCap.Round)
                }
            }
            "uyan" -> { // iç içe halkalar
                drawCircle(renk, 2.6f * u, Offset(12f * u, 12f * u))
                drawCircle(renk, 6.6f * u, Offset(12f * u, 12f * u), style = kalem)
                drawCircle(renk.copy(alpha = 0.55f), 10.4f * u, Offset(12f * u, 12f * u), style = kalem)
            }
            "mik" -> {
                drawRoundRect(renk, Offset(9f * u, 3f * u), Size(6f * u, 11f * u), CornerRadius(3f * u), style = kalem)
                drawArc(renk, 0f, 180f, false, Offset(6f * u, 7f * u), Size(12f * u, 10f * u), style = kalem)
                drawLine(renk, Offset(12f * u, 17f * u), Offset(12f * u, 21f * u), 1.9f * u, StrokeCap.Round)
                drawLine(renk, Offset(8.5f * u, 21f * u), Offset(15.5f * u, 21f * u), 1.9f * u, StrokeCap.Round)
            }
            "baglanti" -> { // birbirine geçen iki halka
                drawRoundRect(renk, Offset(2.5f * u, 8.5f * u), Size(11f * u, 7f * u), CornerRadius(3.5f * u), style = kalem)
                drawRoundRect(renk, Offset(10.5f * u, 8.5f * u), Size(11f * u, 7f * u), CornerRadius(3.5f * u), style = kalem)
            }
            "panel" -> { // telefon ve alttan açılan panel
                drawRoundRect(renk, Offset(6f * u, 2.5f * u), Size(12f * u, 19f * u), CornerRadius(3f * u), style = kalem)
                drawRoundRect(renk, Offset(8.5f * u, 15.5f * u), Size(7f * u, 3.2f * u), CornerRadius(1.6f * u))
            }
            "menu" -> {
                for (y in listOf(7f, 12f, 17f)) drawLine(renk, Offset(4f * u, y * u), Offset(20f * u, y * u), 2f * u, StrokeCap.Round)
            }
            "ayar" -> { // kaydırıcılar
                val satirlar = listOf(6f to 15f, 12f to 8f, 18f to 14f)
                for ((y, x) in satirlar) {
                    drawLine(renk, Offset(4f * u, y * u), Offset(20f * u, y * u), 1.9f * u, StrokeCap.Round)
                    drawCircle(renk, 2.7f * u, Offset(x * u, y * u))
                }
            }
            "gonder" -> {
                drawLine(renk, Offset(12f * u, 19f * u), Offset(12f * u, 6f * u), 2.2f * u, StrokeCap.Round)
                val p = Path()
                p.moveTo(6.5f * u, 11.5f * u)
                p.lineTo(12f * u, 6f * u)
                p.lineTo(17.5f * u, 11.5f * u)
                drawPath(p, renk, style = Stroke(width = 2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            "yeni" -> {
                drawLine(renk, Offset(12f * u, 5f * u), Offset(12f * u, 19f * u), 2f * u, StrokeCap.Round)
                drawLine(renk, Offset(5f * u, 12f * u), Offset(19f * u, 12f * u), 2f * u, StrokeCap.Round)
            }
            "cop" -> {
                drawLine(renk, Offset(5f * u, 7f * u), Offset(19f * u, 7f * u), 1.9f * u, StrokeCap.Round)
                drawLine(renk, Offset(10f * u, 4f * u), Offset(14f * u, 4f * u), 1.9f * u, StrokeCap.Round)
                drawRoundRect(renk, Offset(7f * u, 9.5f * u), Size(10f * u, 10.5f * u), CornerRadius(2.5f * u), style = kalem)
            }
            "geri" -> {
                val p = Path()
                p.moveTo(15f * u, 5f * u)
                p.lineTo(8f * u, 12f * u)
                p.lineTo(15f * u, 19f * u)
                drawPath(p, renk, style = kalem)
            }
        }
    }
}
