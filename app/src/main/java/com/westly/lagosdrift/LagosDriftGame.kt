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
 * Phase 6: drive the 6 GRIND around Ikoyi (south) and Ikorodu (north), get out and walk as your own
 * character, or drive your own danfo in Ikorodu: stop at the bus stops, take passengers, drop them
 * at their stop and earn fares. Other cars and danfos share the road.
 * The world is built in 100 m chunks around whoever the camera follows (a vehicle, or the walker).
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
    private lateinit var danfoModel: Model
    private lateinit var danfoInstance: ModelInstance
    private lateinit var job: DanfoJob
    private lateinit var crowd: PedestrianCrowd
    private lateinit var chunks: ChunkManager
    private lateinit var spriteBatch: SpriteBatch
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var font: BitmapFont

    private val glyphLayout = GlyphLayout()
    private val grind = CarController()
    private val danfo = CarController(topSpeed = 22f, accel = 6f, turnRate = 62f)

    /** The vehicle you are driving, or the one you drove last while you walk. */
    private var controller: CarController = grind
    private val vehiclePositions = listOf(grind.position, danfo.position)
    private val parkedMarkers = ArrayList<Vector3>()
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

    // The 6 GRIND's driver door and the get-out / get-in walk. 0 = none, 1 = getting out, 2 = getting in.
    private var doorSequence = 0
    private var doorTimer = 0f
    private var doorAmount = 0f
    private var doorTarget = 0f
    private var doorHold = 0f
    private val seqFrom = Vector3()
    private val seqTo = Vector3()
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
        carModel = CarModelFactory.build(withDoor = true)
        pedestrianModels = PedestrianModelFactory.buildAll()
        groundInstance = ModelInstance(groundModel)
        carInstance = ModelInstance(carModel)
        danfoModel = TrafficModelFactory.buildDanfo()
        danfoInstance = ModelInstance(danfoModel)
        job = DanfoJob(pedestrianModels)
        // Your danfo waits in Ikorodu, 40 m north of the border road, facing north.
        danfo.place(3f, -40f, 0f)
        crowd = PedestrianCrowd(pedestrianModels)
        playerModel = PlayerModelFactory.load()
        playerInstance = ModelInstance(playerModel)
        traffic.create()
        focus.set(controller.position)
        traffic.populate(focus, vehiclePositions)
        showHint("Your danfo is parked in Ikorodu - find the yellow square on the map", 8f)

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

        // Get out of the vehicle, or back in.
        if (controls.actionTapped && !mapOpen) {
            if (!onFoot) {
                if (doorSequence != 0) {
                    // Already busy with the door.
                } else if (kotlin.math.abs(controller.speed) < EXIT_MAX_SPEED && controller === grind) {
                    // Open the door, step out of the seat and walk round the door.
                    tmp.set(0f, 0f, -1f).rotate(Vector3.Y, grind.yawDegrees + 90f)
                    seqFrom.set(grind.position.x + tmp.x * SEAT_SIDE, 0f, grind.position.z + tmp.z * SEAT_SIDE)
                    seqTo.set(grind.position.x + tmp.x * CAR_EXIT_SIDE, 0f, grind.position.z + tmp.z * CAR_EXIT_SIDE)
                    TownCollision.resolvePoint(seqTo, PLAYER_RADIUS)
                    player.place(seqFrom.x, seqFrom.z, grind.yawDegrees + 90f)
                    doorSequence = 1
                    doorTimer = 0f
                    doorTarget = 1f
                    onFoot = true
                    hasWalkTarget = false
                    chaseCamera.freeLook = true
                    controls.setOnFoot(true)
                    showHint("Stick = walk, JUMP = jump, double-tap = walk there. ENTER next to a vehicle")
                } else if (kotlin.math.abs(controller.speed) < EXIT_MAX_SPEED) {
                    val side = if (controller === danfo) DANFO_EXIT_SIDE else CAR_EXIT_SIDE
                    tmp.set(0f, 0f, -1f).rotate(Vector3.Y, controller.yawDegrees + 90f)
                    player.place(
                        controller.position.x + tmp.x * side,
                        controller.position.z + tmp.z * side,
                        controller.yawDegrees
                    )
                    TownCollision.resolvePoint(player.position, PLAYER_RADIUS)
                    onFoot = true
                    hasWalkTarget = false
                    chaseCamera.freeLook = true
                    controls.setOnFoot(true)
                    showHint("Stick = walk, JUMP = jump, double-tap = walk there. ENTER next to a vehicle")
                } else {
                    showHint("Slow down to get out")
                }
            } else {
                val pick = nearestVehicleInReach()
                if (doorSequence != 0) {
                    // Already busy with the door.
                } else if (pick === grind) {
                    // Open the door, then walk to the seat.
                    tmp.set(0f, 0f, -1f).rotate(Vector3.Y, grind.yawDegrees + 90f)
                    seqFrom.set(player.position)
                    seqTo.set(grind.position.x + tmp.x * SEAT_SIDE, 0f, grind.position.z + tmp.z * SEAT_SIDE)
                    doorSequence = 2
                    doorTimer = 0f
                    doorTarget = 1f
                    hasWalkTarget = false
                } else if (pick != null) {
                    controller = pick
                    onFoot = false
                    hasWalkTarget = false
                    chaseCamera.freeLook = false
                    controls.setOnFoot(false)
                    applyVehicleCamera()
                    if (pick === danfo) showHint("Danfo: stop at the orange bus stops to pick up people")
                } else {
                    showHint("Too far from a vehicle")
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
            // The vehicle you drive gets your controls; the other one just rolls to a stop.
            for (v in arrayOf(grind, danfo)) {
                if (!onFoot && v === controller && doorSequence == 0) {
                    v.update(delta, controls.steer, controls.throttle, controls.brake)
                } else if (v === grind && doorSequence != 0) {
                    v.update(delta, 0f, false, true)
                } else {
                    v.update(delta, 0f, false, false)
                }
                TownCollision.resolve(v, radiusOf(v), offsetOf(v))
                traffic.collide(v, offsetOf(v), radiusOf(v))
            }
            if (onFoot && doorSequence != 0) {
                runDoorSequence(delta)
            } else if (onFoot) {
                walkOnFoot(delta)
                TownCollision.resolvePoint(player.position, PLAYER_RADIUS)
                for (v in arrayOf(grind, danfo)) {
                    TownCollision.pushOutOfCar(
                        player.position, PLAYER_RADIUS, v.position, v.yawDegrees, radiusOf(v), offsetOf(v)
                    )
                }
                traffic.pushOut(player.position, PLAYER_RADIUS)
            }
            job.update(delta, if (!onFoot && controller === danfo) danfo else null)

            // The driver's door swings open and shut.
            if (doorSequence == 0 && doorHold > 0f) {
                doorHold -= delta
                if (doorHold <= 0f) doorTarget = 0f
            }
            val doorStep = delta / DOOR_TIME
            doorAmount += (doorTarget - doorAmount).coerceIn(-doorStep, doorStep)
        }

        if (onFoot) {
            focus.set(player.position)
            focusYaw = player.yawDegrees
        } else {
            focus.set(controller.position)
            focusYaw = controller.yawDegrees
        }

        if (!mapOpen) {
            traffic.update(delta, focus, vehiclePositions, onFoot, player.position)
            crowd.update(delta, focus)
        }
        chunks.update(focus.x, focus.z)

        carInstance.transform.setToRotation(Vector3.Y, grind.yawDegrees)
            .setTranslation(grind.position.x, grind.position.y + grind.bounceHeight, grind.position.z)
        danfoInstance.transform.setToRotation(Vector3.Y, danfo.yawDegrees)
            .setTranslation(danfo.position.x, danfo.position.y + danfo.bounceHeight, danfo.position.z)
        carInstance.getNode(CarModelFactory.DOOR_NODE)?.rotation?.set(Vector3.Y, -DOOR_ANGLE * doorAmount)
        carInstance.calculateTransforms()
        if (onFoot) player.applyTo(playerInstance, clock)

        val scaleTarget = if (onFoot) ON_FOOT_CAMERA_SCALE else if (controller === danfo) DANFO_CAMERA_SCALE else 1f
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
        job.render(modelBatch, environment, camera, focus)
        modelBatch.render(carInstance, environment)
        modelBatch.render(danfoInstance, environment)
        if (onFoot && viewMode != ChaseCamera.MODE_HOOD) modelBatch.render(playerInstance, environment)
        modelBatch.end()

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        parkedMarkers.clear()
        if (onFoot || controller !== grind) parkedMarkers.add(grind.position)
        if (onFoot || controller !== danfo) parkedMarkers.add(danfo.position)
        val parked: List<Vector3> = parkedMarkers
        if (mapOpen) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            mapRenderer.drawFull(shapeRenderer, mapView, focus, focusYaw, parked, job.mapStops)
            mapView.drawShapes(shapeRenderer)
            shapeRenderer.end()
        } else {
            // The small map is cut off at its edges with a scissor box.
            Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST)
            Gdx.gl.glScissor(miniX.toInt(), miniY.toInt(), miniSize.toInt(), miniSize.toInt())
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            mapRenderer.drawMini(shapeRenderer, focus, focusYaw, miniX, miniY, miniSize, parked, job.mapStops)
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

            // Danfo work: passengers, money, where to go next.
            if ((!onFoot && controller === danfo) || job.passengerCount > 0) {
                val line = "Passengers ${job.passengerCount}/${DanfoJob.CAPACITY}     N${job.money}"
                glyphLayout.setText(font, line)
                font.draw(
                    spriteBatch, glyphLayout,
                    screenWidth / 2f - glyphLayout.width / 2f,
                    screenHeight - margin - glyphLayout.height * 3.5f
                )
                val next = job.hintLine(focus.x, focus.z)
                if (next.isNotEmpty()) {
                    glyphLayout.setText(font, next)
                    font.draw(
                        spriteBatch, glyphLayout,
                        screenWidth / 2f - glyphLayout.width / 2f,
                        screenHeight - margin - glyphLayout.height * 5.5f
                    )
                }
            }
            if (job.messageTimer > 0f) {
                font.data.setScale(textScale * 1.5f)
                glyphLayout.setText(font, job.message)
                font.draw(
                    spriteBatch, glyphLayout,
                    screenWidth / 2f - glyphLayout.width / 2f,
                    screenHeight * 0.7f
                )
                font.data.setScale(textScale)
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

    /** Reads the walking stick (or a double-tapped spot) and moves the player. */
    /** Plays the walk out of, or into, the 6 GRIND while its door is open. */
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
            0f,
            seqFrom.z + (seqTo.z - seqFrom.z) * t
        )

        if (doorTimer >= moveEnd) {
            if (doorSequence == 2) {
                controller = grind
                onFoot = false
                chaseCamera.freeLook = false
                controls.setOnFoot(false)
                applyVehicleCamera()
            }
            doorSequence = 0
            doorHold = DOOR_HOLD_TIME
        }
    }

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

    private fun showHint(text: String, seconds: Float = 2.5f) {
        hintText = text
        hintTimer = seconds
    }

    private fun radiusOf(v: CarController): Float = if (v === danfo) DANFO_RADIUS else CAR_RADIUS
    private fun offsetOf(v: CarController): Float = if (v === danfo) DANFO_OFFSET else CAR_OFFSET

    /** The vehicle close enough to get into, or null. If both are close, the nearer one. */
    private fun nearestVehicleInReach(): CarController? {
        var best: CarController? = null
        var bestD = Float.MAX_VALUE
        for (v in arrayOf(grind, danfo)) {
            val dx = player.position.x - v.position.x
            val dz = player.position.z - v.position.z
            val d2 = dx * dx + dz * dz
            val reach = if (v === danfo) DANFO_ENTER_RANGE else ENTER_RANGE
            if (d2 <= reach * reach && d2 < bestD) {
                best = v
                bestD = d2
            }
        }
        return best
    }

    /** The driver's-eye view sits further forward and higher up in the danfo. */
    private fun applyVehicleCamera() {
        if (controller === danfo) {
            chaseCamera.hoodBack = -1.3f
            chaseCamera.hoodHeight = 1.9f
        } else {
            chaseCamera.hoodBack = 0.15f
            chaseCamera.hoodHeight = 1.3f
        }
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
        danfoModel.dispose()
        job.dispose()
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
        const val DOOR_ANGLE = 70f        // how far the driver's door swings open
        const val DOOR_TIME = 0.35f       // seconds to open or close
        const val DOOR_WALK_TIME = 0.6f   // seconds to step out or in
        const val DOOR_HOLD_TIME = 0.35f  // door stays open this long after
        const val SEAT_SIDE = 0.35f       // seat position, metres left of the car centre
        const val ENTER_RANGE = 4.5f
        const val DANFO_ENTER_RANGE = 5.8f
        const val CAR_EXIT_SIDE = 2.4f
        const val DANFO_EXIT_SIDE = 2.9f
        const val CAR_RADIUS = 1.1f
        const val CAR_OFFSET = 1.2f
        const val DANFO_RADIUS = 1.2f
        const val DANFO_OFFSET = 1.5f
        const val DANFO_CAMERA_SCALE = 1.3f
        const val PLAYER_RADIUS = 0.4f
        const val WALK_TO_MAX = 80f
        const val ON_FOOT_CAMERA_SCALE = 0.42f
        const val SKY_R = 0.53f
        const val SKY_G = 0.81f
        const val SKY_B = 0.92f
    }
}
