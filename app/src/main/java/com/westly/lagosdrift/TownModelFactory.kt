package com.westly.lagosdrift

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder

/** Draws every house (walls, flat roof, door, windows) and tree from TownLayout. */
object TownModelFactory {
    private val wallColors = arrayOf(
        Color(0.93f, 0.90f, 0.82f, 1f),
        Color(0.88f, 0.78f, 0.62f, 1f),
        Color(0.80f, 0.86f, 0.90f, 1f),
        Color(0.92f, 0.72f, 0.62f, 1f),
        Color(0.75f, 0.82f, 0.72f, 1f),
        Color(0.86f, 0.86f, 0.88f, 1f)
    )
    private val roofColors = arrayOf(
        Color(0.55f, 0.30f, 0.25f, 1f),
        Color(0.38f, 0.40f, 0.44f, 1f),
        Color(0.28f, 0.30f, 0.34f, 1f)
    )
    private val leafColors = arrayOf(
        Color(0.16f, 0.45f, 0.20f, 1f),
        Color(0.25f, 0.55f, 0.22f, 1f)
    )

    fun build(): Model {
        val b = ModelBuilder()
        b.begin()
        b.node()

        // Walls and roofs, one part per colour so the phone draws few batches.
        for (c in 0 until TownLayout.WALL_COLORS) {
            val part = b.colorPart("wall$c", wallColors[c])
            for (h in TownLayout.houses) {
                if (h.wallColor == c) part.boxAt(h.cx, h.height / 2f, h.cz, h.hw * 2f, h.height, h.hd * 2f)
            }
        }
        for (c in 0 until TownLayout.ROOF_COLORS) {
            val part = b.colorPart("roof$c", roofColors[c])
            for (h in TownLayout.houses) {
                if (h.roofColor == c) part.boxAt(h.cx, h.height + 0.2f, h.cz, h.hw * 2f + 0.6f, 0.4f, h.hd * 2f + 0.6f)
            }
        }

        // Doors: one in the middle of each front wall.
        val doors = b.colorPart("doors", Color(0.35f, 0.22f, 0.14f, 1f))
        for (h in TownLayout.houses) {
            if (h.facingX != 0) {
                doors.boxAt(h.cx + h.facingX * (h.hw + 0.02f), 1.1f, h.cz, 0.1f, 2.2f, 1.5f)
            } else {
                doors.boxAt(h.cx, 1.1f, h.cz + h.facingZ * (h.hd + 0.02f), 1.5f, 2.2f, 0.1f)
            }
        }

        // Windows on the front wall: 2 on the ground floor, 3 on each upper floor.
        val windows = b.colorPart("windows", Color(0.18f, 0.24f, 0.32f, 1f))
        for (h in TownLayout.houses) {
            val floors = maxOf(1, (h.height / 3f).toInt())
            val lateralHalf = if (h.facingX != 0) h.hd else h.hw
            for (floor in 0 until floors) {
                val y = 1.8f + floor * 3f
                val offsets = if (floor == 0) {
                    floatArrayOf(-0.55f * lateralHalf, 0.55f * lateralHalf)
                } else {
                    floatArrayOf(-0.55f * lateralHalf, 0f, 0.55f * lateralHalf)
                }
                for (lat in offsets) {
                    if (h.facingX != 0) {
                        windows.boxAt(h.cx + h.facingX * (h.hw + 0.02f), y, h.cz + lat, 0.1f, 1.3f, 1.4f)
                    } else {
                        windows.boxAt(h.cx + lat, y, h.cz + h.facingZ * (h.hd + 0.02f), 1.4f, 1.3f, 0.1f)
                    }
                }
            }
        }

        // Trees: trunks, then green cones in two shades.
        val trunks = b.colorPart("trunks", Color(0.40f, 0.27f, 0.16f, 1f))
        for (t in TownLayout.trees) {
            trunks.cylinderAt(t.x, 1.1f * t.scale, t.z, 0.5f * t.scale, 2.2f * t.scale, 8)
        }
        for (c in 0 until leafColors.size) {
            val leaves = b.colorPart("leaves$c", leafColors[c])
            for (t in TownLayout.trees) {
                if (t.leafColor == c) {
                    leaves.coneAt(t.x, 2.0f * t.scale + 2.25f * t.scale, t.z, 3.4f * t.scale, 4.5f * t.scale, 9)
                }
            }
        }

        return b.end()
    }
}
