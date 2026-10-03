package com.westly.lagosdrift

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder

/**
 * Low-poly people built from boxes. Each model has five moving pieces (body, two legs,
 * two arms) so they can swing as they walk. They face -Z.
 */
object PedestrianModelFactory {
    private val shirts = arrayOf(
        Color(0.90f, 0.20f, 0.20f, 1f),
        Color(0.20f, 0.45f, 0.90f, 1f),
        Color(0.98f, 0.85f, 0.20f, 1f),
        Color(0.25f, 0.70f, 0.35f, 1f),
        Color(0.97f, 0.97f, 0.95f, 1f),
        Color(0.95f, 0.55f, 0.15f, 1f),
        Color(0.90f, 0.45f, 0.65f, 1f),
        Color(0.55f, 0.30f, 0.75f, 1f)
    )
    private val pants = arrayOf(
        Color(0.20f, 0.30f, 0.50f, 1f),
        Color(0.10f, 0.10f, 0.12f, 1f),
        Color(0.55f, 0.48f, 0.35f, 1f),
        Color(0.30f, 0.30f, 0.34f, 1f)
    )
    private val skins = arrayOf(
        Color(0.36f, 0.24f, 0.17f, 1f),
        Color(0.45f, 0.30f, 0.20f, 1f),
        Color(0.28f, 0.18f, 0.13f, 1f),
        Color(0.55f, 0.38f, 0.27f, 1f)
    )
    private val hair = Color(0.06f, 0.05f, 0.05f, 1f)

    /** Eight different outfits. */
    fun buildAll(): List<Model> = List(8) { build(shirts[it], pants[it % pants.size], skins[(it * 3) % skins.size]) }

    private fun build(shirt: Color, trousers: Color, skin: Color): Model {
        val b = ModelBuilder()
        b.begin()

        val body = b.node()
        body.id = "body"
        b.colorPart("torso", shirt).boxAt(0f, 1.15f, 0f, 0.46f, 0.6f, 0.26f)
        b.colorPart("head", skin).boxAt(0f, 1.62f, 0f, 0.24f, 0.26f, 0.24f)
        b.colorPart("hair", hair).boxAt(0f, 1.77f, 0.01f, 0.26f, 0.1f, 0.26f)

        // Legs and arms hang from a pivot, so the part is drawn below the node origin.
        val legL = b.node()
        legL.id = "legL"
        legL.translation.set(0.11f, 0.85f, 0f)
        b.colorPart("legL", trousers).boxAt(0f, -0.42f, 0f, 0.17f, 0.85f, 0.18f)

        val legR = b.node()
        legR.id = "legR"
        legR.translation.set(-0.11f, 0.85f, 0f)
        b.colorPart("legR", trousers).boxAt(0f, -0.42f, 0f, 0.17f, 0.85f, 0.18f)

        val armL = b.node()
        armL.id = "armL"
        armL.translation.set(0.29f, 1.42f, 0f)
        b.colorPart("armL", skin).boxAt(0f, -0.3f, 0f, 0.11f, 0.6f, 0.13f)

        val armR = b.node()
        armR.id = "armR"
        armR.translation.set(-0.29f, 1.42f, 0f)
        b.colorPart("armR", skin).boxAt(0f, -0.3f, 0f, 0.11f, 0.6f, 0.13f)

        return b.end()
    }
}
