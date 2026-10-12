package com.westly.lagosdrift

import com.badlogic.gdx.graphics.g3d.Model

/**
 * The 2018 Chevrolet Silverado Centennial Edition: a pick-up that you can drive, that also drives about as
 * traffic, and that is parked in the garage of your first house. It was baked from the supplied model into
 * app/src/main/assets/vehicles/silverado/ (silverado.bin and silverado.jpg) in the same piece format as the
 * Camry: the body, and four wheels that can steer and roll. The nose points along -Z and the wheels touch y = 0.
 */
object SilveradoModelFactory {
    const val WHEEL_FL = "wheel_fl"
    const val WHEEL_FR = "wheel_fr"
    const val WHEEL_RL = "wheel_rl"
    const val WHEEL_RR = "wheel_rr"

    /** Half the distance between the axles and half the distance between the wheel centres (from the model). */
    const val HALF_WHEELBASE = 1.57f
    const val HALF_TRACK = 0.83f

    /** The truck is two circles for bumping, one near each end. */
    const val CAR_RADIUS = 1.25f
    const val CAR_OFFSET = 1.45f

    private val IDS = listOf("body", WHEEL_FL, WHEEL_FR, WHEEL_RL, WHEEL_RR)

    fun load(): Model = BakedModel.loadParts(
        "vehicles/silverado/silverado.bin", "vehicles/silverado/silverado.jpg", IDS, true
    )
}
