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

    /**
     * Loads a model baked in several moving pieces (for example a body and two legs). The file
     * starts with the piece count; each piece then has its pivot point (x y z), vertex count,
     * index count, its vertices (positions relative to the pivot) and its triangle indexes.
     * Piece number i becomes a node called ids[i], placed at its pivot, so it can be turned
     * later with instance.getNode(id).
     */
    fun loadParts(binPath: String, texturePath: String, ids: List<String>, doubleSided: Boolean): Model {
        val bytes = Gdx.files.internal(binPath).readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val partCount = buffer.int

        val texture = Texture(Gdx.files.internal(texturePath), true)
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
        val material = if (doubleSided) {
            Material(TextureAttribute.createDiffuse(texture), IntAttribute.createCullFace(GL20.GL_NONE))
        } else {
            Material(TextureAttribute.createDiffuse(texture))
        }

        val b = ModelBuilder()
        b.begin()
        val meshes = ArrayList<Mesh>()
        for (i in 0 until partCount) {
            val px = buffer.float
            val py = buffer.float
            val pz = buffer.float
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

            val node = b.node()
            node.id = ids[i]
            node.translation.set(px, py, pz)
            b.part(ids[i], mesh, GL20.GL_TRIANGLES, material)
        }
        val model = b.end()
        for (m in meshes) model.manageDisposable(m)
        model.manageDisposable(texture)
        return model
    }

    private const val FLOATS_PER_VERTEX = 8
}
