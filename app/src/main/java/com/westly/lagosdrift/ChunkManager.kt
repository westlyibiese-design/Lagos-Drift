package com.westly.lagosdrift

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import kotlin.math.abs
import kotlin.math.max

/**
 * Keeps only the part of the world near the car in memory. Chunks close to the car get the full
 * detailed model, chunks further away a simple one, and chunks out of range are thrown away.
 * A few chunks are built each frame so driving never stalls.
 */
class ChunkManager {
    private class Loaded(val chunk: TownLayout.Chunk, val model: Model, val detailed: Boolean) {
        val instance = ModelInstance(model)
    }

    private val loaded = HashMap<Int, Loaded>()
    private var lastCx = Int.MIN_VALUE
    private var lastCz = Int.MIN_VALUE
    private val wanted = ArrayList<Pair<Int, Int>>()

    /** Builds everything close to the start position now, so the first frame is not empty. */
    fun preload(x: Float, z: Float) {
        val cx = TownLayout.cxOf(x)
        val cz = TownLayout.czOf(z)
        for (dz in -1..1) for (dx in -1..1) load(cx + dx, cz + dz)
    }

    fun update(x: Float, z: Float) {
        val cx = TownLayout.cxOf(x)
        val cz = TownLayout.czOf(z)
        if (cx != lastCx || cz != lastCz) {
            lastCx = cx
            lastCz = cz
            rebuildWantedList(cx, cz)
            unloadFar(cx, cz)
        }

        // Build a few of the wanted chunks each frame, nearest first.
        var budget = BUILD_PER_FRAME
        val iterator = wanted.iterator()
        while (iterator.hasNext() && budget > 0) {
            val (wx, wz) = iterator.next()
            val have = loaded[key(wx, wz)]
            val wantDetail = ringOf(wx, wz, cx, cz) <= DETAIL_RING
            if (have == null || have.detailed != wantDetail) {
                load(wx, wz, wantDetail)
                budget--
            }
            iterator.remove()
        }
    }

    fun render(batch: ModelBatch, environment: Environment, camera: PerspectiveCamera) {
        for (l in loaded.values) {
            val c = l.chunk
            val half = TownLayout.CHUNK / 2f
            if (camera.frustum.boundsInFrustum(c.minX + half, 20f, c.minZ + half, half, 30f, half)) {
                batch.render(l.instance, environment)
            }
        }
    }

    fun dispose() {
        for (l in loaded.values) l.model.dispose()
        loaded.clear()
    }

    private fun key(cx: Int, cz: Int): Int = cz * 100 + cx

    private fun ringOf(wx: Int, wz: Int, cx: Int, cz: Int): Int = max(abs(wx - cx), abs(wz - cz))

    private fun load(cx: Int, cz: Int, detailed: Boolean = true) {
        val chunk = TownLayout.chunkAt(cx, cz) ?: return
        loaded.remove(key(cx, cz))?.model?.dispose()
        loaded[key(cx, cz)] = Loaded(chunk, TownModelFactory.buildChunk(chunk, detailed), detailed)
    }

    private fun rebuildWantedList(cx: Int, cz: Int) {
        wanted.clear()
        for (ring in 0..LOAD_RING) {
            for (dz in -ring..ring) {
                for (dx in -ring..ring) {
                    if (max(abs(dx), abs(dz)) != ring) continue
                    if (TownLayout.chunkAt(cx + dx, cz + dz) != null) wanted.add((cx + dx) to (cz + dz))
                }
            }
        }
    }

    private fun unloadFar(cx: Int, cz: Int) {
        val iterator = loaded.entries.iterator()
        while (iterator.hasNext()) {
            val l = iterator.next().value
            if (ringOf(l.chunk.cx, l.chunk.cz, cx, cz) > LOAD_RING + 1) {
                l.model.dispose()
                iterator.remove()
            }
        }
    }

    companion object {
        /** Chunks within this many chunks of the car are drawn (4 = about 450 m). */
        const val LOAD_RING = 4
        /** Chunks within this many chunks of the car get the full detailed model. */
        const val DETAIL_RING = 1
        private const val BUILD_PER_FRAME = 2
    }
}
