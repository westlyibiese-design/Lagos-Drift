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
 * Phase 5: drive the 6 GRIND around Ikoyi (south) and Ikorodu (north), get out of the car and walk
 * as your own character, get back in, and share the road with other cars and danfo buses.
 * The world is built in 100 m chunks around whoever the camera follows (the car, or the walker).
 */
class LagosDriftGame : ApplicationAdapter() {
    private lateinit var camera: PerspectiveCamera
    private lateinit var chaseCamera: ChaseCamera
    private lateinit var modelBatch: ModelBatch
    private lateinit var environment: Environment
    private lateinit var groundModel: Model
    private lateinit var carModel: Model
    private lateinit var pedestrianModels: List<Model>
    private lateinit var playerModel: Model
    private lateinit var playerInstance: ModelInstance
    private lateinit var groundInstance: ModelInstance
    private lateinit var carInstance: ModelInstance
    private lateinit var crowd: PedestrianCrowd
    private lateinit var chunks: ChunkManager
    private lateinit var spriteBatch: SpriteBatch
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var font: BitmapFont

    private val glyphLayout = GlyphLayout()
    private val controller = CarController()
    private val player = PlayerController()
    private val traffic = TrafficManager()
    private val controls = TouchControls()
    private val mapRenderer = MapRenderer()
    private val mapView = MapView()

    private val copyrightText = "(c) Neribo Group"
    private var viewMode = ChaseCamera.MODE_NEAR
    private var viewLabelTimer = 0f
    private var mapOpen = false

    private var onFoot = false
    private val walkTarget = Vector3()
    private var hasWalkTarget = false
    private var walkTimer = 0f
    private var hintText = ""
    private var hintTimer = 0f
    private var clock = 0f
    private val focus = Vector3()
    private var focusYaw = 0f
    private val tmp = Vector3()

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
        playerModel = PlayerModelFactory.load()
        playerInstance = ModelInstance(playerModel)
        traffic.create()
        focus.set(controller.position)
        traffic.populate(focus, controller.position)

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
        clock += delta

        controls.mapMode = mapOpen
        controls.update()
        if (controls.mapTapped) {
            mapOpen = !mapOpen
            controls.mapMode = mapOpen
            if (mapOpen) mapView.open(focus.x, focus.z)
        }
        if (mapOpen) {
            mapView.update(delta, focus.x, focus.z) { x, y -> controls.hitsButton(x, y) }
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
        if (hintTimer > 0f) hintTimer -= delta

        // Get out of the car, or back in.
        if (controls.actionTapped && !mapOpen) {
            if (!onFoot) {
                if (kotlin.math.abs(controller.speed) < EXIT_MAX_SPEED) {
                    tmp.set(0f, 0f, -1f).rotate(Vector3.Y, controller.yawDegrees + 90f)
                    player.place(
                        controller.position.x + tmp.x * 2.4f,
                        controller.position.z + tmp.z * 2.4f,
                        controller.yawDegrees
                    )
                    TownCollision.resolvePoint(player.position, PLAYER_RADIUS)
                    onFoot = true
                    hasWalkTarget = false
                    chaseCamera.freeLook = true
                    controls.setOnFoot(true)
                    showHint("Stick = walk, JUMP = jump, double-tap = walk there. ENTER at your car")
                } else {
                    showHint("Slow down to get out")
                }
            } else {
                val dx = player.position.x - controller.position.x
                val dz = player.position.z - controller.position.z
                if (dx * dx + dz * dz <= ENTER_RANGE * ENTER_RANGE) {
                    onFoot = false
                    hasWalkTarget = false
                    chaseCamera.freeLook = false
                    controls.setOnFoot(false)
                } else {
                    showHint("Too far from your car")
                }
            }
        }

        // Zoom: pinch, or hold + / -. The map uses pinch for itself while it is open.
        var zoom = chaseCamera.zoom
        if (!mapOpen) zoom *= controls.pinchRatio
        if (controls.zoomInHeld) zoom *= 1f - 0.8f * delta
        if (controls.zoomOutHeld) zoom *= 1f + 0.8f * delta
        chaseCamera.zoom = zoom.coerceIn(0.5f, 2.5f)

        // The game pauses while the big map is open.
        if (!mapOpen) {
            if (onFoot) {
                // The car rolls to a stop by itself (no brake, so it never reverses).
                controller.update(delta, 0f, false, false)
                walkOnFoot(delta)
            } else {
                controller.update(delta, controls.steer, controls.throttle, controls.brake)
            }
            TownCollision.resolve(controller)
            traffic.collide(controller)
            if (onFoot) {
                TownCollision.resolvePoint(player.position, PLAYER_RADIUS)
                TownCollision.pushOutOfCar(player.position, PLAYER_RADIUS, controller.position, controller.yawDegrees)
                traffic.pushOut(player.position, PLAYER_RADIUS)
            }
        }

        if (onFoot) {
            focus.set(player.position)
            focusYaw = player.yawDegrees
        } else {
            focus.set(controller.position)
            focusYaw = controller.yawDegrees
        }

        if (!mapOpen) {
            traffic.update(delta, focus, controller.position, onFoot, player.position)
            crowd.update(delta, focus)
        }
        chunks.update(focus.x, focus.z)

        carInstance.transform.setToRotation(Vector3.Y, controller.yawDegrees)
            .setTranslation(controller.position.x, controller.position.y + controller.bounceHeight, controller.position.z)
        if (onFoot) player.applyTo(playerInstance, clock)

        val scaleTarget = if (onFoot) ON_FOOT_CAMERA_SCALE else 1f
        chaseCamera.distanceScale += (scaleTarget - chaseCamera.distanceScale) * minOf(1f, 4f * delta)
        val followSpeed = if (onFoot) player.speed else controller.speed
        chaseCamera.update(focus, focusYaw, followSpeed, delta, viewMode)

        Gdx.gl.glViewport(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        Gdx.gl.glClearColor(SKY_R, SKY_G, SKY_B, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        modelBatch.begin(camera)
        modelBatch.render(groundInstance, environment)
        chunks.render(modelBatch, environment, camera)
        crowd.render(modelBatch, environment, focus)
        traffic.render(modelBatch, environment, camera, focus)
        modelBatch.render(carInstance, environment)
        if (onFoot && viewMode != ChaseCamera.MODE_HOOD) modelBatch.render(playerInstance, environment)
        modelBatch.end()

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        val parked: Vector3? = if (onFoot) controller.position else null
        if (mapOpen) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            mapRenderer.drawFull(shapeRenderer, mapView, focus, focusYaw, parked)
            mapView.drawShapes(shapeRenderer)
            shapeRenderer.end()
        } else {
            // The small map is cut off at its edges with a scissor box.
            Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST)
            Gdx.gl.glScissor(miniX.toInt(), miniY.toInt(), miniSize.toInt(), miniSize.toInt())
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            mapRenderer.drawMini(shapeRenderer, focus, focusYaw, miniX, miniY, miniSize, parked)
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
            val speedText = if (onFoot) "on foot" else "${controller.speedKmh.toInt()} km/h"
            glyphLayout.setText(font, speedText)
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

            if (hintTimer > 0f) {
                glyphLayout.setText(font, hintText)
                font.draw(
                    spriteBatch, glyphLayout,
                    screenWidth / 2f - glyphLayout.width / 2f,
                    screenHeight * 0.62f
                )
            }

            if (viewLabelTimer > 0f) {
                glyphLayout.setText(font, "Camera: ${ChaseCamera.MODE_NAMES[viewMode]}")
                font.draw(spriteBatch, glyphLayout, screenWidth / 2f - glyphLayout.width / 2f, screenHeight - margin)
            }
        }
        spriteBatch.end()
    }

    /** Reads the walking stick (or a double-tapped spot) and moves the player. */
    private fun walkOnFoot(delta: Float) {
        // Look around by dragging on empty screen.
        if (controls.lookDragX != 0f) chaseCamera.addYaw(controls.lookDragX * 0.2f)

        // Double-tap the ground to walk there.
        if (controls.doubleTapped) {
            val ray = camera.getPickRay(controls.doubleTapX, screenHeight - controls.doubleTapY)
            if (ray.direction.y < -0.01f) {
                val t = -ray.origin.y / ray.direction.y
                val tx = ray.origin.x + ray.direction.x * t
                val tz = ray.origin.z + ray.direction.z * t
                val dx = tx - player.position.x
                val dz = tz - player.position.z
                if (dx * dx + dz * dz < WALK_TO_MAX * WALK_TO_MAX) {
                    walkTarget.set(tx, 0f, tz)
                    hasWalkTarget = true
                    walkTimer = 15f
                }
            }
        }

        var desiredYaw = player.yawDegrees
        var intensity = 0f

        if (controls.joystickActive && (controls.moveX != 0f || controls.moveY != 0f)) {
            hasWalkTarget = false
            // The stick is read relative to where the camera looks.
            var fx = camera.direction.x
            var fz = camera.direction.z
            val flen = kotlin.math.sqrt(fx * fx + fz * fz)
            if (flen > 0.0001f) {
                fx /= flen
                fz /= flen
                val rx = -fz
                val rz = fx
                val dx = fx * controls.moveY + rx * controls.moveX
                val dz = fz * controls.moveY + rz * controls.moveX
                desiredYaw = Math.toDegrees(kotlin.math.atan2(-dx.toDouble(), -dz.toDouble())).toFloat()
                intensity = kotlin.math.sqrt(controls.moveX * controls.moveX + controls.moveY * controls.moveY).coerceIn(0f, 1f)
            }
        } else if (hasWalkTarget) {
            walkTimer -= delta
            val dx = walkTarget.x - player.position.x
            val dz = walkTarget.z - player.position.z
            val dist = kotlin.math.sqrt(dx * dx + dz * dz)
            if (dist < 0.6f || walkTimer <= 0f) {
                hasWalkTarget = false
            } else {
                desiredYaw = Math.toDegrees(kotlin.math.atan2(-dx.toDouble(), -dz.toDouble())).toFloat()
                intensity = if (dist > 14f) 1f else 0.7f
            }
        }

        player.update(delta, desiredYaw, intensity, controls.jumpTapped)
    }

    private fun showHint(text: String) {
        hintText = text
        hintTimer = 2.5f
    }

    /** For example "IKORODU - broken road". */
    private fun placeText(): String {
        val district = Districts.NAMES[Districts.at(focus.z)]
        val surface = if (onFoot) RoadSurface.surfaceAt(focus.x, focus.z) else controller.surface
        val road = when (surface) {
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
        playerModel.dispose()
        traffic.dispose()
        for (m in pedestrianModels) m.dispose()
        spriteBatch.dispose()
        shapeRenderer.dispose()
        font.dispose()
    }

    private companion object {
        const val FAR_DISTANCE = 430f
        const val EXIT_MAX_SPEED = 3f
        const val ENTER_RANGE = 4.5f
        const val PLAYER_RADIUS = 0.4f
        const val WALK_TO_MAX = 80f
        const val ON_FOOT_CAMERA_SCALE = 0.42f
        const val SKY_R = 0.53f
        const val SKY_G = 0.81f
        const val SKY_B = 0.92f
    }
}
