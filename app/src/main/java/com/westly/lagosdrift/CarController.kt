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

    // The last place where all four wheels were on the road, and how long they have been off it.
    private var goodX = 0f
    private var goodY = 0f
    private var goodZ = 0f
    private var goodYaw = 0f
    private var hasGood = false
    private var offRoadTime = 0f
    private var spotX = 0f
    private var spotZ = 0f
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

        // The car can never be left stuck: if a wheel is off the road (a bump from traffic, a seam in the
        // ground, a chunk that just loaded) it is brought back onto the road before anything else happens.
        if (sampleWheels(position.x, position.z, position.y)) {
            offRoadTime = 0f
            goodX = position.x
            goodY = position.y
            goodZ = position.z
            goodYaw = yawDegrees
            hasGood = true
        } else {
            offRoadTime += dt
            getBackOnRoad(dt)
            return
        }

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
        val oldYaw = yawDegrees
        yawDegrees -= steerSmoothed * turnRate * grip * direction * dt
        if (yawDegrees > 180f) yawDegrees -= 360f
        if (yawDegrees < -180f) yawDegrees += 360f
        // A turn that would swing a wheel off the road is not allowed (this is how a car used to get stuck).
        if (!sampleWheels(position.x, position.z, position.y)) yawDegrees = oldYaw

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
     * Called when a wheel is off the road. Moves the car to the nearest spot where all four wheels are on the
     * road (up to [RECOVER_SPEED] metres per second, so it is a smooth slide and not a jump). When there is no
     * such spot close by and it has been off the road for a second, the car goes back to the last place it
     * was fully on the road.
     */
    private fun getBackOnRoad(dt: Float) {
        speed *= 0.5f
        if (findSpotOnRoad()) {
            val dx = spotX - position.x
            val dz = spotZ - position.z
            val dist = sqrt(dx * dx + dz * dz)
            val step = min(dist, RECOVER_SPEED * dt)
            if (dist > 1e-4f) {
                position.x += dx / dist * step
                position.z += dz / dist * step
            }
        } else if (offRoadTime > 1f && hasGood) {
            position.set(goodX, goodY, goodZ)
            yawDegrees = goodYaw
            speed = 0f
            offRoadTime = 0f
        }
        // Keep the height and the lean following the ground wherever the wheels do touch it.
        if (sampleWheels(position.x, position.z, position.y)) applyWheelHeights(dt)
    }

    /** Looks outwards in rings of 1 m for a spot where all four wheels are on the road. Result in spotX / spotZ. */
    private fun findSpotOnRoad(): Boolean {
        var r = 1
        while (r <= RECOVER_RADIUS) {
            val steps = min(48, 8 * r)
            for (k in 0 until steps) {
                val a = k * 6.2831855f / steps
                val px = position.x + cos(a) * r
                val pz = position.z + sin(a) * r
                if (sampleWheels(px, pz, position.y)) {
                    spotX = px
                    spotZ = pz
                    return true
                }
            }
            r++
        }
        return false
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

        // Where is the road? Look sideways a little, then further, until the road shows up on some side.
        var ix = 0f
        var iz = 0f
        var inwardLength = 0f
        var probe = PROBE
        while (probe <= PROBE_MAX) {
            ix = (if (wheelsOnRoad(targetX + probe, targetZ)) 1f else 0f) -
                (if (wheelsOnRoad(targetX - probe, targetZ)) 1f else 0f)
            iz = (if (wheelsOnRoad(targetX, targetZ + probe)) 1f else 0f) -
                (if (wheelsOnRoad(targetX, targetZ - probe)) 1f else 0f)
            inwardLength = sqrt(ix * ix + iz * iz)
            if (inwardLength >= 0.01f) break
            probe *= 2.5f
        }
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
        const val PROBE_MAX = 6f            // and the furthest it looks when the road is not right there
        const val RECOVER_RADIUS = 20       // metres searched for a spot on the road when a wheel is off it
        const val RECOVER_SPEED = 8f        // metres per second the car slides back onto the road
        const val SPAWN_SEARCH_RADIUS = 15f
        const val HIGH_REFERENCE = 10000f   // "any height": picks the highest surface under the car
    }
}
