package com.expresshighway2

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.expresshighway2.game.Game
import com.expresshighway2.game.Mode
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Renders the game on a SurfaceView with its own 60 fps game thread. Pure Android Canvas, no WebView. */
class GameView(context: Context, private val game: Game) :
    SurfaceView(context), SurfaceHolder.Callback, Runnable {

    var onGameOver: ((String) -> Unit)? = null
    var onVoiceToggle: (() -> Unit)? = null
    var onPause: (() -> Unit)? = null

    private val terrain = game.terrain
    private val d = resources.displayMetrics.density

    @Volatile private var alive = false
    private var thread: Thread? = null
    @Volatile private var pendingStart = false
    @Volatile private var pendingToast = ""
    private var notified = true

    private var w = 0f
    private var h = 0f
    private val gasC = FloatArray(3)
    private val brkC = FloatArray(3)
    private val pauseR = RectF(); private val micR = RectF()
    private val minusR = RectF(); private val plusR = RectF(); private val labelR = RectF()

    private fun col(hex: Long) = hex.toInt()
    private val bg = Paint()
    private val grid = Paint().apply { color = col(0x1422D3EE); strokeWidth = 1f }
    private val ground = Paint().apply { color = col(0xFF173A33); style = Paint.Style.FILL }
    private val line = Paint().apply { color = col(0xFF22D3EE); style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
    private val ghost = Paint().apply {
        color = col(0x99FFFFFF); style = Paint.Style.STROKE; strokeWidth = 1.5f; isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(4f, 6f), 0f)
    }
    private val stem = Paint().apply { color = col(0x59FACC15); strokeWidth = 1f }
    private val dot = Paint().apply { color = col(0xFFFACC15); isAntiAlias = true }
    private val coinP = Paint().apply { color = col(0xFFFDE047); isAntiAlias = true }
    private val canP = Paint().apply { color = col(0xFFEF4444) }
    private val pBody = Paint().apply { color = col(0xFFF43F5E); isAntiAlias = true }
    private val pCab = Paint().apply { color = col(0xFFFECDD3) }
    private val pWin = Paint().apply { color = col(0xFF22D3EE) }
    private val pTire = Paint().apply { color = col(0xFF111111); isAntiAlias = true }
    private val pSpoke = Paint().apply { color = col(0xFF94A3B8); strokeWidth = 2f }
    private val text = Paint().apply { color = col(0xFFE6F1FF); isAntiAlias = true }
    private val btnFill = Paint().apply { color = col(0x4022D3EE); isAntiAlias = true }
    private val btnPressed = Paint().apply { color = col(0xAA22D3EE.toLong()); isAntiAlias = true }
    private val btnStroke = Paint().apply { color = col(0xFF22D3EE); style = Paint.Style.STROKE; isAntiAlias = true }
    private val barBg = Paint().apply { color = col(0x33FFFFFF) }
    private val fuelOk = Paint().apply { color = col(0xFF22C55E) }
    private val fuelLow = Paint().apply { color = col(0xFFEF4444) }
    private val toastBg = Paint().apply { color = col(0x99000000) }
    private val ground2 = Path(); private val roadLine = Path(); private val ghostPath = Path()

    init { holder.addCallback(this) }

    /** Called from the UI thread; the game thread performs the reset so there are no races. */
    fun startRun(message: String) { pendingToast = message; pendingStart = true }

    // ---------------------------------------------------------------- lifecycle
    override fun surfaceCreated(holder: SurfaceHolder) {
        alive = true
        thread = Thread(this, "game-loop").also { it.start() }
    }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        alive = false
        try { thread?.join(500) } catch (e: InterruptedException) { /* ignore */ }
        thread = null
    }

    override fun onSizeChanged(nw: Int, nh: Int, ow: Int, oh: Int) {
        w = nw.toFloat(); h = nh.toFloat()
        bg.shader = LinearGradient(0f, 0f, 0f, h, col(0xFF0B1A33), col(0xFF16365A), Shader.TileMode.CLAMP)
        val r = 48 * d
        gasC[0] = w - r - 18 * d; gasC[1] = h - r - 18 * d; gasC[2] = r
        brkC[0] = r + 18 * d; brkC[1] = h - r - 18 * d; brkC[2] = r
        pauseR.set(w - 52 * d, 10 * d, w - 10 * d, 50 * d)
        micR.set(pauseR.left - 8 * d - 118 * d, 10 * d, pauseR.left - 8 * d, 50 * d)
        plusR.set(micR.left - 8 * d - 42 * d, 10 * d, micR.left - 8 * d, 50 * d)
        labelR.set(plusR.left - 56 * d, 10 * d, plusR.left, 50 * d)
        minusR.set(labelR.left - 42 * d, 10 * d, labelR.left, 50 * d)
    }

    // ---------------------------------------------------------------- game loop
    override fun run() {
        var last = System.nanoTime()
        while (alive) {
            val t0 = System.nanoTime()
            val dt = min(0.05, (t0 - last) / 1e9); last = t0
            if (pendingStart) {
                pendingStart = false
                game.reset(); game.running = true; notified = false
                game.say(pendingToast, 7.0)
            }
            if (game.running) game.update(dt)
            val over = game.over
            if (over != null && !notified) { notified = true; post { onGameOver?.invoke(over) } }

            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) render(canvas)
            } catch (e: Exception) { /* surface went away */ }
            finally {
                if (canvas != null) try { holder.unlockCanvasAndPost(canvas) } catch (e: Exception) { /* ignore */ }
            }
            val ms = (System.nanoTime() - t0) / 1_000_000
            if (ms < 16) try { Thread.sleep(16 - ms) } catch (e: InterruptedException) { /* ignore */ }
        }
    }

    // ---------------------------------------------------------------- input
    private fun inCircle(c: FloatArray, x: Float, y: Float): Boolean {
        val dx = x - c[0]; val dy = y - c[1]
        return dx * dx + dy * dy <= (c[2] * 1.25f) * (c[2] * 1.25f)
    }

    private fun tap(x: Float, y: Float) {
        if (!game.running) return
        when {
            pauseR.contains(x, y) -> onPause?.invoke()
            micR.contains(x, y) -> onVoiceToggle?.invoke()
            tunable() && minusR.contains(x, y) -> terrain.adjust(-1)
            tunable() && plusR.contains(x, y) -> terrain.adjust(1)
        }
    }

    private fun tunable() = terrain.mode == Mode.SAMPLING || terrain.mode == Mode.FILTER

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val a = e.actionMasked
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
            val i = e.actionIndex; tap(e.getX(i), e.getY(i))
        }
        var gas = false; var brk = false
        if (a != MotionEvent.ACTION_UP && a != MotionEvent.ACTION_CANCEL) {
            for (i in 0 until e.pointerCount) {
                if (a == MotionEvent.ACTION_POINTER_UP && i == e.actionIndex) continue
                if (inCircle(gasC, e.getX(i), e.getY(i))) gas = true
                if (inCircle(brkC, e.getX(i), e.getY(i))) brk = true
            }
        }
        game.touchGas = gas; game.touchBrake = brk
        return true
    }

    // ---------------------------------------------------------------- drawing
    private fun hudLine(): String = when (terrain.mode) {
        Mode.FOURIER -> "y(x) = sum sin(k*w0*x)/k^1.9   harmonics: ${terrain.harmonics(game.x)}"
        Mode.SAMPLING -> {
            val t = terrain.ts()
            "Ts = ${t.toInt()} px   ${if (t <= 56) "Nyquist OK" else "ALIASING"}   (set fs with +/-)"
        }
        Mode.FILTER -> "Low-pass cutoff: ${terrain.cutS} harmonic(s) pass"
        Mode.QUANT -> { val b = terrain.bits(game.x); "$b-bit ADC   ${1 shl b} levels" }
    }

    private fun render(c: Canvas) {
        c.drawRect(0f, 0f, w, h, bg)
        var gx = 0f; while (gx < w) { c.drawLine(gx, 0f, gx, h, grid); gx += 50 * d }
        var gy = 0f; while (gy < h) { c.drawLine(0f, gy, w, gy, grid); gy += 50 * d }

        val s = max(0.6f, min(1.2f, h / d / 520f)) * d   // 1 world unit ~ 1 dp
        val vw = w / s; val vh = h / s
        val cx = game.x - vw * 0.3; val cy = game.y - vh * 0.6

        c.save(); c.scale(s, s)
        ground2.reset(); roadLine.reset(); ghostPath.reset()
        ground2.moveTo(0f, vh + 10)
        var x = cx - 10; var first = true
        while (x <= cx + vw + 10) {
            val sx = (x - cx).toFloat(); val sy = (terrain.height(x) - cy).toFloat()
            ground2.lineTo(sx, sy)
            if (first) roadLine.moveTo(sx, sy) else roadLine.lineTo(sx, sy)
            if (terrain.mode == Mode.SAMPLING) {
                val gyy = (terrain.filtered(x, 8.0) - cy).toFloat()
                if (first) ghostPath.moveTo(sx, gyy) else ghostPath.lineTo(sx, gyy)
            }
            first = false; x += 8.0
        }
        ground2.lineTo(vw + 10, vh + 10); ground2.close()
        c.drawPath(ground2, ground)
        if (terrain.mode == Mode.SAMPLING) c.drawPath(ghostPath, ghost)
        c.drawPath(roadLine, line)

        // The samples of the road signal: stems and dots.
        val dx = if (terrain.mode == Mode.SAMPLING) terrain.ts() else 48.0
        var sxw = floor(cx / dx) * dx
        while (sxw < cx + vw) {
            val sx = (sxw - cx).toFloat(); val sy = (terrain.height(sxw) - cy).toFloat()
            c.drawLine(sx, sy, sx, vh, stem); c.drawCircle(sx, sy, 3.5f, dot)
            sxw += dx
        }

        // Coins (x[n] samples) and fuel cans.
        text.textSize = 10f
        for (i in max(0, floor((cx - 320) / 140).toInt())..floor((cx + vw - 300) / 140).toInt()) {
            if (Game.coinExists(i) && i !in game.gotCoins) {
                val px = Game.coinX(i)
                val sx = (px - cx).toFloat(); val sy = (terrain.height(px) - 45 - cy).toFloat()
                c.drawCircle(sx, sy, 9f, coinP); c.drawText("x[n]", sx - 10, sy - 14, text)
            }
        }
        for (j in max(0, floor((cx - 1530) / 4500).toInt())..floor((cx + vw - 1500) / 4500).toInt()) {
            if (j !in game.gotCans) {
                val px = Game.canX(j)
                val sx = (px - cx).toFloat(); val sy = (terrain.height(px) - 52 - cy).toFloat()
                c.drawRect(sx - 10, sy, sx + 10, sy + 26, canP)
                text.textSize = 12f; c.drawText("F", sx - 4, sy + 18, text)
            }
        }

        // The car.
        c.save()
        c.translate((game.x - cx).toFloat(), (game.y - cy).toFloat())
        c.rotate(Math.toDegrees(game.a).toFloat())
        c.drawRoundRect(-36f, -10f, 36f, 14f, 6f, 6f, pBody)
        c.drawRect(-12f, -24f, 16f, -10f, pCab)
        c.drawRect(-8f, -21f, 12f, -12f, pWin)
        for (lx in floatArrayOf(-28f, 28f)) {
            c.save(); c.translate(lx, 14f); c.rotate(Math.toDegrees(game.spin).toFloat())
            c.drawCircle(0f, 0f, 11f, pTire)
            c.drawLine(-9f, 0f, 9f, 0f, pSpoke); c.drawLine(0f, -9f, 0f, 9f, pSpoke)
            c.restore()
        }
        c.restore()
        c.restore()

        drawHud(c)
    }

    private fun drawHud(c: Canvas) {
        val m = 14 * d
        text.textSize = 15 * d; text.textAlign = Paint.Align.LEFT
        c.drawText("${game.points()} pts    ${game.coins} samples", m, m + 12 * d, text)
        c.drawRect(m, m + 20 * d, m + 160 * d, m + 30 * d, barBg)
        c.drawRect(m, m + 20 * d, m + 1.6f * d * game.fuel.toFloat(), m + 30 * d, if (game.fuel < 25) fuelLow else fuelOk)
        text.textSize = 12 * d; text.color = col(0xFF22D3EE)
        c.drawText(hudLine(), m, m + 48 * d, text)
        if (game.voiceOn) {
            val st = if (game.voiceBrake) "BRAKE" else if (game.voiceGas) "FORWARD" else "idle"
            c.drawText("mic: \"${game.heard}\"  [$st]", m, m + 64 * d, text)
        }
        text.color = col(0xFFE6F1FF)

        if (game.toastT > 0 && game.toast.isNotEmpty()) {
            text.textSize = 13 * d; text.textAlign = Paint.Align.CENTER
            val tw = min(w - 40 * d, text.measureText(game.toast) + 28 * d)
            c.drawRoundRect(w / 2 - tw / 2, h * 0.2f - 20 * d, w / 2 + tw / 2, h * 0.2f + 12 * d, 10 * d, 10 * d, toastBg)
            text.color = col(0xFFFDE047)
            c.drawText(game.toast, w / 2, h * 0.2f, text)
            text.color = col(0xFFE6F1FF)
        }

        if (!game.running) return
        // Pedals.
        btnStroke.strokeWidth = 3 * d
        c.drawCircle(brkC[0], brkC[1], brkC[2], if (game.touchBrake || game.voiceBrake) btnPressed else btnFill)
        c.drawCircle(brkC[0], brkC[1], brkC[2], btnStroke)
        c.drawCircle(gasC[0], gasC[1], gasC[2], if (game.touchGas || game.voiceGas) btnPressed else btnFill)
        c.drawCircle(gasC[0], gasC[1], gasC[2], btnStroke)
        text.textSize = 15 * d; text.textAlign = Paint.Align.CENTER
        c.drawText("BRAKE", brkC[0], brkC[1] + 5 * d, text)
        c.drawText("GAS", gasC[0], gasC[1] + 5 * d, text)

        // Top-right controls.
        btnStroke.strokeWidth = 2 * d
        fun chip(r: RectF, label: String, on: Boolean = false) {
            c.drawRoundRect(r, 10 * d, 10 * d, if (on) btnPressed else btnFill)
            c.drawRoundRect(r, 10 * d, 10 * d, btnStroke)
            text.textSize = 14 * d
            c.drawText(label, r.centerX(), r.centerY() + 5 * d, text)
        }
        chip(pauseR, "II")
        chip(micR, if (game.voiceOn) "MIC: ON" else "MIC: OFF", game.voiceOn)
        if (tunable()) {
            chip(minusR, "-"); chip(plusR, "+")
            text.textSize = 14 * d
            c.drawText((if (terrain.mode == Mode.SAMPLING) "fs " else "fc ") + terrain.cutS, labelR.centerX(), labelR.centerY() + 5 * d, text)
        }
        text.textAlign = Paint.Align.LEFT
    }
}
