package com.westly.lagosdrift

import com.badlogic.gdx.graphics.g3d.Model

/**
 * The player's car: a 2006 Toyota Camry, baked into the game's own piece format (see BakedModel.loadParts).
 * Model by tonielpro520 (CC BY 4.0), see assets/vehicles/camry/CREDITS.md. The nose points along -Z,
 * the wheels touch y = 0, and the four wheels are pieces of their own so they can steer and roll.
 */
object CamryModelFactory {
    const val WHEEL_FL = "wheel_fl"
    const val WHEEL_FR = "wheel_fr"
    const val WHEEL_RL = "wheel_rl"
    const val WHEEL_RR = "wheel_rr"

    /** Text that must be shown to the player somewhere on screen (the licence asks for it). */
    const val CREDIT = "Car: 2006 Toyota Camry by tonielpro520 (CC BY 4.0)"

    // The body is cut into three pieces only to keep each one small; they never move.
    private val IDS = listOf("body0", "body1", "body2", WHEEL_FL, WHEEL_FR, WHEEL_RL, WHEEL_RR)

    fun load(): Model = BakedModel.loadParts(
        "vehicles/camry/camry.bin", "vehicles/camry/camry.png", IDS, false
    )
}
