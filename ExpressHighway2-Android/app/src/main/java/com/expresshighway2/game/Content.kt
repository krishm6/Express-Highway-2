package com.expresshighway2.game

data class Lesson(val title: String, val body: String)
data class Question(val q: String, val options: List<String>, val answer: Int, val why: String)
data class ModeInfo(val mode: Mode, val title: String, val blurb: String, val tipLesson: Int)

object Content {
    val lessons = listOf(
        Lesson("Sampling",
            "Turning a continuous signal into numbers by measuring it every Ts seconds. The sampling rate is fs = 1/Ts. The yellow dots on the highway are samples of the road signal."),
        Lesson("Nyquist theorem",
            "To capture a signal without losing information, sample at fs >= 2 * fmax. Half the sampling rate (fs/2) is the highest frequency you can represent."),
        Lesson("Aliasing",
            "Sample too slowly and fast frequencies disguise themselves as slow ones, like wagon wheels that seem to spin backwards in films. The fix is a low-pass (anti-aliasing) filter before the sampler."),
        Lesson("Fourier series",
            "Any periodic signal is a sum of sines at multiples of a base frequency f0 (harmonics): x(t) = sum of a_k * sin(2*pi*k*f0*t + phi_k). Each zone of the Fourier Highway adds one harmonic."),
        Lesson("Low-pass filter",
            "Passes slow changes and blocks fast ones, so bumps get smoothed. A moving average is the simplest low-pass FIR filter. The cutoff sets which frequencies survive."),
        Lesson("Convolution",
            "The output of a linear time-invariant system is the input convolved with the impulse response: flip, slide, multiply, sum. Your car's suspension smooths the road in much the same way."),
        Lesson("Quantization",
            "An n-bit ADC rounds each sample to one of 2^n levels, so the error is at most half a step (LSB/2). Each extra bit adds about 6 dB of SNR: SNR = 6.02n + 1.76 dB.")
    )

    val questions = listOf(
        Question("A signal is sampled at 1000 Hz. What is the highest frequency it can capture without aliasing?",
            listOf("250 Hz", "500 Hz", "1000 Hz"), 1, "Nyquist: fmax = fs/2 = 500 Hz."),
        Question("The road is built by adding sines at multiples of a base frequency. What is this called?",
            listOf("Fourier series", "Convolution", "Quantization"), 0, "A Fourier series represents a periodic signal as a sum of harmonics."),
        Question("If the fundamental is 50 Hz, what is the 3rd harmonic?",
            listOf("100 Hz", "150 Hz", "53 Hz"), 1, "Harmonic k sits at k * f0, so 3 * 50 = 150 Hz."),
        Question("How many levels does an 8-bit ADC have?",
            listOf("8", "64", "256"), 2, "2^8 = 256 levels."),
        Question("What does a low-pass filter do to a bumpy road?",
            listOf("Removes the fast bumps", "Removes the big hills", "Makes it louder"), 0,
            "It keeps low frequencies (the hills) and attenuates high frequencies (the bumps)."),
        Question("A 700 Hz tone is sampled at 1000 Hz. At what frequency does it appear?",
            listOf("700 Hz", "500 Hz", "300 Hz"), 2, "700 Hz is above fs/2 = 500 Hz, so it aliases to |1000 - 700| = 300 Hz."),
        Question("Convolving a signal with a unit impulse gives...",
            listOf("Zero", "The same signal", "Its derivative"), 1, "The impulse is the identity of convolution: x * delta = x."),
        Question("Roughly how much SNR does each extra ADC bit add?",
            listOf("3 dB", "6 dB", "12 dB"), 1, "SNR = 6.02n + 1.76 dB, so about 6 dB per bit.")
    )

    val modes = listOf(
        ModeInfo(Mode.FOURIER, "Fourier Highway",
            "The road is a sum of sines. A new harmonic is added every zone.", 3),
        ModeInfo(Mode.SAMPLING, "Sampling Valley",
            "The road is only known at sample points. Use +/- to set the sampling rate and beat aliasing (dashed line = true signal).", 2),
        ModeInfo(Mode.FILTER, "Filter Lab",
            "A noisy road. Use +/- to set the low-pass cutoff: lower = smoother, higher = more points per coin.", 4),
        ModeInfo(Mode.QUANT, "Quantization Quarry",
            "The road is rounded to n bits. Every 1500 px you get one more bit and finer steps.", 6)
    )
}
