package com.westly.lagosdrift

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3

/**
 * Builds a simple low-poly yellow-and-black "danfo" style car from boxes and cylinders,
 * so Phase 1 needs no model files. The nose points along -Z. Replace with a real model later.
 */
object CarModelFactory {
    private val attributes = (Usage.Position or Usage.Normal).toLong()

    fun build(): Model {
        val builder = ModelBuilder()
        builder.begin()
        builder.node()

        val yellow = Color(0.98f, 0.80f, 0.10f, 1f)
        val black = Color(0.08f, 0.08f, 0.08f, 1f)
        val glass = Color(0.15f, 0.20f, 0.26f, 1f)
        val white = Color(0.97f, 0.97f, 0.90f, 1f)
        val red = Color(0.80f, 0.08f, 0.08f, 1f)

        // Body, black side stripe and cabin.
        box(builder, "body", yellow, 0f, 0.65f, 0f, 1.90f, 0.60f, 4.20f)
        box(builder, "stripe", black, 0f, 0.65f, 0f, 1.92f, 0.12f, 4.22f)
        box(builder, "cabin", glass, 0f, 1.225f, 0.20f, 1.60f, 0.55f, 2.00f)

        // Headlights (front, -Z) and taillights (rear, +Z).
        box(builder, "headL", white, -0.60f, 0.75f, -2.11f, 0.40f, 0.20f, 0.05f)
        box(builder, "headR", white, 0.60f, 0.75f, -2.11f, 0.40f, 0.20f, 0.05f)
        box(builder, "tailL", red, -0.60f, 0.75f, 2.11f, 0.40f, 0.20f, 0.05f)
        box(builder, "tailR", red, 0.60f, 0.75f, 2.11f, 0.40f, 0.20f, 0.05f)

        // Four wheels.
        wheel(builder, "wheelFL", black, -1.0f, 0.4f, -1.3f)
        wheel(builder, "wheelFR", black, 1.0f, 0.4f, -1.3f)
        wheel(builder, "wheelRL", black, -1.0f, 0.4f, 1.3f)
        wheel(builder, "wheelRR", black, 1.0f, 0.4f, 1.3f)

        return builder.end()
    }

    private fun box(
        builder: ModelBuilder, id: String, color: Color,
        x: Float, y: Float, z: Float, width: Float, height: Float, depth: Float
    ) {
        val part = builder.part(id, GL20.GL_TRIANGLES, attributes, Material(ColorAttribute.createDiffuse(color)))
        part.setVertexTransform(Matrix4().setToTranslation(x, y, z))
        part.box(width, height, depth)
    }

    private fun wheel(builder: ModelBuilder, id: String, color: Color, x: Float, y: Float, z: Float) {
        val part = builder.part(id, GL20.GL_TRIANGLES, attributes, Material(ColorAttribute.createDiffuse(color)))
        // A cylinder stands along Y by default; turn it onto its side so it rolls forward.
        part.setVertexTransform(Matrix4().setToTranslation(x, y, z).rotate(Vector3.Z, 90f))
        part.cylinder(0.8f, 0.35f, 0.8f, 16)
    }
}
