package com.westly.lagosdrift

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A baked building: its model (several materials) and its floors, as triangles in the building's own frame. */
class BakedScene(val model: Model, val floors: FloatArray)

/**
 * Loads a building baked into assets/houses/<tag>.bin and its textures (<tag>_<n>.jpg / .png). The .bin has:
 * the number of materials, and for each of them r g b a, a 1 if it is see-through, the length of its texture
 * file name and the name (empty = no texture); then the number of pieces, and for each piece its material
 * number, vertex count, index count, its vertices (x y z nx ny nz u v) and its 16-bit triangle indexes; then
 * the number of floor triangles and each one as nine numbers. The building's floor is at y = 0 and its
 * front faces -Z, like the cars.
 */
object BakedSceneLoader {
    fun load(tag: String): BakedScene {
        val bytes = Gdx.files.internal("houses/$tag.bin").readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val materialCount = buffer.int
        val materials = ArrayList<Material>()
        val textures = ArrayList<Texture>()
        for (i in 0 until materialCount) {
            val r = buffer.float
            val g = buffer.float
            val b = buffer.float
            val a = buffer.float
            val blend = buffer.int
            val nameLength = buffer.int
            val nameBytes = ByteArray(nameLength)
            buffer.get(nameBytes)
            val file = String(nameBytes, Charsets.UTF_8)

            val material = Material()
            material.set(ColorAttribute.createDiffuse(r, g, b, 1f))
            if (file.isNotEmpty()) {
                val texture = Texture(Gdx.files.internal("houses/$file"), true)
                texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
                texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
                textures.add(texture)
                material.set(TextureAttribute.createDiffuse(texture))
            }
            material.set(IntAttribute.createCullFace(GL20.GL_NONE))
            if (blend != 0) {
                material.set(BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, a.coerceIn(0.2f, 1f)))
            }
            materials.add(material)
        }

        val partCount = buffer.int
        val builder = ModelBuilder()
        builder.begin()
        val meshes = ArrayList<Mesh>()
        for (i in 0 until partCount) {
            val materialIndex = buffer.int
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

            builder.node()
            builder.part("piece$i", mesh, GL20.GL_TRIANGLES, materials[materialIndex])
        }
        val model = builder.end()
        for (m in meshes) model.manageDisposable(m)
        for (t in textures) model.manageDisposable(t)

        val floorCount = buffer.int
        val floors = FloatArray(floorCount * 9)
        buffer.asFloatBuffer().get(floors)
        return BakedScene(model, floors)
    }

    private const val FLOATS_PER_VERTEX = 8
}
