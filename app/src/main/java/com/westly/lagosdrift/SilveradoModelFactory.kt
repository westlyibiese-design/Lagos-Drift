package com.westly.lagosdrift

import com.badlogic.gdx.graphics.g3d.Model

/**
 * The 2018 Chevrolet Silverado Centennial Edition: a pick-up that you can drive, that also drives about as
 * traffic, and that is parked in the garage of your first house. It was baked from the supplied model into
 * app/src/main/assets/vehicles/silverado/ (silverado.car and its textures) in the same car format as the
 * Camry: the body, four wheels that can steer and roll, and the two front doors that swing open. The nose
 * points along -Z and the wheels touch y = 0. Model by amogusstrikesback2 (CC BY 4.0), see CREDITS.md there.
 */
object SilveradoModelFactory {
    const val WHEEL_FL = "wheel_fl"
    const val WHEEL_FR = "wheel_fr"
    const val WHEEL_RL = "wheel_rl"
    const val WHEEL_RR = "wheel_rr"

    /** Text that must be shown to the player somewhere on screen (the licence asks for it). */
    const val CREDIT = "Truck: 2018 Chevrolet Silverado by amogusstrikesback2 (CC BY 4.0)"

    /** The doors, and how far they swing open (degrees). */
    val DOORS = arrayOf("door_fl", "door_fr")
    const val DOOR_OPEN_DEGREES = 62f

    /** Half the distance between the axles and half the distance between the wheel centres (from the model). */
    const val HALF_WHEELBASE = 1.57f
    const val HALF_TRACK = 0.83f

    /** The truck is two circles for bumping, one near each end. */
    const val CAR_RADIUS = 1.25f
    const val CAR_OFFSET = 1.45f

    /** Where the driver's eyes are in the hood view (see CamryModelFactory). */
    const val HOOD_SIDE = -0.41f
    const val HOOD_HEIGHT = 1.53f
    const val HOOD_BACK = -0.155f

    /** The interior model is made for the Camry; the truck's cabin is taller and a bit longer and wider. */
    const val INTERIOR_SCALE_X = 1.05f
    const val INTERIOR_SCALE_Y = 1.17f
    const val INTERIOR_SCALE_Z = 1.10f
    const val INTERIOR_LIFT = 0.216f
    const val INTERIOR_SHIFT_Z = -0.21f

    fun load(): Model = BakedCar.load("vehicles/silverado/silverado.car", "vehicles/silverado")
}
