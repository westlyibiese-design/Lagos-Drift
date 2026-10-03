package com.westly.lagosdrift

/** Questions about the ground under a point: which kind of road is it, and is there a pothole. */
object RoadSurface {
    /** One of the TownLayout.SURFACE_ numbers; SURFACE_GROUND when off every road. */
    fun surfaceAt(x: Float, z: Float): Int {
        val chunk = TownLayout.chunkAt(TownLayout.cxOf(x), TownLayout.czOf(z)) ?: return TownLayout.SURFACE_GROUND
        var best = TownLayout.SURFACE_GROUND
        for (r in chunk.roads) {
            if (x >= r.x0 && x <= r.x1 && z >= r.z0 && z <= r.z1 && r.surface > best) best = r.surface
        }
        return best
    }

    /** The pothole under a wheel at (x, z), or null. [reach] is the wheel's own radius. */
    fun potholeAt(x: Float, z: Float, reach: Float): TownLayout.Pothole? {
        val cx = TownLayout.cxOf(x)
        val cz = TownLayout.czOf(z)
        for (dz in -1..1) {
            for (dx in -1..1) {
                val chunk = TownLayout.chunkAt(cx + dx, cz + dz) ?: continue
                for (p in chunk.potholes) {
                    val ex = x - p.x
                    val ez = z - p.z
                    val limit = p.r + reach
                    if (ex * ex + ez * ez < limit * limit) return p
                }
            }
        }
        return null
    }
}
