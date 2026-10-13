package com.westly.lagosdrift

import com.badlogic.gdx.graphics.g3d.Model

/**
 * The player's car: a 2006 Toyota Camry, baked into the game's car format (see BakedCar). Model by tonielpro520
 * (CC BY 4.0), see assets/vehicles/camry/CREDITS.md. The nose points along -Z, the wheels touch y = 0, the four
 * wheels are nodes of their own so they can steer and roll, and the four doors are nodes with their hinge as pivot.
 */
object CamryModelFactory {
    const val WHEEL_FL = "wheel_fl"
    const val WHEEL_FR = "wheel_fr"
    const val WHEEL_RL = "wheel_rl"
    const val WHEEL_RR = "wheel_rr"

    /** Text that must be shown to the player somewhere on screen (the licence asks for it). */
    const val CREDIT = "Car: 2006 Toyota Camry by tonielpro520 (CC BY 4.0)"

    /** The doors, and how far they swing open (degrees). */
    val DOORS = arrayOf("door_fl", "door_fr", "door_rl", "door_rr")
    const val DOOR_OPEN_DEGREES = 62f

    /** Where the driver's eyes are in the hood view: metres left of the middle (negative), above the ground, behind the middle. */
    const val HOOD_SIDE = -0.39f
    const val HOOD_HEIGHT = 1.12f
    const val HOOD_BACK = 0.05f

    fun load(): Model = BakedCar.load("vehicles/camry/camry.car", "vehicles/camry")
}
