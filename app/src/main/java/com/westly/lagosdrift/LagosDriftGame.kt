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
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3

/**
 * Phase 6: drive the 6 GRIND around Ikoyi (south) and Ikorodu (north), get out and walk as your own
 * character, or drive your own danfo in Ikorodu: stop at the bus stops, take passengers, drop them
 * at their stop and earn fares. Other cars and danfos share the road.
 * The world is built in 100 m chunks around whoever the camera follows (a vehicle, or the walker).
 *
 * Phase 7: the player's car is a 2006 Toyota Camry (steering and rolling wheels), and the GO button
 * travels between three worlds: Lagos, Neribo City (with the player's house and its garage) and
 * Neribo Desert. The two new worlds have their own sky, taken from a 360 degree HDRI picture.
 */
class LagosDriftGame : ApplicationAdapter() {
    private lateinit var camera: PerspectiveCamera
    private lateinit var chaseCamera: ChaseCamera
    private lateinit var modelBatch: ModelBatch
    private lateinit var environment: Environment
    private lateinit var groundModel: Model
    private lateinit var carModel: Model
    private lateinit var playerModel: Model
    private lateinit var playerInstance: ModelInstance
    private lateinit var groundInstance: ModelInstance
    private lateinit var carInstance: ModelInstance
    private lateinit var danfoModel: Model
    private lateinit var danfoInstance: ModelInstance
    private lateinit var danfoDoorModel: Model
    private lateinit var danfoDoorInstance: ModelInstance
    private lateinit var job: DanfoJob
    private lateinit var crowd: PedestrianCrowd
    private lateinit var chunks: ChunkManager
    private lateinit var spriteBatch: SpriteBatch
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var font: BitmapFont

    private val sun = DirectionalLight()
    private val skies = arrayOfNulls<SkyDome>(Districts.WORLD_COUNT)
    private val crowds = arrayOfNulls<PedestrianCrowd>(Districts.WORLD_COUNT)
    private var sky: SkyDome? = null
    private var dam: DamModel? = null
    private var world = Districts.WORLD_LAGOS
    private var usingCamry = false
    private var clearR = 0.53f
    private var clearG = 0.81f
    private var clearB = 0.92f
    private val wheelSteer = Quaternion()
    private val wheelRoll = Quaternion()

    /** Where the player's car, the player and the on-foot flag were in each world when it was left. */
    private class WorldState {
        var has = false
        var gx = 0f
        var gz = 0f
        var gyaw = 0f
        var px = 0f
        var pz = 0f
        var pyaw = 0f
        var onFoot = false
    }
    private val states = Array(Districts.WORLD_COUNT) { WorldState() }
    private var danfoDriven = false

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
    private val doorAmount = FloatArray(2)   // [0] the 6 GRIND, [1] the danfo: 0 shut, 1 open
    private val doorTarget = FloatArray(2)
    private val doorHold = FloatArray(2)
    private var seqVehicle: CarController? = null
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

        // Distance haze in the colour of the sky hides chunks appearing at the edge of the view.
        environment = Environment()
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.55f, 0.55f, 0.60f, 1f))
        environment.set(ColorAttribute(ColorAttribute.Fog, SKY_R, SKY_G, SKY_B, 1f))
        sun.set(0.90f, 0.88f, 0.80f, -0.6f, -1f, -0.4f)
        environment.add(sun)
        environment.add(DirectionalLight().set(0.25f, 0.25f, 0.30f, 0.6f, -0.3f, 0.5f))

        modelBatch = ModelBatch()
        groundModel = GroundModelFactory.build()
        // The player's car is the Camry. If its files cannot be read, the old box-built car is used instead.
        carModel = try {
            usingCamry = true
            CamryModelFactory.load()
        } catch (e: Exception) {
            usingCamry = false
            CarModelFactory.build(withDoor = true)
        }
        // One human model is shared by the player, the people on the pavements and the bus-stop crowds.
        playerModel = PlayerModelFactory.load()
        groundInstance = ModelInstance(groundModel)
        carInstance = ModelInstance(carModel)
        danfoModel = TrafficModelFactory.buildDanfo()
        danfoInstance = ModelInstance(danfoModel)
        danfoDoorModel = TrafficModelFactory.buildDanfoDoor()
        danfoDoorInstance = ModelInstance(danfoDoorModel)
        job = DanfoJob(playerModel)
        // Your danfo waits in Ikorodu, 40 m north of the border road, facing north.
        danfo.place(3f, -40f, 0f)
        crowd = PedestrianCrowd(playerModel)
        crowds[Districts.WORLD_LAGOS] = crowd
        playerInstance = ModelInstance(playerModel)
        traffic.create()

        // Carry on where the last game stopped, if there is one.
        val resumed = restoreGame()
        focus.set(if (onFoot) player.position else controller.position)
        if (world == Districts.WORLD_LAGOS) traffic.populate(focus, vehiclePositions)
        if (resumed) {
            showHint("Welcome back - carrying on where you stopped", 5f)
        } else {
            showHint("Your danfo is parked in Ikorodu - find the yellow square on the map", 8f)
        }

        chunks = ChunkManager()
        chunks.preload(focus.x, focus.z)
        if (world != Districts.WORLD_LAGOS) applyWorldLook()

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
            if (mapOpen) mapView.open(world, focus.x, focus.z)
        }
        if (mapOpen) {
            mapView.update(delta, world, focus.x, focus.z) { x, y -> controls.hitsButton(x, y) }
            if (mapView.closeRequested) {
                mapOpen = false
                controls.mapMode = false
            } else if (mapView.travelRequested) {
                // TRAVEL HERE: close the map and go to the pin, in whichever world it is.
                mapOpen = false
                controls.mapMode = false
                travelTo(mapView.pinWorld, mapView.pinX, mapView.pinZ)
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
                } else if (kotlin.math.abs(controller.speed) < EXIT_MAX_SPEED) {
                    // Open the door, step out of the seat and walk round the door.
                    val v = controller
                    val seat = if (v === danfo) DANFO_SEAT_SIDE else SEAT_SIDE
                    val side = if (v === danfo) DANFO_EXIT_SIDE else CAR_EXIT_SIDE
                    tmp.set(0f, 0f, -1f).rotate(Vector3.Y, v.yawDegrees + 90f)
                    seqFrom.set(v.position.x + tmp.x * seat, 0f, v.position.z + tmp.z * seat)
                    seqTo.set(v.position.x + tmp.x * side, 0f, v.position.z + tmp.z * side)
                    TownCollision.resolvePoint(seqTo, PLAYER_RADIUS)
                    player.place(seqFrom.x, seqFrom.z, v.yawDegrees + 90f)
                    seqVehicle = v
                    doorSequence = 1
                    doorTimer = 0f
                    doorTarget[doorIndex(v)] = 1f
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
                } else if (pick != null) {
                    // Open the door, then walk to the seat.
                    val seat = if (pick === danfo) DANFO_SEAT_SIDE else SEAT_SIDE
                    tmp.set(0f, 0f, -1f).rotate(Vector3.Y, pick.yawDegrees + 90f)
                    seqFrom.set(player.position)
                    seqTo.set(pick.position.x + tmp.x * seat, 0f, pick.position.z + tmp.z * seat)
                    seqVehicle = pick
                    doorSequence = 2
                    doorTimer = 0f
                    doorTarget[doorIndex(pick)] = 1f
                    hasWalkTarget = false
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
            for (v in activeVehicles()) {
                if (!onFoot && v === controller && doorSequence == 0) {
                    v.update(delta, controls.steer, controls.throttle, controls.brake)
                } else if (v === seqVehicle && doorSequence != 0) {
                    v.update(delta, 0f, false, true)
                } else {
                    v.update(delta, 0f, false, false)
                }
                TownCollision.resolve(v, radiusOf(v), offsetOf(v))
                if (world == Districts.WORLD_LAGOS) traffic.collide(v, offsetOf(v), radiusOf(v))
            }
            if (onFoot && doorSequence != 0) {
                runDoorSequence(delta)
            } else if (onFoot) {
                walkOnFoot(delta)
                TownCollision.resolvePoint(player.position, PLAYER_RADIUS)
                for (v in activeVehicles()) {
                    TownCollision.pushOutOfCar(
                        player.position, PLAYER_RADIUS, v.position, v.yawDegrees, radiusOf(v), offsetOf(v)
                    )
                }
                if (world == Districts.WORLD_LAGOS) traffic.pushOut(player.position, PLAYER_RADIUS)
            }
            if (world == Districts.WORLD_LAGOS) job.update(delta, if (!onFoot && controller === danfo) danfo else null)

            // The driver's doors swing open and shut.
            val doorStep = delta / DOOR_TIME
            for (i in 0..1) {
                if (doorHold[i] > 0f && !(doorSequence != 0 && doorIndex(seqVehicle) == i)) {
                    doorHold[i] -= delta
                    if (doorHold[i] <= 0f) doorTarget[i] = 0f
                }
                doorAmount[i] += (doorTarget[i] - doorAmount[i]).coerceIn(-doorStep, doorStep)
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
            if (world == Districts.WORLD_LAGOS) traffic.update(delta, focus, vehiclePositions, onFoot, player.position)
            crowd.update(delta, focus)
        }
        chunks.update(focus.x, focus.z)

        carInstance.transform.setToRotation(Vector3.Y, grind.yawDegrees)
            .setTranslation(grind.position.x, grind.position.y + grind.bounceHeight, grind.position.z)
        danfoInstance.transform.setToRotation(Vector3.Y, danfo.yawDegrees)
            .setTranslation(danfo.position.x, danfo.position.y + danfo.bounceHeight, danfo.position.z)
        carInstance.getNode(CarModelFactory.DOOR_NODE)?.rotation?.set(Vector3.Y, -DOOR_ANGLE * doorAmount[0])
        if (usingCamry) updateWheels()
        carInstance.calculateTransforms()
        danfoDoorInstance.transform.set(danfoInstance.transform)
        danfoDoorInstance.getNode(TrafficModelFactory.DANFO_DOOR_NODE)?.rotation
            ?.set(Vector3.Y, -DOOR_ANGLE * doorAmount[1])
        danfoDoorInstance.calculateTransforms()
        if (onFoot) player.applyTo(playerInstance, clock)

        val scaleTarget = if (onFoot) ON_FOOT_CAMERA_SCALE else if (controller === danfo) DANFO_CAMERA_SCALE else 1f
        chaseCamera.distanceScale += (scaleTarget - chaseCamera.distanceScale) * minOf(1f, 4f * delta)
        val followSpeed = if (onFoot) player.speed else controller.speed
        chaseCamera.update(focus, focusYaw, followSpeed, delta, viewMode)

        Gdx.gl.glViewport(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        Gdx.gl.glClearColor(clearR, clearG, clearB, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        val skyNow = sky
        if (skyNow != null) {
            modelBatch.begin(camera)
            skyNow.render(modelBatch, camera)
            modelBatch.end()
        }

        modelBatch.begin(camera)
        modelBatch.render(groundInstance, environment)
        chunks.render(modelBatch, environment, camera)
        if (world == Districts.WORLD_DESERT) dam?.render(modelBatch, environment, camera)
        crowd.render(modelBatch, environment, focus)
        if (world == Districts.WORLD_LAGOS) {
            traffic.render(modelBatch, environment, camera, focus)
            job.render(modelBatch, environment, camera, focus)
        }
        modelBatch.render(carInstance, environment)
        if (world == Districts.WORLD_LAGOS) {
            modelBatch.render(danfoInstance, environment)
            modelBatch.render(danfoDoorInstance, environment)
        }
        if (onFoot && viewMode != ChaseCamera.MODE_HOOD) modelBatch.render(playerInstance, environment)
        modelBatch.end()

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        parkedMarkers.clear()
        if (onFoot || controller !== grind) parkedMarkers.add(grind.position)
        if (world == Districts.WORLD_LAGOS && (onFoot || controller !== danfo)) parkedMarkers.add(danfo.position)
        val parked: List<Vector3> = parkedMarkers
        if (mapOpen) {
            // The map can look at any world. TownLayout is switched to it only while the map is drawn.
            val viewWorld = mapView.mapWorld
            val here = viewWorld == world
            val mapParked: List<Vector3>? = when {
                here -> parked
                viewWorld == Districts.WORLD_LAGOS -> listOf(danfo.position)
                else -> null
            }
            val mapStops: List<MapStop>? = if (viewWorld == Districts.WORLD_LAGOS) job.mapStops else null
            if (!here) TownLayout.select(viewWorld)
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            mapRenderer.drawFull(shapeRenderer, mapView, focus, focusYaw, mapParked, mapStops, here)
            mapView.drawShapes(shapeRenderer)
            shapeRenderer.end()
            if (!here) TownLayout.select(world)
        } else {
            // The small map is cut off at its edges with a scissor box.
            Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST)
            Gdx.gl.glScissor(miniX.toInt(), miniY.toInt(), miniSize.toInt(), miniSize.toInt())
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            mapRenderer.drawMini(shapeRenderer, focus, focusYaw, miniX, miniY, miniSize, parked, stopsForMap())
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
            glyphLayout.setText(font, "MAP   drag to scroll, pinch to zoom, tap to drop a pin")
            font.draw(spriteBatch, glyphLayout, screenWidth / 2f - glyphLayout.width / 2f, screenHeight - margin)
        } else {
            val speedText = if (onFoot) "on foot" else "${controller.speedKmh.toInt()} km/h"
            glyphLayout.setText(font, speedText)
            font.draw(spriteBatch, glyphLayout, screenWidth / 2f - glyphLayout.width / 2f, margin + glyphLayout.height)

            glyphLayout.setText(font, copyrightText)
            font.draw(spriteBatch, glyphLayout, screenWidth - safeRight - margin - glyphLayout.width, screenHeight - margin)

            // The Camry's licence asks for its author to be named.
            if (usingCamry) {
                font.data.setScale(textScale * 0.7f)
                font.setColor(1f, 1f, 1f, 0.6f)
                glyphLayout.setText(font, CamryModelFactory.CREDIT)
                font.draw(
                    spriteBatch, glyphLayout,
                    screenWidth - safeRight - margin - glyphLayout.width,
                    screenHeight - margin - glyphLayout.height * 2.6f
                )
                font.data.setScale(textScale)
                font.setColor(1f, 1f, 1f, 0.8f)
            }

            // The dam's licence asks for its author to be named.
            if (world == Districts.WORLD_DESERT && dam != null) {
                font.data.setScale(textScale * 0.7f)
                font.setColor(1f, 1f, 1f, 0.6f)
                glyphLayout.setText(font, DamModel.CREDIT)
                font.draw(
                    spriteBatch, glyphLayout,
                    screenWidth - safeRight - margin - glyphLayout.width,
                    screenHeight - margin - glyphLayout.height * 4.2f
                )
                font.data.setScale(textScale)
                font.setColor(1f, 1f, 1f, 0.8f)
            }

            // Where you are and what the road is like.
            glyphLayout.setText(font, placeText())
            font.draw(
                spriteBatch, glyphLayout,
                screenWidth / 2f - glyphLayout.width / 2f,
                margin + glyphLayout.height * 3f
            )

            // Danfo work: passengers, money, where to go next.
            if (world == Districts.WORLD_LAGOS && ((!onFoot && controller === danfo) || job.passengerCount > 0)) {
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
            if (world == Districts.WORLD_LAGOS && job.messageTimer > 0f) {
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
    private fun doorIndex(v: CarController?): Int = if (v === danfo) 1 else 0

    /** Plays the walk out of, or into, a vehicle while its driver's door is open. */
    private fun runDoorSequence(delta: Float) {
        doorTimer += delta
        val moveStart = DOOR_TIME
        val moveEnd = DOOR_TIME + DOOR_WALK_TIME
        val t = ((doorTimer - moveStart) / DOOR_WALK_TIME).coerceIn(0f, 1f)
        val walking = doorTimer >= moveStart && doorTimer < moveEnd

        // Face the way we are walking (out: away from the car; in: towards it).
        val v = seqVehicle ?: grind
        val faceYaw = v.yawDegrees + if (doorSequence == 1) 90f else -90f
        player.update(delta, faceYaw, if (walking) 0.4f else 0f, false)
        player.position.set(
            seqFrom.x + (seqTo.x - seqFrom.x) * t,
            0f,
            seqFrom.z + (seqTo.z - seqFrom.z) * t
        )

        if (doorTimer >= moveEnd) {
            if (doorSequence == 2) {
                controller = v
                onFoot = false
                chaseCamera.freeLook = false
                controls.setOnFoot(false)
                applyVehicleCamera()
                if (v === danfo) showHint("Danfo: stop at the orange bus stops to pick up people")
            }
            doorSequence = 0
            doorHold[doorIndex(v)] = DOOR_HOLD_TIME
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
        for (v in activeVehicles()) {
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
        val district = Districts.nameAt(focus.z)
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

    /** The game goes to the background (home button, another app, swiping it away): save where we are. */
    override fun pause() {
        saveGame()
    }

    /** The vehicles that exist in the current world: both in Lagos, only the Camry elsewhere. */
    private fun activeVehicles(): Array<CarController> =
        if (world == Districts.WORLD_LAGOS) arrayOf(grind, danfo) else arrayOf(grind)

    private fun stopsForMap(): List<MapStop>? = if (world == Districts.WORLD_LAGOS) job.mapStops else null

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

    // ------------------------------------------------------------------ worlds

    /**
     * TRAVEL HERE on the map: go to a pin in [target] world. Whatever you are using (the car you drive,
     * or yourself while walking) is put at the pin; the rest of the world stays as you left it.
     */
    private fun travelTo(target: Int, x: Float, z: Float) {
        if (doorSequence != 0) {
            showHint("Finish getting in or out first")
            return
        }
        val tx = x.coerceIn(TownLayout.DRIVE_MIN_X, TownLayout.DRIVE_MAX_X)
        val tz = z.coerceIn(TownLayout.DRIVE_MIN_Z, TownLayout.DRIVE_MAX_Z)
        if (target != world) {
            switchWorld(target, tx, tz)
            return
        }
        placeAtPin(tx, tz)
        focus.set(if (onFoot) player.position else controller.position)
        chunks.preload(focus.x, focus.z)
        if (world == Districts.WORLD_LAGOS) traffic.populate(focus, vehiclePositions)
        showHint("Travelled to ${Districts.nameAt(tz)}", 3f)
        saveGame()
    }

    /** Puts you (on foot) or the vehicle you drive at a spot, keeping the way it faces. */
    private fun placeAtPin(x: Float, z: Float) {
        hasWalkTarget = false
        if (onFoot) player.place(x, z, player.yawDegrees) else controller.place(x, z, controller.yawDegrees)
    }

    /** Remembers where the car and the player are in the current world. */
    private fun captureState() {
        var savedOnFoot = onFoot
        var savedVehicle = controller
        var px = player.position.x
        var pz = player.position.z
        if (doorSequence == 1) {
            px = seqTo.x
            pz = seqTo.z
        } else if (doorSequence == 2) {
            savedOnFoot = false
            savedVehicle = seqVehicle ?: controller
        }
        val st = states[world]
        st.has = true
        st.gx = grind.position.x
        st.gz = grind.position.z
        st.gyaw = grind.yawDegrees
        st.px = px
        st.pz = pz
        st.pyaw = player.yawDegrees
        st.onFoot = savedOnFoot
        if (world == Districts.WORLD_LAGOS) danfoDriven = savedVehicle === danfo
    }

    /** Puts the car and the player where they were in world [w], or at its start if it was never visited. */
    private fun loadState(w: Int) {
        val st = states[w]
        if (st.has) {
            grind.place(st.gx, st.gz, st.gyaw)
        } else if (w == Districts.WORLD_CITY) {
            grind.place(TownLayout.HOME_CAR_X, TownLayout.HOME_CAR_Z, TownLayout.HOME_CAR_YAW)
        } else if (w == Districts.WORLD_DESERT) {
            grind.place(TownLayout.DESERT_CAR_X, TownLayout.DESERT_CAR_Z, TownLayout.DESERT_CAR_YAW)
        }
        onFoot = st.has && st.onFoot
        controller = if (w == Districts.WORLD_LAGOS && danfoDriven) danfo else grind
        doorSequence = 0
        for (i in 0..1) {
            doorAmount[i] = 0f
            doorTarget[i] = 0f
            doorHold[i] = 0f
        }
        hasWalkTarget = false
        if (onFoot) {
            player.place(st.px, st.pz, st.pyaw)
            chaseCamera.freeLook = true
            controls.setOnFoot(true)
        } else {
            chaseCamera.freeLook = false
            controls.setOnFoot(false)
            applyVehicleCamera()
        }
        chaseCamera.distanceScale =
            if (onFoot) ON_FOOT_CAMERA_SCALE else if (controller === danfo) DANFO_CAMERA_SCALE else 1f
    }

    private fun switchWorld(target: Int, destX: Float? = null, destZ: Float? = null) {
        captureState()
        world = target
        TownLayout.select(target)
        loadState(target)
        if (destX != null && destZ != null) placeAtPin(destX, destZ)
        focus.set(if (onFoot) player.position else controller.position)
        applyWorldLook()
        chunks.dispose()
        chunks = ChunkManager()
        chunks.preload(focus.x, focus.z)
        if (target == Districts.WORLD_LAGOS) traffic.populate(focus, vehiclePositions)
        if (destX != null) {
            showHint("Travelled to ${Districts.WORLD_NAMES[target]}", 4f)
        } else when (target) {
            Districts.WORLD_CITY -> showHint("Neribo City - your house is at the end of the driveway", 6f)
            Districts.WORLD_DESERT -> showHint("Neribo Desert - the old highway runs north and south", 6f)
            else -> showHint("Lagos - back where you left off", 4f)
        }
        saveGame()
    }

    /** Ground, sky, haze, sunlight and people for the current world. */
    private fun applyWorldLook() {
        groundModel.dispose()
        groundModel = GroundModelFactory.build(world)
        groundInstance = ModelInstance(groundModel)

        var fogR = SKY_R
        var fogG = SKY_G
        var fogB = SKY_B
        when (world) {
            Districts.WORLD_CITY -> {
                // Colours and sun direction come from the ostrich_road HDRI.
                fogR = 0.59f; fogG = 0.61f; fogB = 0.65f
                environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.58f, 0.58f, 0.62f, 1f))
                sun.set(0.95f, 0.93f, 0.86f, -0.08f, -0.71f, -0.70f)
                sky = skies[world] ?: SkyDome("sky/sky_city.jpg").also { skies[world] = it }
            }
            Districts.WORLD_DESERT -> {
                // Colours and sun direction come from the aarfontein_pan HDRI.
                fogR = 0.83f; fogG = 0.87f; fogB = 0.885f
                environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.62f, 0.58f, 0.52f, 1f))
                sun.set(1.0f, 0.92f, 0.78f, 0.42f, -0.71f, -0.57f)
                sky = skies[world] ?: SkyDome("sky/sky_desert.jpg").also { skies[world] = it }
                if (dam == null) dam = try { DamModel() } catch (e: Exception) { null }
            }
            else -> {
                environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.55f, 0.55f, 0.60f, 1f))
                sun.set(0.90f, 0.88f, 0.80f, -0.6f, -1f, -0.4f)
                sky = null
            }
        }
        clearR = fogR
        clearG = fogG
        clearB = fogB
        environment.set(ColorAttribute(ColorAttribute.Fog, fogR, fogG, fogB, 1f))

        crowd = crowds[world] ?: PedestrianCrowd(playerModel).also { crowds[world] = it }
    }

    /**
     * Writes everything needed to carry on later: where the vehicles and the player are in every world
     * you have visited, which world you are in, whether you are walking or driving (and which vehicle),
     * the camera, and the danfo work. A door sequence that is half done is saved as if it had finished.
     * Lagos keeps the original save keys, so older saves still load.
     */
    private fun saveGame() {
        if (!::job.isInitialized || !::chaseCamera.isInitialized) return
        captureState()
        val p = Gdx.app.getPreferences(SAVE_NAME)
        p.putInteger("version", SAVE_VERSION)
        p.putInteger("world", world)
        val lagos = states[Districts.WORLD_LAGOS]
        p.putFloat("grind_x", lagos.gx)
        p.putFloat("grind_z", lagos.gz)
        p.putFloat("grind_yaw", lagos.gyaw)
        p.putFloat("danfo_x", danfo.position.x)
        p.putFloat("danfo_z", danfo.position.z)
        p.putFloat("danfo_yaw", danfo.yawDegrees)
        p.putFloat("player_x", lagos.px)
        p.putFloat("player_z", lagos.pz)
        p.putFloat("player_yaw", lagos.pyaw)
        p.putBoolean("on_foot", lagos.onFoot)
        p.putBoolean("drive_danfo", danfoDriven)
        for (w in 1 until Districts.WORLD_COUNT) {
            val st = states[w]
            p.putBoolean("w${w}_has", st.has)
            if (!st.has) continue
            p.putFloat("w${w}_gx", st.gx)
            p.putFloat("w${w}_gz", st.gz)
            p.putFloat("w${w}_gyaw", st.gyaw)
            p.putFloat("w${w}_px", st.px)
            p.putFloat("w${w}_pz", st.pz)
            p.putFloat("w${w}_pyaw", st.pyaw)
            p.putBoolean("w${w}_foot", st.onFoot)
        }
        p.putInteger("view", viewMode)
        p.putFloat("zoom", chaseCamera.zoom)
        job.save(p)
        p.flush()
    }

    /** Puts everything back the way [saveGame] left it. Returns false when there is no usable save. */
    private fun restoreGame(): Boolean {
        val p = Gdx.app.getPreferences(SAVE_NAME)
        if (p.getInteger("version", 0) != SAVE_VERSION) return false
        val values = floatArrayOf(
            p.getFloat("grind_x"), p.getFloat("grind_z"), p.getFloat("grind_yaw"),
            p.getFloat("danfo_x"), p.getFloat("danfo_z"), p.getFloat("danfo_yaw"),
            p.getFloat("player_x"), p.getFloat("player_z"), p.getFloat("player_yaw")
        )
        for (v in values) if (v.isNaN() || v.isInfinite()) return false

        val lagos = states[Districts.WORLD_LAGOS]
        lagos.has = true
        lagos.gx = values[0]
        lagos.gz = values[1]
        lagos.gyaw = values[2]
        lagos.px = values[6]
        lagos.pz = values[7]
        lagos.pyaw = values[8]
        lagos.onFoot = p.getBoolean("on_foot")
        danfo.place(values[3], values[4], values[5])
        danfoDriven = p.getBoolean("drive_danfo")

        for (w in 1 until Districts.WORLD_COUNT) {
            if (!p.getBoolean("w${w}_has")) continue
            val v = floatArrayOf(
                p.getFloat("w${w}_gx"), p.getFloat("w${w}_gz"), p.getFloat("w${w}_gyaw"),
                p.getFloat("w${w}_px"), p.getFloat("w${w}_pz"), p.getFloat("w${w}_pyaw")
            )
            if (v.any { it.isNaN() || it.isInfinite() }) continue
            val st = states[w]
            st.has = true
            st.gx = v[0]
            st.gz = v[1]
            st.gyaw = v[2]
            st.px = v[3]
            st.pz = v[4]
            st.pyaw = v[5]
            st.onFoot = p.getBoolean("w${w}_foot")
        }

        viewMode = p.getInteger("view", viewMode).coerceIn(0, ChaseCamera.MODE_COUNT - 1)
        chaseCamera.zoom = p.getFloat("zoom", 1f).coerceIn(0.5f, 2.5f)
        job.load(p)

        // The job above is built from the Lagos roads, so the saved world is only selected now.
        world = p.getInteger("world", Districts.WORLD_LAGOS).coerceIn(0, Districts.WORLD_COUNT - 1)
        TownLayout.select(world)
        loadState(world)
        return true
    }

    override fun dispose() {
        saveGame()
        modelBatch.dispose()
        groundModel.dispose()
        for (sk in skies) sk?.dispose()
        dam?.dispose()
        chunks.dispose()
        carModel.dispose()
        danfoModel.dispose()
        danfoDoorModel.dispose()
        job.dispose()
        playerModel.dispose()
        traffic.dispose()
        spriteBatch.dispose()
        shapeRenderer.dispose()
        font.dispose()
    }

    private companion object {
        const val FAR_DISTANCE = 430f
        const val SAVE_NAME = "lagosdrift_save"
        const val SAVE_VERSION = 1
        const val SAVE_EVERY_SECONDS = 5f
        const val EXIT_MAX_SPEED = 3f
        const val DOOR_ANGLE = 70f        // how far the driver's door swings open
        const val DOOR_TIME = 0.35f       // seconds to open or close
        const val DOOR_WALK_TIME = 0.6f   // seconds to step out or in
        const val DOOR_HOLD_TIME = 0.35f  // door stays open this long after
        const val DANFO_SEAT_SIDE = 0.5f
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
        const val MAX_WHEEL_STEER = 28f   // degrees the front wheels turn at full steering
        const val SKY_R = 0.53f
        const val SKY_G = 0.81f
        const val SKY_B = 0.92f
    }
}
