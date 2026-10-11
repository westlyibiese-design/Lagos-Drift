package com.westly.lagosdrift

/**
 * The places you can drive. There are three worlds: Lagos (the original map, with Ikoyi in the south and
 * Ikorodu in the north), Neribo City (a new modern city with the player's house) and Neribo Desert.
 *
 * Inside Lagos, Ikoyi (south) is the rich, clean part with wide smooth roads and tall towers, and Ikorodu
 * (north) is the busy local town with narrow, broken roads. The border between them is the east-west
 * road at z = 0. The two new worlds each count as a single district.
 */
object Districts {
    const val IKOYI = 0
    const val IKORODU = 1
    const val DESERT = 2
    const val CITY = 3

    const val BORDER_Z = 0f

    const val WORLD_LAGOS = 0
    const val WORLD_CITY = 1
    const val WORLD_DESERT = 2
    const val WORLD_COUNT = 3

    val NAMES = arrayOf("IKOYI", "IKORODU")
    val WORLD_NAMES = arrayOf("LAGOS", "NERIBO CITY", "NERIBO DESERT")

    /** Which world the layout is currently built for. TownLayout sets this. */
    var world = WORLD_LAGOS

    fun at(z: Float): Int = when (world) {
        WORLD_CITY -> CITY
        WORLD_DESERT -> DESERT
        else -> if (z > BORDER_Z) IKOYI else IKORODU
    }

    /** Clean, modern districts: Ikoyi and Neribo City. */
    fun isModern(district: Int): Boolean = district == IKOYI || district == CITY

    /** The name shown on the screen for the place at z. */
    fun nameAt(z: Float): String = when (world) {
        WORLD_CITY -> WORLD_NAMES[WORLD_CITY]
        WORLD_DESERT -> WORLD_NAMES[WORLD_DESERT]
        else -> NAMES[at(z)]
    }
}
