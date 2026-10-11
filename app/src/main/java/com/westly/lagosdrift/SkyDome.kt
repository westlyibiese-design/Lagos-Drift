package com.westly.lagosdrift

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A sky: a big ball around the camera wearing a 360 degree picture (an equirectangular panorama made
 * from a Poly Haven HDRI). It follows the camera, so it never gets closer, is not lit and does not
 * touch the depth buffer, so everything else is drawn in front of it.
 * Draw it in its own ModelBatch begin/end, before the world.
 */
class SkyDome(texturePath: String) {
    private val texture = Texture(Gdx.files.internal(texturePath), true)
    private val model: Model
    private val instance: ModelInstance

    init {
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)

        val vertexCount = (STACKS + 1) * (SLICES + 1)
        val vertices = FloatArray(vertexCount * 8)
        var o = 0
        for (j in 0..STACKS) {
            val v = j.toFloat() / STACKS
            val elevation = (0.5f - v) * PI.toFloat()
            for (i in 0..SLICES) {
                val u = i.toFloat() / SLICES
                val azimuth = u * 2f * PI.toFloat()
                val dx = cos(elevation) * sin(azimuth)
                val dy = sin(elevation)
                val dz = -cos(elevation) * cos(azimuth)
                vertices[o++] = dx * RADIUS
                vertices[o++] = dy * RADIUS
                vertices[o++] = dz * RADIUS
                vertices[o++] = -dx
                vertices[o++] = -dy
                vertices[o++] = -dz
                vertices[o++] = u
                vertices[o++] = v
            }
        }
        val indices = ShortArray(STACKS * SLICES * 6)
        var k = 0
        for (j in 0 until STACKS) {
            for (i in 0 until SLICES) {
                val a = j * (SLICES + 1) + i
                val b = a + SLICES + 1
                indices[k++] = a.toShort()
                indices[k++] = b.toShort()
                indices[k++] = (a + 1).toShort()
                indices[k++] = (a + 1).toShort()
                indices[k++] = b.toShort()
                indices[k++] = (b + 1).toShort()
            }
        }
        val mesh = Mesh(
            true, vertexCount, indices.size,
            VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0)
        )
        mesh.setVertices(vertices)
        mesh.setIndices(indices)

        val material = Material(
            TextureAttribute.createDiffuse(texture),
            IntAttribute.createCullFace(GL20.GL_NONE),
            DepthTestAttribute(GL20.GL_LEQUAL, false)
        )
        val b = ModelBuilder()
        b.begin()
        b.node()
        b.part("sky", mesh, GL20.GL_TRIANGLES, material)
        model = b.end()
        model.manageDisposable(mesh)
        model.manageDisposable(texture)
        instance = ModelInstance(model)
    }

    fun render(batch: ModelBatch, camera: PerspectiveCamera) {
        instance.transform.setToTranslation(camera.position)
        batch.render(instance)
    }

    fun dispose() {
        model.dispose()
    }

    private companion object {
        const val RADIUS = 300f
        const val STACKS = 16
        const val SLICES = 36
    }
}
