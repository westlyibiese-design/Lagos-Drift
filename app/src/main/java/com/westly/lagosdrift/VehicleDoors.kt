package com.westly.lagosdrift

import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3

/**
 * The doors of one vehicle. They all open and shut together, slowly, around their hinges: the nodes called
 * [ids] (left doors end in "l", right doors in "r") are turned about the vertical axis at their pivot.
 * [amount] goes from 0 (shut) to 1 (fully open) and chases [target].
 */
class VehicleDoors(private val ids: Array<String>, private val openDegrees: Float) {
    var amount = 0f
        private set
    var target = 0f
        private set

    /** True when the doors are open, or on their way to open. */
    val wantsOpen: Boolean
        get() = target > 0.5f

    fun open() {
        target = 1f
    }

    fun close() {
        target = 0f
    }

    fun update(delta: Float) {
        if (amount < target) {
            amount += delta / OPEN_SECONDS
            if (amount > target) amount = target
        } else if (amount > target) {
            amount -= delta / OPEN_SECONDS
            if (amount < target) amount = target
        }
    }

    /** Turns the door nodes of [instance]. Call before instance.calculateTransforms(). */
    fun apply(instance: ModelInstance) {
        val eased = amount * amount * (3f - 2f * amount)
        for (i in ids.indices) {
            val id = ids[i]
            val node = instance.getNode(id) ?: continue
            val sign = if (id.endsWith("l")) -1f else 1f
            node.rotation.set(Vector3.Y, sign * eased * openDegrees)
        }
    }

    private companion object {
        const val OPEN_SECONDS = 1.1f
    }
}
