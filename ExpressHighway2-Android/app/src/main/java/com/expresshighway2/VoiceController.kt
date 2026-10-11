package com.expresshighway2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Hands-free driving using Android's native SpeechRecognizer (no WebView involved).
 * Listens continuously, restarting after every utterance, and turns spoken words into commands:
 *   "forward" / "go" / "gas" / "drive"  -> FORWARD (latched until another command)
 *   "brake" / "stop" / "slow"           -> BRAKE   (latched)
 *   "neutral" / "coast"                 -> NEUTRAL (release both)
 * Must be used from the main thread.
 */
class VoiceController(
    private val ctx: Context,
    private val onCommand: (Command) -> Unit,
    private val onHeard: (String) -> Unit,
    private val onStatus: (String) -> Unit
) {
    enum class Command { FORWARD, BRAKE, NEUTRAL }

    var active = false
        private set

    private var rec: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val forwardWords = setOf("forward", "forwards", "go", "gas", "drive", "ahead", "accelerate")
    private val brakeWords = setOf("brake", "brakes", "break", "stop", "slow", "halt")
    private val neutralWords = setOf("neutral", "coast", "relax")

    fun start() {
        if (active) return
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            onStatus("No speech recogniser found on this device")
            return
        }
        active = true
        rec = SpeechRecognizer.createSpeechRecognizer(ctx).also { it.setRecognitionListener(listener) }
        listen()
    }

    fun stop() {
        active = false
        handler.removeCallbacksAndMessages(null)
        rec?.let {
            try { it.cancel() } catch (e: Exception) { /* ignore */ }
            try { it.destroy() } catch (e: Exception) { /* ignore */ }
        }
        rec = null
    }

    private fun listen() {
        if (!active) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        }
        try { rec?.startListening(intent) } catch (e: Exception) { restart(600) }
    }

    private fun restart(delayMs: Long) {
        if (!active) return
        handler.postDelayed({ listen() }, delayMs)
    }

    private fun parse(text: String) {
        val words = text.lowercase().split(Regex("\\W+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return
        onHeard(words.takeLast(3).joinToString(" "))
        // The most recent command word in the phrase wins ("forward forward brake" -> BRAKE).
        for (w in words.asReversed()) {
            when (w) {
                in brakeWords -> { onCommand(Command.BRAKE); return }
                in forwardWords -> { onCommand(Command.FORWARD); return }
                in neutralWords -> { onCommand(Command.NEUTRAL); return }
            }
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.let { parse(it) }
        }

        override fun onResults(results: Bundle?) {
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.let { parse(it) }
            restart(120)
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    stop(); onStatus("Microphone permission is needed for voice control")
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    try { rec?.cancel() } catch (e: Exception) { /* ignore */ }
                    restart(700)
                }
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER -> {
                    onStatus("Voice recognition needs a network connection on this device")
                    restart(2000)
                }
                // NO_MATCH / SPEECH_TIMEOUT / CLIENT: normal while idle, just listen again.
                else -> restart(150)
            }
        }
    }
}
