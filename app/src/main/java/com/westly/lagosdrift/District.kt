package com.westly.lagosdrift

/**
 * The two districts of the map. Ikoyi (south) is the rich, clean part of Lagos with wide smooth
 * roads and tall towers. Ikorodu (north) is the busy local town with narrow, broken roads.
 * The border between them is the east-west road at z = 0.
 */
object Districts {
    const val IKOYI = 0
    const val IKORODU = 1

    const val BORDER_Z = 0f

    val NAMES = arrayOf("IKOYI", "IKORODU")

    fun at(z: Float): Int = if (z > BORDER_Z) IKOYI else IKORODU
}
