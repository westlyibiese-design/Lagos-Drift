package com.westly.lagosdrift

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3

/** Small helpers shared by the code that builds the ground, the town and the car. */

internal val POSITION_NORMAL: Long = (Usage.Position or Usage.Normal).toLong()

/** Starts a new single-colour part. Finish adding shapes to one part before starting the next. */
internal fun ModelBuilder.colorPart(id: String, color: Color): MeshPartBuilder =
    part(id, GL20.GL_TRIANGLES, POSITION_NORMAL, Material(ColorAttribute.createDiffuse(color)))

internal fun MeshPartBuilder.boxAt(x: Float, y: Float, z: Float, width: Float, height: Float, depth: Float) {
    setVertexTransform(Matrix4().setToTranslation(x, y, z))
    box(width, height, depth)
}

/** A box leaned along Z: the top shifts by (shear * height) relative to the bottom. */
internal fun MeshPartBuilder.slantedBoxAt(
    x: Float, y: Float, z: Float, width: Float, height: Float, depth: Float, shear: Float
) {
    val m = Matrix4().setToTranslation(x, y, z)
    m.`val`[Matrix4.M21] = shear
    setVertexTransform(m)
    box(width, height, depth)
}

/** A cylinder standing upright, or turned onto its side when an axis is given (90 degrees). */
internal fun MeshPartBuilder.cylinderAt(
    x: Float, y: Float, z: Float, diameter: Float, length: Float, divisions: Int, turnAxis: Vector3? = null
) {
    val m = Matrix4().setToTranslation(x, y, z)
    if (turnAxis != null) m.rotate(turnAxis, 90f)
    setVertexTransform(m)
    cylinder(diameter, length, diameter, divisions)
}

internal fun MeshPartBuilder.coneAt(x: Float, y: Float, z: Float, width: Float, height: Float, divisions: Int) {
    setVertexTransform(Matrix4().setToTranslation(x, y, z))
    cone(width, height, width, divisions)
}
