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
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Align
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lagos Drift: one world, the Glen Canyon road with the Futatsugoya tunnel. You drive a 2006 Toyota
 * Camry (steering and rolling wheels), or get out and walk.
 *
 * The road is loaded and drawn by RoadWorld, and RoadGround knows its surface: the car follows it with
 * pitch and roll, the walking player stands on it, and neither can leave the road.
 */
class LagosDriftGame : ApplicationAdapter() {
    private lateinit var camera: PerspectiveCamera
    private lateinit var chaseCamera: ChaseCamera
    private lateinit var modelBatch: ModelBatch
    private lateinit var environment: Environment
    private lateinit var carModel: Model
    private lateinit var roads: RoadWorld
    private lateinit var traffic: TrafficManager
    private var danfoModel: Model? = null
    private lateinit var playerModel: Model
    private lateinit var playerInstance: ModelInstance
    private lateinit var carInstance: ModelInstance
    private lateinit var spriteBatch: SpriteBatch
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var font: BitmapFont

    private val sun = DirectionalLight()
    private var sky: SkyDome? = null

    /** If the Camry cannot be loaded there is no other car: the exception text is shown and the game stops. */
    private var fatalError: String? = null
    private var roadReady = false
    private var savedCarY = Float.NaN
    private var savedPlayerY = Float.NaN

    private val wheelSteer = Quaternion()
    private val wheelRoll = Quaternion()

    private val glyphLayout = GlyphLayout()
    private val grind = CarController()
    private val parkedMarkers = ArrayList<Vector3>()
    private val player = PlayerController()
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

    // The Camry's get-out / get-in walk. 0 = none, 1 = getting out, 2 = getting in.
    private var doorSequence = 0
    private var doorTimer = 0f
    private val seqFrom = Vector3()
    private val seqTo = Vector3()
    private var walkTimer = 0f
    private var hintText = ""
    private var hintTimer = 0f
    private var clock = 0f
    private var saveTimer = 0f
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

        spriteBatch = SpriteBatch()
        shapeRenderer = ShapeRenderer()
        font = BitmapFont()
        font.region.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        font.setColor(1f, 1f, 1f, 0.8f)

        // Colours and sun direction come from the desert HDRI sky.
        environment = Environment()
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.62f, 0.58f, 0.52f, 1f))
        environment.set(ColorAttribute(ColorAttribute.Fog, SKY_R, SKY_G, SKY_B, 1f))
        sun.set(1.0f, 0.92f, 0.78f, 0.42f, -0.71f, -0.57f)
        environment.add(sun)
        environment.add(DirectionalLight().set(0.25f, 0.25f, 0.30f, 0.6f, -0.3f, 0.5f))

        modelBatch = ModelBatch()

        // The player's car is the Camry, and there is no other car to fall back on.
        try {
            carModel = CamryModelFactory.load()
            carInstance = ModelInstance(carModel)
            playerModel = PlayerModelFactory.load()
            playerInstance = ModelInstance(playerModel)
            sky = SkyDome("sky/sky_desert.jpg")
        } catch (e: Exception) {
            fatalError = "Could not load the game: $e"
            return
        }

        // Carry on where the last game stopped, if there is one; otherwise start at the spawn point.
        val resumed = restoreGame()
        if (!resumed) grind.place(SPAWN_X, SPAWN_Z, SPAWN_YAW)
        focus.set(if (onFoot) player.position else grind.position)
        roads = RoadWorld(SKY_R, SKY_G, SKY_B)

        // Passer-by traffic: Danfos and Camrys. If the Danfo cannot be loaded, the exception text is shown
        // and the traffic is Camrys only.
        try {
            danfoModel = TrafficModelFactory.loadDanfo()
        } catch (e: Exception) {
            showHint("Danfo traffic car could not be loaded: $e", 30f)
        }
        traffic = TrafficManager(carModel, danfoModel)
        if (resumed) showHint("Welcome back - carrying on where you stopped", 5f)
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
        if (fatalError != null) {
            renderFatalError()
            return
        }
        val delta = Gdx.graphics.deltaTime
        if (!roadReady) {
            // Loading: read the road around the start before the first frame of the game.
            roads.update(delta, focus.x, focus.z, LOADING_BUDGET_MS)
            if (roads.isReady(focus.x, focus.z)) {
                roadReady = true
                settleOnGround()
            }
            renderLoading()
            return
        }
        clock += delta

        // Keep the saved game fresh in case the phone kills the game without warning.
        saveTimer += delta
        if (saveTimer >= SAVE_EVERY_SECONDS) {
            saveTimer = 0f
            saveGame()
        }

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

        // Get out of the Camry, or back in.
        if (controls.actionTapped && !mapOpen) {
            if (!onFoot) {
                if (doorSequence != 0) {
                    // Already busy with the door.
                } else if (abs(grind.speed) < EXIT_MAX_SPEED) {
                    // Step out of the seat and walk round the door.
                    tmp.set(0f, 0f, -1f).rotate(Vector3.Y, grind.yawDegrees + 90f)
                    seqFrom.set(grind.position.x + tmp.x * SEAT_SIDE, grind.position.y, grind.position.z + tmp.z * SEAT_SIDE)
                    seqTo.set(grind.position.x + tmp.x * CAR_EXIT_SIDE, grind.position.y, grind.position.z + tmp.z * CAR_EXIT_SIDE)
                    player.place(seqFrom.x, seqFrom.z, grind.yawDegrees + 90f)
                    player.position.y = grind.position.y
                    doorSequence = 1
                    doorTimer = 0f
                    onFoot = true
                    hasWalkTarget = false
                    chaseCamera.freeLook = true
                    controls.setOnFoot(true)
                    showHint("Stick = walk, JUMP = jump, double-tap = walk there. ENTER next to the car")
                } else {
                    showHint("Slow down to get out")
                }
            } else {
                val dx = player.position.x - grind.position.x
                val dz = player.position.z - grind.position.z
                if (doorSequence != 0) {
                    // Already busy with the door.
                } else if (dx * dx + dz * dz <= ENTER_RANGE * ENTER_RANGE) {
                    // Walk to the seat.
                    tmp.set(0f, 0f, -1f).rotate(Vector3.Y, grind.yawDegrees + 90f)
                    seqFrom.set(player.position)
                    seqTo.set(grind.position.x + tmp.x * SEAT_SIDE, grind.position.y, grind.position.z + tmp.z * SEAT_SIDE)
                    doorSequence = 2
                    doorTimer = 0f
                    hasWalkTarget = false
                } else {
                    showHint("Too far from the car")
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
            if (!onFoot && doorSequence == 0) {
                grind.update(delta, controls.steer, controls.throttle, controls.brake)
            } else if (doorSequence != 0) {
                grind.update(delta, 0f, false, true)
            } else {
                grind.update(delta, 0f, false, false)
            }
            if (onFoot && doorSequence != 0) {
                runDoorSequence(delta)
            } else if (onFoot) {
                walkOnFoot(delta)
                pushPlayerOutOfCar()
            }

            // Traffic drives about, and bumps the car (and the walking player) but is never driven.
            val subject = if (onFoot) player.position else grind.position
            traffic.update(delta, subject.x, subject.z, grind.position.x, grind.position.z, grind.yawDegrees, grind.speed)
            traffic.collide(grind)
            if (onFoot && doorSequence == 0) traffic.pushOut(player.position, PLAYER_RADIUS)
        }

        if (onFoot) {
            focus.set(player.position)
            focusYaw = player.yawDegrees
        } else {
            focus.set(grind.position)
            focusYaw = grind.yawDegrees
        }

        roads.update(delta, focus.x, focus.z, STREAM_BUDGET_MS)

        // The car leans with the road: heading, then nose up/down, then side to side.
        carInstance.transform.setToRotation(Vector3.Y, grind.yawDegrees)
            .rotate(Vector3.X, grind.pitchDegrees)
            .rotate(Vector3.Z, grind.rollDegrees)
            .setTranslation(grind.position.x, grind.position.y, grind.position.z)
        updateWheels()
        carInstance.calculateTransforms()
        if (onFoot) player.applyTo(playerInstance, clock)

        val scaleTarget = if (onFoot) ON_FOOT_CAMERA_SCALE else 1f
        chaseCamera.distanceScale += (scaleTarget - chaseCamera.distanceScale) * minOf(1f, 4f * delta)
        val followSpeed = if (onFoot) player.speed else grind.speed
        chaseCamera.tunnelLimit = RoadGround.inTunnel(focus.x, focus.z)
        chaseCamera.update(focus, focusYaw, followSpeed, delta, viewMode)

        Gdx.gl.glViewport(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        Gdx.gl.glClearColor(SKY_R, SKY_G, SKY_B, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        val skyNow = sky
        if (skyNow != null) {
            modelBatch.begin(camera)
            skyNow.render(modelBatch, camera)
            modelBatch.end()
        }

        modelBatch.begin(camera)
        roads.render(modelBatch, camera)
        traffic.render(modelBatch, environment, camera)
        modelBatch.render(carInstance, environment)
        if (onFoot && viewMode != ChaseCamera.MODE_HOOD) modelBatch.render(playerInstance, environment)
        modelBatch.end()

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        // Your Camry is marked on the maps while you walk.
        parkedMarkers.clear()
        if (onFoot) parkedMarkers.add(grind.position)
        val parked: List<Vector3> = parkedMarkers
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
            val speedText = if (onFoot) "on foot" else "${grind.speedKmh.toInt()} km/h"
            glyphLayout.setText(font, speedText)
            font.draw(spriteBatch, glyphLayout, screenWidth / 2f - glyphLayout.width / 2f, margin + glyphLayout.height)

            glyphLayout.setText(font, copyrightText)
            font.draw(spriteBatch, glyphLayout, screenWidth - safeRight - margin - glyphLayout.width, screenHeight - margin)

            // The licences ask for the authors to be named. The lines are always on screen.
            font.data.setScale(textScale * 0.7f)
            font.setColor(1f, 1f, 1f, 0.6f)
            creditLine(CamryModelFactory.CREDIT, 2.6f)
            creditLine(ROAD_CREDIT, 4.2f)
            creditLine(TUNNEL_CREDIT, 5.8f)
            font.data.setScale(textScale)
            font.setColor(1f, 1f, 1f, 0.8f)

            // Where you are.
            glyphLayout.setText(font, placeText())
            font.draw(
                spriteBatch, glyphLayout,
                screenWidth / 2f - glyphLayout.width / 2f,
                margin + glyphLayout.height * 3f
            )

            // A road file that failed to load: the exact exception text, so it can be copied back.
            val roadError = roads.errorText
            if (roadError != null) {
                font.setColor(1f, 0.5f, 0.5f, 1f)
                font.draw(
                    spriteBatch, roadError, screenWidth * 0.1f, screenHeight * 0.55f,
                    screenWidth * 0.8f, Align.center, true
                )
                font.setColor(1f, 1f, 1f, 0.8f)
            }

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

    /** One small credit line under the copyright, [step] line heights down from the top, right-aligned. */
    private fun creditLine(text: String, step: Float) {
        glyphLayout.setText(font, text)
        font.draw(
            spriteBatch, glyphLayout,
            screenWidth - safeRight - margin - glyphLayout.width,
            screenHeight - margin - glyphLayout.height * step
        )
    }

    /** The start-up screen while the road around the start is read. */
    private fun renderLoading() {
        Gdx.gl.glViewport(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        Gdx.gl.glClearColor(SKY_R, SKY_G, SKY_B, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        spriteBatch.begin()
        font.setColor(0.15f, 0.15f, 0.15f, 1f)
        val percent = (roads.readyProgress(focus.x, focus.z) * 100f).toInt()
        glyphLayout.setText(font, "Loading road... $percent%")
        font.draw(
            spriteBatch, glyphLayout,
            screenWidth / 2f - glyphLayout.width / 2f, screenHeight / 2f + glyphLayout.height / 2f
        )
        val roadError = roads.errorText
        if (roadError != null) {
            font.setColor(0.7f, 0.05f, 0.05f, 1f)
            font.draw(
                spriteBatch, roadError, screenWidth * 0.1f, screenHeight * 0.4f,
                screenWidth * 0.8f, Align.center, true
            )
        }
        font.setColor(1f, 1f, 1f, 0.8f)
        spriteBatch.end()
    }

    /** Shows the exact exception text, so it can be copied back, and nothing else. */
    private fun renderFatalError() {
        Gdx.gl.glViewport(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.07f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        spriteBatch.begin()
        font.setColor(1f, 1f, 1f, 1f)
        font.draw(
            spriteBatch, fatalError ?: "", margin, screenHeight - margin,
            screenWidth - margin * 2f, Align.left, true
        )
        spriteBatch.end()
    }

    /** Plays the walk out of, or into, the Camry. */
    private fun runDoorSequence(delta: Float) {
        doorTimer += delta
        val moveStart = DOOR_TIME
        val moveEnd = DOOR_TIME + DOOR_WALK_TIME
        val t = ((doorTimer - moveStart) / DOOR_WALK_TIME).coerceIn(0f, 1f)
        val walking = doorTimer >= moveStart && doorTimer < moveEnd

        // Face the way we are walking (out: away from the car; in: towards it).
        val faceYaw = grind.yawDegrees + if (doorSequence == 1) 90f else -90f
        player.update(delta, faceYaw, if (walking) 0.4f else 0f, false)
        player.position.set(
            seqFrom.x + (seqTo.x - seqFrom.x) * t,
            player.position.y,
            seqFrom.z + (seqTo.z - seqFrom.z) * t
        )

        if (doorTimer >= moveEnd) {
            if (doorSequence == 2) {
                onFoot = false
                chaseCamera.freeLook = false
                controls.setOnFoot(false)
            }
            doorSequence = 0
        }
    }

    /** Reads the walking stick (or a double-tapped spot) and moves the player. */
    private fun walkOnFoot(delta: Float) {
        // Look around by dragging on empty screen.
        if (controls.lookDragX != 0f) chaseCamera.addYaw(controls.lookDragX * 0.2f)

        // Double-tap the ground to walk there.
        if (controls.doubleTapped) {
            val ray = camera.getPickRay(controls.doubleTapX, screenHeight - controls.doubleTapY)
            if (ray.direction.y < -0.01f) {
                val t = (player.position.y - ray.origin.y) / ray.direction.y
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
            val flen = sqrt(fx * fx + fz * fz)
            if (flen > 0.0001f) {
                fx /= flen
                fz /= flen
                val rx = -fz
                val rz = fx
                val dx = fx * controls.moveY + rx * controls.moveX
                val dz = fz * controls.moveY + rz * controls.moveX
                desiredYaw = Math.toDegrees(kotlin.math.atan2(-dx.toDouble(), -dz.toDouble())).toFloat()
                intensity = sqrt(controls.moveX * controls.moveX + controls.moveY * controls.moveY).coerceIn(0f, 1f)
            }
        } else if (hasWalkTarget) {
            walkTimer -= delta
            val dx = walkTarget.x - player.position.x
            val dz = walkTarget.z - player.position.z
            val dist = sqrt(dx * dx + dz * dz)
            if (dist < 0.6f || walkTimer <= 0f) {
                hasWalkTarget = false
            } else {
                desiredYaw = Math.toDegrees(kotlin.math.atan2(-dx.toDouble(), -dz.toDouble())).toFloat()
                intensity = if (dist > 14f) 1f else 0.7f
            }
        }

        player.update(delta, desiredYaw, intensity, controls.jumpTapped)
    }

    /** Keeps the walking player out of the Camry (the car is two circles, one near each end). */
    private fun pushPlayerOutOfCar() {
        val rad = grind.yawDegrees * MathUtils.degreesToRadians
        val ox = -sin(rad) * CAR_OFFSET
        val oz = -cos(rad) * CAR_OFFSET
        pushOutOfCircle(grind.position.x + ox, grind.position.z + oz)
        pushOutOfCircle(grind.position.x - ox, grind.position.z - oz)
    }

    private fun pushOutOfCircle(cx: Float, cz: Float) {
        val ex = player.position.x - cx
        val ez = player.position.z - cz
        val limit = PLAYER_RADIUS + CAR_RADIUS
        val d2 = ex * ex + ez * ez
        if (d2 >= limit * limit) return
        val d = maxOf(sqrt(d2), 0.001f)
        player.position.x += ex / d * (limit - d)
        player.position.z += ez / d * (limit - d)
    }

    private fun showHint(text: String, seconds: Float = 2.5f) {
        hintText = text
        hintTimer = seconds
    }

    /** "GLEN CANYON ROAD" on the open road, "TUNNEL" inside the Futatsugoya tunnel. */
    private fun placeText(): String = if (RoadGround.inTunnel(focus.x, focus.z)) "TUNNEL" else "GLEN CANYON ROAD"

    /** Once the road around the start is loaded: puts the car (and the player, if walking) on the road. */
    private fun settleOnGround() {
        if (!grind.settleOnGround(savedCarY)) showHint("Could not find the road under the car", 8f)
        if (onFoot && !player.settleOnGround(savedPlayerY)) showHint("Could not find the road under you", 8f)
    }

    /** The game goes to the background (home button, another app, swiping it away): save where we are. */
    override fun pause() {
        saveGame()
    }

    /** Front wheels steer with the steering, all four wheels roll with the speed. */
    private fun updateWheels() {
        val roll = -grind.wheelRollDegrees
        val steer = -grind.steerAmount * MAX_WHEEL_STEER
        wheelRoll.set(Vector3.X, roll)
        carInstance.getNode(CamryModelFactory.WHEEL_RL)?.rotation?.set(wheelRoll)
        carInstance.getNode(CamryModelFactory.WHEEL_RR)?.rotation?.set(wheelRoll)
        wheelSteer.set(Vector3.Y, steer).mul(wheelRoll)
        carInstance.getNode(CamryModelFactory.WHEEL_FL)?.rotation?.set(wheelSteer)
        carInstance.getNode(CamryModelFactory.WHEEL_FR)?.rotation?.set(wheelSteer)
    }

    /**
     * Writes everything needed to carry on later: where the Camry and the player are, whether you are
     * walking or driving, and the camera. A door sequence that is half done is saved as if it had finished.
     */
    private fun saveGame() {
        if (fatalError != null || !::chaseCamera.isInitialized) return
        var px = player.position.x
        var pz = player.position.z
        var py = player.position.y
        var savedOnFoot = onFoot
        if (doorSequence == 1) {
            px = seqTo.x
            pz = seqTo.z
            py = seqTo.y
        } else if (doorSequence == 2) {
            savedOnFoot = false
        }
        val p = Gdx.app.getPreferences(SAVE_NAME)
        p.putInteger("version", SAVE_VERSION)
        p.putFloat("car_x", grind.position.x)
        p.putFloat("car_z", grind.position.z)
        p.putFloat("car_y", grind.position.y)
        p.putFloat("car_yaw", grind.yawDegrees)
        p.putFloat("player_x", px)
        p.putFloat("player_z", pz)
        p.putFloat("player_y", py)
        p.putFloat("player_yaw", player.yawDegrees)
        p.putBoolean("on_foot", savedOnFoot)
        p.putInteger("view", viewMode)
        p.putFloat("zoom", chaseCamera.zoom)
        p.flush()
    }

    /** Puts everything back the way [saveGame] left it. Returns false when there is no usable save. */
    private fun restoreGame(): Boolean {
        val p = Gdx.app.getPreferences(SAVE_NAME)
        if (p.getInteger("version", 0) != SAVE_VERSION) return false
        val values = floatArrayOf(
            p.getFloat("car_x"), p.getFloat("car_z"), p.getFloat("car_yaw"),
            p.getFloat("player_x"), p.getFloat("player_z"), p.getFloat("player_yaw")
        )
        for (v in values) if (v.isNaN() || v.isInfinite()) return false
        if (values[0] < WorldLimits.MIN_X || values[0] > WorldLimits.MAX_X ||
            values[1] < WorldLimits.MIN_Z || values[1] > WorldLimits.MAX_Z
        ) return false

        // The heights are read when the road is loaded; a saved height tells the levels apart on bridges.
        savedCarY = p.getFloat("car_y", Float.NaN)
        savedPlayerY = p.getFloat("player_y", Float.NaN)
        grind.place(values[0], values[1], values[2])
        onFoot = p.getBoolean("on_foot")
        doorSequence = 0
        hasWalkTarget = false
        if (onFoot) {
            player.place(values[3], values[4], values[5])
            chaseCamera.freeLook = true
            controls.setOnFoot(true)
            chaseCamera.distanceScale = ON_FOOT_CAMERA_SCALE
        } else {
            chaseCamera.freeLook = false
            controls.setOnFoot(false)
        }
        viewMode = p.getInteger("view", viewMode).coerceIn(0, ChaseCamera.MODE_COUNT - 1)
        chaseCamera.zoom = p.getFloat("zoom", 1f).coerceIn(0.5f, 2.5f)
        return true
    }

    override fun dispose() {
        saveGame()
        if (::modelBatch.isInitialized) modelBatch.dispose()
        sky?.dispose()
        if (::roads.isInitialized) roads.dispose()
        danfoModel?.dispose()
        if (::carModel.isInitialized) carModel.dispose()
        if (::playerModel.isInitialized) playerModel.dispose()
        if (::spriteBatch.isInitialized) spriteBatch.dispose()
        if (::shapeRenderer.isInitialized) shapeRenderer.dispose()
        if (::font.isInitialized) font.dispose()
    }

    private companion object {
        // Far enough to see the tunnel mouth and the dam from about a kilometre away. The haze is in the
        // sky colour, so the edge of the view fades out and chunks do not pop in.
        const val FAR_DISTANCE = 1100f
        const val LOADING_BUDGET_MS = 30    // milliseconds of loading work per frame on the start-up screen
        const val STREAM_BUDGET_MS = 6      // and while playing, so the game does not freeze
        const val SAVE_NAME = "lagosdrift_save"
        const val SAVE_VERSION = 2          // 1 was the old three-world game; those saves are ignored
        const val SAVE_EVERY_SECONDS = 5f
        const val EXIT_MAX_SPEED = 3f
        const val DOOR_TIME = 0.35f         // seconds before the player starts to walk
        const val DOOR_WALK_TIME = 0.6f     // seconds to step out or in
        const val SEAT_SIDE = 0.35f         // seat position, metres left of the car centre
        const val ENTER_RANGE = 4.5f
        const val CAR_EXIT_SIDE = 2.4f
        const val CAR_RADIUS = 1.1f
        const val CAR_OFFSET = 1.2f
        const val PLAYER_RADIUS = 0.4f
        const val WALK_TO_MAX = 80f
        const val ON_FOOT_CAMERA_SCALE = 0.42f
        const val MAX_WHEEL_STEER = 28f     // degrees the front wheels turn at full steering
        const val SKY_R = 0.83f
        const val SKY_G = 0.87f
        const val SKY_B = 0.885f

        // Where the Camry starts: on the straight part, 100 m before the join with the tunnel,
        // facing the tunnel (south, yaw -3.8 degrees).
        const val SPAWN_X = RoadGround.JOIN_X - 100f * RoadGround.TUNNEL_DIR_X
        const val SPAWN_Z = RoadGround.JOIN_Z - 100f * RoadGround.TUNNEL_DIR_Z
        const val SPAWN_YAW = -3.8f

        const val ROAD_CREDIT = "Road: Glen Canyon Dam by amogusstrikesback2 (CC BY 4.0)"
        const val TUNNEL_CREDIT = "Tunnel: Futatsugoya Tunnel by Fuji (CC BY 4.0)"
    }
}
