package com.westly.lagosdrift

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3

/** Keeps the camera behind and above the car, looking slightly ahead of it. */
class ChaseCamera(private val camera: PerspectiveCamera) {
    var distance = 9f
    var height = 3.8f
    var lookAhead = 4f

    private val forward = Vector3()
    private val lookTarget = Vector3()

    fun update(carPosition: Vector3, carYawDegrees: Float) {
        // The car's nose points along -Z at yaw 0.
        forward.set(0f, 0f, -1f).rotate(Vector3.Y, carYawDegrees)

        camera.position.set(carPosition).mulAdd(forward, -distance)
        camera.position.y += height

        lookTarget.set(carPosition).add(0f, 1f, 0f).mulAdd(forward, lookAhead)
        camera.up.set(Vector3.Y)
        camera.lookAt(lookTarget)
        camera.update()
    }
}
