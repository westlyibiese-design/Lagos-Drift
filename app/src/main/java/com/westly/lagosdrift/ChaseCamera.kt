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
                camera.position.y = hoodHeight
                lookTarget.set(carPosition).mulAdd(forward, 20f)
                lookTarget.y = 1.2f
            }
            MODE_ORBIT -> {
                if (!freeLook) cameraYaw = carYawDegrees
                camera.fieldOfView = 65f
                orbitAngle += 28f * delta
                val a = orbitAngle * MathUtils.degreesToRadians
                val d = 9.5f * zoom * distanceScale
                camera.position.set(
                    carPosition.x + sin(a) * d, (3.2f * zoom + 0.8f) * distanceScale, carPosition.z + cos(a) * d
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

                val distance = (baseDistance + abs(speed) * 0.08f) * zoom * distanceScale
                forward.set(0f, 0f, -1f).rotate(Vector3.Y, cameraYaw)
                camera.position.set(carPosition).mulAdd(forward, -distance)
                camera.position.y += height * zoom * distanceScale
                lookTarget.set(carPosition).add(0f, 1f, 0f).mulAdd(forward, 4f * distanceScale)
            }
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
        val MODE_NAMES = arrayOf("Near", "Far", "Hood", "Orbit")
    }
}
