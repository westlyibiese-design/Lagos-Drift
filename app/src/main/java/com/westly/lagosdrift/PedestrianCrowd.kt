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
 * They walk to the end of their pavement, turn round and come back.
 * They do not react to the car yet.
 */
class PedestrianCrowd(models: List<Model>) {
    private class Walker(
        val instance: ModelInstance,
        val legL: Node, val legR: Node, val armL: Node, val armR: Node,
        val way: TownLayout.Walkway,
        var along: Float, var direction: Float,
        val speed: Float, val lateral: Float, var phase: Float
    ) {
        var x = 0f
        var z = 0f
    }

    private val walkers = ArrayList<Walker>()

    init {
        val rnd = Random(77)
        val ways = TownLayout.walkways
        val totalLength = ways.sumOf { it.length.toDouble() }.toFloat()
        repeat(COUNT) {
            // Pick a pavement, longer ones more often.
            var pick = rnd.nextFloat() * totalLength
            var way = ways[0]
            for (w in ways) {
                if (pick < w.length) {
                    way = w
                    break
                }
                pick -= w.length
            }
            val instance = ModelInstance(models[rnd.nextInt(models.size)])
            walkers.add(
                Walker(
                    instance,
                    instance.getNode("legL"), instance.getNode("legR"),
                    instance.getNode("armL"), instance.getNode("armR"),
                    way,
                    rnd.nextFloat() * way.length,
                    if (rnd.nextBoolean()) 1f else -1f,
                    1.1f + rnd.nextFloat() * 0.8f,
                    (rnd.nextFloat() - 0.5f) * 1.8f,
                    rnd.nextFloat() * 6.28f
                )
            )
        }
    }

    fun update(delta: Float) {
        for (w in walkers) {
            w.along += w.direction * w.speed * delta
            if (w.along > w.way.length) {
                w.along = w.way.length
                w.direction = -1f
            } else if (w.along < 0f) {
                w.along = 0f
                w.direction = 1f
            }

            val dx = w.direction * w.way.dirX
            val dz = w.direction * w.way.dirZ
            val x = w.way.x1 + w.way.dirX * w.along + (-w.way.dirZ) * w.lateral
            val z = w.way.z1 + w.way.dirZ * w.along + w.way.dirX * w.lateral
            val yaw = atan2(-dx, -dz) * MathUtils.radiansToDegrees

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
            val dx = w.x - carPosition.x
            val dz = w.z - carPosition.z
            if (dx * dx + dz * dz < DRAW_DISTANCE * DRAW_DISTANCE) batch.render(w.instance, environment)
        }
    }

    private companion object {
        const val COUNT = 28
        const val DRAW_DISTANCE = 190f
    }
}
