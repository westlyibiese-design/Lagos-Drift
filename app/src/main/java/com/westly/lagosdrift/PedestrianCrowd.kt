package com.westly.lagosdrift

import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.random.Random

/**
 * People strolling up and down the pavements, swinging their arms and legs.
 * The world is big, so the crowd lives around the car: when a person is left far behind they
 * start again on a pavement near the car. There are more people on the unpaved roads of Ikorodu.
 * They walk to the end of their pavement, turn round and come back. They do not react to the car yet.
 */
class PedestrianCrowd(models: List<Model>) {
    private class Walker(
        val instance: ModelInstance,
        val legL: Node, val legR: Node, val armL: Node, val armR: Node
    ) {
        var way: TownLayout.Walkway? = null
        var along = 0f
        var direction = 1f
        var speed = 1.4f
        var lateral = 0f
        var phase = 0f
        var x = 0f
        var z = 0f
    }

    private val rnd = Random(77)
    private val walkers = ArrayList<Walker>()
    private val nearby = ArrayList<TownLayout.Walkway>()
    private var nearbyCx = Int.MIN_VALUE
    private var nearbyCz = Int.MIN_VALUE
    private var nearbyWeight = 0f

    init {
        repeat(COUNT) {
            val instance = ModelInstance(models[rnd.nextInt(models.size)])
            walkers.add(
                Walker(
                    instance,
                    instance.getNode("legL"), instance.getNode("legR"),
                    instance.getNode("armL"), instance.getNode("armR")
                )
            )
        }
    }

    /** Collects the pavements within two chunks of the car (only when the car changes chunk). */
    private fun refreshNearby(carX: Float, carZ: Float) {
        val cx = TownLayout.cxOf(carX)
        val cz = TownLayout.czOf(carZ)
        if (cx == nearbyCx && cz == nearbyCz) return
        nearbyCx = cx
        nearbyCz = cz
        nearby.clear()
        nearbyWeight = 0f
        for (dz in -2..2) {
            for (dx in -2..2) {
                val chunk = TownLayout.chunkAt(cx + dx, cz + dz) ?: continue
                for (w in chunk.walkways) {
                    if (!nearby.contains(w)) {
                        nearby.add(w)
                        nearbyWeight += weight(w)
                    }
                }
            }
        }
    }

    private fun weight(w: TownLayout.Walkway): Float = w.length * if (w.paved) 1f else 1.8f

    /** Puts a walker on a random nearby pavement, not right next to the car. */
    private fun respawn(w: Walker, carX: Float, carZ: Float) {
        if (nearby.isEmpty()) {
            w.way = null
            return
        }
        repeat(6) {
            var pick = rnd.nextFloat() * nearbyWeight
            var way = nearby[0]
            for (candidate in nearby) {
                val cw = weight(candidate)
                if (pick < cw) {
                    way = candidate
                    break
                }
                pick -= cw
            }
            val along = rnd.nextFloat() * way.length
            val x = way.x1 + way.dirX * along
            val z = way.z1 + way.dirZ * along
            val dx = x - carX
            val dz = z - carZ
            if (dx * dx + dz * dz < MIN_SPAWN_DISTANCE * MIN_SPAWN_DISTANCE) return@repeat

            w.way = way
            w.along = along
            w.direction = if (rnd.nextBoolean()) 1f else -1f
            w.speed = 1.1f + rnd.nextFloat() * 0.8f
            w.lateral = (rnd.nextFloat() - 0.5f) * 1.8f
            w.phase = rnd.nextFloat() * 6.28f
            return
        }
    }

    fun update(delta: Float, car: Vector3) {
        refreshNearby(car.x, car.z)
        for (w in walkers) {
            var way = w.way
            if (way != null) {
                val dx = w.x - car.x
                val dz = w.z - car.z
                if (dx * dx + dz * dz > DESPAWN_DISTANCE * DESPAWN_DISTANCE) way = null
            }
            if (way == null) {
                respawn(w, car.x, car.z)
                way = w.way ?: continue
                if (way.length <= 0f) continue
            }

            w.along += w.direction * w.speed * delta
            if (w.along > way.length) {
                w.along = way.length
                w.direction = -1f
            } else if (w.along < 0f) {
                w.along = 0f
                w.direction = 1f
            }

            val dirX = w.direction * way.dirX
            val dirZ = w.direction * way.dirZ
            val x = way.x1 + way.dirX * w.along + (-way.dirZ) * w.lateral
            val z = way.z1 + way.dirZ * w.along + way.dirX * w.lateral
            val yaw = atan2(-dirX, -dirZ) * MathUtils.radiansToDegrees

            w.phase += delta * w.speed * 4.5f
            val swing = sin(w.phase) * 38f
            w.legL.rotation.set(Vector3.X, swing)
            w.legR.rotation.set(Vector3.X, -swing)
            w.armL.rotation.set(Vector3.X, -swing * 0.8f)
            w.armR.rotation.set(Vector3.X, swing * 0.8f)

            w.x = x
            w.z = z
            w.instance.transform.setToTranslation(x, 0.12f, z).rotate(Vector3.Y, yaw)
            w.instance.calculateTransforms()
        }
    }

    /** Draws only the people close to the car, to keep the phone fast. */
    fun render(batch: ModelBatch, environment: Environment, carPosition: Vector3) {
        for (w in walkers) {
            if (w.way == null) continue
            val dx = w.x - carPosition.x
            val dz = w.z - carPosition.z
            if (dx * dx + dz * dz < DRAW_DISTANCE * DRAW_DISTANCE) batch.render(w.instance, environment)
        }
    }

    private companion object {
        const val COUNT = 40
        const val DRAW_DISTANCE = 190f
        const val DESPAWN_DISTANCE = 230f
        const val MIN_SPAWN_DISTANCE = 30f
    }
}
