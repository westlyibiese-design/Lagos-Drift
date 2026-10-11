package com.westly.lagosdrift

import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.math.Vector3
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Poses the human model (player, passers-by, people at the bus stops - they all share one model).
 * The model is baked in nine pieces: the body, two arms and two forearms, two thighs and two shins.
 * Each arm and thigh turns about its shoulder or hip, and each forearm / shin turns about its
 * elbow or knee. A positive turn about X moves the hand or foot forward (-Z).
 * With every turn at zero the person stands in the model's natural relaxed pose.
 */
class HumanPose(val instance: ModelInstance) {
    private val armL: Node = instance.getNode(ARM_L)
    private val armR: Node = instance.getNode(ARM_R)
    private val foreL: Node = instance.getNode(FORE_L)
    private val foreR: Node = instance.getNode(FORE_R)
    private val legL: Node = instance.getNode(LEG_L)
    private val legR: Node = instance.getNode(LEG_R)
    private val shinL: Node = instance.getNode(SHIN_L)
    private val shinR: Node = instance.getNode(SHIN_R)

    /**
     * [phase] is the walking cycle in radians, [amount] is 0 for standing still, 1 for a walk and
     * up to 1.3 for a run. [airborne] is the jump pose. Call instance.calculateTransforms() after.
     */
    fun update(phase: Float, amount: Float, airborne: Boolean) {
        if (airborne) {
            legL.rotation.set(Vector3.X, 24f)
            legR.rotation.set(Vector3.X, -10f)
            shinL.rotation.set(Vector3.X, -40f)
            shinR.rotation.set(Vector3.X, -24f)
            armL.rotation.set(Vector3.X, 40f)
            armR.rotation.set(Vector3.X, 40f)
            foreL.rotation.set(Vector3.X, 22f)
            foreR.rotation.set(Vector3.X, 22f)
            return
        }
        val a = min(amount, 1.3f)
        val swing = sin(phase) * LEG_SWING * a
        val lift = cos(phase)
        legL.rotation.set(Vector3.X, swing)
        legR.rotation.set(Vector3.X, -swing)
        // The knee bends most while the leg swings forward.
        shinL.rotation.set(Vector3.X, -max(0f, lift) * KNEE * a)
        shinR.rotation.set(Vector3.X, -max(0f, -lift) * KNEE * a)
        // Arms swing against the legs; the elbow bends when the arm is forward.
        armL.rotation.set(Vector3.X, -swing * 0.8f)
        armR.rotation.set(Vector3.X, swing * 0.8f)
        foreL.rotation.set(Vector3.X, a * (10f + ELBOW * max(0f, -sin(phase))))
        foreR.rotation.set(Vector3.X, a * (10f + ELBOW * max(0f, sin(phase))))
    }

    companion object {
        const val BODY = "body"
        const val ARM_L = "armL"
        const val FORE_L = "foreL"
        const val ARM_R = "armR"
        const val FORE_R = "foreR"
        const val LEG_L = "legL"
        const val SHIN_L = "shinL"
        const val LEG_R = "legR"
        const val SHIN_R = "shinR"

        /** The pieces in human.bin, in file order. A forearm or shin hangs from its arm or thigh. */
        val PART_IDS = listOf(BODY, ARM_L, FORE_L, ARM_R, FORE_R, LEG_L, SHIN_L, LEG_R, SHIN_R)

        private const val LEG_SWING = 32f   // degrees each way at a walk
        private const val KNEE = 34f
        private const val ELBOW = 24f
    }
}

/** Makes people who share one model look a little different: a colour wash and a slightly different height. */
object PeopleLook {
    private val washes = arrayOf(
        floatArrayOf(1f, 1f, 1f),
        floatArrayOf(1f, 0.86f, 0.80f),
        floatArrayOf(0.82f, 0.90f, 1f),
        floatArrayOf(0.88f, 1f, 0.88f),
        floatArrayOf(1f, 0.95f, 0.78f),
        floatArrayOf(0.88f, 0.80f, 1f),
        floatArrayOf(0.95f, 0.78f, 0.78f),
        floatArrayOf(0.80f, 0.80f, 0.80f)
    )

    /** Tints this person's own copy of the material. Returns the height scale to draw them at. */
    fun dress(instance: ModelInstance, rnd: Random): Float {
        val w = washes[rnd.nextInt(washes.size)]
        for (m in instance.materials) m.set(ColorAttribute.createDiffuse(w[0], w[1], w[2], 1f))
        return 0.94f + rnd.nextFloat() * 0.10f
    }
}
