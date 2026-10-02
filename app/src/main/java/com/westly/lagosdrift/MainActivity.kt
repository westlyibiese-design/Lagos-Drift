package com.westly.lagosdrift

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration

class MainActivity : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw into the camera cutout area so there is no black strip on the side.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val params = window.attributes
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = params
        }

        val config = AndroidApplicationConfiguration()
        config.useImmersiveMode = true
        config.useAccelerometer = false
        config.useCompass = false
        config.numSamples = 2
        initialize(LagosDriftGame(), config)
    }
}
