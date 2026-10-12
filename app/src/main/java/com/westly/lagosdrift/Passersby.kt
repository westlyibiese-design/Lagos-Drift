package com.westly.lagosdrift

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The passers-by: a boy (with arms and legs that swing) and a chibi figure (all in one piece, he sways and bobs),
 * three of each, who stroll about on the sand around the player. They appear 25 to 80 m away, walk straight on,
 * turn when the ground ends (the road, a wall, the edge of the yard) and every few seconds for no reason, and
 * vanish when the player has left them far behind. They never step onto the road, so traffic never meets them.
 */
class Passersby(private val boyModel: Model, private val chibiModel: Model) {
    private class Walker(val boy: Boolean, val instance: ModelInstance) {
        var pose: HumanPose? = null
        var active = false
        var x = 0f
        var y = 0f
        var z = 0f
        var yaw = 0f
        var speed = 1.3f
        var phase = 0f
        var turnTimer = 0f
    }

    private val rng = Random()
    private val walkers = Array(COUNT) { i ->
        val boy = i % 2 == 0
        val w = Walker(boy, ModelInstance(if (boy) boyModel else chibiModel))
        if (boy) w.pose = HumanPose(w.instance)
        w
    }
    private var spawnTimer = 1f

    fun update(delta: Float, subjectX: Float, subjectY: Float, subjectZ: Float) {
        val dt = min(delta, 0.05f)
        spawnTimer -= dt
        for (w in walkers) {
            if (!w.active) {
                if (spawnTimer <= 0f) {
                    spawn(w, subjectX, subjectY, subjectZ)
                    spawnTimer = SPAWN_EVERY
                }
                continue
            }
            val dx = w.x - subjectX
            val dz = w.z - subjectZ
            if (dx * dx + dz * dz > DESPAWN_RANGE * DESPAWN_RANGE) {
                w.active = false
                continue
            }
            walk(w, dt)
            pose(w)
        }
    }

    private fun spawn(w: Walker, sx: Float, sy: Float, sz: Float) {
        val angle = rng.nextFloat() * 6.2831855f
        val r = SPAWN_MIN + rng.nextFloat() * (SPAWN_MAX - SPAWN_MIN)
        val px = sx + cos(angle) * r
        val pz = sz + sin(angle) * r
        val h = RoadGround.carHeightAt(px, pz, sy + 1.5f, 4f)
        if (h.isNaN() || RoadGround.lastKind != RoadGround.SURFACE_TERRAIN) return
        w.x = px
        w.y = h
        w.z = pz
        w.yaw = rng.nextFloat() * 360f
        w.speed = 1.0f + rng.nextFloat() * 0.7f
        w.phase = rng.nextFloat() * 6.2831855f
        w.turnTimer = 3f + rng.nextFloat() * 6f
        w.active = true
    }

    private fun walk(w: Walker, dt: Float) {
        w.turnTimer -= dt
        if (w.turnTimer <= 0f) {
            w.yaw += (rng.nextFloat() - 0.5f) * 80f
            w.turnTimer = 3f + rng.nextFloat() * 6f
        }
        val rad = w.yaw * 0.017453292f
        val nx = w.x - sin(rad) * w.speed * dt
        val nz = w.z - cos(rad) * w.speed * dt
        // Look a little ahead so a wall or the road is seen before the walker is on it.
        val ax = w.x - sin(rad) * LOOK_AHEAD
        val az = w.z - cos(rad) * LOOK_AHEAD
        val ahead = RoadGround.carHeightAt(ax, az, w.y, 1.0f)
        val ok = !ahead.isNaN() && RoadGround.lastKind == RoadGround.SURFACE_TERRAIN
        if (!ok) {
            w.yaw += 120f + rng.nextFloat() * 120f
            w.turnTimer = 3f + rng.nextFloat() * 4f
            return
        }
        val h = RoadGround.carHeightAt(nx, nz, w.y, 1.0f)
        if (h.isNaN()) return
        w.x = nx
        w.z = nz
        w.y += (h - w.y) * min(1f, 12f * dt)
        w.phase += w.speed * dt * 2.4f
    }

    private fun pose(w: Walker) {
        if (w.boy) {
            val s = PlayerModelFactory.BOY_SCALE
            w.instance.transform.idt()
                .translate(w.x, w.y, w.z)
                .rotate(Vector3.Y, w.yaw)
                .scale(s, s, s)
            w.pose?.update(w.phase, 1f, false)
            w.instance.calculateTransforms()
        } else {
            val s = PlayerModelFactory.CHIBI_SCALE
            val bob = abs(sin(w.phase)) * 0.05f
            val sway = sin(w.phase) * 4f
            w.instance.transform.idt()
                .translate(w.x, w.y + bob, w.z)
                .rotate(Vector3.Y, w.yaw)
                .rotate(Vector3.Z, sway)
                .scale(s, s, s)
        }
    }

    fun render(batch: ModelBatch, environment: Environment, camera: Camera) {
        for (w in walkers) {
            if (!w.active) continue
            val dx = w.x - camera.position.x
            val dz = w.z - camera.position.z
            if (dx * dx + dz * dz > DRAW_RANGE * DRAW_RANGE) continue
            if (!camera.frustum.sphereInFrustum(w.x, w.y + 0.9f, w.z, 1.2f)) continue
            batch.render(w.instance, environment)
        }
    }

    /** Keeps the walking player from standing inside a passer-by. */
    fun pushOut(p: Vector3, radius: Float) {
        for (w in walkers) {
            if (!w.active) continue
            val ex = p.x - w.x
            val ez = p.z - w.z
            val limit = radius + WALKER_RADIUS
            val d2 = ex * ex + ez * ez
            if (d2 >= limit * limit) continue
            val d = maxOf(sqrt(d2), 0.001f)
            p.x += ex / d * (limit - d)
            p.z += ez / d * (limit - d)
        }
    }

    private companion object {
        const val COUNT = 6
        const val SPAWN_MIN = 25f
        const val SPAWN_MAX = 80f
        const val SPAWN_EVERY = 0.8f
        const val DESPAWN_RANGE = 140f
        const val DRAW_RANGE = 220f
        const val LOOK_AHEAD = 1.5f
        const val WALKER_RADIUS = 0.35f
    }
}
