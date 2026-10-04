package com.westly.lagosdrift

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Loads the player character: your own art (the Meshy chibi figure).
 *
 * The model was baked into two small files in app/src/main/assets/player/:
 *   player.bin  - the mesh: vertex count, index count, then x y z nx ny nz u v for every vertex,
 *                 then the triangle indexes. The feet are at y = 0 and the figure faces -Z, like the car.
 *   player.jpg  - the colour texture (1024 x 1024).
 * To swap the character later, bake a new model into the same two files; no code changes.
 */
object PlayerModelFactory {
    /** How tall the figure is on screen, in metres. (The baked model is 1.9 units tall.) */
    const val HEIGHT = 1.45f
    private const val MODEL_HEIGHT = 1.899f

    /** Scale to apply to the model instance so it is [HEIGHT] metres tall. */
    const val SCALE = HEIGHT / MODEL_HEIGHT

    fun load(): Model {
        val bytes = Gdx.files.internal("player/player.bin").readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val vertexCount = buffer.int
        val indexCount = buffer.int

        val vertices = FloatArray(vertexCount * FLOATS_PER_VERTEX)
        buffer.asFloatBuffer().get(vertices)
        buffer.position(buffer.position() + vertices.size * 4)

        val indices = ShortArray(indexCount)
        buffer.asShortBuffer().get(indices)

        val mesh = Mesh(
            true, vertexCount, indexCount,
            VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0)
        )
        mesh.setVertices(vertices)
        mesh.setIndices(indices)

        val texture = Texture(Gdx.files.internal("player/player.jpg"), true)
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)

        // Double-sided, like the original file (hair and the cap brim are thin).
        val material = Material(
            TextureAttribute.createDiffuse(texture),
            IntAttribute.createCullFace(GL20.GL_NONE)
        )

        val b = ModelBuilder()
        b.begin()
        b.node()
        b.part("player", mesh, GL20.GL_TRIANGLES, material)
        val model = b.end()
        model.manageDisposable(mesh)
        model.manageDisposable(texture)
        return model
    }

    private const val FLOATS_PER_VERTEX = 8
}
