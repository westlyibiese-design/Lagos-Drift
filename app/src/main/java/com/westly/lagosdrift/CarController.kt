package com.westly.lagosdrift

import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Arcade driving on the road: speed, steering, and the ground under the four wheels.
 * The nose points along -Z at yaw 0, and a positive yaw turns left. Pitch is positive with the nose up
 * and roll is positive with the right side up.
 *
 * Each frame the four wheel points ask RoadGround for the road height. The car's height is their
 * average, its pitch and roll follow from them, and a slope slows or speeds the car a little. A wheel
 * that would leave the road is not allowed to: the car slides along the edge instead.
 */
class CarController(
    private val topSpeed: Float = 30f,
    private val accel: Float = 11f,
    private val turnRate: Float = 80f
) {
    val position = Vector3()

    var yawDegrees = 0f
        private set

    /** Nose up is positive. */
    var pitchDegrees = 0f
        private set

    /** Right side up is positive. */
    var rollDegrees = 0f
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
    private val found = Vector3()
    private val wheelY = FloatArray(4)    // front left, front right, rear left, rear right

    /** Parks the vehicle somewhere, facing a direction, standing still. The height comes from [settleOnGround]. */
    fun place(x: Float, z: Float, yaw: Float) {
        position.x = x
        position.z = z
        yawDegrees = yaw
        speed = 0f
        steerSmoothed = 0f
        pitchDegrees = 0f
        rollDegrees = 0f
    }

    /**
     * Puts the car on the road at (or near) where it stands: finds the nearest road within a few metres,
     * and sets height, pitch and roll at once. [knownY] is the height it had before (from a saved game);
     * leave it NaN for a new game. Returns false when there is no road there.
     */
    fun settleOnGround(knownY: Float = Float.NaN): Boolean {
        val refY = if (knownY.isNaN()) HIGH_REFERENCE else knownY + 0.5f
        if (!RoadGround.findNearestRoad(position.x, position.z, SPAWN_SEARCH_RADIUS, refY, found)) return false
        position.set(found.x, found.y, found.z)
        if (sampleWheels(position.x, position.z, position.y + 0.3f)) {
            applyWheelHeights(0f)
        }
        return true
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
        // A slope: uphill (nose up) slows the car a little, downhill speeds it up.
        val slope = (-GRAVITY * sin(Math.toRadians(pitchDegrees.toDouble())).toFloat() * SLOPE_FACTOR)
            .coerceIn(-SLOPE_MAX, SLOPE_MAX)
        speed += slope * dt
        speed = speed.coerceIn(-MAX_REVERSE, topSpeed)

        // Steering: smoothed, weaker when slow and when very fast, flipped when reversing.
        steerSmoothed += (steerInput - steerSmoothed) * min(1f, 8f * dt)
        val speedAbs = abs(speed)
        val grip = (speedAbs / 4f).coerceIn(0f, 1f) * (1f - 0.5f * (speedAbs / topSpeed).coerceIn(0f, 1f))
        val direction = if (speed >= 0f) 1f else -1f
        yawDegrees -= steerSmoothed * turnRate * grip * direction * dt
        if (yawDegrees > 180f) yawDegrees -= 360f
        if (yawDegrees < -180f) yawDegrees += 360f

        // Move, but never let a wheel leave the road.
        forward.set(0f, 0f, -1f).rotate(Vector3.Y, yawDegrees)
        val targetX = position.x + forward.x * speed * dt
        val targetZ = position.z + forward.z * speed * dt
        if (wheelsOnRoad(targetX, targetZ)) {
            position.x = targetX
            position.z = targetZ
        } else {
            slideAlongEdge(targetX, targetZ)
        }
        wheelRollDegrees = (wheelRollDegrees + speed * dt / WHEEL_RADIUS * 57.29578f) % 360f

        // Follow the ground: height, pitch and roll from the four wheels, smoothed so it does not jitter.
        if (sampleWheels(position.x, position.z, position.y)) applyWheelHeights(dt)

        // Very large safety clamp: nobody can leave the map.
        if (position.x < WorldLimits.MIN_X || position.x > WorldLimits.MAX_X ||
            position.z < WorldLimits.MIN_Z || position.z > WorldLimits.MAX_Z
        ) {
            position.x = position.x.coerceIn(WorldLimits.MIN_X, WorldLimits.MAX_X)
            position.z = position.z.coerceIn(WorldLimits.MIN_Z, WorldLimits.MAX_Z)
            speed *= 0.5f
        }
    }

    /** Reads the road height under the four wheels for a car at (cx, cz) with the current heading. */
    private fun sampleWheels(cx: Float, cz: Float, refY: Float): Boolean {
        val rad = yawDegrees * 0.017453292f
        val sinYaw = sin(rad)
        val cosYaw = cos(rad)
        val fwdX = -sinYaw
        val fwdZ = -cosYaw
        val rightX = cosYaw
        val rightZ = -sinYaw
        for (i in 0 until 4) {
            val px = cx + fwdX * FRONT[i] * HALF_WHEELBASE + rightX * SIDE[i] * HALF_TRACK
            val pz = cz + fwdZ * FRONT[i] * HALF_WHEELBASE + rightZ * SIDE[i] * HALF_TRACK
            val h = RoadGround.heightAt(px, pz, refY, MAX_DROP)
            if (h.isNaN()) return false
            wheelY[i] = h
        }
        return true
    }

    private fun wheelsOnRoad(x: Float, z: Float): Boolean = sampleWheels(x, z, position.y)

    /** Sets height, pitch and roll from the wheel heights. [dt] = 0 jumps there at once. */
    private fun applyWheelHeights(dt: Float) {
        val front = (wheelY[0] + wheelY[1]) / 2f
        val rear = (wheelY[2] + wheelY[3]) / 2f
        val left = (wheelY[0] + wheelY[2]) / 2f
        val right = (wheelY[1] + wheelY[3]) / 2f
        val targetY = (wheelY[0] + wheelY[1] + wheelY[2] + wheelY[3]) / 4f
        val targetPitch = Math.toDegrees(atan2((front - rear).toDouble(), (2f * HALF_WHEELBASE).toDouble())).toFloat()
        val targetRoll = Math.toDegrees(atan2((right - left).toDouble(), (2f * HALF_TRACK).toDouble())).toFloat()
        if (dt <= 0f) {
            position.y = targetY
            pitchDegrees = targetPitch
            rollDegrees = targetRoll
            return
        }
        val ky = 1f - exp(-20f * dt)
        position.y += (targetY - position.y) * ky
        // Never let the car sink into the road while the smoothing catches up.
        if (position.y < targetY - 0.05f) position.y = targetY - 0.05f
        val ka = 1f - exp(-12f * dt)
        pitchDegrees += (targetPitch - pitchDegrees) * ka
        rollDegrees += (targetRoll - rollDegrees) * ka
    }

    /**
     * The move was blocked because a wheel would leave the road. Looks at which side of the spot the road
     * is on, takes away the part of the move that points off the road, and slides along the edge.
     */
    private fun slideAlongEdge(targetX: Float, targetZ: Float) {
        val mx = targetX - position.x
        val mz = targetZ - position.z
        val length = sqrt(mx * mx + mz * mz)
        if (length < 1e-5f) return

        val ix = (if (wheelsOnRoad(targetX + PROBE, targetZ)) 1f else 0f) -
            (if (wheelsOnRoad(targetX - PROBE, targetZ)) 1f else 0f)
        val iz = (if (wheelsOnRoad(targetX, targetZ + PROBE)) 1f else 0f) -
            (if (wheelsOnRoad(targetX, targetZ - PROBE)) 1f else 0f)
        val inwardLength = sqrt(ix * ix + iz * iz)
        if (inwardLength < 0.01f) {
            speed *= 0.4f
            return
        }
        val inX = ix / inwardLength
        val inZ = iz / inwardLength
        val towardRoad = mx * inX + mz * inZ
        val removed = if (towardRoad < 0f) towardRoad else 0f
        val slideX = mx - inX * removed
        val slideZ = mz - inZ * removed
        val slideLength = sqrt(slideX * slideX + slideZ * slideZ)
        val newX = position.x + slideX
        val newZ = position.z + slideZ
        if (slideLength > 1e-5f && wheelsOnRoad(newX, newZ)) {
            position.x = newX
            position.z = newZ
            speed *= (slideLength / length).coerceIn(0.3f, 1f)
        } else {
            speed *= 0.3f
        }
    }

    private companion object {
        const val MAX_REVERSE = 9f
        const val BRAKE_DECEL = 24f
        const val REVERSE_ACCEL = 7f
        const val COAST_DECEL = 5f
        const val WHEEL_RADIUS = 0.33f
        const val GRAVITY = 9.81f
        const val SLOPE_FACTOR = 0.35f
        const val SLOPE_MAX = 3f

        // The Camry's wheels (from the model): 2.776 m between axles, 1.564 m between wheel centres.
        const val HALF_WHEELBASE = 1.388f
        const val HALF_TRACK = 0.79f
        val FRONT = floatArrayOf(1f, 1f, -1f, -1f)
        val SIDE = floatArrayOf(-1f, 1f, -1f, 1f)

        const val MAX_DROP = 1.2f           // a wheel finds no road if it is more than this above it
        const val PROBE = 0.6f              // how far to look sideways to learn where the road is
        const val SPAWN_SEARCH_RADIUS = 15f
        const val HIGH_REFERENCE = 10000f   // "any height": picks the highest surface under the car
    }
}
