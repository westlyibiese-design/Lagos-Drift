package com.westly.lagosdrift

import com.badlogic.gdx.graphics.g3d.Model

/**
 * The inside of the car, drawn instead of the body when the camera is the hood (driver's) view: a dashboard, steering
 * wheel, seats, door panels, roof and glass you can see through. It was baked from the supplied model into
 * app/src/main/assets/vehicles/interior/ (see CREDITS.md there), cut down to the cabin and simplified so it runs on
 * mid-range phones, and fitted to the Camry. The Silverado uses the same model, a little taller and longer.
 */
object InteriorModelFactory {
    /** Text that must be shown to the player somewhere on screen (the licence asks for it). */
    const val CREDIT = "Interior: Autonomous GT Car Interior Design by benlockett (CC BY 4.0)"

    fun load(): Model = BakedCar.load("vehicles/interior/interior.car", "vehicles/interior")
}
