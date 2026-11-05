package com.westly.lagosdrift

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import java.util.Random
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The zombies of the forest. They are the bodyguard's model with a grey-green wash and their arms held
 * out in front. They exist only while you are in the forest (or just outside it): up to [MAX_ZOMBIES] of
 * them appear in the trees 35 to 85 m from you, wander about slowly, and shuffle after you once you are
 * within [DETECT_RANGE]. They cannot go through the tree trunks, they stay on the forest ground, and they
 * keep out of each other.
 *
 * On foot: a zombie that reaches you claws at you (health goes down, the screen flashes red). You are
 * faster than they are when you run. If your health runs out you start again at the forest entrance.
 * In a car: you are safe inside, and a car going faster than [KILL_SPEED] knocks a zombie down for good.
 * Slower than that the zombies are pushed aside. Your health slowly comes back when you are left alone.
 */
class ZombieManager(private val model: Model, private val zones: ZoneWorld) {
    private class Zombie(val instance: ModelInstance) {
        val pose = HumanPose(instance)
        val pos = Vector3()
        var alive = false
        var dying = false
        var dieTimer = 0f
        var yaw = 0f
        var speed = 0f
        var chaseSpeed = 2.8f
        var phase = 0f
        var wanderTimer = 0f
        var hitTimer = 0f
        var scale = 1f
    }

    private val rng = Random()
    private val zombies = Array(MAX_ZOMBIES) { i ->
        val z = Zombie(ModelInstance(model))
        // Grey-green skin and rags: a wash over the model's own picture, a little different for each.
        val shade = 0.80f + (i % 3) * 0.08f
        for (m in z.instance.materials) m.set(ColorAttribute.createDiffuse(0.58f * shade, 0.78f * shade, 0.52f * shade, 1f))
        z
    }
    private val armL = Array(MAX_ZOMBIES) { zombies[it].instance.getNode(HumanPose.ARM_L) }
    private val armR = Array(MAX_ZOMBIES) { zombies[it].instance.getNode(HumanPose.ARM_R) }
    private val foreL = Array(MAX_ZOMBIES) { zombies[it].instance.getNode(HumanPose.FORE_L) }
    private val foreR = Array(MAX_ZOMBIES) { zombies[it].instance.getNode(HumanPose.FORE_R) }

    private var spawnTimer = 1.5f
    private var calmTimer = 0f
    private var damageFlash = 0f
    private var playerDownFlag = false
    private var inForestNow = false

    /** The player's health, 0 to 100. */
    var health = MAX_HEALTH
        private set

    /** How many zombies the car has knocked down. */
    var kills = 0
        private set

    /** True while the health bar should be shown: in the forest, or while recovering. */
    val hudVisible: Boolean
        get() = inForestNow || health < MAX_HEALTH - 0.5f || damageFlash > 0.01f

    /** How many zombies are walking about right now. */
    val aliveCount: Int
        get() {
            var n = 0
            for (z in zombies) if (z.alive && !z.dying) n++
            return n
        }

    /** True once, when the player has been caught: the game then starts the player again at the entrance. */
    fun takePlayerDown(): Boolean {
        val v = playerDownFlag
        playerDownFlag = false
        return v
    }

    /** Everyone goes away and the health is full again (after the player was caught). */
    fun reset() {
        for (z in zombies) {
            z.alive = false
            z.dying = false
        }
        health = MAX_HEALTH
        damageFlash = 0f
        calmTimer = 0f
        spawnTimer = 3f
    }

    /**
     * Moves everyone. [onFoot] says whether the player walks (position [player]) or drives [car]
     * (a car is two circles of [carRadius], [carOffset] in front of and behind its middle).
     */
    fun update(
        delta: Float, onFoot: Boolean, player: Vector3, car: CarController, carRadius: Float, carOffset: Float
    ) {
        val dt = min(delta, 0.05f)
        val sx = if (onFoot) player.x else car.position.x
        val sy = if (onFoot) player.y else car.position.y
        val sz = if (onFoot) player.z else car.position.z
        inForestNow = zones.inForest(sx, sz, 0f)
        if (damageFlash > 0f) damageFlash = maxOf(0f, damageFlash - dt * 1.6f)

        // Out of the forest the zombies fade away from the game; in it they keep coming.
        val active = zones.inForest(sx, sz, KEEP_MARGIN)
        spawnTimer -= dt
        if (active && spawnTimer <= 0f) {
            trySpawn(sx, sy, sz)
            spawnTimer = SPAWN_EVERY
        }

        val carRad = car.yawDegrees * 0.017453292f
        val carFx = -sin(carRad)
        val carFz = -cos(carRad)
        val carSpeed = abs(car.speed)
        var nearest = Float.MAX_VALUE

        for (i in zombies.indices) {
            val z = zombies[i]
            if (!z.alive) continue

            if (z.dying) {
                z.dieTimer += dt
                if (z.dieTimer >= DIE_TIME) {
                    z.alive = false
                    z.dying = false
                }
                continue
            }

            val dx = sx - z.pos.x
            val dz = sz - z.pos.z
            val dist = sqrt(dx * dx + dz * dz)
            if (!active || dist > DESPAWN_RANGE) {
                z.alive = false
                continue
            }
            if (dist < nearest) nearest = dist

            // Chase when close, otherwise shuffle about.
            val chasing = dist < DETECT_RANGE
            if (chasing) {
                val want = Math.toDegrees(atan2(-dx.toDouble(), -dz.toDouble())).toFloat()
                z.yaw = turnToward(z.yaw, want, 200f * dt)
                z.speed += (z.chaseSpeed - z.speed) * min(1f, 3f * dt)
            } else {
                z.wanderTimer -= dt
                if (z.wanderTimer <= 0f) {
                    z.yaw += (rng.nextFloat() - 0.5f) * 140f
                    z.wanderTimer = 2f + rng.nextFloat() * 4f
                }
                z.speed += (WANDER_SPEED - z.speed) * min(1f, 2f * dt)
            }

            // Step forward, but only onto forest ground.
            val rad = z.yaw * 0.017453292f
            val nx = z.pos.x - sin(rad) * z.speed * dt
            val nz = z.pos.z - cos(rad) * z.speed * dt
            val h = RoadGround.carHeightAt(nx, nz, z.pos.y, 1.2f, false)
            if (h.isNaN()) {
                if (!chasing) z.yaw += 140f + rng.nextFloat() * 80f
                z.speed *= 0.5f
            } else {
                z.pos.x = nx
                z.pos.z = nz
                z.pos.y += (h - z.pos.y) * min(1f, 12f * dt)
            }
            z.phase += z.speed * dt * 2.6f

            // Not through the trees, not through each other.
            zones.pushPlayer(z.pos, ZOMBIE_RADIUS)
            for (j in i + 1 until zombies.size) {
                val o = zombies[j]
                if (!o.alive || o.dying) continue
                val ex = z.pos.x - o.pos.x
                val ez = z.pos.z - o.pos.z
                val d2 = ex * ex + ez * ez
                val lim = ZOMBIE_RADIUS * 2f
                if (d2 >= lim * lim) continue
                val d = maxOf(sqrt(d2), 0.001f)
                val push = (lim - d) * 0.5f
                z.pos.x += ex / d * push
                z.pos.z += ez / d * push
                o.pos.x -= ex / d * push
                o.pos.z -= ez / d * push
            }

            if (onFoot) {
                // Claw at the player when close enough.
                z.hitTimer -= dt
                if (dist < ATTACK_RANGE && z.hitTimer <= 0f && abs(player.y - z.pos.y) < 1.6f) {
                    z.hitTimer = HIT_EVERY
                    health -= HIT_DAMAGE
                    damageFlash = 1f
                    calmTimer = 0f
                }
            } else {
                // The car: fast enough and the zombie goes down, otherwise it is shoved aside.
                for (end in -1..1 step 2) {
                    val cx = car.position.x + carFx * carOffset * end
                    val cz = car.position.z + carFz * carOffset * end
                    var ex = z.pos.x - cx
                    var ez = z.pos.z - cz
                    val lim = carRadius + ZOMBIE_RADIUS
                    val d2 = ex * ex + ez * ez
                    if (d2 >= lim * lim) continue
                    if (carSpeed >= KILL_SPEED) {
                        z.dying = true
                        z.dieTimer = 0f
                        kills++
                        car.bump(0.3f)
                        break
                    }
                    var d = sqrt(d2)
                    if (d < 0.001f) {
                        ex = 1f
                        ez = 0f
                        d = 1f
                    }
                    z.pos.x += ex / d * (lim - d)
                    z.pos.z += ez / d * (lim - d)
                }
            }
        }

        // Health comes back when nobody has hurt you for a while.
        calmTimer += dt
        if (calmTimer > CALM_BEFORE_HEAL && health < MAX_HEALTH) {
            health = min(MAX_HEALTH, health + HEAL_PER_SECOND * dt)
        }
        if (health <= 0f) {
            health = 0f
            playerDownFlag = true
        }
    }

    /** Puts a zombie into the trees at some distance from [sx], [sz], on ground and away from the trunks. */
    private fun trySpawn(sx: Float, sy: Float, sz: Float) {
        var slot: Zombie? = null
        var count = 0
        for (z in zombies) {
            if (z.alive && !z.dying) count++
            if (!z.alive && slot == null) slot = z
        }
        val z = slot ?: return
        if (count >= MAX_ZOMBIES) return
        val angle = rng.nextFloat() * 6.2831855f
        val r = SPAWN_MIN + rng.nextFloat() * (SPAWN_MAX - SPAWN_MIN)
        val px = sx + cos(angle) * r
        val pz = sz + sin(angle) * r
        // Only inside the forest, and not in the first stretch, so the way in is quiet.
        if (!zones.inForest(px, pz, 0f)) return
        if (zones.along(px, pz) < ZoneWorld.ALONG0 + SAFE_START) return
        if (zones.touchesTree(px, pz, 0.7f)) return
        val h = RoadGround.carHeightAt(px, pz, sy + 4f, 14f, false)
        if (h.isNaN()) return
        z.pos.set(px, h, pz)
        z.yaw = rng.nextFloat() * 360f
        z.speed = 0f
        z.chaseSpeed = 2.4f + rng.nextFloat() * 0.9f
        z.phase = rng.nextFloat() * 6.2831855f
        z.wanderTimer = 1f + rng.nextFloat() * 3f
        z.hitTimer = 0.4f
        z.scale = PlayerModelFactory.SCALE * (0.94f + rng.nextFloat() * 0.12f)
        z.alive = true
        z.dying = false
        z.dieTimer = 0f
    }

    private fun turnToward(current: Float, target: Float, maxStep: Float): Float {
        var diff = target - current
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        return current + diff.coerceIn(-maxStep, maxStep)
    }

    /** Keeps the walking player from standing inside a zombie. */
    fun pushOut(p: Vector3, radius: Float) {
        for (z in zombies) {
            if (!z.alive || z.dying) continue
            val ex = p.x - z.pos.x
            val ez = p.z - z.pos.z
            val limit = radius + ZOMBIE_RADIUS
            val d2 = ex * ex + ez * ez
            if (d2 >= limit * limit) continue
            val d = maxOf(sqrt(d2), 0.001f)
            p.x += ex / d * (limit - d)
            p.z += ez / d * (limit - d)
        }
    }

    fun render(batch: ModelBatch, environment: Environment, camera: Camera) {
        for (i in zombies.indices) {
            val z = zombies[i]
            if (!z.alive) continue
            val dx = z.pos.x - camera.position.x
            val dz = z.pos.z - camera.position.z
            if (dx * dx + dz * dz > DRAW_RANGE * DRAW_RANGE) continue
            if (!camera.frustum.sphereInFrustum(z.pos.x, z.pos.y + 0.9f, z.pos.z, 1.4f)) continue

            val t = z.instance.transform.idt().translate(z.pos.x, z.pos.y, z.pos.z).rotate(Vector3.Y, z.yaw)
            if (z.dying) {
                // Falls over backwards and sinks a little.
                val k = (z.dieTimer / DIE_TIME).coerceIn(0f, 1f)
                t.translate(0f, -0.5f * k, 0f).rotate(Vector3.X, -85f * k)
            } else {
                // The shuffle: a small lurch from side to side.
                t.rotate(Vector3.Z, sin(z.phase) * 3f)
            }
            t.scale(z.scale, z.scale, z.scale)

            z.pose.update(z.phase, if (z.dying) 0f else min(1f, z.speed / 2.2f), false)
            // Arms stretched out in front, the way zombies hold them.
            armL[i].rotation.set(Vector3.X, 78f)
            armR[i].rotation.set(Vector3.X, 84f)
            foreL[i].rotation.set(Vector3.X, 12f)
            foreR[i].rotation.set(Vector3.X, 8f)
            z.instance.calculateTransforms()
            batch.render(z.instance, environment)
        }
    }

    // ------------------------------------------------------------------ the screen

    /** The health bar and the red flash when you are hurt. Call between shapeRenderer.begin(Filled) and end(). */
    fun drawShapes(shapes: ShapeRenderer, screenWidth: Float, screenHeight: Float, margin: Float) {
        if (damageFlash > 0.01f) {
            shapes.setColor(0.8f, 0f, 0f, damageFlash * 0.35f)
            shapes.rect(0f, 0f, screenWidth, screenHeight)
        }
        if (!hudVisible) return
        val w = screenHeight * 0.5f
        val h = screenHeight * 0.022f
        val x = screenWidth / 2f - w / 2f
        val y = barY(screenHeight, margin)
        shapes.setColor(0f, 0f, 0f, 0.55f)
        shapes.rect(x - 3f, y - 3f, w + 6f, h + 6f)
        val f = (health / MAX_HEALTH).coerceIn(0f, 1f)
        shapes.setColor(1f - f * 0.7f, 0.25f + f * 0.65f, 0.15f, 0.95f)
        shapes.rect(x, y, w * f, h)
    }

    /** Where the bar sits; the game puts its text just above. */
    fun barY(screenHeight: Float, margin: Float): Float = screenHeight - margin - screenHeight * 0.035f * 6.8f

    /** The line of text above the bar. */
    fun hudText(): String = "HEALTH ${health.toInt()}   ZOMBIES DOWN $kills"

    fun dispose() {
        // The model belongs to the game (it is the bodyguard's model); nothing of our own to free.
    }

    private companion object {
        const val MAX_ZOMBIES = 12
        const val MAX_HEALTH = 100f
        const val SPAWN_MIN = 35f
        const val SPAWN_MAX = 85f
        const val SPAWN_EVERY = 1.2f
        const val DESPAWN_RANGE = 170f
        const val DRAW_RANGE = 200f
        const val KEEP_MARGIN = 40f
        const val SAFE_START = 45f          // metres past the start of the forest with no zombies
        const val DETECT_RANGE = 45f
        const val WANDER_SPEED = 0.7f
        const val ZOMBIE_RADIUS = 0.4f
        const val ATTACK_RANGE = 1.15f
        const val HIT_EVERY = 0.9f
        const val HIT_DAMAGE = 9f
        const val KILL_SPEED = 4f           // metres per second (about 14 km/h)
        const val DIE_TIME = 1.6f
        const val CALM_BEFORE_HEAL = 8f
        const val HEAL_PER_SECOND = 4f
    }
}
