package com.westly.lagosdrift

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetErrorListener
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.ModelLoader
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.utils.JsonReader
import kotlin.math.sqrt

private const val STATE_IDLE = 0
private const val STATE_LOADING = 1
private const val STATE_READY = 2
private const val STATE_FAILED = 3

/**
 * The one road: the Glen Canyon road with the Futatsugoya tunnel, stored as about 90 chunk files
 * (assets/roads/glen_tunnel/road_XXX_YYY.g3db) listed in chunks.json with their boxes.
 *
 * Chunks are loaded by distance with libGDX's AssetManager (the files are read and parsed on a
 * loader thread, the models and textures are created on the game thread), and freed again when the
 * car is far away. Textures are shared: the AssetManager loads each picture once, however many
 * chunks use it, and frees it when the last chunk using it goes.
 *
 * The pictures already contain their light, so the road has its own Environment: white ambient
 * light and no sunlight, plus haze in the sky colour. Chunks are drawn double-sided, and each one
 * is skipped when it is behind the camera or beyond the draw distance. When a chunk is ready its
 * drivable triangles are handed to RoadGround, and taken away again when the chunk is freed.
 */
class RoadWorld(fogR: Float, fogG: Float, fogB: Float) {
    private class Chunk(
        val path: String,
        val minX: Float, val minZ: Float, val maxX: Float, val maxZ: Float,
        val cx: Float, val cy: Float, val cz: Float,
        val hx: Float, val hy: Float, val hz: Float
    ) {
        var state = STATE_IDLE
        var instance: ModelInstance? = null

        /** Distance on the ground from a point to this chunk's box (0 when the point is inside). */
        fun distanceTo(x: Float, z: Float): Float {
            val dx = maxOf(minX - x, 0f, x - maxX)
            val dz = maxOf(minZ - z, 0f, z - maxZ)
            return sqrt(dx * dx + dz * dz)
        }
    }

    /** White ambient light only (the pictures are baked) and haze in the sky colour. */
    val environment = Environment()

    /** The exact text of the last loading problem, or null. The game shows it on screen. */
    var errorText: String? = null
        private set

    private val manager = AssetManager(InternalFileHandleResolver())
    private val parameters = ModelLoader.ModelParameters()
    private val chunks = ArrayList<Chunk>()
    private var refreshTimer = 0f
    private var started = false

    init {
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 1f, 1f, 1f, 1f))
        environment.set(ColorAttribute(ColorAttribute.Fog, fogR, fogG, fogB, 1f))

        // The road pictures tile (their UVs go beyond 0..1), so they repeat and have mipmaps.
        parameters.textureParameter.genMipMaps = true
        parameters.textureParameter.minFilter = Texture.TextureFilter.MipMapLinearLinear
        parameters.textureParameter.magFilter = Texture.TextureFilter.Linear
        parameters.textureParameter.wrapU = Texture.TextureWrap.Repeat
        parameters.textureParameter.wrapV = Texture.TextureWrap.Repeat

        manager.setErrorListener(AssetErrorListener { asset, throwable ->
            report("Road load error (${asset.fileName}): ${describe(throwable)}")
        })

        try {
            readChunkList()
        } catch (e: Exception) {
            report("Road list error ($FOLDER/chunks.json): ${describe(e)}")
        }
    }

    private fun report(text: String) {
        errorText = text
        Gdx.app.error("LagosDrift", text)
    }

    private fun describe(t: Throwable): String {
        val sb = StringBuilder(t.toString())
        var cause = t.cause
        var depth = 0
        while (cause != null && depth < 4) {
            sb.append(" | caused by ").append(cause.toString())
            cause = cause.cause
            depth++
        }
        return sb.toString()
    }

    /** Reads chunks.json (the sky dome in it is not used, the game has its own sky). */
    private fun readChunkList() {
        val root = JsonReader().parse(Gdx.files.internal("$FOLDER/chunks.json"))
        var e = root.child
        while (e != null) {
            val file = e.getString("file")
            if (file != "sky.g3db") {
                val mn = e.get("min")
                val mx = e.get("max")
                val minX = mn.getFloat(0)
                val minY = mn.getFloat(1)
                val minZ = mn.getFloat(2)
                val maxX = mx.getFloat(0)
                val maxY = mx.getFloat(1)
                val maxZ = mx.getFloat(2)
                chunks.add(
                    Chunk(
                        "$FOLDER/$file", minX, minZ, maxX, maxZ,
                        (minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f,
                        (maxX - minX) / 2f, (maxY - minY) / 2f, (maxZ - minZ) / 2f
                    )
                )
            }
            e = e.next
        }
    }

    /**
     * Call every frame with the position the camera follows. Asks for the chunks near it, frees the far
     * ones, and finishes loading for at most [budgetMs] milliseconds this frame.
     */
    fun update(delta: Float, x: Float, z: Float, budgetMs: Int) {
        refreshTimer -= delta
        if (!started || refreshTimer <= 0f) {
            started = true
            refreshTimer = REFRESH_SECONDS
            refresh(x, z)
        }
        manager.update(budgetMs)
        // At most one chunk is turned into a model (and its ground read) per frame, so there is no hitch.
        for (i in 0 until chunks.size) {
            val c = chunks[i]
            if (c.state == STATE_LOADING && manager.isLoaded(c.path)) {
                makeInstance(c)
                break
            }
        }
    }

    private fun refresh(x: Float, z: Float) {
        // First only the road right around the start, so the start-up screen is short; then the whole ring.
        val radius = if (readyProgress(x, z) >= 1f || errorText != null) LOAD_RADIUS else READY_RADIUS
        for (i in 0 until chunks.size) {
            val c = chunks[i]
            val d = c.distanceTo(x, z)
            if (c.state == STATE_IDLE && d <= radius) {
                manager.load(c.path, Model::class.java, parameters)
                c.state = STATE_LOADING
            } else if (c.state != STATE_IDLE && d > UNLOAD_RADIUS) {
                RoadGround.remove(c.path)
                manager.unload(c.path)
                c.instance = null
                c.state = STATE_IDLE
            }
        }
    }

    private fun makeInstance(c: Chunk) {
        try {
            val model = manager.get(c.path, Model::class.java)
            val instance = ModelInstance(model)
            // The materials are double-sided: no face culling.
            for (m in instance.materials) m.set(IntAttribute.createCullFace(GL20.GL_NONE))
            c.instance = instance
            c.state = STATE_READY
            // The drivable triangles of this chunk join the ground.
            try {
                RoadGround.addModel(c.path, model)
            } catch (e: Exception) {
                report("Road ground error (${c.path}): ${describe(e)}")
            }
        } catch (e: Exception) {
            report("Road model error (${c.path}): ${describe(e)}")
            c.state = STATE_FAILED
        }
    }

    /** 0..1: how much of the road close to this point is loaded. */
    fun readyProgress(x: Float, z: Float): Float {
        var need = 0
        var have = 0
        for (i in 0 until chunks.size) {
            val c = chunks[i]
            if (c.distanceTo(x, z) <= READY_RADIUS) {
                need++
                if (c.state == STATE_READY) have++
            }
        }
        return if (need == 0) 1f else have.toFloat() / need
    }

    /** True when the road around this point is loaded, or when loading failed and nothing is left to wait for. */
    fun isReady(x: Float, z: Float): Boolean =
        readyProgress(x, z) >= 1f || (errorText != null && manager.queuedAssets == 0)

    /**
     * Draws the loaded chunks that are in front of the camera and within its draw distance. Opaque
     * parts are drawn first and the alpha ones after them (the ModelBatch sorts them that way).
     */
    fun render(batch: ModelBatch, camera: Camera) {
        val camX = camera.position.x
        val camZ = camera.position.z
        for (i in 0 until chunks.size) {
            val c = chunks[i]
            if (c.state != STATE_READY) continue
            val instance = c.instance ?: continue
            if (c.distanceTo(camX, camZ) > camera.far) continue
            if (!camera.frustum.boundsInFrustum(c.cx, c.cy, c.cz, c.hx, c.hy, c.hz)) continue
            batch.render(instance, environment)
        }
    }

    fun dispose() {
        for (i in 0 until chunks.size) chunks[i].instance = null
        RoadGround.clear()
        manager.dispose()
    }

    private companion object {
        const val FOLDER = "roads/glen_tunnel"
        const val LOAD_RADIUS = 1300f       // chunks closer than this (to their box) are loaded
        const val UNLOAD_RADIUS = 1500f     // and freed again beyond this, so they do not flicker
        const val READY_RADIUS = 350f       // the game starts when everything this close is loaded
        const val REFRESH_SECONDS = 0.5f
    }
}
