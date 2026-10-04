package com.westly.lagosdrift

import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Simple arcade driving: speed, steering, the world boundary, and the road underneath.
 * Driving off the road slows the car, dirt roads slow it a little, and potholes make it bounce
 * and lose speed. No physics engine yet. The nose points along -Z at yaw 0, and a positive yaw
 * turns left.
 */
class CarController(
    private val topSpeed: Float = 30f,
    private val accel: Float = 11f,
    private val turnRate: Float = 80f
) {
    val position = Vector3(0f, 0f, 265f)

    var yawDegrees = 0f
        private set

    /** Forward speed in metres per second. Negative means reversing. */
    var speed = 0f
        private set

    val speedKmh: Float
        get() = abs(speed) * 3.6f

    /** One of the TownLayout.SURFACE_ numbers: what the car is driving on right now. */
    var surface = TownLayout.SURFACE_SMOOTH
        private set

    /** How far the car body is lifted by the last pothole bump, in metres. Add it to the car's y. */
    var bounceHeight = 0f
        private set

    /** Set to 1 for the frame a pothole is hit; the game can use it for effects. */
    var potholeHitThisFrame = false
        private set

    private var steerSmoothed = 0f
    private val forward = Vector3()
    private var bounceAmp = 0f
    private var bounceTime = 0f
    private var lastFrontHole: TownLayout.Pothole? = null
    private var lastRearHole: TownLayout.Pothole? = null

    /** Parks the vehicle somewhere, facing a direction, standing still. */
    fun place(x: Float, z: Float, yaw: Float) {
        position.set(x, 0f, z)
        yawDegrees = yaw
        speed = 0f
        steerSmoothed = 0f
        bounceHeight = 0f
    }

    /** Called when the car hits a house or tree: multiplies the speed (0.5 = lose half). */
    fun bump(factor: Float) {
        speed *= factor
    }

    /** steerInput: -1 = left, +1 = right. */
    fun update(delta: Float, steerInput: Float, throttle: Boolean, brake: Boolean) {
        val dt = min(delta, 0.05f)
        potholeHitThisFrame = false

        // Speed.
        if (throttle && !brake) {
            speed += if (speed < 0f) {
                BRAKE_DECEL * dt
            } else {
                accel * dt * (1f - 0.8f * (speed / topSpeed).coerceIn(0f, 1f))
            }
        } else if (brake && !throttle) {
            speed -= if (speed > 0.1f) BRAKE_DECEL * dt else REVERSE_ACCEL * dt
        } else {
            val drag = COAST_DECEL * dt
            speed = when {
                speed > drag -> speed - drag
                speed < -drag -> speed + drag
                else -> 0f
            }
        }
        speed = speed.coerceIn(-MAX_REVERSE, topSpeed)

        // The road underneath slows the car: grass most, then dirt, then broken tarmac.
        surface = RoadSurface.surfaceAt(position.x, position.z)
        val surfaceDrag = when (surface) {
            TownLayout.SURFACE_GROUND -> 0.5f
            TownLayout.SURFACE_DIRT -> 0.25f
            TownLayout.SURFACE_POTHOLED -> 0.08f
            else -> 0f
        }
        speed *= 1f - surfaceDrag * dt

        // Steering: smoothed, weaker when slow and when very fast, flipped when reversing.
        steerSmoothed += (steerInput - steerSmoothed) * min(1f, 8f * dt)
        val speedAbs = abs(speed)
        val grip = (speedAbs / 4f).coerceIn(0f, 1f) * (1f - 0.5f * (speedAbs / topSpeed).coerceIn(0f, 1f))
        val direction = if (speed >= 0f) 1f else -1f
        yawDegrees -= steerSmoothed * turnRate * grip * direction * dt
        if (yawDegrees > 180f) yawDegrees -= 360f
        if (yawDegrees < -180f) yawDegrees += 360f

        // Move.
        forward.set(0f, 0f, -1f).rotate(Vector3.Y, yawDegrees)
        position.mulAdd(forward, speed * dt)

        // Stop at the edge of the world.
        if (position.x < TownLayout.DRIVE_MIN_X || position.x > TownLayout.DRIVE_MAX_X ||
            position.z < TownLayout.DRIVE_MIN_Z || position.z > TownLayout.DRIVE_MAX_Z
        ) {
            position.x = position.x.coerceIn(TownLayout.DRIVE_MIN_X, TownLayout.DRIVE_MAX_X)
            position.z = position.z.coerceIn(TownLayout.DRIVE_MIN_Z, TownLayout.DRIVE_MAX_Z)
            speed *= 0.5f
        }

        checkPotholes(dt)
    }

    /** Front and rear wheels each get one jolt as they drop into a pothole. */
    private fun checkPotholes(dt: Float) {
        val frontHole = RoadSurface.potholeAt(
            position.x + forward.x * WHEEL_OFFSET, position.z + forward.z * WHEEL_OFFSET, WHEEL_REACH
        )
        val rearHole = RoadSurface.potholeAt(
            position.x - forward.x * WHEEL_OFFSET, position.z - forward.z * WHEEL_OFFSET, WHEEL_REACH
        )
        if (frontHole != null && frontHole !== lastFrontHole) jolt(frontHole)
        if (rearHole != null && rearHole !== lastRearHole) jolt(rearHole)
        lastFrontHole = frontHole
        lastRearHole = rearHole

        // The bounce dies away quickly.
        bounceTime += dt
        bounceHeight = bounceAmp * exp(-bounceTime * 5f) * abs(sin(bounceTime * 18f))
    }

    private fun jolt(hole: TownLayout.Pothole) {
        val moving = abs(speed)
        if (moving < 1.5f) return
        potholeHitThisFrame = true
        val size = hole.r.coerceIn(0.4f, 1.6f)
        speed *= 1f - (0.04f + 0.05f * size)
        bounceAmp = min(0.32f, (0.04f + 0.011f * moving) * size)
        bounceTime = 0f
    }

    private companion object {
        const val MAX_REVERSE = 9f
        const val BRAKE_DECEL = 24f
        const val REVERSE_ACCEL = 7f
        const val COAST_DECEL = 5f
        const val WHEEL_OFFSET = 1.5f
        const val WHEEL_REACH = 0.35f
    }
}
