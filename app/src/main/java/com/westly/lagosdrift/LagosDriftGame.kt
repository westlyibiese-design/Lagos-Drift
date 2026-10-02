package com.westly.lagosdrift

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.math.Vector3

/**
 * Phase 1: flat ground, one parked car, chase camera. No driving, input or missions yet.
 */
class LagosDriftGame : ApplicationAdapter() {
    private lateinit var camera: PerspectiveCamera
    private lateinit var chaseCamera: ChaseCamera
    private lateinit var modelBatch: ModelBatch
    private lateinit var environment: Environment
    private lateinit var groundModel: Model
    private lateinit var carModel: Model
    private lateinit var groundInstance: ModelInstance
    private lateinit var carInstance: ModelInstance
    private lateinit var spriteBatch: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var copyrightText: String

    private val carPosition = Vector3(0f, 0f, 0f)
    private var carYawDegrees = 0f

    override fun create() {
        val width = Gdx.graphics.width.toFloat()
        val height = Gdx.graphics.height.toFloat()

        camera = PerspectiveCamera(65f, width, height)
        camera.near = 1f
        camera.far = 400f
        chaseCamera = ChaseCamera(camera)

        environment = Environment()
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.55f, 0.55f, 0.60f, 1f))
        environment.add(DirectionalLight().set(0.90f, 0.88f, 0.80f, -0.6f, -1f, -0.4f))

        modelBatch = ModelBatch()
        groundModel = GroundModelFactory.build()
        carModel = CarModelFactory.build()
        groundInstance = ModelInstance(groundModel)
        carInstance = ModelInstance(carModel)

        spriteBatch = SpriteBatch()
        font = BitmapFont()
        font.setColor(1f, 1f, 1f, 0.8f)
        val symbol = if (font.data.hasGlyph('\u00A9')) "\u00A9" else "(c)"
        copyrightText = "$symbol Neribo Group"
    }

    override fun resize(width: Int, height: Int) {
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        camera.update()
        spriteBatch.projectionMatrix.setToOrtho2D(0f, 0f, width.toFloat(), height.toFloat())
        font.data.setScale(height / 900f)
    }

    override fun render() {
        carInstance.transform.setToRotation(Vector3.Y, carYawDegrees).setTranslation(carPosition)
        chaseCamera.update(carPosition, carYawDegrees)

        Gdx.gl.glViewport(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        Gdx.gl.glClearColor(0.53f, 0.81f, 0.92f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        modelBatch.begin(camera)
        modelBatch.render(groundInstance, environment)
        modelBatch.render(carInstance, environment)
        modelBatch.end()

        spriteBatch.begin()
        font.draw(spriteBatch, copyrightText, 16f, 16f + font.lineHeight)
        spriteBatch.end()
    }

    override fun dispose() {
        modelBatch.dispose()
        groundModel.dispose()
        carModel.dispose()
        spriteBatch.dispose()
        font.dispose()
    }
}
