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
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Loads a vehicle (or a vehicle's interior) baked into one .car file, with its textures next to it.
 *
 * The file has: the number of materials, and for each of them r g b a, flags (1 = see-through, 4 = both
 * sides are drawn, 8 = a small palette picture: no mipmaps, no wrapping), the length of its texture file name and the name (empty = no texture); then the number
 * of nodes, and for each node: the length of its id and the id, its pivot point (x y z), and the number of
 * parts; each part has its material number, vertex count, index count, its vertices (x y z nx ny nz u v,
 * positions relative to the node's pivot) and its 16-bit triangle indexes.
 *
 * Node "body" never moves, "wheel_fl / fr / rl / rr" steer and roll, and "door_.." swing open around their
 * pivot (the hinge). Like the other baked models the nose points along -Z and the wheels touch y = 0.
 */
object BakedCar {
    fun load(binPath: String, textureDir: String): Model {
        val bytes = Gdx.files.internal(binPath).readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val materialCount = buffer.int
        val materials = ArrayList<Material>()
        val textures = ArrayList<Texture>()
        for (i in 0 until materialCount) {
            val r = buffer.float
            val g = buffer.float
            val b = buffer.float
            val a = buffer.float
            val flags = buffer.int
            val nameLength = buffer.int
            val nameBytes = ByteArray(nameLength)
            buffer.get(nameBytes)
            val file = String(nameBytes, Charsets.UTF_8)

            val material = Material()
            material.set(ColorAttribute.createDiffuse(r, g, b, 1f))
            if (file.isNotEmpty()) {
                val palette = flags and FLAG_PALETTE != 0
                val texture = Texture(Gdx.files.internal("$textureDir/$file"), !palette)
                if (palette) {
                    texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                    texture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
                } else {
                    texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
                    texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
                }
                textures.add(texture)
                material.set(TextureAttribute.createDiffuse(texture))
            }
            if (flags and FLAG_DOUBLE_SIDED != 0) material.set(IntAttribute.createCullFace(GL20.GL_NONE))
            if (flags and FLAG_BLEND != 0) {
                material.set(BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, a.coerceIn(0.05f, 1f)))
                // See-through glass must not hide what is behind it.
                material.set(DepthTestAttribute(GL20.GL_LEQUAL, false))
            }
            materials.add(material)
        }

        val nodeCount = buffer.int
        val builder = ModelBuilder()
        builder.begin()
        val meshes = ArrayList<Mesh>()
        for (n in 0 until nodeCount) {
            val idLength = buffer.int
            val idBytes = ByteArray(idLength)
            buffer.get(idBytes)
            val id = String(idBytes, Charsets.UTF_8)
            val px = buffer.float
            val py = buffer.float
            val pz = buffer.float
            val partCount = buffer.int

            val node = builder.node()
            node.id = id
            node.translation.set(px, py, pz)
            for (p in 0 until partCount) {
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
                builder.part("$id.$p", mesh, GL20.GL_TRIANGLES, materials[materialIndex])
            }
        }
        val model = builder.end()
        for (m in meshes) model.manageDisposable(m)
        for (t in textures) model.manageDisposable(t)
        return model
    }

    private const val FLAG_BLEND = 1
    private const val FLAG_DOUBLE_SIDED = 4
    private const val FLAG_PALETTE = 8
    private const val FLOATS_PER_VERTEX = 8
}
