package com.westly.lagosdrift

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder

/**
 * Draws every building and tree from TownLayout:
 * bungalows and two-storey houses with pitched roofs, shops with awnings and signs,
 * and towers with balconies and a water tank, plus round trees and palms.
 */
object TownModelFactory {
    /** Puts a flat box on the front wall. width runs along the wall, thickness sticks out. */
    private fun MeshPartBuilder.facadeBoxAt(
        h: TownLayout.House, lateral: Float, y: Float, out: Float, thickness: Float, height: Float, width: Float
    ) {
        if (h.facingX != 0) {
            boxAt(h.cx + h.facingX * (h.hw + out), y, h.cz + lateral, thickness, height, width)
        } else {
            boxAt(h.cx + lateral, y, h.cz + h.facingZ * (h.hd + out), width, height, thickness)
        }
    }

    private fun lateralHalf(h: TownLayout.House): Float = if (h.facingX != 0) h.hd else h.hw

    fun build(): Model {
        val b = ModelBuilder()
        b.begin()
        b.node()
        val houses = TownLayout.houses

        // Walls, one part per colour so the phone draws few batches. Towers get a top block.
        for (c in 0 until TownLayout.WALL_COLORS) {
            val part = b.colorPart("wall$c", TownPalette.WALLS[c])
            for (h in houses) {
                if (h.wallColor != c) continue
                part.boxAt(h.cx, h.height / 2f, h.cz, h.hw * 2f, h.height, h.hd * 2f)
                if (h.type == TownLayout.TYPE_TOWER) {
                    part.boxAt(h.cx, h.height + 1.5f, h.cz, h.hw * 1.1f, 2.2f, h.hd * 1.1f)
                }
            }
        }

        // Roofs: pitched on bungalows and houses, flat on shops and towers.
        for (c in 0 until TownLayout.ROOF_COLORS) {
            val part = b.colorPart("roof$c", TownPalette.ROOFS[c])
            for (h in houses) {
                if (h.roofColor != c) continue
                when (h.type) {
                    TownLayout.TYPE_BUNGALOW, TownLayout.TYPE_HOUSE -> {
                        part.boxAt(h.cx, h.height + 0.1f, h.cz, h.hw * 2f, 0.2f, h.hd * 2f)
                        val rise = if (h.type == TownLayout.TYPE_HOUSE) 2.6f else 2.0f
                        part.gableRoof(h.cx, h.height + 0.1f, h.cz, h.hw, h.hd, rise, h.facingX != 0, 0.5f)
                    }
                    else -> {
                        part.boxAt(h.cx, h.height + 0.2f, h.cz, h.hw * 2f + 0.6f, 0.4f, h.hd * 2f + 0.6f)
                    }
                }
            }
        }

        // Shop awnings and signs, one part per accent colour (the sign uses a different colour).
        for (a in 0 until TownLayout.ACCENT_COLORS) {
            val awnings = b.colorPart("awning$a", TownPalette.ACCENTS[a])
            for (h in houses) {
                if (h.type == TownLayout.TYPE_SHOP && h.accent == a) {
                    awnings.facadeBoxAt(h, 0f, 3.1f, 1.2f, 2.4f, 0.18f, lateralHalf(h) * 1.8f)
                }
            }
        }
        for (a in 0 until TownLayout.ACCENT_COLORS) {
            val signs = b.colorPart("sign$a", TownPalette.ACCENTS[a])
            for (h in houses) {
                if (h.type == TownLayout.TYPE_SHOP && (h.accent + 2) % TownLayout.ACCENT_COLORS == a) {
                    signs.facadeBoxAt(h, 0f, 3.95f, 0.1f, 0.3f, 0.6f, lateralHalf(h) * 1.5f)
                }
            }
        }

        // White trim: balconies on houses and towers.
        val trim = b.colorPart("trim", Color(0.96f, 0.96f, 0.96f, 1f))
        for (h in houses) {
            val half = lateralHalf(h)
            if (h.type == TownLayout.TYPE_HOUSE) {
                trim.facadeBoxAt(h, 0f, 3.1f, 0.55f, 1.1f, 0.15f, half * 1.7f)
            } else if (h.type == TownLayout.TYPE_TOWER) {
                val floors = maxOf(2, (h.height / 3f).toInt())
                for (f in 1 until floors) {
                    trim.facadeBoxAt(h, 0f, 3f * f + 0.1f, 0.55f, 1.1f, 0.15f, half * 1.7f)
                }
            }
        }

        // Water tanks on top of the towers.
        val tanks = b.colorPart("tanks", Color(0.14f, 0.18f, 0.28f, 1f))
        for (h in houses) {
            if (h.type == TownLayout.TYPE_TOWER) {
                tanks.cylinderAt(h.cx + h.hw * 0.25f, h.height + 3.6f, h.cz, 2.2f, 2.0f, 12)
            }
        }

        // Doors: one in the middle of each front wall (wider on shops).
        val doors = b.colorPart("doors", Color(0.30f, 0.20f, 0.14f, 1f))
        for (h in houses) {
            val width = if (h.type == TownLayout.TYPE_SHOP) 1.8f else 1.5f
            doors.facadeBoxAt(h, 0f, 1.1f, 0.02f, 0.1f, 2.2f, width)
        }

        // Windows on the front wall.
        val windows = b.colorPart("windows", Color(0.30f, 0.46f, 0.64f, 1f))
        for (h in houses) {
            val half = lateralHalf(h)
            if (h.type == TownLayout.TYPE_SHOP) {
                // Two big shop windows, one each side of the door.
                for (lat in floatArrayOf(-0.6f * half, 0.6f * half)) {
                    windows.facadeBoxAt(h, lat, 1.9f, 0.02f, 0.1f, 2.0f, half * 0.5f)
                }
                continue
            }
            val floors = when (h.type) {
                TownLayout.TYPE_BUNGALOW -> 1
                TownLayout.TYPE_HOUSE -> 2
                else -> maxOf(2, (h.height / 3f).toInt())
            }
            for (floor in 0 until floors) {
                val y = 1.8f + floor * 3f
                val offsets = if (floor == 0) {
                    floatArrayOf(-0.55f * half, 0.55f * half)
                } else {
                    floatArrayOf(-0.55f * half, 0f, 0.55f * half)
                }
                for (lat in offsets) {
                    windows.facadeBoxAt(h, lat, y, 0.02f, 0.1f, 1.3f, 1.4f)
                }
            }
        }

        // Trees: round trunks, then crowns in several colours.
        val trunks = b.colorPart("trunks", Color(0.40f, 0.27f, 0.16f, 1f))
        for (t in TownLayout.trees) {
            if (t.kind == 0) trunks.cylinderAt(t.x, 1.1f * t.scale, t.z, 0.5f * t.scale, 2.2f * t.scale, 8)
        }
        for (c in 0 until TownLayout.LEAF_COLORS) {
            val leaves = b.colorPart("leaves$c", TownPalette.LEAVES[c])
            for (t in TownLayout.trees) {
                if (t.kind == 0 && t.leafColor == c) {
                    leaves.coneAt(t.x, 2.0f * t.scale + 2.25f * t.scale, t.z, 3.4f * t.scale, 4.5f * t.scale, 9)
                }
            }
        }

        // Palms: tall thin trunk and a ring of drooping leaves.
        val palmTrunks = b.colorPart("palmTrunks", Color(0.62f, 0.50f, 0.34f, 1f))
        for (t in TownLayout.trees) {
            if (t.kind == 1) palmTrunks.cylinderAt(t.x, 2.75f * t.scale, t.z, 0.38f * t.scale, 5.5f * t.scale, 8)
        }
        val fronds = b.colorPart("fronds", Color(0.15f, 0.55f, 0.20f, 1f))
        for (t in TownLayout.trees) {
            if (t.kind == 1) {
                for (i in 0 until 7) {
                    fronds.frondAt(t.x, 5.5f * t.scale, t.z, i * (360f / 7f), 3.2f * t.scale, 0.6f * t.scale)
                }
            }
        }

        return b.end()
    }
}
