package com.westly.lagosdrift

import com.badlogic.gdx.math.MathUtils
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Stops the car going through houses and trees. The car is treated as two circles,
 * one near the nose and one near the tail. Hitting something slows the car right down.
 */
object TownCollision {
    private const val RADIUS = 1.1f
    private const val OFFSET = 1.2f
    private const val TRUNK_RADIUS = 0.5f

    fun resolve(car: CarController) {
        val rad = car.yawDegrees * MathUtils.degreesToRadians
        val ox = -sin(rad) * OFFSET
        val oz = -cos(rad) * OFFSET
        pushOut(car, ox, oz)
        pushOut(car, -ox, -oz)
    }

    private fun pushOut(car: CarController, ox: Float, oz: Float) {
        val p = car.position

        for (h in TownLayout.houses) {
            val cx = p.x + ox
            val cz = p.z + oz
            val nearX = cx.coerceIn(h.cx - h.hw, h.cx + h.hw)
            val nearZ = cz.coerceIn(h.cz - h.hd, h.cz + h.hd)
            val dx = cx - nearX
            val dz = cz - nearZ
            val d2 = dx * dx + dz * dz
            if (d2 >= RADIUS * RADIUS) continue

            if (d2 > 0.0001f) {
                val d = sqrt(d2)
                val push = RADIUS - d
                p.x += dx / d * push
                p.z += dz / d * push
            } else {
                // The circle centre is inside the house: leave by the shortest way.
                val left = cx - (h.cx - h.hw)
                val right = (h.cx + h.hw) - cx
                val back = cz - (h.cz - h.hd)
                val front = (h.cz + h.hd) - cz
                val m = minOf(minOf(left, right), minOf(back, front))
                when (m) {
                    left -> p.x = h.cx - h.hw - RADIUS - ox
                    right -> p.x = h.cx + h.hw + RADIUS - ox
                    back -> p.z = h.cz - h.hd - RADIUS - oz
                    else -> p.z = h.cz + h.hd + RADIUS - oz
                }
            }
            car.bump(0.5f)
        }

        for (t in TownLayout.trees) {
            val dx = p.x + ox - t.x
            val dz = p.z + oz - t.z
            val d2 = dx * dx + dz * dz
            val limit = RADIUS + TRUNK_RADIUS * t.scale
            if (d2 >= limit * limit) continue

            val d = maxOf(sqrt(d2), 0.001f)
            val push = limit - d
            p.x += dx / d * push
            p.z += dz / d * push
            car.bump(0.5f)
        }
    }
}
