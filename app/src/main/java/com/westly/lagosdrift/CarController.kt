package com.westly.lagosdrift

import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.min

/**
 * Simple arcade driving: speed, steering and the edge of the world. No physics engine yet.
 * The nose points along -Z at yaw 0, and a positive yaw turns left.
 */
class CarController(
    private val topSpeed: Float = 30f,
    private val accel: Float = 11f,
    private val turnRate: Float = 80f
) {
    val position = Vector3()

    var yawDegrees = 0f
        private set

    /** Forward speed in metres per second. Negative means reversing. */
    var speed = 0f
        private set

    val speedKmh: Float
        get() = abs(speed) * 3.6f

    private var steerSmoothed = 0f

    /** How far the steering is turned, -1 (left) to +1 (right). The front wheels follow it. */
    val steerAmount: Float
        get() = steerSmoothed

    /** How far the wheels have rolled, in degrees. Grows when driving forward. */
    var wheelRollDegrees = 0f
        private set
    private val forward = Vector3()

    /** Parks the vehicle somewhere, facing a direction, standing still. */
    fun place(x: Float, z: Float, yaw: Float) {
        position.set(x, 0f, z)
        yawDegrees = yaw
        speed = 0f
        steerSmoothed = 0f
    }

    /** Called when the car hits something: multiplies the speed (0.5 = lose half). */
    fun bump(factor: Float) {
        speed *= factor
    }

    /** steerInput: -1 = left, +1 = right. */
    fun update(delta: Float, steerInput: Float, throttle: Boolean, brake: Boolean) {
        val dt = min(delta, 0.05f)

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
        wheelRollDegrees = (wheelRollDegrees + speed * dt / WHEEL_RADIUS * 57.29578f) % 360f

        // Very large safety clamp: nobody can leave the map.
        if (position.x < WorldLimits.MIN_X || position.x > WorldLimits.MAX_X ||
            position.z < WorldLimits.MIN_Z || position.z > WorldLimits.MAX_Z
        ) {
            position.x = position.x.coerceIn(WorldLimits.MIN_X, WorldLimits.MAX_X)
            position.z = position.z.coerceIn(WorldLimits.MIN_Z, WorldLimits.MAX_Z)
            speed *= 0.5f
        }
    }

    private companion object {
        const val MAX_REVERSE = 9f
        const val BRAKE_DECEL = 24f
        const val REVERSE_ACCEL = 7f
        const val COAST_DECEL = 5f
        const val WHEEL_RADIUS = 0.33f
    }
}
