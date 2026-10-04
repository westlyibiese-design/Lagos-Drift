package com.westly.lagosdrift

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/** The places in Ikorodu where people wait for a danfo. */
object BusStops {
    class Stop(
        val name: String,
        /** Where the danfo should stop (in the road). */
        val x: Float, val z: Float,
        /** Where the people stand (beside the road). */
        val peopleX: Float, val peopleZ: Float,
        /** Direction the people face (toward the road), degrees, same rule as the car's yaw. */
        val faceYaw: Float
    )

    private val names = arrayOf(
        "Ikorodu Garage", "Majidun", "Sagamu Road", "Ijede Junction", "Igbogbo", "Ofin", "Odogunyan",
        "Imota", "Agura", "Oke-Eletu", "Laspotech", "Ebute", "Maya", "Ibeshe", "Owode", "Itamaga",
        "Ayangburen", "Isiu", "Ipakodo", "Ita-Elewa", "Erikorodo", "Gberigbe", "Itoikin", "Bayeku"
    )

    val all: List<Stop> = build()

    private fun build(): List<Stop> {
        val rnd = Random(88)
        val list = ArrayList<Stop>()
        val ikorodu = TownLayout.roads.filter {
            if (it.vertical) it.end <= 0.5f else it.centre < -50f
        }
        // Stops start close to the border road and run north, so there are some near your danfo.
        for (r in ikorodu) {
            if (list.size >= names.size) break
            var count = 0
            if (r.vertical) {
                var t = -85f - rnd.nextFloat() * 40f
                while (t > -700f && count < 3 && list.size < names.size) {
                    if (clearOfJunctions(r, t)) {
                        list.add(makeStop(r, t, list.size))
                        count++
                        t -= 190f + rnd.nextFloat() * 90f
                    } else {
                        t -= 25f
                    }
                }
            } else {
                val lo = maxOf(r.start, -700f) + 60f
                val hi = minOf(r.end, 600f) - 60f
                var t = lo + rnd.nextFloat() * 100f
                while (t < hi && count < 2 && list.size < names.size) {
                    if (clearOfJunctions(r, t)) {
                        list.add(makeStop(r, t, list.size))
                        count++
                        t += 230f + rnd.nextFloat() * 100f
                    } else {
                        t += 25f
                    }
                }
            }
        }
        return list
    }

    private fun clearOfJunctions(r: TownLayout.Road, t: Float): Boolean {
        for (p in TownLayout.roads) {
            if (p.vertical == r.vertical) continue
            val crosses = r.centre >= p.start - 1f && r.centre <= p.end + 1f && p.centre >= r.start - 1f && p.centre <= r.end + 1f
            if (crosses && abs(p.centre - t) < p.half + 16f) return false
        }
        return true
    }

    private fun makeStop(r: TownLayout.Road, t: Float, index: Int): Stop {
        val name = names[index % names.size]
        val lane = r.half * 0.5f
        val edge = r.half + 1.6f
        return if (r.vertical) {
            Stop(name, r.centre + lane, t, r.centre + edge, t, 90f)
        } else {
            Stop(name, t, r.centre + lane, t, r.centre + edge, 0f)
        }
    }
}

/** A stop on the map: state 0 = quiet, 1 = people waiting, 2 = a passenger wants to get off here. */
class MapStop(val x: Float, val z: Float) {
    var state = 0
}

/**
 * Driving the danfo for money. People wait at the stops. Stop the danfo close to a stop and they get
 * on, one by one. Each passenger has a stop to get off at; stop there and they pay the fare.
 */
class DanfoJob(private val pedestrianModels: List<Model>) {
    private class Passenger(val destination: Int, val fromX: Float, val fromZ: Float)

    private val stops = BusStops.all
    private val rnd = Random(4242)
    private val onBoard = ArrayList<Passenger>()
    private val waiting = IntArray(stops.size)
    private val refillTimer = FloatArray(stops.size)
    private val people = Array(stops.size) { ArrayList<ModelInstance>() }
    private val signModel: Model = buildSignModel()
    private val signs = Array(stops.size) { ModelInstance(signModel) }
    private var actionTimer = 0f

    val mapStops: List<MapStop> = stops.map { MapStop(it.x, it.z) }

    var money = 0
        private set
    val passengerCount: Int get() = onBoard.size

    /** A short message for the screen, and how long it still has to show. */
    var message = ""
        private set
    var messageTimer = 0f
        private set

    init {
        for (i in stops.indices) {
            waiting[i] = 1 + rnd.nextInt(3)
            val s = stops[i]
            signs[i].transform.setToTranslation(s.peopleX + (s.peopleX - s.x) * 0.25f, 0f, s.peopleZ + (s.peopleZ - s.z) * 0.25f)
            syncPeople(i)
        }
    }

    fun update(delta: Float, danfo: CarController?) {
        if (messageTimer > 0f) messageTimer -= delta

        for (i in stops.indices) {
            if (waiting[i] == 0) {
                refillTimer[i] -= delta
                if (refillTimer[i] <= 0f) {
                    waiting[i] = 1 + rnd.nextInt(4)
                    syncPeople(i)
                }
            }
        }

        refreshMapStops()
        if (danfo == null) return

        actionTimer -= delta
        if (actionTimer > 0f || abs(danfo.speed) > STOP_SPEED) return

        val near = nearestStop(danfo.position.x, danfo.position.z)
        if (near < 0) return
        val s = stops[near]
        val dx = danfo.position.x - s.x
        val dz = danfo.position.z - s.z
        if (dx * dx + dz * dz > BOARD_RANGE * BOARD_RANGE) return

        // First anyone getting off here, then anyone getting on.
        val leaving = onBoard.indexOfFirst { it.destination == near }
        if (leaving >= 0) {
            val p = onBoard.removeAt(leaving)
            val dist = sqrt((s.x - p.fromX) * (s.x - p.fromX) + (s.z - p.fromZ) * (s.z - p.fromZ))
            val fare = (((100f + dist * 0.5f) / 10f).toInt() * 10).coerceIn(100, 500)
            money += fare
            say("+N$fare  (${s.name})")
            actionTimer = ACTION_DELAY
            return
        }
        if (waiting[near] > 0 && onBoard.size < CAPACITY) {
            waiting[near]--
            if (waiting[near] == 0) refillTimer[near] = REFILL_SECONDS
            syncPeople(near)
            val dest = pickDestination(near)
            onBoard.add(Passenger(dest, s.x, s.z))
            say("Passenger going to ${stops[dest].name}")
            actionTimer = ACTION_DELAY
        } else if (waiting[near] > 0) {
            say("Danfo is full")
            actionTimer = 1.5f
        }
    }

    fun render(batch: ModelBatch, environment: Environment, camera: PerspectiveCamera, focus: Vector3) {
        for (i in stops.indices) {
            val s = stops[i]
            val dx = s.x - focus.x
            val dz = s.z - focus.z
            val d2 = dx * dx + dz * dz
            if (d2 > SIGN_RANGE * SIGN_RANGE) continue
            if (!camera.frustum.sphereInFrustum(s.x, 2f, s.z, 8f)) continue
            batch.render(signs[i], environment)
            if (d2 < PEOPLE_RANGE * PEOPLE_RANGE) for (p in people[i]) batch.render(p, environment)
        }
    }

    /** "Drop: Majidun 320 m" or the nearest stop with people waiting; empty if there is nothing to say. */
    fun hintLine(x: Float, z: Float): String {
        var best = -1
        var bestD = Float.MAX_VALUE
        for (p in onBoard) {
            val d = dist2(stops[p.destination].x, stops[p.destination].z, x, z)
            if (d < bestD) {
                bestD = d
                best = p.destination
            }
        }
        if (best >= 0) return "Drop: ${stops[best].name}  ${sqrt(bestD).toInt()} m"
        for (i in stops.indices) {
            if (waiting[i] == 0) continue
            val d = dist2(stops[i].x, stops[i].z, x, z)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return if (best >= 0) "Pick up: ${stops[best].name}  ${sqrt(bestD).toInt()} m" else ""
    }

    fun dispose() {
        signModel.dispose()
    }

    private fun say(text: String) {
        message = text
        messageTimer = 2.2f
    }

    private fun dist2(ax: Float, az: Float, bx: Float, bz: Float): Float = (ax - bx) * (ax - bx) + (az - bz) * (az - bz)

    private fun nearestStop(x: Float, z: Float): Int {
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in stops.indices) {
            val d = dist2(stops[i].x, stops[i].z, x, z)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }

    private fun pickDestination(from: Int): Int {
        // Prefer a stop 150 to 700 metres away.
        val good = ArrayList<Int>()
        for (i in stops.indices) {
            if (i == from) continue
            val d = sqrt(dist2(stops[i].x, stops[i].z, stops[from].x, stops[from].z))
            if (d in 150f..700f) good.add(i)
        }
        if (good.isNotEmpty()) return good[rnd.nextInt(good.size)]
        var pick = rnd.nextInt(stops.size)
        if (pick == from) pick = (pick + 1) % stops.size
        return pick
    }

    private fun refreshMapStops() {
        for (i in stops.indices) {
            mapStops[i].state = when {
                onBoard.any { it.destination == i } -> 2
                waiting[i] > 0 -> 1
                else -> 0
            }
        }
    }

    /** Makes the number of people drawn at a stop match the number waiting. */
    private fun syncPeople(i: Int) {
        val list = people[i]
        val s = stops[i]
        while (list.size > waiting[i]) list.removeAt(list.size - 1)
        while (list.size < waiting[i]) {
            val inst = ModelInstance(pedestrianModels[rnd.nextInt(pedestrianModels.size)])
            val k = list.size
            val along = (k - 1.5f) * 0.9f
            // Spread the group along the road edge.
            val px: Float
            val pz: Float
            if (abs(s.peopleX - s.x) > abs(s.peopleZ - s.z)) {
                px = s.peopleX
                pz = s.peopleZ + along
            } else {
                px = s.peopleX + along
                pz = s.peopleZ
            }
            inst.transform.setToTranslation(px, 0.12f, pz).rotate(Vector3.Y, s.faceYaw + (rnd.nextFloat() - 0.5f) * 40f)
            inst.calculateTransforms()
            list.add(inst)
        }
    }

    private fun buildSignModel(): Model {
        val b = ModelBuilder()
        b.begin()
        b.node()
        val pole = b.colorPart("stopPole", Color(0.25f, 0.26f, 0.28f, 1f))
        pole.cylinderAt(0f, 1.7f, 0f, 0.14f, 3.4f, 10)
        val board = b.colorPart("stopBoard", Color(0.97f, 0.78f, 0.10f, 1f))
        board.boxAt(0f, 3.05f, 0f, 1.1f, 0.7f, 0.1f)
        val band = b.colorPart("stopBand", Color(0.08f, 0.08f, 0.09f, 1f))
        band.boxAt(0f, 3.05f, 0f, 1.12f, 0.12f, 0.12f)
        return b.end()
    }

    companion object {
        const val CAPACITY = 14
        const val BOARD_RANGE = 11f
        const val STOP_SPEED = 1.8f
        const val ACTION_DELAY = 0.6f
        const val REFILL_SECONDS = 25f
        const val SIGN_RANGE = 260f
        const val PEOPLE_RANGE = 170f
    }
}
