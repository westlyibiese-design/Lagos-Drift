package com.westly.lagosdrift

import com.badlogic.gdx.graphics.g3d.Model

/**
 * Loads the player character: your own art (the Meshy chibi figure).
 *
 * The model was baked into two small files in app/src/main/assets/player/:
 *   player.bin  - three pieces (body, left leg, right leg) in the BakedModel parts format.
 *                 The feet are at y = 0 and the figure faces -Z, like the car.
 *   player.jpg  - the colour texture (1024 x 1024).
 * To swap the character later, bake a new model into the same two files; no code changes.
 */
object PlayerModelFactory {
    /** How tall the figure is on screen, in metres. (The baked model is 1.9 units tall.) */
    const val HEIGHT = 1.45f
    private const val MODEL_HEIGHT = 1.899f

    /** Scale to apply to the model instance so it is [HEIGHT] metres tall. */
    const val SCALE = HEIGHT / MODEL_HEIGHT

    /** The pieces in player.bin, in file order: the body and the two legs (hips are the pivots). */
    const val BODY = "body"
    const val LEG_A = "legA"
    const val LEG_B = "legB"

    fun load(): Model = BakedModel.loadParts(
        "player/player.bin", "player/player.jpg", listOf(BODY, LEG_A, LEG_B), doubleSided = true
    )
}
