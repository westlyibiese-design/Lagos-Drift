package com.westly.lagosdrift

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Matrix4

/** A big flat grass plane with one straight road and a dashed centre line. */
object GroundModelFactory {
    private val attributes = (Usage.Position or Usage.Normal).toLong()

    fun build(): Model {
        val builder = ModelBuilder()
        builder.begin()
        builder.node()

        part(builder, "grass", Color(0.30f, 0.55f, 0.25f, 1f), 0f, -0.10f, 0f).box(600f, 0.2f, 600f)
        part(builder, "road", Color(0.22f, 0.22f, 0.24f, 1f), 0f, -0.07f, 0f).box(12f, 0.2f, 600f)

        // Dashed centre line, one dash every 8 units along the road.
        val dashes = part(builder, "dashes", Color(0.95f, 0.95f, 0.90f, 1f), 0f, 0f, 0f)
        var z = -120f
        while (z <= 120f) {
            dashes.setVertexTransform(Matrix4().setToTranslation(0f, 0.04f, z))
            dashes.box(0.25f, 0.02f, 3f)
            z += 8f
        }

        return builder.end()
    }

    private fun part(
        builder: ModelBuilder, id: String, color: Color, x: Float, y: Float, z: Float
    ): com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder {
        val part = builder.part(id, GL20.GL_TRIANGLES, attributes, Material(ColorAttribute.createDiffuse(color)))
        part.setVertexTransform(Matrix4().setToTranslation(x, y, z))
        return part
    }
}
