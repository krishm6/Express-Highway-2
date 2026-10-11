package com.expresshighway2

import android.Manifest
import android.app.Activity
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.expresshighway2.game.Content
import com.expresshighway2.game.Game
import com.expresshighway2.game.ModeInfo
import com.expresshighway2.game.Terrain
import kotlin.math.max

class MainActivity : Activity() {

    private val cyan = 0xFF22D3EE.toInt()
    private val dim = 0xFF9FB3C8.toInt()
    private val white = 0xFFE6F1FF.toInt()
    private val ink = 0xFF04121F.toInt()
    private val cardBg = 0xFF13203B.toInt()

    private val terrain = Terrain()
    private val game = Game(terrain)
    private lateinit var view: GameView
    private lateinit var panel: FrameLayout
    private lateinit var voice: VoiceController
    private lateinit var prefs: SharedPreferences
    private var quizIndex = 0
    private var onMainMenu = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = getSharedPreferences("eh2", MODE_PRIVATE)

        game.reset()
        view = GameView(this, game)
        panel = FrameLayout(this).apply { setBackgroundColor(0xE6050A16.toInt()) }
        setContentView(FrameLayout(this).apply {
            addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(panel, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        })

        voice = VoiceController(
            this,
            onCommand = { cmd ->
                when (cmd) {
                    VoiceController.Command.FORWARD -> { game.voiceGas = true; game.voiceBrake = false }
                    VoiceController.Command.BRAKE -> { game.voiceGas = false; game.voiceBrake = true }
                    VoiceController.Command.NEUTRAL -> { game.voiceGas = false; game.voiceBrake = false }
                }
            },
            onHeard = { game.heard = it },
            onStatus = { game.say(it, 4.0) }
        )
        view.onGameOver = { showGameOver(it) }
        view.onVoiceToggle = { toggleVoice() }
        view.onPause = { showMenu() }
        showMenu()
    }

    // ------------------------------------------------------------------ system
    @Suppress("DEPRECATION")
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }

    override fun onPause() {
        super.onPause()
        if (game.running) showMenu() else stopVoice()
    }

    override fun onDestroy() {
        stopVoice()
        super.onDestroy()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (game.running || !onMainMenu) showMenu() else super.onBackPressed()
    }

    // ------------------------------------------------------------------ voice
    private fun toggleVoice() {
        if (voice.active) { stopVoice(); game.say("Voice control off", 2.0); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        startVoice()
    }

    private fun startVoice() {
        voice.start()
        if (voice.active) {
            game.voiceOn = true
            game.say("Say \"forward\" to go, \"brake\" to stop", 4.0)
        }
    }

    private fun stopVoice() {
        voice.stop()
        game.voiceOn = false; game.voiceGas = false; game.voiceBrake = false; game.heard = ""
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startVoice()
            else game.say("Microphone permission denied", 4.0)
        }
    }

    // ------------------------------------------------------------------ ui helpers
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun round(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radiusDp).toFloat()
    }

    private fun label(text: String, sp: Float, color: Int = white, bold: Boolean = false, center: Boolean = true) =
        TextView(this).apply {
            this.text = text; textSize = sp; setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            if (center) gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; textSize = 16f
        setTextColor(ink); background = round(cyan, 12)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(380), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
    }

    private fun card(title: String, body: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = round(cardBg, 10); setPadding(dp(14), dp(10), dp(14), dp(10))
        addView(label(title, 15f, cyan, bold = true, center = false))
        addView(label(body, 14f, white, center = false))
        layoutParams = LinearLayout.LayoutParams(dp(520), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
    }

    private fun screen(build: LinearLayout.() -> Unit) {
        panel.removeAllViews()
        panel.visibility = View.VISIBLE
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(24), dp(16), dp(24), dp(16))
            build()
        }
        panel.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun hideOverlay() {
        panel.removeAllViews(); panel.visibility = View.GONE; onMainMenu = false
    }

    private fun bestFor(info: ModeInfo) = prefs.getInt("best${info.mode.ordinal}", 0)

    // ------------------------------------------------------------------ screens
    private fun showMenu() {
        stopVoice(); game.running = false; onMainMenu = true
        screen {
            addView(label("EXPRESS HIGHWAY 2", 34f, cyan, bold = true))
            addView(label("Drive the waveform. Learn DSP.", 15f, dim))
            addView(button("Play") { showModes() })
            addView(button("DSP Handbook") { showHandbook() })
            addView(button("Practice quiz") { quizIndex = 0; showQuiz() })
        }
    }

    private fun showModes() {
        stopVoice(); game.running = false; onMainMenu = false
        screen {
            addView(label("Choose a mode", 24f, cyan, bold = true))
            for (info in Content.modes) {
                addView(button("${info.title}\n${info.blurb}\nBest: ${bestFor(info)}") { startRun(info) })
            }
            addView(button("Back") { showMenu() })
        }
    }

    private fun startRun(info: ModeInfo) {
        terrain.mode = info.mode
        hideOverlay()
        view.startRun(info.blurb)
    }

    private fun showHandbook() {
        onMainMenu = false
        screen {
            addView(label("DSP Handbook", 24f, cyan, bold = true))
            for (l in Content.lessons) addView(card(l.title, l.body))
            addView(button("Back") { showMenu() })
        }
    }

    private fun showQuiz() {
        onMainMenu = false
        val q = Content.questions[quizIndex % Content.questions.size]
        screen {
            addView(label("Practice quiz ${quizIndex + 1}", 20f, cyan, bold = true))
            addView(label(q.q, 16f))
            q.options.forEachIndexed { i, opt -> addView(button(opt) { showAnswer(i == q.answer, q.why) }) }
            addView(button("Back") { showMenu() })
        }
    }

    private fun showAnswer(correct: Boolean, why: String) {
        screen {
            addView(label(if (correct) "Correct!" else "Not quite", 24f, if (correct) 0xFF22C55E.toInt() else 0xFFEF4444.toInt(), bold = true))
            addView(label(why, 16f))
            addView(button("Next question") { quizIndex++; showQuiz() })
            addView(button("Menu") { showMenu() })
        }
    }

    private fun showGameOver(reason: String) {
        stopVoice(); onMainMenu = false
        val info = Content.modes[terrain.mode.ordinal]
        val pts = game.points()
        val best = max(bestFor(info), pts)
        prefs.edit().putInt("best${info.mode.ordinal}", best).apply()
        val tip = Content.lessons[info.tipLesson]
        screen {
            addView(label(reason, 28f, cyan, bold = true))
            addView(label("$pts pts   ${game.meters()} m   best $best", 18f))
            addView(card("Did you know? ${tip.title}", tip.body))
            addView(button("Retry") { startRun(info) })
            addView(button("Modes") { showModes() })
            addView(button("Menu") { showMenu() })
        }
    }
}
