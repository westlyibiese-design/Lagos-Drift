package com.westly.lagosdrift

import com.badlogic.gdx.graphics.g3d.Model

/**
 * Loads the human model (character-2): the player, the people walking on the pavements and the
 * people waiting at the bus stops all use this one model.
 *
 * It was baked into two files in app/src/main/assets/player/:
 *   human.bin - nine pieces (body, arms, forearms, thighs, shins) in the BakedModel rigged format.
 *               The feet are at y = 0 and the person faces -Z, like the car.
 *   human.jpg - the colour texture (1024 x 1024), all materials packed into one picture.
 * To swap the character later, bake a new model into the same two files and keep the piece names.
 */
object PlayerModelFactory {
    /** How tall the player is on screen, in metres. (The baked model is 1.811 units tall.) */
    const val HEIGHT = 1.78f
    private const val MODEL_HEIGHT = 1.811f

    /** Scale to apply to the model instance so it is [HEIGHT] metres tall. */
    const val SCALE = HEIGHT / MODEL_HEIGHT

    fun load(): Model = BakedModel.loadRigged("player/human.bin", "player/human.jpg", HumanPose.PART_IDS)
}
