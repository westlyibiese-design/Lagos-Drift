package com.westly.lagosdrift

import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder

/**
 * The parts of the ground that never change: a huge plain under the whole world, so there is never a
 * gap to the sky, and (in Lagos only) the open lagoon past the east edge of the map. Everything else on the
 * ground (roads, pavements, potholes, grass tiles, the beach) is built per chunk in
 * TownModelFactory.
 */
object GroundModelFactory {
    fun build(world: Int = Districts.WORLD_LAGOS): Model {
        val b = ModelBuilder()
        b.begin()
        b.node()

        val far = when (world) {
            Districts.WORLD_DESERT -> TownPalette.DESERT_FAR_GROUND
            Districts.WORLD_CITY -> TownPalette.CITY_GROUND
            else -> TownPalette.FAR_GROUND
        }
        b.colorPart("farGround", far).boxAt(0f, -0.3f, 0f, 8000f, 0.2f, 8000f)

        if (world == Districts.WORLD_LAGOS) {
            val waterStart = TownLayout.MAX_X
            val waterWidth = 4000f
            b.colorPart("lagoon", TownPalette.WATER)
                .boxAt(waterStart + waterWidth / 2f, -0.12f, 0f, waterWidth, 0.2f, 8000f)
        }

        return b.end()
    }
}
