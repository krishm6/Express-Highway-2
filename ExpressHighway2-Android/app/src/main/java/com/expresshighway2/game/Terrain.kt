package com.expresshighway2.game

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** The four DSP-themed game modes. */
enum class Mode { FOURIER, SAMPLING, FILTER, QUANT }

/**
 * The road is a signal. Each mode builds it with a different DSP idea:
 *  FOURIER  - sum of harmonics that fade in as you drive (Fourier series)
 *  SAMPLING - road is only known at samples spaced Ts apart, linearly reconstructed (Nyquist / aliasing)
 *  FILTER   - noisy road passed through an adjustable low-pass cutoff
 *  QUANT    - road rounded to n bits; more bits = finer steps
 */
class Terrain(@Volatile var mode: Mode = Mode.FOURIER) {
    /** Target value of the +/- control (1..8). */
    @Volatile var cutS = 3
    /** Smoothed value actually used by the road, so the ground never jumps. */
    @Volatile var cut = 3.0

    private val ph = doubleArrayOf(0.0, 1.3, 2.9, 4.1, 5.5, 0.7, 2.2, 3.6)

    private fun sm(a: Double, b: Double, x: Double) = min(1.0, max(0.0, (x - a) / (b - a)))

    /** Fourier series: harmonic k fades in over the zone starting at (k-1)*1200 px. */
    fun fourier(x: Double): Double {
        var s = 0.0
        for (k in 1..6) {
            val w = sm((k - 1) * 1200.0, (k - 1) * 1200.0 + 600.0, x)
            if (w > 0) s += w * (66.0 / k.toDouble().pow(1.9)) * sin(k * x * 0.007 + ph[k - 1])
        }
        return 420.0 - s
    }

    /** Eight-harmonic road with an ideal-ish low-pass: harmonic k passes with weight clamp(c-k+1, 0, 1). */
    fun filtered(x: Double, c: Double): Double {
        var s = 0.0
        for (k in 1..8) {
            val w = min(1.0, max(0.0, c - k + 1))
            if (w > 0) s += w * (64.0 / k.toDouble().pow(1.8)) * sin(k * x * 0.007 + ph[k - 1])
        }
        return 420.0 - s * sm(0.0, 500.0, x)
    }

    /** Sample spacing in px for SAMPLING mode. Nyquist is met when Ts <= 56 px (shortest wavelength is 112 px). */
    fun ts(): Double = 220.0 * 0.82.pow(cut - 1)

    private fun quant(v: Double, bits: Int): Double {
        val q = 170.0 / 2.0.pow(bits)
        val f = v / q
        val fl = floor(f)
        return (fl + sm(0.5, 1.0, f - fl)) * q
    }

    fun height(x: Double): Double = when (mode) {
        Mode.FOURIER -> fourier(x)
        Mode.SAMPLING -> {
            val t = ts()
            val i = floor(x / t)
            val u = x / t - i
            filtered(i * t, 8.0) * (1 - u) + filtered((i + 1) * t, 8.0) * u
        }
        Mode.FILTER -> filtered(x, cut)
        Mode.QUANT -> {
            val zf = max(0.0, x) / 1500
            val zi = floor(zf).toInt()
            val u = sm(0.6, 1.0, zf - zi)
            val v = fourier(x)
            quant(v, min(7, 2 + zi)) * (1 - u) + quant(v, min(7, 3 + zi)) * u
        }
    }

    fun harmonics(x: Double) = min(6, 1 + floor(max(0.0, x) / 1200).toInt())
    fun bits(x: Double) = min(7, 2 + floor(max(0.0, x) / 1500).toInt())

    fun adjust(d: Int) { cutS = (cutS + d).coerceIn(1, 8) }
    fun resetTuning() { cutS = if (mode == Mode.SAMPLING) 4 else 3; cut = cutS.toDouble() }
    fun animate(dt: Double) { cut += (cutS - cut) * min(1.0, dt * 2) }
}
