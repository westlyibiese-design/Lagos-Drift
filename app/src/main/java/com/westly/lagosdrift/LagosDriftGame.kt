package com.westly.lagosdrift

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3

/**
 * Phase 4: drive the 6 GRIND around a big world: Ikoyi (clean, wide roads, towers) in the south and
 * Ikorodu (broken roads, potholes, market stalls) in the north. The world is built in 100 m chunks
 * around the car. The map scrolls and zooms like a real pause-menu map.
 */
class LagosDriftGame : ApplicationAdapter() {
    private lateinit var camera: PerspectiveCamera
    private lateinit var chaseCamera: ChaseCamera
    private lateinit var modelBatch: ModelBatch
    private lateinit var environment: Environment
    private lateinit var groundModel: Model
    private lateinit var carModel: Model
    private lateinit var pedestrianModels: List<Model>
    private lateinit var groundInstance: ModelInstance
    private lateinit var carInstance: ModelInstance
    private lateinit var crowd: PedestrianCrowd
    private lateinit var chunks: ChunkManager
    private lateinit var spriteBatch: SpriteBatch
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var font: BitmapFont

    private val glyphLayout = GlyphLayout()
    private val controller = CarController()
    private val controls = TouchControls()
    private val mapRenderer = MapRenderer()
    private val mapView = MapView()

    private val copyrightText = "(c) Neribo Group"
    private var viewMode = ChaseCamera.MODE_NEAR
    private var viewLabelTimer = 0f
    private var mapOpen = false

    private var screenWidth = 1f
    private var screenHeight = 1f
    private var safeRight = 0f
    private var margin = 0f
    private var textScale = 1f
    private var miniX = 0f
    private var miniY = 0f
    private var miniSize = 0f

    override fun create() {
        val width = Gdx.graphics.width.toFloat()
        val height = Gdx.graphics.height.toFloat()

        camera = PerspectiveCamera(65f, width, height)
        camera.near = 1f
        camera.far = FAR_DISTANCE
        chaseCamera = ChaseCamera(camera)

        // Distance haze in the colour of the sky hides chunks appearing at the edge of the view.
        environment = Environment()
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.55f, 0.55f, 0.60f, 1f))
        environment.set(ColorAttribute(ColorAttribute.Fog, SKY_R, SKY_G, SKY_B, 1f))
        environment.add(DirectionalLight().set(0.90f, 0.88f, 0.80f, -0.6f, -1f, -0.4f))
        environment.add(DirectionalLight().set(0.25f, 0.25f, 0.30f, 0.6f, -0.3f, 0.5f))

        modelBatch = ModelBatch()
        groundModel = GroundModelFactory.build()
        carModel = CarModelFactory.build()
        pedestrianModels = PedestrianModelFactory.buildAll()
        groundInstance = ModelInstance(groundModel)
        carInstance = ModelInstance(carModel)
        crowd = PedestrianCrowd(pedestrianModels)

        chunks = ChunkManager()
        chunks.preload(controller.position.x, controller.position.z)

        spriteBatch = SpriteBatch()
        shapeRenderer = ShapeRenderer()
        font = BitmapFont()
        font.region.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        font.setColor(1f, 1f, 1f, 0.8f)
    }

    override fun resize(width: Int, height: Int) {
        screenWidth = width.toFloat()
        screenHeight = height.toFloat()
        camera.viewportWidth = screenWidth
        camera.viewportHeight = screenHeight
        camera.update()

        spriteBatch.projectionMatrix.setToOrtho2D(0f, 0f, screenWidth, screenHeight)
        shapeRenderer.projectionMatrix.setToOrtho2D(0f, 0f, screenWidth, screenHeight)
        shapeRenderer.updateMatrices()

        textScale = screenHeight / 900f
        font.data.setScale(textScale)
        margin = screenHeight * 0.03f

        // Keep buttons and text clear of the camera cutout when the phone has one.
        val safeLeft = Gdx.graphics.safeInsetLeft.toFloat()
        safeRight = Gdx.graphics.safeInsetRight.toFloat()
        controls.layout(width, height, safeLeft, safeRight)
        mapView.layout(screenWidth, screenHeight, safeLeft, safeRight)

        // Small map in the top-left corner.
        miniSize = screenHeight * 0.36f
        miniX = safeLeft + margin
        miniY = screenHeight - margin - miniSize
    }

    override fun render() {
        val delta = Gdx.graphics.deltaTime

        controls.mapMode = mapOpen
        controls.update()
        if (controls.mapTapped) {
            mapOpen = !mapOpen
            controls.mapMode = mapOpen
            if (mapOpen) mapView.open(controller.position.x, controller.position.z)
        }
        if (mapOpen) {
            mapView.update(delta, controller.position.x, controller.position.z) { x, y -> controls.hitsButton(x, y) }
            if (mapView.closeRequested) {
                mapOpen = false
                controls.mapMode = false
            }
        }

        if (controls.cameraTapped) {
            viewMode = (viewMode + 1) % ChaseCamera.MODE_COUNT
            viewLabelTimer = 1.8f
        }
        if (viewLabelTimer > 0f) viewLabelTimer -= delta

        // Zoom: pinch, or hold + / -. The map uses pinch for itself while it is open.
        var zoom = chaseCamera.zoom
        if (!mapOpen) zoom *= controls.pinchRatio
        if (controls.zoomInHeld) zoom *= 1f - 0.8f * delta
        if (controls.zoomOutHeld) zoom *= 1f + 0.8f * delta
        chaseCamera.zoom = zoom.coerceIn(0.5f, 2.5f)

        // The game pauses while the big map is open.
        if (!mapOpen) {
            controller.update(delta, controls.steer, controls.throttle, controls.brake)
            TownCollision.resolve(controller)
            crowd.update(delta, controller.position)
        }
        chunks.update(controller.position.x, controller.position.z)

        carInstance.transform.setToRotation(Vector3.Y, controller.yawDegrees)
            .setTranslation(controller.position.x, controller.position.y + controller.bounceHeight, controller.position.z)
        chaseCamera.update(controller.position, controller.yawDegrees, controller.speed, delta, viewMode)

        Gdx.gl.glViewport(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        Gdx.gl.glClearColor(SKY_R, SKY_G, SKY_B, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        modelBatch.begin(camera)
        modelBatch.render(groundInstance, environment)
        chunks.render(modelBatch, environment, camera)
        crowd.render(modelBatch, environment, controller.position)
        modelBatch.render(carInstance, environment)
        modelBatch.end()

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        if (mapOpen) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            mapRenderer.drawFull(shapeRenderer, mapView, controller.position, controller.yawDegrees)
            mapView.drawShapes(shapeRenderer)
            shapeRenderer.end()
        } else {
            // The small map is cut off at its edges with a scissor box.
            Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST)
            Gdx.gl.glScissor(miniX.toInt(), miniY.toInt(), miniSize.toInt(), miniSize.toInt())
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            mapRenderer.drawMini(shapeRenderer, controller.position, controller.yawDegrees, miniX, miniY, miniSize)
            shapeRenderer.end()
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)

            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            mapRenderer.drawFrame(shapeRenderer, miniX, miniY, miniSize)
            shapeRenderer.end()
        }

        // Touch buttons.
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        controls.drawShapes(shapeRenderer)
        shapeRenderer.end()

        // Text.
        spriteBatch.begin()
        controls.drawLabels(spriteBatch, font, glyphLayout, textScale)

        if (mapOpen) {
            mapView.drawLabels(spriteBatch, font, glyphLayout, textScale)
            glyphLayout.setText(font, "MAP   drag to scroll, pinch to zoom")
            font.draw(spriteBatch, glyphLayout, screenWidth / 2f - glyphLayout.width / 2f, screenHeight - margin)
        } else {
            glyphLayout.setText(font, "${controller.speedKmh.toInt()} km/h")
            font.draw(spriteBatch, glyphLayout, screenWidth / 2f - glyphLayout.width / 2f, margin + glyphLayout.height)

            glyphLayout.setText(font, copyrightText)
            font.draw(spriteBatch, glyphLayout, screenWidth - safeRight - margin - glyphLayout.width, screenHeight - margin)

            // Where you are and what the road is like.
            glyphLayout.setText(font, placeText())
            font.draw(
                spriteBatch, glyphLayout,
                screenWidth / 2f - glyphLayout.width / 2f,
                margin + glyphLayout.height * 3f
            )

            if (viewLabelTimer > 0f) {
                glyphLayout.setText(font, "Camera: ${ChaseCamera.MODE_NAMES[viewMode]}")
                font.draw(spriteBatch, glyphLayout, screenWidth / 2f - glyphLayout.width / 2f, screenHeight - margin)
            }
        }
        spriteBatch.end()
    }

    /** For example "IKORODU - broken road". */
    private fun placeText(): String {
        val district = Districts.NAMES[Districts.at(controller.position.z)]
        val road = when (controller.surface) {
            TownLayout.SURFACE_SMOOTH -> "smooth road"
            TownLayout.SURFACE_WORN -> "worn road"
            TownLayout.SURFACE_POTHOLED -> "broken road"
            TownLayout.SURFACE_DIRT -> "dirt road"
            else -> "off road"
        }
        return "$district - $road"
    }

    override fun dispose() {
        modelBatch.dispose()
        groundModel.dispose()
        chunks.dispose()
        carModel.dispose()
        for (m in pedestrianModels) m.dispose()
        spriteBatch.dispose()
        shapeRenderer.dispose()
        font.dispose()
    }

    private companion object {
        const val FAR_DISTANCE = 430f
        const val SKY_R = 0.53f
        const val SKY_G = 0.81f
        const val SKY_B = 0.92f
    }
}
