package com.westly.lagosdrift

import com.badlogic.gdx.math.MathUtils
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Stops the car and the person on foot going through houses, market stalls and trees. The car is treated as two circles,
 * one near the nose and one near the tail. Hitting something slows the car right down. Only the
 * chunks around the car are checked.
 */
object TownCollision {
    private const val RADIUS = 1.1f
    private const val OFFSET = 1.2f
    private const val TRUNK_RADIUS = 0.5f

    /** [radius] and [offset] describe the vehicle: two circles of that radius, that far from the middle. */
    fun resolve(car: CarController, radius: Float = RADIUS, offset: Float = OFFSET) {
        val rad = car.yawDegrees * MathUtils.degreesToRadians
        val ox = -sin(rad) * offset
        val oz = -cos(rad) * offset
        pushOut(car, ox, oz, radius)
        pushOut(car, -ox, -oz, radius)
    }

    private fun pushOut(car: CarController, ox: Float, oz: Float, radius: Float) {
        val p = car.position
        val cx0 = TownLayout.cxOf(p.x)
        val cz0 = TownLayout.czOf(p.z)

        for (dz in -1..1) {
            for (dx in -1..1) {
                val chunk = TownLayout.chunkAt(cx0 + dx, cz0 + dz) ?: continue
                pushOutOfHouses(car, chunk, ox, oz, radius)
                pushOutOfTrees(car, chunk, ox, oz, radius)
            }
        }
    }

    private fun pushOutOfHouses(car: CarController, chunk: TownLayout.Chunk, ox: Float, oz: Float, radius: Float) {
        val p = car.position
        for (h in chunk.houses) {
            val cx = p.x + ox
            val cz = p.z + oz
            val nearX = cx.coerceIn(h.cx - h.hw, h.cx + h.hw)
            val nearZ = cz.coerceIn(h.cz - h.hd, h.cz + h.hd)
            val dx = cx - nearX
            val dz = cz - nearZ
            val d2 = dx * dx + dz * dz
            if (d2 >= radius * radius) continue

            if (d2 > 0.0001f) {
                val d = sqrt(d2)
                val push = radius - d
                p.x += dx / d * push
                p.z += dz / d * push
            } else {
                // The circle centre is inside the building: leave by the shortest way.
                val left = cx - (h.cx - h.hw)
                val right = (h.cx + h.hw) - cx
                val back = cz - (h.cz - h.hd)
                val front = (h.cz + h.hd) - cz
                val m = minOf(minOf(left, right), minOf(back, front))
                when (m) {
                    left -> p.x = h.cx - h.hw - radius - ox
                    right -> p.x = h.cx + h.hw + radius - ox
                    back -> p.z = h.cz - h.hd - radius - oz
                    else -> p.z = h.cz + h.hd + radius - oz
                }
            }
            // A market stall gives way more than a wall does.
            car.bump(if (h.type == TownLayout.TYPE_STALL) 0.75f else 0.5f)
        }
    }

    private fun pushOutOfTrees(car: CarController, chunk: TownLayout.Chunk, ox: Float, oz: Float, radius: Float) {
        val p = car.position
        for (t in chunk.trees) {
            val dx = p.x + ox - t.x
            val dz = p.z + oz - t.z
            val d2 = dx * dx + dz * dz
            val limit = radius + TRUNK_RADIUS * t.scale
            if (d2 >= limit * limit) continue

            val d = maxOf(sqrt(d2), 0.001f)
            val push = limit - d
            p.x += dx / d * push
            p.z += dz / d * push
            car.bump(0.5f)
        }
    }

    // ------------------------------------------------------------------ people on foot

    /** Keeps a walking person (a circle of [radius]) out of buildings, stalls and trees. */
    fun resolvePoint(p: com.badlogic.gdx.math.Vector3, radius: Float) {
        val cx0 = TownLayout.cxOf(p.x)
        val cz0 = TownLayout.czOf(p.z)
        for (dz in -1..1) {
            for (dx in -1..1) {
                val chunk = TownLayout.chunkAt(cx0 + dx, cz0 + dz) ?: continue
                for (h in chunk.houses) {
                    val nearX = p.x.coerceIn(h.cx - h.hw, h.cx + h.hw)
                    val nearZ = p.z.coerceIn(h.cz - h.hd, h.cz + h.hd)
                    val ex = p.x - nearX
                    val ez = p.z - nearZ
                    val d2 = ex * ex + ez * ez
                    if (d2 >= radius * radius) continue
                    if (d2 > 0.0001f) {
                        val d = sqrt(d2)
                        p.x += ex / d * (radius - d)
                        p.z += ez / d * (radius - d)
                    } else {
                        // Standing inside a building: leave by the shortest way.
                        val left = p.x - (h.cx - h.hw)
                        val right = (h.cx + h.hw) - p.x
                        val back = p.z - (h.cz - h.hd)
                        val front = (h.cz + h.hd) - p.z
                        val m = minOf(minOf(left, right), minOf(back, front))
                        when (m) {
                            left -> p.x = h.cx - h.hw - radius
                            right -> p.x = h.cx + h.hw + radius
                            back -> p.z = h.cz - h.hd - radius
                            else -> p.z = h.cz + h.hd + radius
                        }
                    }
                }
                for (t in chunk.trees) {
                    val ex = p.x - t.x
                    val ez = p.z - t.z
                    val limit = radius + TRUNK_RADIUS * t.scale
                    val d2 = ex * ex + ez * ez
                    if (d2 >= limit * limit) continue
                    val d = maxOf(sqrt(d2), 0.001f)
                    p.x += ex / d * (limit - d)
                    p.z += ez / d * (limit - d)
                }
            }
        }
    }

    /** Keeps a walking person out of a car (the car is two circles, like in resolve). */
    fun pushOutOfCar(
        p: com.badlogic.gdx.math.Vector3, radius: Float, carPos: com.badlogic.gdx.math.Vector3, carYaw: Float,
        carRadius: Float = RADIUS, carOffset: Float = OFFSET
    ) {
        val rad = carYaw * MathUtils.degreesToRadians
        val ox = -sin(rad) * carOffset
        val oz = -cos(rad) * carOffset
        pushOutOfCircle(p, radius, carPos.x + ox, carPos.z + oz, carRadius)
        pushOutOfCircle(p, radius, carPos.x - ox, carPos.z - oz, carRadius)
    }

    /** Pushes the person out of one circle. Used for cars, parked or driving. */
    fun pushOutOfCircle(p: com.badlogic.gdx.math.Vector3, radius: Float, cx: Float, cz: Float, circleRadius: Float) {
        val ex = p.x - cx
        val ez = p.z - cz
        val limit = radius + circleRadius
        val d2 = ex * ex + ez * ez
        if (d2 >= limit * limit) return
        val d = maxOf(sqrt(d2), 0.001f)
        p.x += ex / d * (limit - d)
        p.z += ez / d * (limit - d)
    }
}
