package com.expresshighway2.game

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Car physics + rules. No Android classes here so it can be unit-tested on the JVM. */
class Game(val terrain: Terrain) {
    var x = 150.0; var y = 0.0
    var vx = 0.0; var vy = 0.0
    var a = 0.0; var w = 0.0
    var spin = 0.0
    /** Number of wheels touching the road this step (0 = airborne). */
    @Volatile var contacts = 0
    var fuel = 100.0
    var coins = 0
    var cp = 0
    var maxX = 150.0

    @Volatile var running = false
    @Volatile var over: String? = null

    // Inputs: touch buttons and voice commands are tracked separately so they never cancel each other.
    @Volatile var touchGas = false
    @Volatile var touchBrake = false
    @Volatile var voiceGas = false
    @Volatile var voiceBrake = false
    /** True while voice control is on. Voice "brake" stops the car but does not reverse it. */
    @Volatile var voiceOn = false
    @Volatile var heard = ""

    var toast = ""; var toastT = 0.0
    private var lowT = 0.0
    private var lastH = 1

    val gotCoins = HashSet<Int>()
    val gotCans = HashSet<Int>()

    private val gas get() = touchGas || voiceGas
    private val brake get() = touchBrake || voiceBrake

    fun say(msg: String, secs: Double = 4.0) { toast = msg; toastT = secs }

    fun points() = cp + max(0, floor((maxX - 150) / 12).toInt())
    fun meters() = max(0, floor((maxX - 150) / 12).toInt())

    fun reset() {
        terrain.resetTuning()
        x = 150.0; y = terrain.height(150.0) - 45
        vx = 0.0; vy = 0.0; a = 0.0; w = 0.0; spin = 0.0
        fuel = 100.0; coins = 0; cp = 0; maxX = 150.0
        gotCoins.clear(); gotCans.clear()
        over = null; toast = ""; toastT = 0.0; lowT = 0.0; lastH = 1
        touchGas = false; touchBrake = false; voiceGas = false; voiceBrake = false
    }

    companion object {
        fun hash(i: Int): Double {
            val s = sin(i * 127.1) * 43758.5453
            return ((s % 1.0) + 1.0) % 1.0
        }
        fun coinExists(i: Int) = i >= 0 && hash(i) < 0.45
        fun coinX(i: Int) = 300.0 + i * 140.0
        fun canX(j: Int) = 1500.0 + j * 4500.0
    }

    /** Returns true if the driver's head hit the ground. */
    private fun step(dt: Double): Boolean {
        var fxT = 0.0; var fyT = 900.0; var tq = 0.0; var grounded = 0
        val c = cos(a); val s = sin(a)
        val g = gas; val b = brake
        for (lx in doubleArrayOf(-28.0, 28.0)) {
            val rx = lx * c - 14 * s
            val ry = lx * s + 14 * c
            val wx = x + rx; val wy = y + ry + 11
            val p = wy - terrain.height(wx)
            if (p > 0) {
                grounded++
                val vyw = vy + w * rx
                val vxw = vx - w * ry
                // Progressive spring: gets much stiffer when heavily compressed, so hard landings do not bottom out.
                var fy = -min(6000.0, max(0.0, 60 * p + 4 * p * p + 10 * vyw))
                var fx = 0.0
                val sl = (terrain.height(wx + 2) - terrain.height(wx - 2)) / 4
                val n = hypot(1.0, sl)
                val tx = 1 / n; val ty = sl / n
                if (g) { fx += 350 * tx; fy += 350 * ty }
                if (b) {
                    if (vx > 15) fx -= min(400.0, vxw * 3)
                    else if (!voiceOn) { fx -= 250 * tx; fy -= 250 * ty }
                }
                fxT += fx; fyT += fy
                // Drive force acts near the centre of mass (small lever) so accelerating does not cause wheelies.
                tq += rx * fy - 3 * ry * fx / 14
            }
        }
        fxT -= vx
        contacts = grounded
        if (grounded > 0) {
            // Stabiliser: gently align the body with the road under the wheels.
            val gAng = Math.atan2(terrain.height(x + 28 * c) - terrain.height(x - 28 * c), 56 * c)
            tq -= 12000 * (a - gAng)
        }
        val air = if (grounded == 0) ((if (b) 1 else 0) - (if (g) 1 else 0)) * 2400.0 else 0.0
        tq += air - (if (grounded == 0) 900 else 3000) * w
        vx += fxT * dt; vy += fyT * dt; w += tq / 1100 * dt
        x += vx * dt; y += vy * dt; a += w * dt
        val hx = x + 24 * sin(a); val hy = y - 24 * cos(a)
        return hy > terrain.height(hx) - 2
    }

    fun update(dt: Double) {
        if (!running || over != null) return
        terrain.animate(dt)
        val n = max(1, ceil(dt * 240).toInt())
        repeat(n) {
            if (step(dt / n)) { over = "Signal clipped!"; running = false; return }
        }
        maxX = max(maxX, x)
        spin += vx * dt / 11
        if (gas) fuel -= dt * 4.0
        if (fuel <= 0) {
            fuel = 0.0; lowT += dt
            if (abs(vx) < 10 && lowT > 1.2) { over = "Out of fuel"; running = false; return }
        } else lowT = 0.0

        for (i in floor((x - 60 - 300) / 140).toInt()..floor((x + 60 - 300) / 140).toInt()) {
            if (coinExists(i) && i !in gotCoins && abs(x - coinX(i)) < 32 &&
                abs(y - (terrain.height(coinX(i)) - 45)) < 50
            ) {
                gotCoins.add(i); coins++
                cp += if (terrain.mode == Mode.FILTER) 5 * terrain.cutS else 10
            }
        }
        for (j in floor((x - 60 - 1500) / 4500).toInt()..floor((x + 60 - 1500) / 4500).toInt()) {
            if (j >= 0 && j !in gotCans && abs(x - canX(j)) < 36 &&
                abs(y - (terrain.height(canX(j)) - 40)) < 55
            ) { gotCans.add(j); fuel = min(100.0, fuel + 40) }
        }
        if (terrain.mode == Mode.FOURIER) {
            val h = terrain.harmonics(x)
            if (h > lastH) { lastH = h; say("Harmonic $h added: ${h}x f0 makes the road bumpier") }
        }
        if (toastT > 0) toastT -= dt
    }
}
