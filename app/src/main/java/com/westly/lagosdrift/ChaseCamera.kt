package com.westly.lagosdrift

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.min

/** Follows the car from behind and above. Turns a little late and pulls back at speed. */
class ChaseCamera(private val camera: PerspectiveCamera) {
    var baseDistance = 8.5f
    var height = 3.6f
    var lookAhead = 4f

    private var cameraYaw = 0f
    private var started = false
    private val forward = Vector3()
    private val lookTarget = Vector3()

    fun update(carPosition: Vector3, carYawDegrees: Float, speed: Float, delta: Float) {
        if (!started) {
            cameraYaw = carYawDegrees
            started = true
        }

        // Ease the camera heading toward the car heading, the short way round.
        var diff = (carYawDegrees - cameraYaw) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        cameraYaw += diff * min(1f, 4f * delta)

        val distance = baseDistance + abs(speed) * 0.08f
        forward.set(0f, 0f, -1f).rotate(Vector3.Y, cameraYaw)

        camera.position.set(carPosition).mulAdd(forward, -distance)
        camera.position.y += height

        lookTarget.set(carPosition).add(0f, 1f, 0f).mulAdd(forward, lookAhead)
        camera.up.set(Vector3.Y)
        camera.lookAt(lookTarget)
        camera.update()
    }
}
