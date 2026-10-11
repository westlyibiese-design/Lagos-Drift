package com.westly.lagosdrift

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.AudioDevice
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The sound of the Camry, made in code (the game has no sound files): the engine, whose pitch follows
 * the speed and the gear and whose loudness follows the gas; the tyre screech in hard turns and
 * braking; the rumble and crunch of rough ground; wind; the thump of a landing, a bump or a crash; and
 * an echo inside the tunnel.
 *
 * The game thread only writes a few numbers with [update]. A background thread makes the sound in
 * small blocks and plays it with one libGDX AudioDevice, so nothing is allocated while playing and
 * the game never waits for the sound. [start] and [stop] are called when the game starts, goes to the
 * background and comes back.
 */
class CarAudio {
    /** The exact reason the sound could not start, or null. The game shows it. */
    @Volatile
    var errorText: String? = null
        private set

    // Written by the game thread, read by the sound thread.
    @Volatile private var engineOn = false
    @Volatile private var speedMs = 0f
    @Volatile private var throttleOn = false
    @Volatile private var skid = 0f
    @Volatile private var rough = 0f
    @Volatile private var airborne = false
    @Volatile private var inTunnel = false
    @Volatile private var muted = false
    @Volatile private var impactIn = 0f

    @Volatile private var running = false
    private var device: AudioDevice? = null
    private var thread: Thread? = null

    // Only the sound thread touches these.
    private var rpm = 0.14f
    private var enginePhase = 0f
    private var engineLevel = 0f
    private var masterLevel = 0f
    private var skidLevel = 0f
    private var roughLevel = 0f
    private var windLevel = 0f
    private var reverbMix = 0f
    private var thud = 0f
    private var thudPhase = 0f
    private var skidPhase = 0f
    private var seed = 0x2545F491
    private var engineLow = 0f
    private var skidLow = 0f
    private var roughLow = 0f
    private var windLow = 0f
    private val delayA = FloatArray(DELAY_A)
    private val delayB = FloatArray(DELAY_B)
    private var posA = 0
    private var posB = 0
    private val block = ShortArray(BLOCK)

    /** Called every frame by the game thread. [impact] comes from CarController.takeImpact. */
    fun update(
        engineOn: Boolean, speed: Float, throttle: Boolean, skid: Float, rough: Float,
        airborne: Boolean, tunnel: Boolean, muted: Boolean, impact: Float
    ) {
        this.engineOn = engineOn
        this.speedMs = abs(speed)
        this.throttleOn = throttle
        this.skid = skid
        this.rough = rough
        this.airborne = airborne
        this.inTunnel = tunnel
        this.muted = muted
        if (impact > impactIn) impactIn = impact
    }

    /** Opens the sound device and starts the sound thread. Does nothing when it is already running. */
    fun start() {
        if (running) return
        try {
            val dev = Gdx.audio.newAudioDevice(SAMPLE_RATE, true)
            device = dev
            running = true
            val t = Thread({ loop(dev) }, "LagosDriftSound")
            t.isDaemon = true
            thread = t
            t.start()
        } catch (e: Exception) {
            running = false
            device = null
            errorText = "Sound could not start: $e"
        }
    }

    /** Stops the sound thread and closes the device (the game goes to the background, or ends). */
    fun stop() {
        running = false
        try {
            thread?.join(800)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        thread = null
        try {
            device?.dispose()
        } catch (e: Exception) {
            Gdx.app.error("LagosDrift", "Sound close error: $e")
        }
        device = null
    }

    private fun loop(dev: AudioDevice) {
        try {
            while (running) {
                render(block)
                dev.writeSamples(block, 0, BLOCK)
            }
        } catch (e: Exception) {
            running = false
            errorText = "Sound stopped: $e"
        }
    }

    // ------------------------------------------------------------------ making the sound

    private fun noise(): Float {
        var x = seed
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        seed = x
        return x * (1f / 2147483648f)
    }

    /** The engine speed (0..1) for a road speed: five gears, and the engine revs up a little with the gas. */
    private fun revsFor(speed: Float, gas: Boolean): Float {
        var g = 0
        while (g < GEAR_TOP.size - 1 && speed > GEAR_TOP[g]) g++
        val low = if (g == 0) 0f else GEAR_TOP[g - 1]
        val share = ((speed - low) / (GEAR_TOP[g] - low)).coerceIn(0f, 1f)
        val wheels = if (speed < 0.8f) IDLE else 0.30f + 0.60f * share
        val blip = if (gas) 0.07f else 0f
        val standing = if (gas) IDLE + 0.28f else IDLE
        return max(standing, wheels + blip)
    }

    private fun render(out: ShortArray) {
        val dt = BLOCK.toFloat() / SAMPLE_RATE
        val on = engineOn && !muted
        val spd = speedMs
        val gas = throttleOn && on
        val air = airborne

        // A knock since the last block starts a thump.
        val knock = impactIn
        if (knock > 0f) {
            impactIn = 0f
            thud = max(thud, min(1f, knock / 7f))
        }

        // Smooth the slow things once per block.
        var goal = if (on) revsFor(spd, gas) else rpm
        if (air && on) goal = min(1f, goal + 0.25f)
        val rpmFrom = rpm
        rpm += (goal - rpm) * min(1f, (if (goal > rpm) 6f else 3.5f) * dt)
        val rpmTo = rpm

        val engineGoal = if (on) 0.34f + (if (gas) 0.28f else 0.06f) + 0.18f * rpm else 0f
        engineLevel += (engineGoal - engineLevel) * min(1f, 5f * dt)
        masterLevel += ((if (muted) 0f else 1f) - masterLevel) * min(1f, 6f * dt)
        val skidGoal = if (muted) 0f else skid
        skidLevel += (skidGoal - skidLevel) * min(1f, 12f * dt)
        val roughGoal = if (muted) 0f else rough * (spd / 8f).coerceIn(0f, 1f) * (if (air) 0f else 1f)
        roughLevel += (roughGoal - roughLevel) * min(1f, 8f * dt)
        val windGoal = if (muted) 0f else (spd / 30f).coerceIn(0f, 1f)
        windLevel += (windGoal - windLevel) * min(1f, 4f * dt)
        reverbMix += ((if (inTunnel) 0.55f else 0f) - reverbMix) * min(1f, 2.5f * dt)
        val load = if (gas) 1f else 0f
        val brightness = 0.22f + 0.5f * rpmTo

        for (i in 0 until BLOCK) {
            val mixRpm = rpmFrom + (rpmTo - rpmFrom) * (i.toFloat() / BLOCK)

            // The engine: the firing rate and its harmonics, squashed a little (more with the gas).
            enginePhase += mixRpm * ENGINE_HZ / SAMPLE_RATE
            if (enginePhase >= 1f) enginePhase -= 1f
            val p = enginePhase * TWO_PI
            var e = sin(p) + 0.62f * sin(2f * p + 0.4f) + 0.42f * sin(3f * p + 1.1f) +
                0.28f * sin(4f * p) + 0.18f * sin(5f * p + 0.7f) + 0.10f * sin(6f * p)
            e = e * 0.3f + noise() * 0.05f
            val drive = e * (1.1f + 1.4f * load)
            e = drive / (1f + abs(drive))
            engineLow += brightness * (e - engineLow)
            val engine = (engineLow * 0.7f + e * 0.3f) * engineLevel

            // Tyres: a thin, high hiss with a faint squeal that rises with the screech.
            val n1 = noise()
            skidLow += 0.22f * (n1 - skidLow)
            skidPhase += (880f + 380f * skidLevel) / SAMPLE_RATE
            if (skidPhase >= 1f) skidPhase -= 1f
            val screech = ((n1 - skidLow) * 0.30f + sin(skidPhase * TWO_PI) * 0.06f) * skidLevel

            // Rough ground: a low rumble with crunch in it.
            val n2 = noise()
            roughLow += 0.07f * (n2 - roughLow)
            val crunch = if (abs(n2) > 0.93f) n2 * 0.6f else 0f
            val rumble = (roughLow * 2.2f + crunch + sin(p * 0.5f) * 0.05f) * roughLevel * 0.55f

            // Wind and road hiss.
            val n3 = noise()
            windLow += 0.04f * (n3 - windLow)
            val wind = windLow * 1.6f * windLevel * windLevel * 0.5f

            // The thump of a knock: a low tone and a burst of noise, dying away.
            var knockSound = 0f
            if (thud > 0.001f) {
                thudPhase += THUD_HZ / SAMPLE_RATE
                if (thudPhase >= 1f) thudPhase -= 1f
                knockSound = thud * (sin(thudPhase * TWO_PI) * 0.85f + noise() * 0.30f)
                thud *= THUD_DECAY
            } else {
                thud = 0f
            }

            // The tunnel: two echoes of the whole sound.
            val dry = engine + screech + rumble + wind + knockSound
            val a = delayA[posA]
            val b = delayB[posB]
            delayA[posA] = dry + a * 0.46f
            delayB[posB] = dry + b * 0.40f
            if (++posA >= DELAY_A) posA = 0
            if (++posB >= DELAY_B) posB = 0
            val wet = (a + b) * 0.5f

            var s = (dry + wet * reverbMix) * masterLevel * MASTER
            if (s > 0.98f) s = 0.98f
            if (s < -0.98f) s = -0.98f
            out[i] = (s * 32000f).toInt().toShort()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 22050
        const val BLOCK = 512
        const val TWO_PI = 6.2831855f
        const val MASTER = 0.9f
        const val IDLE = 0.14f
        const val ENGINE_HZ = 210f          // firing rate at full revs (a four-cylinder at about 6000 rpm)
        const val THUD_HZ = 52f
        const val THUD_DECAY = 0.99935f
        const val DELAY_A = 1511            // 68 ms
        const val DELAY_B = 2203            // 100 ms

        // Road speed (m/s) at which each of the five gears reaches its top revs.
        val GEAR_TOP = floatArrayOf(7f, 12.5f, 18f, 24f, 31f)
    }
}
