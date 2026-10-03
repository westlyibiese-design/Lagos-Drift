package com.westly.lagosdrift

import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.min

/**
 * Simple arcade driving: speed, steering and a world boundary.
 * No physics engine yet. The nose points along -Z at yaw 0, and a positive yaw turns left.
 */
class CarController {
    val position = Vector3(0f, 0f, 240f)

    var yawDegrees = 0f
        private set

    /** Forward speed in metres per second. Negative means reversing. */
    var speed = 0f
        private set

    val speedKmh: Float
        get() = abs(speed) * 3.6f

    private var steerSmoothed = 0f
    private val forward = Vector3()

    /** Called when the car hits a house or tree: multiplies the speed (0.5 = lose half). */
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
                ACCEL * dt * (1f - 0.8f * (speed / MAX_SPEED).coerceIn(0f, 1f))
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
        speed = speed.coerceIn(-MAX_REVERSE, MAX_SPEED)

        // Steering: smoothed, weaker when slow and when very fast, flipped when reversing.
        steerSmoothed += (steerInput - steerSmoothed) * min(1f, 8f * dt)
        val speedAbs = abs(speed)
        val grip = (speedAbs / 4f).coerceIn(0f, 1f) * (1f - 0.5f * (speedAbs / MAX_SPEED).coerceIn(0f, 1f))
        val direction = if (speed >= 0f) 1f else -1f
        yawDegrees -= steerSmoothed * MAX_TURN_RATE * grip * direction * dt
        if (yawDegrees > 180f) yawDegrees -= 360f
        if (yawDegrees < -180f) yawDegrees += 360f

        // Move.
        forward.set(0f, 0f, -1f).rotate(Vector3.Y, yawDegrees)
        position.mulAdd(forward, speed * dt)

        // Stop at the edge of the drivable area (the beach is east of it).
        if (position.x < MIN_X || position.x > MAX_X || position.z < MIN_Z || position.z > MAX_Z) {
            position.x = position.x.coerceIn(MIN_X, MAX_X)
            position.z = position.z.coerceIn(MIN_Z, MAX_Z)
            speed *= 0.5f
        }
    }

    private companion object {
        const val MAX_SPEED = 30f        // about 108 km/h
        const val MAX_REVERSE = 9f
        const val ACCEL = 11f
        const val BRAKE_DECEL = 24f
        const val REVERSE_ACCEL = 7f
        const val COAST_DECEL = 5f
        const val MAX_TURN_RATE = 80f    // degrees per second
        const val MIN_X = -280f
        const val MAX_X = 150f
        const val MIN_Z = -280f
        const val MAX_Z = 280f
    }
}
