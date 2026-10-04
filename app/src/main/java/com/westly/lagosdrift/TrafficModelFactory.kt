package com.westly.lagosdrift

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3

/**
 * The cars that drive about on their own. Ikoyi cars are clean, newer colours. Ikorodu cars are
 * older, faded colours, and the yellow-and-black danfo bus only runs there. Everything is built
 * from boxes and cylinders, so no model files are needed. The nose points along -Z.
 */
object TrafficModelFactory {
    private val ikoyiColors = arrayOf(
        CarPalette.BLACK, CarPalette.GREY, CarPalette.SILVER, CarPalette.BLUE,
        Color(0.93f, 0.93f, 0.92f, 1f),   // white
        Color(0.45f, 0.06f, 0.08f, 1f)    // deep red
    )
    private val ikoroduColors = arrayOf(
        Color(0.32f, 0.46f, 0.34f, 1f),   // faded green
        Color(0.62f, 0.60f, 0.56f, 1f),   // dusty silver
        Color(0.75f, 0.68f, 0.50f, 1f),   // beige
        Color(0.55f, 0.22f, 0.16f, 1f),   // rusty red
        Color(0.78f, 0.78f, 0.80f, 1f)    // old white
    )

    fun buildIkoyiCars(): List<Model> = ikoyiColors.map { CarModelFactory.build(it) }

    fun buildIkoroduCars(): List<Model> = ikoroduColors.map { CarModelFactory.build(it) }

    private val danfoYellow = Color(0.96f, 0.78f, 0.10f, 1f)
    private val danfoBlack = Color(0.08f, 0.08f, 0.09f, 1f)
    private val glass = Color(0.10f, 0.14f, 0.18f, 1f)
    private val lamp = Color(0.96f, 0.96f, 0.90f, 1f)
    private val tail = Color(0.80f, 0.07f, 0.07f, 1f)
    private val tyre = Color(0.07f, 0.07f, 0.08f, 1f)
    private val rim = Color(0.58f, 0.60f, 0.64f, 1f)

    const val DANFO_DOOR_NODE = "danfoDoor"
    private const val DOOR_HINGE_X = -1.06f
    private const val DOOR_HINGE_Z = -1.76f

    /**
     * The danfo's driver's door, drawn on top of the baked bus model. It is a yellow panel with a
     * black stripe and a dark window, hinged at its front edge, plus a dark doorway on the bus
     * that shows when it swings open. The bus model itself is one solid piece, so this stands in.
     */
    fun buildDanfoDoor(): Model {
        val b = ModelBuilder()
        b.begin()
        b.node()
        // The doorway: a dark box lying just outside the bus side (the door covers it when shut).
        val doorway = b.colorPart("danfoDoorway", Color(0.05f, 0.05f, 0.06f, 1f))
        doorway.boxAt(-1.05f, 1.2f, -1.28f, 0.02f, 1.3f, 0.96f)

        val node = b.node()
        node.id = DANFO_DOOR_NODE
        node.translation.set(DOOR_HINGE_X, 0f, DOOR_HINGE_Z)
        val paint = b.colorPart("danfoDoorPaint", danfoYellow)
        paint.boxAt(-0.02f, 0.90f, 0.48f, 0.04f, 0.70f, 0.96f)
        val glassPart = b.colorPart("danfoDoorGlass", glass)
        glassPart.boxAt(-0.02f, 1.55f, 0.48f, 0.04f, 0.60f, 0.96f)
        val stripePart = b.colorPart("danfoDoorStripe", danfoBlack)
        stripePart.boxAt(-0.03f, 0.95f, 0.48f, 0.04f, 0.20f, 0.96f)
        val handle = b.colorPart("danfoDoorHandle", rim)
        handle.boxAt(-0.05f, 1.15f, 0.82f, 0.04f, 0.05f, 0.16f)
        return b.end()
    }

    /**
     * The danfo: your own art (assets/traffic/danfo.bin and danfo.jpg). If those files are missing
     * or cannot be read, the simple box-built danfo below is used instead.
     */
    fun buildDanfo(): Model =
        try {
            BakedModel.load("traffic/danfo.bin", "traffic/danfo.jpg")
        } catch (e: Exception) {
            buildSimpleDanfo()
        }

    /** A simple danfo built from boxes: a yellow minibus with a black stripe along the sides. */
    private fun buildSimpleDanfo(): Model {
        val b = ModelBuilder()
        b.begin()
        b.node()

        val body = b.colorPart("danfoBody", danfoYellow)
        body.boxAt(0f, 1.0f, 0f, 2.0f, 1.5f, 4.6f)
        body.boxAt(0f, 1.82f, 0.2f, 1.9f, 0.14f, 4.2f)
        body.boxAt(0f, 0.55f, -2.2f, 1.9f, 0.5f, 0.3f)

        val stripe = b.colorPart("danfoStripe", danfoBlack)
        stripe.boxAt(-1.006f, 0.95f, 0f, 0.02f, 0.28f, 4.62f)
        stripe.boxAt(1.006f, 0.95f, 0f, 0.02f, 0.28f, 4.62f)
        stripe.boxAt(0f, 0.30f, -2.3f, 2.0f, 0.18f, 0.2f)
        stripe.boxAt(0f, 0.30f, 2.3f, 2.0f, 0.18f, 0.2f)

        val windows = b.colorPart("danfoGlass", glass)
        windows.boxAt(-1.008f, 1.45f, 0.35f, 0.02f, 0.55f, 3.0f)
        windows.boxAt(1.008f, 1.45f, 0.35f, 0.02f, 0.55f, 3.0f)
        windows.boxAt(0f, 1.4f, -2.305f, 1.7f, 0.6f, 0.02f)
        windows.boxAt(0f, 1.4f, 2.305f, 1.7f, 0.5f, 0.02f)

        val heads = b.colorPart("danfoHeads", lamp)
        heads.boxAt(-0.7f, 0.75f, -2.31f, 0.4f, 0.2f, 0.04f)
        heads.boxAt(0.7f, 0.75f, -2.31f, 0.4f, 0.2f, 0.04f)
        val tails = b.colorPart("danfoTails", tail)
        tails.boxAt(-0.8f, 0.8f, 2.31f, 0.3f, 0.3f, 0.04f)
        tails.boxAt(0.8f, 0.8f, 2.31f, 0.3f, 0.3f, 0.04f)

        val tyres = b.colorPart("danfoTyres", tyre)
        val rims = b.colorPart("danfoRims", rim)
        for (sx in floatArrayOf(-1f, 1f)) {
            for (sz in floatArrayOf(-1f, 1f)) {
                tyres.cylinderAt(sx * 0.95f, 0.38f, sz * 1.5f, 0.76f, 0.28f, 18, Vector3.Z)
            }
        }
        // The rims are a separate part, so they must be added after the tyres part is finished.
        for (sx in floatArrayOf(-1f, 1f)) {
            for (sz in floatArrayOf(-1f, 1f)) {
                rims.cylinderAt(sx * 0.95f, 0.38f, sz * 1.5f, 0.4f, 0.3f, 12, Vector3.Z)
            }
        }
        return b.end()
    }
}
