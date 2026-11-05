package com.westly.lagosdrift

/**
 * The outer edge of the one world (the Glen Canyon road and the Futatsugoya tunnel), in metres.
 * It is a safety clamp only, a little bigger than the road itself, so nobody can leave the map.
 */
object WorldLimits {
    const val MIN_X = -1000f
    const val MAX_X = 1000f
    const val MIN_Z = -2100f
    const val MAX_Z = 300f

    /**
     * Where a vehicle may be: the map area above plus the forest and the island past the tunnel.
     * (The map picture keeps using the numbers above, so it does not change size.)
     */
    const val PLAY_MIN_X = -1500f
    const val PLAY_MAX_X = 1000f
    const val PLAY_MIN_Z = -3300f
    const val PLAY_MAX_Z = 300f
}
