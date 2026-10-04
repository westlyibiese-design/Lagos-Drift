package com.westly.lagosdrift

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The other cars on the road. They drive in the right-hand lane, slow down for bends and bad
 * roads, turn at junctions, and stop for the player's car and for the player on foot.
 * Ikoyi has clean newer cars; Ikorodu has older faded cars and yellow danfo buses.
 * They live around the player: far ones disappear and new ones appear on roads out of sight.
 */
private var nextId = 0

class TrafficManager {
    private class Cut(val other: TownLayout.Road, val centre: Float)

    private class Vehicle(val instance: ModelInstance, val danfo: Boolean) {
        lateinit var road: TownLayout.Road
        var dir = 1
        var t = 0f
        var lat = 0f
        var curLat = 0f
        var speed = 0f
        var factor = 1f
        var cruise = 10f
        var yaw = 0f
        var yawVis = 0f
        var x = 0f
        var z = 0f
        var pending: Cut? = null
        var action = STRAIGHT
        var turnT = 0f
        var turnDir = 1
        var skipRoad: TownLayout.Road? = null
        var stuck = 0f
        var ghost = 0f
        var remove = false
        val id = nextId++
        val halfLength: Float = if (danfo) 2.3f else 2.1f
        val circleOffset: Float = if (danfo) 1.4f else 1.2f
        val radius: Float = if (danfo) 1.15f else 1.1f
    }

    private val cuts = HashMap<TownLayout.Road, List<Cut>>()
    private val nextForward = HashMap<TownLayout.Road, TownLayout.Road>()
    private val nextBackward = HashMap<TownLayout.Road, TownLayout.Road>()
    private val vehicles = ArrayList<Vehicle>()
    private val rnd = Random(2026)
    private var spawnTimer = 0f
    private var models: List<Model> = emptyList()
    private var ikoyiModels: List<Model> = emptyList()
    private var ikoroduModels: List<Model> = emptyList()
    private lateinit var danfoModel: Model

    fun create() {
        ikoyiModels = TrafficModelFactory.buildIkoyiCars()
        ikoroduModels = TrafficModelFactory.buildIkoroduCars()
        danfoModel = TrafficModelFactory.buildDanfo()
        models = ikoyiModels + ikoroduModels + danfoModel

        val roads = TownLayout.roads
        for (r in roads) {
            val list = ArrayList<Cut>()
            for (p in roads) {
                if (p.vertical == r.vertical) continue
                val crossesR = p.centre >= r.start - 0.5f && p.centre <= r.end + 0.5f
                val crossesP = r.centre >= p.start - 0.5f && r.centre <= p.end + 0.5f
                if (crossesR && crossesP) list.add(Cut(p, p.centre))
            }
            list.sortBy { it.centre }
            cuts[r] = list

            for (o in roads) {
                if (o === r || o.vertical != r.vertical || abs(o.centre - r.centre) > 0.01f) continue
                if (abs(o.start - r.end) < 0.01f) nextForward[r] = o
                if (abs(o.end - r.start) < 0.01f) nextBackward[r] = o
            }
        }
    }

    /** Fills the roads around [focus] with cars when the game starts. */
    fun populate(focus: Vector3, car: Vector3) {
        for (i in 0 until MAX_VEHICLES * 6) {
            if (vehicles.size >= MAX_VEHICLES) break
            trySpawn(focus, car, 45f, SPAWN_MAX)
        }
    }

    fun update(delta: Float, focus: Vector3, car: Vector3, onFoot: Boolean, foot: Vector3) {
        val dt = min(delta, 0.05f)

        for (v in vehicles) step(v, dt, car, onFoot, foot, focus)

        for (i in vehicles.size - 1 downTo 0) {
            if (vehicles[i].remove) vehicles.removeAt(i)
        }

        spawnTimer += dt
        if (vehicles.size < MAX_VEHICLES && spawnTimer > 0.3f) {
            spawnTimer = 0f
            trySpawn(focus, car, SPAWN_MIN, SPAWN_MAX)
        }
    }

    fun render(batch: ModelBatch, environment: Environment, camera: PerspectiveCamera, focus: Vector3) {
        for (v in vehicles) {
            val dx = v.x - focus.x
            val dz = v.z - focus.z
            if (dx * dx + dz * dz > DRAW_RANGE * DRAW_RANGE) continue
            if (!camera.frustum.sphereInFrustum(v.x, 1f, v.z, 3.2f)) continue
            v.instance.transform.setToRotation(Vector3.Y, v.yawVis).setTranslation(v.x, 0f, v.z)
            batch.render(v.instance, environment)
        }
    }

    /** Stops the player's car driving through other cars. Slows both. */
    fun collide(car: CarController) {
        val rad = car.yawDegrees * MathUtils.degreesToRadians
        val fx = -MathUtils.sin(rad)
        val fz = -MathUtils.cos(rad)
        for (v in vehicles) {
            val dx = v.x - car.position.x
            val dz = v.z - car.position.z
            if (dx * dx + dz * dz > 40f) continue
            val vf = headingOf(v)
            var hit = false
            for (a in -1..1 step 2) {
                for (b in -1..1 step 2) {
                    val ax = car.position.x + fx * CAR_OFFSET * a
                    val az = car.position.z + fz * CAR_OFFSET * a
                    val bx = v.x + vf[0] * v.circleOffset * b
                    val bz = v.z + vf[1] * v.circleOffset * b
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

    /** Keeps a person on foot from standing inside a car. */
    fun pushOut(p: Vector3, radius: Float) {
        for (v in vehicles) {
            val dx = v.x - p.x
            val dz = v.z - p.z
            if (dx * dx + dz * dz > 40f) continue
            val vf = headingOf(v)
            for (b in -1..1 step 2) {
                val bx = v.x + vf[0] * v.circleOffset * b
                val bz = v.z + vf[1] * v.circleOffset * b
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

    fun dispose() {
        for (m in models) m.dispose()
    }

    // ------------------------------------------------------------------ driving

    private val headingTmp = FloatArray(2)

    private fun headingOf(v: Vehicle): FloatArray {
        headingTmp[0] = if (v.road.vertical) 0f else v.dir.toFloat()
        headingTmp[1] = if (v.road.vertical) v.dir.toFloat() else 0f
        return headingTmp
    }

    private fun step(v: Vehicle, dt: Float, car: Vector3, onFoot: Boolean, foot: Vector3, focus: Vector3) {
        val fdx = v.x - focus.x
        val fdz = v.z - focus.z
        if (fdx * fdx + fdz * fdz > DESPAWN_RANGE * DESPAWN_RANGE) {
            v.remove = true
            return
        }

        planCut(v)
        if (v.remove) return

        // Speed we would like.
        var target = v.cruise
        if (v.pending != null && v.action != STRAIGHT) {
            val toTurn = (v.turnT - v.t) * v.dir
            if (toTurn < 18f) target = min(target, TURN_SPEED)
        }

        val fx = if (v.road.vertical) 0f else v.dir.toFloat()
        val fz = if (v.road.vertical) v.dir.toFloat() else 0f
        val look = 14f + 0.8f * v.speed

        var hardBrake = false
        // The player's car.
        var allowed = obstacleSpeed(v, car.x, car.z, fx, fz, look, 2.4f, 6f)
        if (allowed < target) {
            target = allowed
            if (allowed < 1f) hardBrake = true
        }
        if (onFoot) {
            allowed = obstacleSpeed(v, foot.x, foot.z, fx, fz, look, 2.2f, 6f)
            if (allowed < target) {
                target = allowed
                if (allowed < 1f) hardBrake = true
            }
        }
        if (v.ghost <= 0f) {
            for (o in vehicles) {
                if (o === v) continue
                allowed = obstacleSpeed(v, o.x, o.z, fx, fz, look, 2.4f, 6.5f)
                if (allowed < target) target = allowed
                allowed = yieldSpeed(v, o, look)
                if (allowed < target) target = allowed
            }
        } else {
            v.ghost -= dt
        }

        val accel = if (target >= v.speed) ACCEL else (if (hardBrake) 14f else BRAKE)
        v.speed += (target - v.speed).coerceIn(-accel * dt, accel * dt)
        if (v.speed < 0f) v.speed = 0f

        // Waiting for a long time behind another car: push through it.
        if (v.speed < 0.2f && target < 0.2f) {
            v.stuck += dt
            if (v.stuck > 5f) {
                v.ghost = 4f
                v.stuck = 0f
            }
        } else {
            v.stuck = max(0f, v.stuck - dt)
        }

        v.t += v.dir * v.speed * dt
        v.curLat += (v.lat - v.curLat).coerceIn(-3f * dt, 3f * dt)

        // Turn when the lane line of the new road is reached.
        val p = v.pending
        if (p != null && v.action != STRAIGHT && (v.turnT - v.t) * v.dir <= 0f) {
            doTurn(v, p)
        } else if (p == null || v.action == STRAIGHT) {
            val past = if (v.dir > 0) v.t >= v.road.end else v.t <= v.road.start
            if (past) {
                val next = if (v.dir > 0) nextForward[v.road] else nextBackward[v.road]
                if (next == null) {
                    v.remove = true
                    return
                }
                v.skipRoad = v.road
                v.road = next
                v.lat = laneOf(next, v.dir)
                v.pending = null
                setCruise(v)
            }
        }

        // Straight past the end, or far past a dead end, is the end of the line.
        if (v.t < v.road.start - 8f || v.t > v.road.end + 8f) {
            v.remove = true
            return
        }

        if (v.road.vertical) {
            v.x = v.curLat
            v.z = v.t
        } else {
            v.x = v.t
            v.z = v.curLat
        }

        // Ease the drawn heading toward the road heading, the short way round.
        var diff = v.yaw - v.yawVis
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        val maxTurn = 240f * dt
        v.yawVis += diff.coerceIn(-maxTurn, maxTurn)
    }

    /**
     * Two cars heading for the same crossing: the one that would get there second slows down
     * and stops short of it. Returns a big number when there is nothing to give way to.
     */
    private fun yieldSpeed(v: Vehicle, o: Vehicle, look: Float): Float {
        if (o.road.vertical == v.road.vertical) return 99f
        val dv = (o.curLat - v.t) * v.dir
        val dOther = (v.curLat - o.t) * o.dir
        if (dv <= 0f || dv > look) return 99f
        if (dOther < -4f || dOther > look) return 99f
        if (o.speed < 1f && dOther > 0f) return 99f
        val tv = dv / max(v.speed, 2f)
        val to = max(dOther, 0f) / max(o.speed, 2f)
        val giveWay = to < tv - 0.3f || (abs(tv - to) <= 1.2f && o.id < v.id) || (to < tv + 1.2f && dOther <= 0f)
        if (!giveWay) return 99f
        return max(0f, (dv - 7f) * 1.5f)
    }

    /** The fastest this vehicle may go because of something at (ox, oz), or a big number if clear. */
    private fun obstacleSpeed(
        v: Vehicle, ox: Float, oz: Float, fx: Float, fz: Float, look: Float, side: Float, stopGap: Float
    ): Float {
        val rx = ox - v.x
        val rz = oz - v.z
        val along = rx * fx + rz * fz
        if (along <= 0f || along > look) return 99f
        val lateral = abs(rx * fz - rz * fx)
        if (lateral > side) return 99f
        return max(0f, (along - stopGap) * 1.5f)
    }

    private fun laneOf(r: TownLayout.Road, d: Int): Float {
        val off = r.half * 0.5f
        return if (r.vertical) r.centre - d * off else r.centre + d * off
    }

    private fun yawFor(r: TownLayout.Road, d: Int): Float =
        if (r.vertical) (if (d < 0) 0f else 180f) else (if (d > 0) -90f else 90f)

    private fun setCruise(v: Vehicle) {
        val base = when (v.road.surface) {
            TownLayout.SURFACE_SMOOTH -> 13f
            TownLayout.SURFACE_WORN -> 10f
            TownLayout.SURFACE_POTHOLED -> 6.5f
            else -> 4.5f
        }
        v.cruise = base * v.factor * (if (v.danfo) 0.9f else 1f)
    }

    /** New direction on the crossing road when turning right (+1) or left (-1) off [r]. */
    private fun turnedDir(r: TownLayout.Road, d: Int, action: Int): Int {
        return if (r.vertical) {
            if (action == RIGHT) -d else d
        } else {
            if (action == RIGHT) d else -d
        }
    }

    private fun hasRoom(p: TownLayout.Road, d: Int, at: Float): Boolean =
        if (d > 0) at < p.end - 3f else at > p.start + 3f

    /** Looks for the next junction and, when close, decides straight, right or left. */
    private fun planCut(v: Vehicle) {
        val list = cuts[v.road] ?: return
        val pending = v.pending
        if (pending != null) {
            if (v.action == STRAIGHT && (pending.centre - v.t) * v.dir < -0.3f) v.pending = null else return
        }

        var found: Cut? = null
        for (k in list.indices) {
            val c = if (v.dir > 0) list[k] else list[list.size - 1 - k]
            if (c.other === v.skipRoad) continue
            if ((c.centre - v.t) * v.dir > 0.3f) {
                found = c
                break
            }
        }
        val c = found ?: return
        val off = v.road.half * 0.5f
        if ((c.centre - v.t) * v.dir > DECIDE_DISTANCE + off) return

        val p = c.other
        val end = if (v.dir > 0) v.road.end else v.road.start
        val atEnd = abs(c.centre - end) < 0.5f
        val canGoStraight = !(atEnd && (if (v.dir > 0) nextForward[v.road] else nextBackward[v.road]) == null)

        val rightDir = turnedDir(v.road, v.dir, RIGHT)
        val leftDir = turnedDir(v.road, v.dir, LEFT)
        val rightLat = if (p.vertical) p.centre - rightDir * p.half * 0.5f else p.centre + rightDir * p.half * 0.5f
        val leftLat = if (p.vertical) p.centre - leftDir * p.half * 0.5f else p.centre + leftDir * p.half * 0.5f
        val canRight = hasRoom(p, rightDir, v.curLat)
        val canLeft = hasRoom(p, leftDir, v.curLat)

        var wStraight = if (canGoStraight) 0.55f else 0f
        var wRight = if (canRight) 0.20f else 0f
        var wLeft = if (canLeft) 0.25f else 0f
        val total = wStraight + wRight + wLeft
        if (total <= 0f) {
            v.remove = true
            return
        }
        wStraight /= total
        wRight /= total
        wLeft /= total
        val r = rnd.nextFloat()
        v.pending = c
        when {
            r < wStraight -> v.action = STRAIGHT
            r < wStraight + wRight -> {
                v.action = RIGHT
                v.turnT = rightLat
                v.turnDir = rightDir
            }
            else -> {
                v.action = LEFT
                v.turnT = leftLat
                v.turnDir = leftDir
            }
        }
    }

    private fun doTurn(v: Vehicle, c: Cut) {
        val old = v.road
        val newRoad = c.other
        val crossAxis = v.curLat
        v.road = newRoad
        v.dir = v.turnDir
        v.lat = laneOf(newRoad, v.dir)
        v.curLat = v.turnT
        v.t = crossAxis
        v.yaw = yawFor(newRoad, v.dir)
        v.pending = null
        v.action = STRAIGHT
        v.skipRoad = old
        setCruise(v)
    }

    // ------------------------------------------------------------------ spawning

    private fun trySpawn(focus: Vector3, car: Vector3, minD: Float, maxD: Float) {
        val roads = TownLayout.roads
        for (attempt in 0 until 8) {
            val r = roads[rnd.nextInt(roads.size)]
            val along = if (r.vertical) focus.z else focus.x
            val across = if (r.vertical) focus.x else focus.z
            val dAcross = abs(across - r.centre)
            if (dAcross > maxD) continue
            val reach = sqrt(maxD * maxD - dAcross * dAcross)
            val lo = max(r.start + 4f, along - reach)
            val hi = min(r.end - 4f, along + reach)
            if (hi <= lo) continue
            val t = lo + rnd.nextFloat() * (hi - lo)
            val dir = if (rnd.nextBoolean()) 1 else -1
            val lat = laneOf(r, dir)
            val x = if (r.vertical) lat else t
            val z = if (r.vertical) t else lat

            val fx = x - focus.x
            val fz = z - focus.z
            val d2 = fx * fx + fz * fz
            if (d2 < minD * minD || d2 > maxD * maxD) continue
            if (x < TownLayout.DRIVE_MIN_X || x > TownLayout.DRIVE_MAX_X) continue
            if (z < TownLayout.DRIVE_MIN_Z || z > TownLayout.DRIVE_MAX_Z) continue
            val cx = x - car.x
            val cz = z - car.z
            if (cx * cx + cz * cz < 20f * 20f) continue

            var clear = true
            for (o in vehicles) {
                val ox = o.x - x
                val oz = o.z - z
                if (ox * ox + oz * oz < 14f * 14f) {
                    clear = false
                    break
                }
            }
            if (!clear) continue

            spawnAt(r, dir, t, lat, z)
            return
        }
    }

    private fun spawnAt(r: TownLayout.Road, dir: Int, t: Float, lat: Float, z: Float) {
        val inIkorodu = Districts.at(z) == Districts.IKORODU
        val danfo = inIkorodu && rnd.nextFloat() < 0.35f
        val model = when {
            danfo -> danfoModel
            inIkorodu -> ikoroduModels[rnd.nextInt(ikoroduModels.size)]
            else -> ikoyiModels[rnd.nextInt(ikoyiModels.size)]
        }
        val v = Vehicle(ModelInstance(model), danfo)
        v.road = r
        v.dir = dir
        v.t = t
        v.lat = lat
        v.curLat = lat
        v.factor = 0.8f + rnd.nextFloat() * 0.35f
        setCruise(v)
        v.speed = v.cruise * 0.9f
        v.yaw = yawFor(r, dir)
        v.yawVis = v.yaw
        v.x = if (r.vertical) lat else t
        v.z = if (r.vertical) t else lat
        vehicles.add(v)
    }

    private companion object {
        const val STRAIGHT = 0
        const val RIGHT = 1
        const val LEFT = 2

        const val MAX_VEHICLES = 22
        const val SPAWN_MIN = 110f
        const val SPAWN_MAX = 300f
        const val DESPAWN_RANGE = 340f
        const val DRAW_RANGE = 300f

        const val DECIDE_DISTANCE = 14f
        const val TURN_SPEED = 6f
        const val ACCEL = 3.5f
        const val BRAKE = 9f

        const val CAR_OFFSET = 1.2f
        const val CAR_RADIUS = 1.1f
    }
}
