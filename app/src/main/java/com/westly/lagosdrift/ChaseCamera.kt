package com.westly.lagosdrift

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Four views of the car: near chase, far chase, hood (inside the car) and a slow orbit.
 * Pinch or the + and - buttons change zoom in every view except the hood view.
 *
 * Every height is measured from the car (or player), so the views follow hills. The camera is kept above
 * the road surface, and inside the tunnel it is pulled closer and lower so it stays between the floor and
 * the ceiling and does not poke through the walls.
 */
class ChaseCamera(private val camera: PerspectiveCamera) {
    /** 1 = normal. Smaller is closer. */
    var zoom = 1f

    /** Extra closeness for the walking player (about 0.4); 1 when driving. */
    var distanceScale = 1f

    /** Where the hood (driver's) view sits: metres behind the vehicle's middle, and height. */
    var hoodBack = 0.15f
    var hoodHeight = 1.3f

    /** While walking the camera keeps its own heading, so the stick can be read relative to the view. */
    var freeLook = false

    /** Set by the game while the followed thing is inside the tunnel. */
    var tunnelLimit = false

    private var cameraYaw = 0f

    /** The direction the camera looks (same rule as the car's yaw: 0 faces -Z, positive turns left). */
    val yaw: Float get() = cameraYaw

    /** Turns the camera by [degrees] (positive turns left). Only used while walking. */
    fun addYaw(degrees: Float) {
        cameraYaw += degrees
        if (cameraYaw > 180f) cameraYaw -= 360f
        if (cameraYaw < -180f) cameraYaw += 360f
    }

    private var orbitAngle = 0f
    private var started = false
    private val forward = Vector3()
    private val lookTarget = Vector3()

    fun update(carPosition: Vector3, carYawDegrees: Float, speed: Float, delta: Float, mode: Int) {
        if (!started) {
            cameraYaw = carYawDegrees
            started = true
        }

        when (mode) {
            MODE_HOOD -> {
                if (!freeLook) cameraYaw = carYawDegrees
                camera.fieldOfView = 72f
                forward.set(0f, 0f, -1f).rotate(Vector3.Y, cameraYaw)
                camera.position.set(carPosition).mulAdd(forward, -hoodBack)
                camera.position.y = carPosition.y + hoodHeight
                lookTarget.set(carPosition).mulAdd(forward, 20f)
                lookTarget.y = carPosition.y + 1.2f
            }
            MODE_ORBIT -> {
                if (!freeLook) cameraYaw = carYawDegrees
                camera.fieldOfView = 65f
                orbitAngle += 28f * delta
                val a = orbitAngle * MathUtils.degreesToRadians
                var d = 9.5f * zoom * distanceScale
                var rise = (3.2f * zoom + 0.8f) * distanceScale
                if (tunnelLimit) {
                    val s = min(1f, min(TUNNEL_MAX_SIDE / d, TUNNEL_MAX_RISE / rise))
                    d *= s
                    rise *= s
                }
                camera.position.set(
                    carPosition.x + sin(a) * d, carPosition.y + rise, carPosition.z + cos(a) * d
                )
                lookTarget.set(carPosition).add(0f, 1f, 0f)
            }
            else -> {
                camera.fieldOfView = 65f
                val baseDistance = if (mode == MODE_FAR) 15f else 8.5f
                val height = if (mode == MODE_FAR) 6.5f else 3.6f

                // Ease the camera heading toward the car heading, the short way round.
                var diff = (carYawDegrees - cameraYaw) % 360f
                if (diff > 180f) diff -= 360f
                if (diff < -180f) diff += 360f
                if (!freeLook) cameraYaw += diff * min(1f, 4f * delta)

                var distance = (baseDistance + abs(speed) * 0.08f) * zoom * distanceScale
                var rise = height * zoom * distanceScale
                if (tunnelLimit) {
                    val s = min(1f, TUNNEL_MAX_RISE / rise)
                    distance *= s
                    rise *= s
                }
                forward.set(0f, 0f, -1f).rotate(Vector3.Y, cameraYaw)
                camera.position.set(carPosition).mulAdd(forward, -distance)
                camera.position.y += rise
                lookTarget.set(carPosition).add(0f, 1f, 0f).mulAdd(forward, 4f * distanceScale)
            }
        }

        // Never below the road under the camera.
        val ground = RoadGround.heightAt(camera.position.x, camera.position.z, carPosition.y + 3f)
        if (!ground.isNaN() && camera.position.y < ground + MIN_GROUND_CLEARANCE) {
            camera.position.y = ground + MIN_GROUND_CLEARANCE
        }

        camera.up.set(Vector3.Y)
        camera.lookAt(lookTarget)
        camera.update()
    }

    companion object {
        const val MODE_NEAR = 0
        const val MODE_FAR = 1
        const val MODE_HOOD = 2
        const val MODE_ORBIT = 3
        const val MODE_COUNT = 4
        private const val MIN_GROUND_CLEARANCE = 0.5f
        private const val TUNNEL_MAX_RISE = 5.2f     // the ceiling is about 7.7 m above the floor
        private const val TUNNEL_MAX_SIDE = 4f       // the walls are about 5.2 m from the middle
        val MODE_NAMES = arrayOf("Near", "Far", "Hood", "Orbit")
    }
}
