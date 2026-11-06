package com.ayaraya.voicegunz

import kotlin.math.PI
import kotlin.math.sin

/**
 * Pengubah nada real-time (granular / dua kepala baca dengan crossfade).
 * Latensi rendah (~20-40 ms), cocok untuk suara.
 */
class PitchShifter(private val window: Int = 1764) {

    private val size = window * 2 + 8
    private val buf = FloatArray(size)
    private var w = 0
    private var phase = 0.0

    @Volatile
    var ratio: Double = 1.0

    /** Penguat volume keluaran. */
    @Volatile
    var gain: Double = 1.0

    fun process(input: ShortArray, output: ShortArray, n: Int) {
        val r = ratio
        val g = gain
        for (i in 0 until n) {
            buf[w] = input[i] / 32768f

            phase += (1.0 - r) / window
            if (phase >= 1.0) phase -= 1.0
            if (phase < 0.0) phase += 1.0

            val pa = phase
            var pb = phase + 0.5
            if (pb >= 1.0) pb -= 1.0

            val sa = sin(PI * pa)
            val sb = sin(PI * pb)
            val y = (sa * sa * read(pa * window) + sb * sb * read(pb * window)) * g

            val v = (y * 32767.0).toInt().coerceIn(-32768, 32767)
            output[i] = v.toShort()

            w++
            if (w == size) w = 0
        }
    }

    private fun read(delay: Double): Float {
        var pos = w - delay
        if (pos < 0) pos += size
        var i0 = pos.toInt()
        if (i0 >= size) i0 -= size
        val frac = (pos - i0).toFloat()
        val i1 = if (i0 + 1 >= size) 0 else i0 + 1
        return buf[i0] * (1f - frac) + buf[i1] * frac
    }
}
