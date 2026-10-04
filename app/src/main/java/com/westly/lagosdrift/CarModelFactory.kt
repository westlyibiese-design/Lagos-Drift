package com.westly.lagosdrift

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3

/** The five colours from the 6 GRIND design sheet. */
object CarPalette {
    val BLACK = Color(0.08f, 0.08f, 0.09f, 1f)
    val GREY = Color(0.27f, 0.28f, 0.31f, 1f)
    val SILVER = Color(0.72f, 0.74f, 0.77f, 1f)
    val RED = Color(0.70f, 0.08f, 0.10f, 1f)
    val BLUE = Color(0.12f, 0.25f, 0.62f, 1f)
}

/**
 * A low-poly 6 GRIND sedan built from boxes and cylinders, so no model files are needed.
 * The nose points along -Z. Pass another CarPalette colour to change the paint.
 */
object CarModelFactory {
    private val glass = Color(0.10f, 0.14f, 0.18f, 1f)
    private val trim = Color(0.05f, 0.05f, 0.06f, 1f)
    private val lamp = Color(0.96f, 0.96f, 0.90f, 1f)
    private val tail = Color(0.80f, 0.07f, 0.07f, 1f)
    private val tyre = Color(0.07f, 0.07f, 0.08f, 1f)
    private val rim = Color(0.58f, 0.60f, 0.64f, 1f)
    private val accent = Color(0.85f, 0.10f, 0.10f, 1f)

    /** Where the driver's door hinges: left side, front edge. The door node turns about here. */
    const val DOOR_HINGE_X = -0.93f
    const val DOOR_HINGE_Z = -0.45f
    const val DOOR_NODE = "door"

    /** With [withDoor] the driver's door is its own piece that can swing open (the player's car). */
    fun build(bodyColor: Color = CarPalette.GREY, withDoor: Boolean = false): Model {
        val b = ModelBuilder()
        b.begin()
        b.node()

        // Paint: lower body, roof, hood panel, mirrors, pillars, boot spoiler.
        val body = b.colorPart("body", bodyColor)
        body.boxAt(0f, 0.625f, 0f, 1.85f, 0.55f, 4.8f)
        body.boxAt(0f, 1.45f, 0.3f, 1.52f, 0.06f, 1.7f)
        body.boxAt(0f, 0.93f, -1.2f, 1.5f, 0.06f, 1.6f)
        body.boxAt(-0.98f, 1.0f, -0.55f, 0.12f, 0.1f, 0.22f)
        body.boxAt(0.98f, 1.0f, -0.55f, 0.12f, 0.1f, 0.22f)
        body.boxAt(-0.76f, 1.17f, 0.45f, 0.04f, 0.5f, 0.12f)
        body.boxAt(0.76f, 1.17f, 0.45f, 0.04f, 0.5f, 0.12f)
        body.boxAt(0f, 0.96f, 2.25f, 1.5f, 0.05f, 0.28f)

        // Glass: two leaning blocks make the sloped windscreen and rear window.
        val windows = b.colorPart("glass", glass)
        windows.slantedBoxAt(0f, 1.16f, -0.1f, 1.5f, 0.52f, 1.5f, 1.1538f)
        windows.slantedBoxAt(0f, 1.16f, 0.7f, 1.5f, 0.52f, 1.6f, -1.346f)

        // Dark trim: grille, front splitter, rear diffuser.
        val dark = b.colorPart("trim", trim)
        dark.boxAt(0f, 0.62f, -2.41f, 1.1f, 0.26f, 0.06f)
        dark.boxAt(0f, 0.40f, -2.35f, 1.75f, 0.08f, 0.18f)
        dark.boxAt(0f, 0.40f, 2.38f, 1.5f, 0.1f, 0.15f)
        // A dark doorway behind the driver's door, seen when it swings open.
        if (withDoor) dark.boxAt(-0.93f, 0.66f, 0.05f, 0.02f, 0.5f, 0.98f)

        // Headlights.
        val heads = b.colorPart("headlights", lamp)
        heads.boxAt(-0.65f, 0.80f, -2.41f, 0.5f, 0.09f, 0.06f)
        heads.boxAt(0.65f, 0.80f, -2.41f, 0.5f, 0.09f, 0.06f)

        // Tail light strip across the back.
        val tails = b.colorPart("taillights", tail)
        tails.boxAt(0f, 0.80f, 2.41f, 1.4f, 0.08f, 0.06f)
        tails.boxAt(-0.7f, 0.78f, 2.41f, 0.4f, 0.16f, 0.06f)
        tails.boxAt(0.7f, 0.78f, 2.41f, 0.4f, 0.16f, 0.06f)

        // Wheels: tyres, silver rims, red centre caps.
        val wheelX = 0.85f
        val wheelY = 0.36f
        val wheelZ = 1.5f
        val tyres = b.colorPart("tyres", tyre)
        for (sx in floatArrayOf(-1f, 1f)) {
            for (sz in floatArrayOf(-1f, 1f)) {
                tyres.cylinderAt(sx * wheelX, wheelY, sz * wheelZ, 0.72f, 0.26f, 20, Vector3.Z)
            }
        }
        val rims = b.colorPart("rims", rim)
        for (sx in floatArrayOf(-1f, 1f)) {
            for (sz in floatArrayOf(-1f, 1f)) {
                rims.cylinderAt(sx * wheelX, wheelY, sz * wheelZ, 0.46f, 0.28f, 16, Vector3.Z)
            }
        }
        // Twin exhaust tips share the rim colour.
        rims.cylinderAt(-0.55f, 0.32f, 2.45f, 0.13f, 0.25f, 10, Vector3.X)
        rims.cylinderAt(0.55f, 0.32f, 2.45f, 0.13f, 0.25f, 10, Vector3.X)
        val caps = b.colorPart("caps", accent)
        for (sx in floatArrayOf(-1f, 1f)) {
            for (sz in floatArrayOf(-1f, 1f)) {
                caps.cylinderAt(sx * wheelX, wheelY, sz * wheelZ, 0.14f, 0.32f, 8, Vector3.Z)
            }
        }

        if (withDoor) {
            // The door panel and handle, positioned relative to the hinge.
            val node = b.node()
            node.id = DOOR_NODE
            node.translation.set(DOOR_HINGE_X, 0f, DOOR_HINGE_Z)
            val panel = b.colorPart("doorPaint", bodyColor)
            panel.boxAt(-0.02f, 0.66f, 0.5f, 0.05f, 0.52f, 1.0f)
            val handle = b.colorPart("doorHandle", rim)
            handle.boxAt(-0.06f, 0.80f, 0.85f, 0.04f, 0.04f, 0.18f)
        }

        return b.end()
    }
}
