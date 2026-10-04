package com.westly.lagosdrift

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Loads a model that was baked into two small files: a .bin with the mesh (vertex count, index
 * count, then x y z nx ny nz u v per vertex, then the 16-bit triangle indexes) and a .jpg texture.
 * The model already has its final size and faces -Z like the car, with its wheels on y = 0.
 */
object BakedModel {
    fun load(binPath: String, texturePath: String): Model {
        val bytes = Gdx.files.internal(binPath).readBytes()
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

        val texture = Texture(Gdx.files.internal(texturePath), true)
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)

        val b = ModelBuilder()
        b.begin()
        b.node()
        b.part("baked", mesh, GL20.GL_TRIANGLES, Material(TextureAttribute.createDiffuse(texture)))
        val model = b.end()
        model.manageDisposable(mesh)
        model.manageDisposable(texture)
        return model
    }

    private const val FLOATS_PER_VERTEX = 8
}
