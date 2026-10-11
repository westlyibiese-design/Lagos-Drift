package com.westly.lagosdrift

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import java.util.Random
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Passer-by traffic: Danfos and Camrys that drive along the traffic corridor (the straight stretch
 * of road, through the join and the whole tunnel, about 1.1 km). Two lanes, one each way, on the
 * right of each car's direction. The player can never enter or drive them; they only get in the way.
 *
 * A car keeps its lane by looking ahead and probing the road to the left and right with RoadGround
 * to find the road edges, then steering toward a spot 2.6 m to the right of the road's middle, so
 * bends and the tunnel need no stored path. The ground gives each car its height, pitch and roll
 * the same way it does for the player's car. Cars slow down for a car in front (or for the player).
 *
 * At most [MAX_CARS] cars exist at once. They appear 110 to 300 m from the player, inside the corridor,
 * and disappear when far away or when they reach an end of the corridor.
 */
class TrafficManager(private val camryModel: Model, private val danfoModel: Model?) {
    private class Vehicle(val camryInstance: ModelInstance, val danfoInstance: ModelInstance?) {
        var active = false
        var danfo = false

        var x = 0f
        var y = 0f
        var z = 0f
        var yaw = 0f
        var targetYaw = 0f
        var pitch = 0f
        var roll = 0f
        var speed = 0f
        var cruise = 15f
        var fx = 0f
        var fz = -1f

        var radius = 1.05f
        var offset = 1.25f
        var halfBase = 1.388f
        var halfTrack = 0.79f

        var probeTimer = 0f
        var lostTime = 0f
        var wheelRoll = 0f

        val wheelY = FloatArray(4)

        val wheelFL: Node? = camryInstance.getNode(CamryModelFactory.WHEEL_FL)
        val wheelFR: Node? = camryInstance.getNode(CamryModelFactory.WHEEL_FR)
        val wheelRL: Node? = camryInstance.getNode(CamryModelFactory.WHEEL_RL)
        val wheelRR: Node? = camryInstance.getNode(CamryModelFactory.WHEEL_RR)
    }

    private val pool = Array(MAX_CARS) {
        Vehicle(ModelInstance(camryModel), if (danfoModel != null) ModelInstance(danfoModel) else null)
    }

    private val rng = Random()
    private val wheelQ = Quaternion()
    private val found = Vector3()
    private var spawnTimer = 1f

    // The corridor axis: from the start of the straight stretch to the far end of the tunnel.
    private val axisX: Float
    private val axisZ: Float
    private val corridorLength: Float

    init {
        val dx = END_X - START_X
        val dz = END_Z - START_Z
        corridorLength = sqrt(dx * dx + dz * dz)
        axisX = dx / corridorLength
        axisZ = dz / corridorLength

        // Different body-colour tints for the traffic Camry (the texture is multiplied by the tint).
        // The first one leaves the picture as it is.
        for (i in 0 until pool.size) {
            val tint = TINTS[i % TINTS.size]
            val material = pool[i].camryInstance.materials[0]
            material.set(ColorAttribute.createDiffuse(tint))
        }
    }

    // ------------------------------------------------------------------ the corridor

    private fun alongAxis(x: Float, z: Float): Float = (x - START_X) * axisX + (z - START_Z) * axisZ

    /** Sideways distance from the axis, positive to the right of the direction the axis runs. */
    private fun sideOfAxis(x: Float, z: Float): Float = (x - START_X) * -axisZ + (z - START_Z) * axisX

    // ------------------------------------------------------------------ every frame

    /**
     * [subjectX]/[subjectZ] is where the player is (on foot or in the car): cars appear around it.
     * The car's own position, heading and speed are used so that traffic slows down for it.
     */
    fun update(
        delta: Float, subjectX: Float, subjectZ: Float,
        carX: Float, carZ: Float, carYaw: Float, carSpeed: Float
    ) {
        val dt = min(delta, 0.05f)
        for (i in 0 until pool.size) {
            val v = pool[i]
            if (v.active) updateVehicle(v, dt, subjectX, subjectZ, carX, carZ, carYaw, carSpeed)
        }
        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            spawnTimer = SPAWN_INTERVAL
            trySpawn(subjectX, subjectZ)
        }
    }

    private fun updateVehicle(
        v: Vehicle, dt: Float, subjectX: Float, subjectZ: Float,
        carX: Float, carZ: Float, carYaw: Float, carSpeed: Float
    ) {
        // Far from the player, or at an end of the corridor: gone.
        val dxs = v.x - subjectX
        val dzs = v.z - subjectZ
        if (dxs * dxs + dzs * dzs > DESPAWN_DISTANCE * DESPAWN_DISTANCE) {
            v.active = false
            return
        }
        val s = alongAxis(v.x, v.z)
        if (s < -10f || s > corridorLength + 10f) {
            v.active = false
            return
        }

        val rad = v.yaw * 0.017453292f
        v.fx = -sin(rad)
        v.fz = -cos(rad)

        // Steering: look at the road ahead a few times a second, turn smoothly toward the lane.
        v.probeTimer -= dt
        if (v.probeTimer <= 0f) {
            v.probeTimer = PROBE_INTERVAL
            probeLane(v)
        }
        val diff = wrap(v.targetYaw - v.yaw)
        val maxTurn = TURN_RATE * dt
        v.yaw = wrap(v.yaw + diff.coerceIn(-maxTurn, maxTurn))

        // Speed: cruise, but keep a gap to the car in front (or to the player's car).
        var target = v.cruise
        var gap = Float.MAX_VALUE
        var frontSpeed = 0f
        val rx = -v.fz
        val rz = v.fx
        for (j in 0 until pool.size) {
            val w = pool[j]
            if (w === v || !w.active) continue
            val dx = w.x - v.x
            val dz = w.z - v.z
            val along = dx * v.fx + dz * v.fz
            if (along <= 0f || along > OBSTACLE_RANGE) continue
            if (abs(dx * rx + dz * rz) > LANE_WIDTH) continue
            if (w.fx * v.fx + w.fz * v.fz < 0.3f) continue      // oncoming cars use the other lane
            val g = along - BUMPER_TO_BUMPER
            if (g < gap) {
                gap = g
                frontSpeed = w.speed
            }
        }
        run {
            val dx = carX - v.x
            val dz = carZ - v.z
            val along = dx * v.fx + dz * v.fz
            if (along > 0f && along <= OBSTACLE_RANGE && abs(dx * rx + dz * rz) <= LANE_WIDTH) {
                val g = along - BUMPER_TO_BUMPER
                if (g < gap) {
                    gap = g
                    val carRad = carYaw * 0.017453292f
                    val same = -sin(carRad) * v.fx + -cos(carRad) * v.fz > 0.3f
                    frontSpeed = if (same) maxOf(0f, carSpeed) else 0f
                }
            }
        }
        if (gap < OBSTACLE_RANGE) {
            target = min(target, maxOf(0f, frontSpeed * 0.9f + (gap - MIN_GAP) * 0.5f))
        }
        v.speed += (target - v.speed).coerceIn(-BRAKE * dt, ACCEL * dt)

        // Move, and follow the ground. If there is no ground ahead (not loaded yet) the car waits.
        val oldX = v.x
        val oldZ = v.z
        v.x += v.fx * v.speed * dt
        v.z += v.fz * v.speed * dt
        if (poseOnGround(v, dt)) {
            v.lostTime = 0f
        } else {
            v.x = oldX
            v.z = oldZ
            v.speed = 0f
            v.lostTime += dt
            if (v.lostTime > 2f) v.active = false
        }
        v.wheelRoll = (v.wheelRoll + v.speed * dt / WHEEL_RADIUS * 57.29578f) % 360f
    }

    // ------------------------------------------------------------------ keeping the lane

    /**
     * Looks ahead, measures how far the road edge is to the right and to the left there, and aims at
     * the spot [LANE_OFFSET] to the right of the middle of the road (right-hand traffic).
     */
    private fun probeLane(v: Vehicle) {
        val look = LOOK_BASE + abs(v.speed) * LOOK_TIME
        val px = v.x + v.fx * look
        val pz = v.z + v.fz * look
        val rx = -v.fz
        val rz = v.fx
        val ref = v.y + PROBE_HEIGHT
        if (!RoadGround.isRoad(px, pz, ref, PROBE_DROP)) {
            v.targetYaw = v.yaw      // no road there: keep going straight
            return
        }
        val toRight = scanEdge(px, pz, rx, rz, ref)
        val toLeft = scanEdge(px, pz, -rx, -rz, ref)
        val middleToRight = (toRight - toLeft) / 2f
        val aim = middleToRight + LANE_OFFSET
        val bend = Math.toDegrees(atan2(aim.toDouble(), look.toDouble())).toFloat().coerceIn(-25f, 25f)
        v.targetYaw = wrap(v.yaw - bend)     // a positive yaw turns left, so aiming right lowers it
    }

    /** Distance from (x, z) to the road edge in direction (dx, dz): the first spot with no road, up to 10 m. */
    private fun scanEdge(x: Float, z: Float, dx: Float, dz: Float, ref: Float): Float {
        var d = 0f
        while (d < MAX_SCAN) {
            d += SCAN_STEP
            if (!RoadGround.isRoad(x + dx * d, z + dz * d, ref, PROBE_DROP)) return d - SCAN_STEP / 2f
        }
        return MAX_SCAN
    }

    // ------------------------------------------------------------------ height, pitch, roll

    /** Reads the road under the four wheels. Returns false if any wheel has no road. dt = 0 snaps at once. */
    private fun poseOnGround(v: Vehicle, dt: Float): Boolean {
        val rad = v.yaw * 0.017453292f
        val fx = -sin(rad)
        val fz = -cos(rad)
        val rx = cos(rad)
        val rz = -sin(rad)
        for (i in 0 until 4) {
            val px = v.x + fx * FRONT[i] * v.halfBase + rx * SIDE[i] * v.halfTrack
            val pz = v.z + fz * FRONT[i] * v.halfBase + rz * SIDE[i] * v.halfTrack
            val h = RoadGround.heightAt(px, pz, v.y, MAX_DROP)
            if (h.isNaN()) return false
            v.wheelY[i] = h
        }
        val front = (v.wheelY[0] + v.wheelY[1]) / 2f
        val rear = (v.wheelY[2] + v.wheelY[3]) / 2f
        val left = (v.wheelY[0] + v.wheelY[2]) / 2f
        val right = (v.wheelY[1] + v.wheelY[3]) / 2f
        val targetY = (v.wheelY[0] + v.wheelY[1] + v.wheelY[2] + v.wheelY[3]) / 4f
        val targetPitch = Math.toDegrees(atan2((front - rear).toDouble(), (2f * v.halfBase).toDouble())).toFloat()
        val targetRoll = Math.toDegrees(atan2((right - left).toDouble(), (2f * v.halfTrack).toDouble())).toFloat()
        if (dt <= 0f) {
            v.y = targetY
            v.pitch = targetPitch
            v.roll = targetRoll
        } else {
            v.y += (targetY - v.y) * (1f - exp(-20f * dt))
            if (v.y < targetY - 0.05f) v.y = targetY - 0.05f
            val k = 1f - exp(-12f * dt)
            v.pitch += (targetPitch - v.pitch) * k
            v.roll += (targetRoll - v.roll) * k
        }
        return true
    }

    // ------------------------------------------------------------------ appearing

    private fun trySpawn(subjectX: Float, subjectZ: Float) {
        var free: Vehicle? = null
        for (i in 0 until pool.size) {
            if (!pool[i].active) {
                free = pool[i]
                break
            }
        }
        val v = free ?: return

        // Only while the player is near the corridor.
        val playerS = alongAxis(subjectX, subjectZ)
        if (abs(sideOfAxis(subjectX, subjectZ)) > CORRIDOR_NEAR) return
        if (playerS < -SPAWN_MAX || playerS > corridorLength + SPAWN_MAX) return

        // A spot 110 to 300 m ahead of or behind the player, inside the corridor.
        val ahead = if (rng.nextBoolean()) 1f else -1f
        val s = playerS + ahead * (SPAWN_MIN + rng.nextFloat() * (SPAWN_MAX - SPAWN_MIN))
        if (s < 20f || s > corridorLength - 20f) return

        val travel = if (rng.nextBoolean()) 1f else -1f      // +1 runs with the axis, -1 against it
        val hx = axisX * travel
        val hz = axisZ * travel
        val rightX = -hz
        val rightZ = hx
        val ax = START_X + axisX * s
        val az = START_Z + axisZ * s

        // Find the road near the axis, measure it edge to edge across the axis, and take the lane spot.
        if (!RoadGround.findNearestRoad(ax, az, 12f, HIGH_REFERENCE, found)) return
        val qx = found.x
        val qz = found.z
        val ref = found.y + PROBE_HEIGHT
        val axisRightX = -axisZ
        val axisRightZ = axisX
        val toRight = scanEdge(qx, qz, axisRightX, axisRightZ, ref)
        val toLeft = scanEdge(qx, qz, -axisRightX, -axisRightZ, ref)
        val middleOffset = (toRight - toLeft) / 2f
        val mx = qx + axisRightX * middleOffset
        val mz = qz + axisRightZ * middleOffset
        val lx = mx + rightX * LANE_OFFSET
        val lz = mz + rightZ * LANE_OFFSET
        val ground = RoadGround.heightAt(lx, lz, ref, PROBE_DROP)
        if (ground.isNaN()) return

        // Not on top of another car.
        for (i in 0 until pool.size) {
            val w = pool[i]
            if (!w.active) continue
            val dx = w.x - lx
            val dz = w.z - lz
            if (dx * dx + dz * dz < SPAWN_CLEARANCE * SPAWN_CLEARANCE) return
        }

        v.danfo = danfoModel != null && rng.nextFloat() < DANFO_SHARE
        if (v.danfo) {
            v.radius = 1.15f
            v.offset = 1.25f
            v.halfBase = 1.5f
            v.halfTrack = 0.95f
        } else {
            v.radius = 1.05f
            v.offset = 1.25f
            v.halfBase = 1.388f
            v.halfTrack = 0.79f
        }
        v.x = lx
        v.y = ground
        v.z = lz
        v.yaw = Math.toDegrees(atan2((-hx).toDouble(), (-hz).toDouble())).toFloat()
        v.targetYaw = v.yaw
        v.fx = hx
        v.fz = hz
        v.pitch = 0f
        v.roll = 0f
        v.cruise = MIN_CRUISE + rng.nextFloat() * (MAX_CRUISE - MIN_CRUISE)
        v.speed = v.cruise * 0.7f
        v.probeTimer = rng.nextFloat() * PROBE_INTERVAL
        v.lostTime = 0f
        v.wheelRoll = 0f
        if (!poseOnGround(v, 0f)) return
        v.active = true
    }

    // ------------------------------------------------------------------ drawing

    fun render(batch: ModelBatch, environment: Environment, camera: Camera) {
        for (i in 0 until pool.size) {
            val v = pool[i]
            if (!v.active) continue
            val dx = v.x - camera.position.x
            val dz = v.z - camera.position.z
            if (dx * dx + dz * dz > DRAW_RANGE * DRAW_RANGE) continue
            if (!camera.frustum.sphereInFrustum(v.x, v.y + 1.2f, v.z, 3.2f)) continue
            val instance = if (v.danfo) (v.danfoInstance ?: continue) else v.camryInstance
            instance.transform.setToRotation(Vector3.Y, v.yaw)
                .rotate(Vector3.X, v.pitch)
                .rotate(Vector3.Z, v.roll)
                .setTranslation(v.x, v.y, v.z)
            if (!v.danfo) {
                wheelQ.set(Vector3.X, -v.wheelRoll)
                v.wheelFL?.rotation?.set(wheelQ)
                v.wheelFR?.rotation?.set(wheelQ)
                v.wheelRL?.rotation?.set(wheelQ)
                v.wheelRR?.rotation?.set(wheelQ)
                instance.calculateTransforms()
            }
            batch.render(instance, environment)
        }
    }

    // ------------------------------------------------------------------ bumping

    /** Stops the player's car driving through traffic. Slows both. Each car is two circles, one near each end. */
    fun collide(car: CarController) {
        val rad = car.yawDegrees * 0.017453292f
        val cfx = -sin(rad)
        val cfz = -cos(rad)
        for (i in 0 until pool.size) {
            val v = pool[i]
            if (!v.active) continue
            val dx = v.x - car.position.x
            val dz = v.z - car.position.z
            if (dx * dx + dz * dz > 40f) continue
            if (abs(v.y - car.position.y) > 3f) continue
            var hit = false
            for (a in -1..1 step 2) {
                for (b in -1..1 step 2) {
                    val ax = car.position.x + cfx * CAR_OFFSET * a
                    val az = car.position.z + cfz * CAR_OFFSET * a
                    val bx = v.x + v.fx * v.offset * b
                    val bz = v.z + v.fz * v.offset * b
                    var ex = ax - bx
                    var ez = az - bz
                    val limit = CAR_RADIUS + v.radius
                    val d2 = ex * ex + ez * ez
                    if (d2 >= limit * limit) continue
                    var d = sqrt(d2)
                    if (d < 0.001f) {
                        ex = 1f
                        ez = 0f
                        d = 1f
                    }
                    val push = limit - d
                    car.position.x += ex / d * push
                    car.position.z += ez / d * push
                    hit = true
                }
            }
            if (hit) {
                car.bump(0.6f)
                v.speed *= 0.5f
            }
        }
    }

    /** Keeps a person on foot from standing inside a traffic car. */
    fun pushOut(p: Vector3, radius: Float) {
        for (i in 0 until pool.size) {
            val v = pool[i]
            if (!v.active) continue
            val dx = v.x - p.x
            val dz = v.z - p.z
            if (dx * dx + dz * dz > 40f) continue
            if (abs(v.y - p.y) > 3f) continue
            for (b in -1..1 step 2) {
                val bx = v.x + v.fx * v.offset * b
                val bz = v.z + v.fz * v.offset * b
                var ex = p.x - bx
                var ez = p.z - bz
                val limit = radius + v.radius
                val d2 = ex * ex + ez * ez
                if (d2 >= limit * limit) continue
                var d = sqrt(d2)
                if (d < 0.001f) {
                    ex = 1f
                    ez = 0f
                    d = 1f
                }
                p.x += ex / d * (limit - d)
                p.z += ez / d * (limit - d)
            }
        }
    }

    private fun wrap(angle: Float): Float {
        var a = angle
        while (a > 180f) a -= 360f
        while (a < -180f) a += 360f
        return a
    }

    private companion object {
        const val MAX_CARS = 12
        const val DANFO_SHARE = 0.4f

        // The traffic corridor: the straight stretch through the join to the far end of the tunnel.
        const val START_X = -470.5f
        const val START_Z = -856.5f
        const val END_X = -372f
        const val END_Z = -1955f

        const val LANE_OFFSET = 2.6f        // metres right of the road's middle
        const val LANE_WIDTH = 1.9f         // another car this close to our line is "in front"
        const val MIN_CRUISE = 12f
        const val MAX_CRUISE = 22f
        const val ACCEL = 3f
        const val BRAKE = 10f
        const val TURN_RATE = 40f           // degrees per second
        const val MIN_GAP = 7f
        const val OBSTACLE_RANGE = 50f
        const val BUMPER_TO_BUMPER = 4.8f   // centre to centre distance at which two cars touch

        const val LOOK_BASE = 10f
        const val LOOK_TIME = 0.5f
        const val PROBE_INTERVAL = 0.12f
        const val PROBE_HEIGHT = 3f
        const val PROBE_DROP = 8f
        const val SCAN_STEP = 0.5f
        const val MAX_SCAN = 10f
        const val MAX_DROP = 1.2f
        const val HIGH_REFERENCE = 10000f

        const val SPAWN_MIN = 110f
        const val SPAWN_MAX = 300f
        const val SPAWN_INTERVAL = 1.0f
        const val SPAWN_CLEARANCE = 35f
        const val CORRIDOR_NEAR = 250f
        const val DESPAWN_DISTANCE = 400f
        const val DRAW_RANGE = 450f
        const val WHEEL_RADIUS = 0.33f

        // The player's car, for bumping.
        const val CAR_RADIUS = 1.1f
        const val CAR_OFFSET = 1.2f

        val FRONT = floatArrayOf(1f, 1f, -1f, -1f)
        val SIDE = floatArrayOf(-1f, 1f, -1f, 1f)

        // Camry body tints. The picture is dark grey, so the brighter ones need a number above 1.
        val TINTS = arrayOf(
            Color(1f, 1f, 1f, 1f),
            Color(2.4f, 2.4f, 2.5f, 1f),
            Color(2.6f, 1.2f, 1.2f, 1f),
            Color(1.2f, 1.7f, 2.6f, 1f)
        )
    }
}
