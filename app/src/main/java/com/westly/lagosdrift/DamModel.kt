package com.westly.lagosdrift

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Glen Canyon Dam, a landmark in Neribo Desert: two rock walls with the dam road between them.
 * Model by amogusstrikesback2 (CC BY 4.0), see assets/dam/CREDITS.md. It is baked in the game's own
 * piece format: dam.bin holds the pieces (texture number, vertex count, index count, then x y z nx ny nz u v
 * per vertex and the 16-bit triangle indexes) and tex0.jpg, tex1.jpg ... are the pictures the pieces wear.
 * The model is centred on x = 0, z = 0 and its valley floor is at y = 0; it is moved to [X], [Z] in the desert.
 */
class DamModel {
    private val model: Model
    private val instance: ModelInstance
    private val centre = Vector3(X, 40f, Z)

    init {
        val bytes = Gdx.files.internal("dam/dam.bin").readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val partCount = buffer.int

        val textures = HashMap<Int, Texture>()
        val meshes = ArrayList<Mesh>()
        val b = ModelBuilder()
        b.begin()
        for (i in 0 until partCount) {
            val textureIndex = buffer.int
            val vertexCount = buffer.int
            val indexCount = buffer.int

            val vertices = FloatArray(vertexCount * FLOATS_PER_VERTEX)
            buffer.asFloatBuffer().get(vertices)
            buffer.position(buffer.position() + vertices.size * 4)
            val indices = ShortArray(indexCount)
            buffer.asShortBuffer().get(indices)
            buffer.position(buffer.position() + indexCount * 2)

            val mesh = Mesh(
                true, vertexCount, indexCount,
                VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0)
            )
            mesh.setVertices(vertices)
            mesh.setIndices(indices)
            meshes.add(mesh)

            val texture = textures.getOrPut(textureIndex) {
                Texture(Gdx.files.internal("dam/tex$textureIndex.jpg"), true).also {
                    it.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
                    it.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
                }
            }
            val material = Material(TextureAttribute.createDiffuse(texture), IntAttribute.createCullFace(GL20.GL_NONE))
            b.node().id = "dam$i"
            b.part("dam$i", mesh, GL20.GL_TRIANGLES, material)
        }
        model = b.end()
        for (m in meshes) model.manageDisposable(m)
        for (t in textures.values) model.manageDisposable(t)
        instance = ModelInstance(model)
        instance.transform.setToTranslation(X, 0f, Z)
    }

    /** Draws the dam when any of it can be on the screen. */
    fun render(batch: ModelBatch, environment: Environment, camera: PerspectiveCamera) {
        if (camera.frustum.sphereInFrustum(centre, RADIUS)) batch.render(instance, environment)
    }

    /** True when the point is close enough to the dam for its credit to be shown. */
    fun isNear(x: Float, z: Float): Boolean {
        val dx = x - X
        val dz = z - Z
        return dx * dx + dz * dz < CREDIT_RANGE * CREDIT_RANGE
    }

    fun dispose() {
        model.dispose()
    }

    companion object {
        /** Where the middle of the dam is in Neribo Desert: north of the dirt track, clear of the old highway. */
        const val X = 330f
        const val Z = -595f
        const val RADIUS = 380f
        const val CREDIT_RANGE = 700f
        const val NAME = "GLEN CANYON DAM"

        /** Text that must be shown to the player somewhere on screen (the licence asks for it). */
        const val CREDIT = "Dam: Glen Canyon Dam by amogusstrikesback2 (CC BY 4.0)"

        private const val FLOATS_PER_VERTEX = 8
    }
}
