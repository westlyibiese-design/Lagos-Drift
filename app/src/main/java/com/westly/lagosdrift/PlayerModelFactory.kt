package com.westly.lagosdrift

import com.badlogic.gdx.graphics.g3d.Model

/**
 * Loads the people of the game. All of them were baked from the supplied models into app/src/main/assets/player/:
 *   hero.bin / hero.jpg    - the player (the man in the suit): nine pieces (body, arms, forearms, thighs, shins)
 *                            in the BakedModel rigged format, feet at y = 0, facing -Z, 1.811 units tall.
 *   human.bin / human.jpg  - the old player character, now the bodyguard at the gate of each house.
 *   boy.bin / boy.png      - a passer-by with moving arms and legs.
 *   chibi.bin / chibi.jpg  - a passer-by in one piece (he sways and bobs as he walks).
 */
object PlayerModelFactory {
    /** How tall the player is on screen, in metres. (The baked models are 1.811 units tall.) */
    const val HEIGHT = 1.78f
    private const val MODEL_HEIGHT = 1.811f

    /** Scale to apply to the model instance so it is [HEIGHT] metres tall. */
    const val SCALE = HEIGHT / MODEL_HEIGHT

    /** The passers-by are not as tall as the player. */
    const val BOY_SCALE = 0.86f
    const val CHIBI_SCALE = 0.80f

    /** The player. */
    fun load(): Model = BakedModel.loadRigged("player/hero.bin", "player/hero.jpg", HumanPose.PART_IDS)

    /** The bodyguard (the old player character). */
    fun loadGuard(): Model = BakedModel.loadRigged("player/human.bin", "player/human.jpg", HumanPose.PART_IDS)

    fun loadBoy(): Model = BakedModel.loadRigged("player/boy.bin", "player/boy.png", HumanPose.PART_IDS)

    fun loadChibi(): Model = BakedModel.load("player/chibi.bin", "player/chibi.jpg")
}
