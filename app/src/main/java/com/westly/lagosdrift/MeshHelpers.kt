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

/** Small helpers shared by the code that builds the ground, the town, the car and the people. */

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

/**
 * A triangle that can be seen from both sides. It is added twice with opposite winding, so
 * whichever copy faces the viewer is drawn with a normal that points toward the viewer.
 * Points are given in world space.
 */
internal fun MeshPartBuilder.faceTriangle(a: Vector3, b: Vector3, c: Vector3) {
    val n = Vector3(b).sub(a).crs(Vector3(c).sub(a))
    if (n.len2() < 0.00000001f) return
    n.nor()
    setVertexTransform(null)

    val front1 = MeshPartBuilder.VertexInfo().setPos(a.x, a.y, a.z).setNor(n.x, n.y, n.z)
    val front2 = MeshPartBuilder.VertexInfo().setPos(b.x, b.y, b.z).setNor(n.x, n.y, n.z)
    val front3 = MeshPartBuilder.VertexInfo().setPos(c.x, c.y, c.z).setNor(n.x, n.y, n.z)
    triangle(front1, front2, front3)

    val back1 = MeshPartBuilder.VertexInfo().setPos(a.x, a.y, a.z).setNor(-n.x, -n.y, -n.z)
    val back2 = MeshPartBuilder.VertexInfo().setPos(b.x, b.y, b.z).setNor(-n.x, -n.y, -n.z)
    val back3 = MeshPartBuilder.VertexInfo().setPos(c.x, c.y, c.z).setNor(-n.x, -n.y, -n.z)
    triangle(back1, back3, back2)
}

/** A flat four-corner shape. Corners go around the edge in order. */
internal fun MeshPartBuilder.faceQuad(a: Vector3, b: Vector3, c: Vector3, d: Vector3) {
    faceTriangle(a, b, c)
    faceTriangle(a, c, d)
}

/**
 * A pitched roof with two sloping sides and two triangle ends.
 * ridgeAlongZ = true means the top ridge runs north-south.
 */
internal fun MeshPartBuilder.gableRoof(
    cx: Float, baseY: Float, cz: Float, hw: Float, hd: Float, rise: Float, ridgeAlongZ: Boolean, overhang: Float
) {
    val x0 = cx - hw - overhang
    val x1 = cx + hw + overhang
    val z0 = cz - hd - overhang
    val z1 = cz + hd + overhang
    val top = baseY + rise
    if (ridgeAlongZ) {
        faceQuad(Vector3(x0, baseY, z0), Vector3(x0, baseY, z1), Vector3(cx, top, z1), Vector3(cx, top, z0))
        faceQuad(Vector3(x1, baseY, z0), Vector3(x1, baseY, z1), Vector3(cx, top, z1), Vector3(cx, top, z0))
        faceTriangle(Vector3(x0, baseY, z0), Vector3(x1, baseY, z0), Vector3(cx, top, z0))
        faceTriangle(Vector3(x0, baseY, z1), Vector3(x1, baseY, z1), Vector3(cx, top, z1))
    } else {
        faceQuad(Vector3(x0, baseY, z0), Vector3(x1, baseY, z0), Vector3(x1, top, cz), Vector3(x0, top, cz))
        faceQuad(Vector3(x0, baseY, z1), Vector3(x1, baseY, z1), Vector3(x1, top, cz), Vector3(x0, top, cz))
        faceTriangle(Vector3(x0, baseY, z0), Vector3(x0, baseY, z1), Vector3(x0, top, cz))
        faceTriangle(Vector3(x1, baseY, z0), Vector3(x1, baseY, z1), Vector3(x1, top, cz))
    }
}

/** One palm leaf: a long thin slab that points outward and droops. angle is in degrees. */
internal fun MeshPartBuilder.frondAt(x: Float, y: Float, z: Float, angle: Float, length: Float, width: Float) {
    val m = Matrix4().setToTranslation(x, y, z)
    m.rotate(Vector3.Y, angle)
    m.rotate(Vector3.X, -22f)
    m.translate(0f, 0f, -length / 2f)
    setVertexTransform(m)
    box(width, 0.08f, length)
}
