package com.ayaraya.voicegunz

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.widget.TextView

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("voicegunz", Context.MODE_PRIVATE)

    fun getPkg(c: Context): String? = sp(c).getString("pkg", null)

    fun setPkg(c: Context, value: String) {
        sp(c).edit().putString("pkg", value).apply()
    }
}

/** Beri warna gradasi pada tulisan logo "Ayaraya". */
fun TextView.applyLogoGradient() {
    val w = paint.measureText(text.toString())
    paint.shader = LinearGradient(
        0f, 0f, w, 0f,
        intArrayOf(0xFF9C7BFF.toInt(), 0xFF00E5FF.toInt()),
        null, Shader.TileMode.CLAMP
    )
    invalidate()
}

/** Warna rata-rata dari ikon (untuk dijadikan background). */
fun averageColor(d: Drawable): Int {
    val s = 32
    val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    d.setBounds(0, 0, s, s)
    d.draw(canvas)
    var r = 0L
    var g = 0L
    var b = 0L
    var n = 0L
    for (y in 0 until s) {
        for (x in 0 until s) {
            val p = bmp.getPixel(x, y)
            if (Color.alpha(p) > 128) {
                r += Color.red(p)
                g += Color.green(p)
                b += Color.blue(p)
                n++
            }
        }
    }
    bmp.recycle()
    if (n == 0L) return 0xFF3A2A8A.toInt()
    return Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
}

fun darken(color: Int, factor: Float): Int {
    return Color.rgb(
        (Color.red(color) * factor).toInt(),
        (Color.green(color) * factor).toInt(),
        (Color.blue(color) * factor).toInt()
    )
}
