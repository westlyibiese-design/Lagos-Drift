package com.westly.lagosdrift

import com.badlogic.gdx.graphics.g3d.Model

/**
 * The Danfo bus that drives about as passer-by traffic: your own art in assets/traffic/danfo.bin
 * and danfo.jpg. It has no doors and no passengers; it is only another car on the road.
 * The nose points along -Z.
 */
object TrafficModelFactory {
    fun loadDanfo(): Model = BakedModel.load("traffic/danfo.bin", "traffic/danfo.jpg")
}
