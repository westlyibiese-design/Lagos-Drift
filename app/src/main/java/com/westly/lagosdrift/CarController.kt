package com.westly.lagosdrift

import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Arcade driving on the road: speed, steering, and the ground under the four wheels.
 * The nose points along -Z at yaw 0, and a positive yaw turns left. Pitch is positive with the nose up
 * and roll is positive with the right side up.
 *
 * Each frame the four wheel points ask RoadGround for the ground height. The ground gives the height,
 * pitch and roll the body wants, and the body follows them on springs: it bounces over bumps, rocks
 * when it climbs rubble, leans in turns, squats when it accelerates, dives when it brakes, flies off a
 * crest and lands with a thump. The tunnel and the sand are rough: they slow the car and shake it, but
 * a slope or rubble can never stop it while the gas is held. A wheel that would leave the ground is
 * not allowed to: the car slides along the edge instead.
 */
class CarController(
    private val topSpeed: Float = 30f,
    private val accel: Float = 11f,
    private val turnRate: Float = 80f,
    private val halfWheelbase: Float = 1.388f,
    private val halfTrack: Float = 0.79f
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

    /** 0 on asphalt, about 0.7 on the tunnel rubble and 0.55 on the sand: how rough the ground is. */
    var roughness = 0f
        private set

    /** 0..1: how much the tyres are screeching (hard steering at speed, hard braking). */
    var skidAmount = 0f
        private set

    /** True while the car is in the air (after a crest or a ramp). */
    var isAirborne = false
        private set

    private var impactEvent = 0f

    /** The hardest knock since the last call (a landing, a heavy bump, a wall, another car), then 0. */
    fun takeImpact(): Float {
        val v = impactEvent
        impactEvent = 0f
        return v
    }

    private val forward = Vector3()
    private val found = Vector3()

    // The last place where all four wheels were on the ground, and how long they have been off it.
    private var goodX = 0f
    private var goodY = 0f
    private var goodZ = 0f
    private var goodYaw = 0f
    private var hasGood = false
    private var offRoadTime = 0f
    private var spotX = 0f
    private var spotZ = 0f
    private val wheelY = FloatArray(4)      // front left, front right, rear left, rear right
    private val wheelKind = IntArray(4)     // what each wheel stands on (RoadGround.SURFACE_...)

    // The suspension. The ground height under the wheels (groundRef) is what is asked about; the body
    // height (position.y) follows it on a spring, so it can bounce and fly.
    private var groundRef = 0f
    private var vy = 0f
    private var prevTargetY = 0f
    private var targetVel = 0f
    private var pitchVel = 0f
    private var rollVel = 0f
    private var surfaceTop = 1f
    private var lastSpeed = 0f
    private var accSm = 0f

    /** Parks the vehicle somewhere, facing a direction, standing still. The height comes from [settleOnGround]. */
    fun place(x: Float, z: Float, yaw: Float) {
        position.x = x
        position.z = z
        yawDegrees = yaw
        speed = 0f
        steerSmoothed = 0f
        pitchDegrees = 0f
        rollDegrees = 0f
        vy = 0f
        pitchVel = 0f
        rollVel = 0f
        targetVel = 0f
        surfaceTop = 1f
        roughness = 0f
        skidAmount = 0f
        isAirborne = false
        lastSpeed = 0f
        accSm = 0f
        impactEvent = 0f
    }

    /**
     * Puts the car on the ground at (or near) where it stands: first the exact spot, then the nearest road
     * within a few metres, then the nearest sand, and sets height, pitch and roll at once. [knownY] is the
     * height it had before (from a saved game); leave it NaN for a new game. Returns false when there is
     * nothing to stand on there.
     */
    fun settleOnGround(knownY: Float = Float.NaN): Boolean {
        val refY = if (knownY.isNaN()) HIGH_REFERENCE else knownY + 0.5f
        val exact = RoadGround.carHeightAt(position.x, position.z, refY, NO_LIMIT_DROP)
        if (!exact.isNaN()) {
            found.set(position.x, exact, position.z)
        } else if (!RoadGround.findNearestRoad(position.x, position.z, SPAWN_SEARCH_RADIUS, refY, found) &&
            !RoadGround.carFindNearest(position.x, position.z, SPAWN_SEARCH_RADIUS, refY, found)
        ) {
            return false
        }
        position.set(found.x, found.y, found.z)
        groundRef = position.y
        if (sampleWheels(position.x, position.z, position.y + 0.3f)) {
            applyWheelHeights(0f)
        }
        return true
    }

    /** Called when the car hits something: multiplies the speed (0.5 = lose half). */
    fun bump(factor: Float) {
        val lost = abs(speed) * (1f - factor)
        if (lost > 1f) impactEvent = max(impactEvent, lost * 1.2f)
        speed *= factor
    }

    /** steerInput: -1 = left, +1 = right. */
    fun update(delta: Float, steerInput: Float, throttle: Boolean, brake: Boolean) {
        val dt = min(delta, 0.05f)
        if (dt <= 0f) return

        // The car can never be left stuck: if a wheel has no ground (a bump from traffic, a seam in the
        // ground, a chunk that just loaded) it is brought back onto the ground before anything else happens.
        if (sampleWheels(position.x, position.z, groundRef)) {
            offRoadTime = 0f
            goodX = position.x
            goodY = position.y
            goodZ = position.z
            goodYaw = yawDegrees
            hasGood = true
            readSurface(dt)
        } else {
            offRoadTime += dt
            getBackOnRoad(dt)
            return
        }

        // The top speed is lower on the rubble and on the sand.
        val top = topSpeed * surfaceTop

        // Speed. In the air the wheels turn freely and nothing pushes the car.
        if (!isAirborne) {
            val accelNow = accel * (1f - 0.35f * roughness)
            if (throttle && !brake) {
                speed += if (speed < 0f) {
                    BRAKE_DECEL * dt
                } else {
                    accelNow * dt * (1f - 0.8f * (speed / top).coerceIn(0f, 1f))
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
            // Rough ground holds the car back. It is always weaker than the engine, so the gas always wins.
            val rolling = SURFACE_DRAG * roughness * dt
            speed = when {
                speed > rolling -> speed - rolling
                speed < -rolling -> speed + rolling
                else -> speed
            }
            // A slope: uphill (nose up) slows the car a little, downhill speeds it up.
            val slope = (-GRAVITY * sin(Math.toRadians(pitchDegrees.toDouble())).toFloat() * SLOPE_FACTOR)
                .coerceIn(-SLOPE_MAX, SLOPE_MAX)
            speed += slope * dt
        }
        // Coming onto rough ground fast: the car is slowed down, not stopped dead.
        if (speed > top) speed = max(top, speed - OVER_TOP_DECEL * dt)
        speed = speed.coerceIn(-MAX_REVERSE, topSpeed)

        // Steering: smoothed, weaker when slow, when very fast and on rough ground, flipped when reversing.
        steerSmoothed += (steerInput - steerSmoothed) * min(1f, 8f * dt)
        val speedAbs = abs(speed)
        var grip = (speedAbs / 4f).coerceIn(0f, 1f) * (1f - 0.5f * (speedAbs / topSpeed).coerceIn(0f, 1f))
        grip *= 1f - 0.3f * roughness
        if (isAirborne) grip = 0f
        val direction = if (speed >= 0f) 1f else -1f
        val oldYaw = yawDegrees
        val yawRate = -steerSmoothed * turnRate * grip * direction
        yawDegrees += yawRate * dt
        if (yawDegrees > 180f) yawDegrees -= 360f
        if (yawDegrees < -180f) yawDegrees += 360f
        // A turn that would swing a wheel off the ground is not allowed (this is how a car used to get stuck).
        if (!sampleWheels(position.x, position.z, groundRef)) yawDegrees = oldYaw

        // Tyre screech: hard steering at speed, or hard braking.
        var skidTarget = 0f
        if (!isAirborne) {
            if (speedAbs > 9f) {
                val turning = ((abs(steerSmoothed) - 0.5f) * 2f).coerceIn(0f, 1f) *
                    (speedAbs / topSpeed).coerceIn(0f, 1f) * 1.6f
                skidTarget = max(skidTarget, turning)
            }
            if (brake && speed > 8f) skidTarget = max(skidTarget, ((speed - 8f) / 14f).coerceIn(0f, 1f))
        }
        skidTarget = (skidTarget * (1f - 0.7f * roughness)).coerceIn(0f, 1f)
        skidAmount += (skidTarget - skidAmount) * min(1f, 10f * dt)

        // Move, but never let a wheel leave the ground.
        forward.set(0f, 0f, -1f).rotate(Vector3.Y, yawDegrees)
        val targetX = position.x + forward.x * speed * dt
        val targetZ = position.z + forward.z * speed * dt
        if (wheelsOnRoad(targetX, targetZ)) {
            position.x = targetX
            position.z = targetZ
        } else {
            val before = speed
            slideAlongEdge(targetX, targetZ)
            // Hitting a wall (the edge of the ground, a step too high) is a knock you can hear.
            val lost = abs(before) - abs(speed)
            if (lost > 1.5f) impactEvent = max(impactEvent, lost * 1.2f)
        }
        wheelRollDegrees = (wheelRollDegrees + speed * dt / WHEEL_RADIUS * 57.29578f) % 360f

        // How hard the car is accelerating, smoothed: it tips the body back and forth.
        val accRaw = (speed - lastSpeed) / dt
        lastSpeed = speed
        accSm += (accRaw - accSm) * min(1f, 8f * dt)

        // Follow the ground: the body rides on its springs over the ground under the four wheels.
        if (sampleWheels(position.x, position.z, groundRef)) applyWheelHeights(dt, accSm, yawRate)

        // Very large safety clamp: nobody can leave the map.
        if (position.x < WorldLimits.MIN_X || position.x > WorldLimits.MAX_X ||
            position.z < WorldLimits.MIN_Z || position.z > WorldLimits.MAX_Z
        ) {
            position.x = position.x.coerceIn(WorldLimits.MIN_X, WorldLimits.MAX_X)
            position.z = position.z.coerceIn(WorldLimits.MIN_Z, WorldLimits.MAX_Z)
            speed *= 0.5f
        }
    }

    /** Reads the ground height under the four wheels for a car at (cx, cz) with the current heading. */
    private fun sampleWheels(cx: Float, cz: Float, refY: Float): Boolean {
        val rad = yawDegrees * 0.017453292f
        val sinYaw = sin(rad)
        val cosYaw = cos(rad)
        val fwdX = -sinYaw
        val fwdZ = -cosYaw
        val rightX = cosYaw
        val rightZ = -sinYaw
        val drop = if (isAirborne) MAX_DROP_AIR else MAX_DROP
        for (i in 0 until 4) {
            val px = cx + fwdX * FRONT[i] * halfWheelbase + rightX * SIDE[i] * halfTrack
            val pz = cz + fwdZ * FRONT[i] * halfWheelbase + rightZ * SIDE[i] * halfTrack
            val h = RoadGround.carHeightAt(px, pz, refY, drop)
            if (h.isNaN()) return false
            wheelY[i] = h
            wheelKind[i] = RoadGround.lastKind
        }
        return true
    }

    private fun wheelsOnRoad(x: Float, z: Float): Boolean = sampleWheels(x, z, groundRef)

    /** From what the four wheels stand on: the top speed that surface allows, and how rough it is. */
    private fun readSurface(dt: Float) {
        var top = 0f
        var rough = 0f
        for (i in 0 until 4) {
            when (wheelKind[i]) {
                RoadGround.SURFACE_TUNNEL -> {
                    top += TUNNEL_TOP
                    rough += TUNNEL_ROUGH
                }
                RoadGround.SURFACE_TERRAIN -> {
                    top += TERRAIN_TOP
                    rough += TERRAIN_ROUGH
                }
                else -> top += 1f
            }
        }
        top *= 0.25f
        rough *= 0.25f
        surfaceTop += (top - surfaceTop) * min(1f, 3f * dt)
        roughness += (rough - roughness) * min(1f, 6f * dt)
    }

    /**
     * Sets height, pitch and roll from the wheel heights. [dt] = 0 jumps there at once. Otherwise the body
     * follows on springs: [longAcc] (m/s squared, forward) tips it back and forth, and [yawRateDeg] makes
     * it lean in a turn.
     */
    private fun applyWheelHeights(dt: Float, longAcc: Float = 0f, yawRateDeg: Float = 0f) {
        val front = (wheelY[0] + wheelY[1]) / 2f
        val rear = (wheelY[2] + wheelY[3]) / 2f
        val left = (wheelY[0] + wheelY[2]) / 2f
        val right = (wheelY[1] + wheelY[3]) / 2f
        val targetY = (wheelY[0] + wheelY[1] + wheelY[2] + wheelY[3]) / 4f
        val targetPitch = Math.toDegrees(atan2((front - rear).toDouble(), (2f * halfWheelbase).toDouble())).toFloat()
        val targetRoll = Math.toDegrees(atan2((right - left).toDouble(), (2f * halfTrack).toDouble())).toFloat()
        groundRef = targetY
        if (dt <= 0f) {
            position.y = targetY
            pitchDegrees = targetPitch
            rollDegrees = targetRoll
            vy = 0f
            pitchVel = 0f
            rollVel = 0f
            prevTargetY = targetY
            targetVel = 0f
            isAirborne = false
            return
        }

        // Height: a spring pulls the body to where the ground is, and a damper to the speed the ground
        // moves at, so a steady slope is followed exactly and a sudden step is a bump. The body can never
        // be pulled down faster than it falls: a crest throws it into the air.
        val groundSpeed = ((targetY - prevTargetY) / dt).coerceIn(-MAX_GROUND_SPEED, MAX_GROUND_SPEED)
        prevTargetY = targetY
        targetVel += (groundSpeed - targetVel) * min(1f, GROUND_SPEED_FILTER * dt)

        // A slow frame is worked through in small steps so the spring stays steady.
        val steps = min(MAX_STEPS, (dt / STEP_SECONDS).toInt() + 1)
        val h = dt / steps
        for (n in 0 until steps) {
            var acc = SPRING_K * (targetY - position.y) + DAMP_C * (targetVel - vy)
            if (acc < -GRAVITY) acc = -GRAVITY
            vy += acc * h
            position.y += vy * h

            val gap = position.y - targetY
            val wasAirborne = isAirborne
            isAirborne = gap > AIR_GAP
            if (wasAirborne && !isAirborne) {
                // Landing: the harder it comes down, the louder it is and the more speed it costs.
                val hit = targetVel - vy
                if (hit > LAND_MIN) {
                    impactEvent = max(impactEvent, hit)
                    speed *= 1f - min(0.2f, hit * 0.015f)
                }
            }
            if (gap < -TRAVEL) {
                // The suspension is at its stop: a heap of rubble hit hard.
                position.y = targetY - TRAVEL
                if (vy < targetVel) {
                    val hit = targetVel - vy
                    if (hit > BUMP_MIN) impactEvent = max(impactEvent, hit * 0.8f)
                    vy = targetVel
                }
            }
        }

        // Pitch and roll follow the ground on springs, plus the weight shifting: nose up when accelerating,
        // down when braking, and the body leaning to the outside of a turn.
        val loadPitch = if (isAirborne) 0f else (longAcc * LOAD_PITCH).coerceIn(-3.5f, 3.5f)
        val loadRoll = if (isAirborne) 0f else
            (-yawRateDeg * 0.017453292f * speed * LOAD_ROLL).coerceIn(-4f, 4f)
        pitchVel += (ANGLE_K * (targetPitch + loadPitch - pitchDegrees) - ANGLE_C * pitchVel) * dt
        pitchDegrees = (pitchDegrees + pitchVel * dt).coerceIn(-MAX_TILT, MAX_TILT)
        rollVel += (ANGLE_K * (targetRoll + loadRoll - rollDegrees) - ANGLE_C * rollVel) * dt
        rollDegrees = (rollDegrees + rollVel * dt).coerceIn(-MAX_TILT, MAX_TILT)
    }

    /**
     * Called when a wheel has no ground. Moves the car to the nearest spot where all four wheels are on the
     * ground (up to [RECOVER_SPEED] metres per second, so it is a smooth slide and not a jump). When there is
     * no such spot close by and it has been off the ground for a second, the car goes back to the last place
     * it was fully on the ground.
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
            groundRef = goodY
            yawDegrees = goodYaw
            speed = 0f
            vy = 0f
            isAirborne = false
            offRoadTime = 0f
        }
        // Keep the height and the lean following the ground wherever the wheels do touch it.
        if (sampleWheels(position.x, position.z, groundRef)) applyWheelHeights(dt)
    }

    /** Looks outwards in rings of 1 m for a spot where all four wheels are on the ground. Result in spotX / spotZ. */
    private fun findSpotOnRoad(): Boolean {
        var r = 1
        while (r <= RECOVER_RADIUS) {
            val steps = min(48, 8 * r)
            for (k in 0 until steps) {
                val a = k * 6.2831855f / steps
                val px = position.x + cos(a) * r
                val pz = position.z + sin(a) * r
                if (sampleWheels(px, pz, groundRef)) {
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
     * The move was blocked because a wheel would leave the ground. Looks at which side of the spot the ground
     * is on, takes away the part of the move that points off it, and slides along the edge.
     */
    private fun slideAlongEdge(targetX: Float, targetZ: Float) {
        val mx = targetX - position.x
        val mz = targetZ - position.z
        val length = sqrt(mx * mx + mz * mz)
        if (length < 1e-5f) return

        // Where is the ground? Look sideways a little, then further, until it shows up on some side.
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

        val FRONT = floatArrayOf(1f, 1f, -1f, -1f)
        val SIDE = floatArrayOf(-1f, 1f, -1f, 1f)

        const val MAX_DROP = 1.2f           // a wheel finds no ground if it is more than this above it
        const val MAX_DROP_AIR = 8f         // ... but in the air the ground can be much further down
        const val NO_LIMIT_DROP = 100000f
        const val PROBE = 0.6f              // how far to look sideways to learn where the ground is
        const val PROBE_MAX = 6f            // and the furthest it looks when the ground is not right there
        const val RECOVER_RADIUS = 20       // metres searched for a spot on the ground when a wheel is off it
        const val RECOVER_SPEED = 8f        // metres per second the car slides back onto the ground
        const val SPAWN_SEARCH_RADIUS = 15f
        const val HIGH_REFERENCE = 10000f   // "any height": picks the highest surface under the car

        // Surfaces: the share of the top speed that is allowed, and how rough they are (0..1).
        const val TUNNEL_TOP = 0.62f
        const val TERRAIN_TOP = 0.72f
        const val TUNNEL_ROUGH = 0.7f
        const val TERRAIN_ROUGH = 0.55f
        const val SURFACE_DRAG = 3.5f       // m/s squared of braking at roughness 1 (the engine gives 11)
        const val OVER_TOP_DECEL = 14f      // how fast a car that is too fast for the ground is slowed

        // Suspension: about 1.9 bounces a second, and it settles after two or three.
        const val SPRING_K = 150f
        const val DAMP_C = 12f
        const val ANGLE_K = 95f
        const val ANGLE_C = 6f
        const val TRAVEL = 0.22f            // how far the body can sink below the ground before the stop
        const val AIR_GAP = 0.3f            // the body this far above the ground means the wheels are off it
        const val MAX_GROUND_SPEED = 8f
        const val GROUND_SPEED_FILTER = 4f   // how slowly the speed of the ground is picked up (per second)
        const val STEP_SECONDS = 1f / 120f
        const val MAX_STEPS = 8
        const val LAND_MIN = 2f
        const val BUMP_MIN = 1.5f
        const val LOAD_PITCH = 0.28f        // degrees of tilt per m/s squared of acceleration
        const val LOAD_ROLL = 0.45f         // degrees of lean per m/s squared of sideways acceleration
        const val MAX_TILT = 40f
    }
}
