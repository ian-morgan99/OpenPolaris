package dev.openpolaris.android

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.openpolaris.core.domain.MarkerStateBus
import dev.openpolaris.core.domain.PreviewController
import dev.openpolaris.core.domain.SolveTargetProjector
import dev.openpolaris.core.domain.VrStereoShaders
import dev.openpolaris.core.solver.SolveResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.microedition.khronos.egl.EGLConfig

/**
 * Immersive VR-style viewer for the camera preview. Hands the screen over
 * to a [GLSurfaceView] that draws the MJPEG texture twice — once per eye —
 * with a Cardboard-v1 barrel-distortion pass so a Cardboard-class viewer
 * (Cardboard, Gear VR, Quest in phone-in-headset mode) sees a clean image.
 *
 * - Optional: invoked from the rail, default off.
 * - Locked to landscape and immersive (system bars hidden).
 * - Quits on back press.
 * - Head motion via [SensorManager] (TYPE_ROTATION_VECTOR). Yaw/pitch
 *   drive an in-plane pan of the sampled image, giving a "look around"
 *   feel without a full 6-DoF scene.
 *
 * No Cardboard SDK dependency — the v1 viewer profile is public spec.
 * The few constants below (FoV, inter-pupillary distance, distortion
 * coefficients) are exactly the ones the official profile ships with.
 */
class VRActivity : ComponentActivity() {

    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: StereoRenderer
    private lateinit var preview: PreviewController
    private var sensorManager: SensorManager? = null
    private var rotationVector: Sensor? = null
    private var collectJob: Job? = null
    private var markerBusJob: Job? = null

    // Overlay widgets (set in onCreate; read by the HUD tick).
    private lateinit var hudText: TextView
    private lateinit var connLoss: TextView
    private var hudHost: String = DEFAULT_HOST
    private var hudPort: Int = DEFAULT_PORT
    private var hudTickHandler: Handler? = null
    private val hudTick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val last = renderer.lastFrameMs
            val stale = last == 0L || now - last > 2_000L
            val fps = renderer.fps()
            val frames = renderer.frameCount
            hudText.text = "host=$hudHost:$hudPort  fps=${"%.1f".format(fps)}  frames=$frames"
            if (stale) {
                if (connLoss.visibility != View.VISIBLE) connLoss.visibility = View.VISIBLE
            } else {
                if (connLoss.visibility != View.GONE) connLoss.visibility = View.GONE
            }
            hudTickHandler?.postDelayed(this, 250L)
        }
    }

    private val headListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
            val q = FloatArray(4)
            SensorManager.getQuaternionFromVector(q, event.values)
            renderer.setYawPitch(qToYaw(q), qToPitch(q))
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )

        hudHost = intent.getStringExtra(EXTRA_HOST) ?: DEFAULT_HOST
        // 3h-BUG: read the live port from the launching Intent so the
        // user-chosen port from the reconnect dialog (or the persisted
        // SessionMarker on disk) actually flows into the MJPEG transport.
        // Previously the intent had no port extra and we fell back to
        // 8080, so a non-default port never received frames. The
        // fallback is 8080 to preserve the pre-3h default rather than
        // fail loudly — this activity is launched by the user explicitly
        // and a wrong port just shows "no frames" in the HUD.
        hudPort = intent.getIntExtra(EXTRA_PORT, DEFAULT_PORT)

        renderer = StereoRenderer()

        // 7.4 — read solve/target marker extras if present. Absent if
        // the launching screen had no solve yet (very first launch, or
        // the user hasn't run Solve). Absent → marker is hidden; no
        // crash, no fallback math.
        val solveRa = intent.getDoubleExtra(EXTRA_SOLVE_RA_DEG, Double.NaN)
        if (!solveRa.isNaN()) {
            val solveDec = intent.getDoubleExtra(EXTRA_SOLVE_DEC_DEG, Double.NaN)
            val targetRa = intent.getDoubleExtra(EXTRA_TARGET_RA_DEG, Double.NaN)
            val targetDec = intent.getDoubleExtra(EXTRA_TARGET_DEC_DEG, Double.NaN)
            if (!solveDec.isNaN() && !targetRa.isNaN() && !targetDec.isNaN()) {
                renderer.setSolveTarget(
                    fieldRaDeg = solveRa,
                    fieldDecDeg = solveDec,
                    targetRaDeg = targetRa,
                    targetDecDeg = targetDec,
                    confidence = intent.getFloatExtra(EXTRA_SOLVE_CONFIDENCE, 0.6f),
                    ageMs = intent.getLongExtra(EXTRA_SOLVE_AGE_MS, 0L),
                    // -1f is the "absent" sentinel: a missing/garbled
                    // Intent extra lands here and is caught by the
                    // sanity-check in [setSolveTarget], which logs and
                    // clamps to a 1° minimum instead of silently
                    // substituting 60°/45°. See issue #15.
                    fovXDeg = intent.getFloatExtra(EXTRA_FOV_X_DEG, -1f),
                    fovYDeg = intent.getFloatExtra(EXTRA_FOV_Y_DEG, -1f),
                )
            }
        }
        // #14. Start the live-update collector after the Intent-extras
        // snapshot so the first emission (if any) lands on the first
        // rendered frame instead of the second.
        startMarkerBus()
        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            // GLSurfaceView is not clickable by default; explicit isClickable=false
            // ensures touches pass through to the FrameLayout's onClickListener
            // (and not to this view, which has no listener and would otherwise
            // swallow the event).
            isClickable = false
        }
        val root = FrameLayout(this).apply {
            addView(
                glView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            // Tap anywhere on the VR view to exit. Useful when the screen
            // is inside a Cardboard-class viewer and the back button is
            // hard to find. The GL view is the click target; the overlay
            // TextViews are not (they would dismiss the hint the moment
            // the user tried to tap-to-exit).
            isClickable = true
            setOnClickListener { finish() }
        }
        val exitHint = TextView(this).apply {
            text = "tap to exit"
            setTextColor(Color.WHITE)
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setBackgroundColor(0x66000000.toInt())
            setPadding(32, 16, 32, 16)
        }
        root.addView(
            exitHint,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            ).apply {
                topMargin = 32; rightMargin = 32
            },
        )

        // Top-left HUD pill: host, fps, frame count. Updated every 250ms.
        hudText = TextView(this).apply {
            setTextColor(Color.WHITE)
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setBackgroundColor(0x66000000.toInt())
            setPadding(32, 16, 32, 16)
            // Monospace would be nicer but we don't want a font dependency.
            text = "host=$hudHost  fps=…  frames=0"
        }
        root.addView(
            hudText,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START,
            ).apply {
                topMargin = 32; leftMargin = 32
            },
        )

        // Bottom-center connection-loss banner; starts hidden. Fades in
        // when the renderer hasn't seen a frame for >2 seconds.
        connLoss = TextView(this).apply {
            text = "No preview — connection lost"
            setTextColor(Color.WHITE)
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setBackgroundColor(0xCCB71C1C.toInt()) // translucent red
            setPadding(48, 24, 48, 24)
            visibility = View.GONE
        }
        root.addView(
            connLoss,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply {
                bottomMargin = 64
            },
        )

        // Tilt/recenter hint at bottom-left (small, fades after 2s).
        val centerHint = TextView(this).apply {
            text = "Tilt your head to recenter"
            setTextColor(Color.WHITE)
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setBackgroundColor(0x66000000.toInt())
            setPadding(32, 16, 32, 16)
        }
        root.addView(
            centerHint,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.START,
            ).apply {
                bottomMargin = 32; leftMargin = 32
            },
        )

        setContentView(root)

        // Fade the recenter hint out after 2 seconds. (The exit hint
        // and HUD stay on so the user can always see them.)
        Handler(Looper.getMainLooper()).postDelayed({
            centerHint.animate().alpha(0f).setDuration(400L).withEndAction {
                centerHint.visibility = View.GONE
            }.start()
        }, 2_000L)

        // 250ms HUD tick — reads renderer.fps() / frameCount / lastFrameMs.
        // We stop it in onPause and re-start in onResume.
        hudTickHandler = Handler(Looper.getMainLooper())
        hudTickHandler?.post(hudTick)

        // 3f. Create the preview controller ONCE here. onResume calls
        // startPreview() to re-arm the collect loop after a pause; the
        // controller itself is reused (start() is idempotent) so we don't
        // reset its state to Idle/Connecting on every foreground transition.
        preview = PreviewController(parent = lifecycleScope.coroutineContext[Job])
        startPreview()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        rotationVector = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }

    /**
     * 3f. Re-arm the preview pipeline after the activity returns to the
     * foreground. Mirrors [MainActivity.onResume] (which guards
     * `tryReconnectIfMarkerExists` with an `isInitialized` check): here
     * the guard is `collectJob?.isActive != true` so a no-op rotation
     * (onResume fired twice without an onPause in between) doesn't kick
     * the controller back to Connecting when it's already Streaming.
     */
    override fun onResume() {
        super.onResume()
        glView.onResume()
        rotationVector?.let {
            sensorManager?.registerListener(headListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        hudTickHandler?.post(hudTick)
        if (::preview.isInitialized) startPreview()
    }

    /**
     * 3f. Cancel the collect job and stop the transport so a backgrounded
     * activity doesn't keep the socket open. The controller itself is
     * reused on the next onResume — see [startPreview].
     */
    override fun onPause() {
        sensorManager?.unregisterListener(headListener)
        glView.onPause()
        stopPreview()
        stopMarkerBus()
        hudTickHandler?.removeCallbacks(hudTick)
        super.onPause()
    }

    /**
     * 3f. Release the controller's own [SupervisorJob]. lifecycleScope
     * cancels its own children on destroy, but the controller's job is
     * parented on the lifecycleScope's Job (not lifecycleScope itself),
     * so we have to do it explicitly.
     */
    override fun onDestroy() {
        if (::preview.isInitialized) preview.shutdown()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        finish()
    }

    /**
     * 3f. Start the MJPEG collect loop and (idempotently) the transport.
     * Called from onCreate and onResume. Safe to call when already running
     * — the `collectJob?.isActive` guard makes it a no-op.
     */
    private fun startPreview() {
        if (collectJob?.isActive == true) return
        // 3h-BUG: pass hudPort (seeded from EXTRA_PORT) instead of a
        // hard-coded 8080. Combined with the new EXTRA_PORT intent extra
        // in MainActivity this lets the user pick a non-default port.
        preview.start(hudHost, hudPort)
        collectJob = lifecycleScope.launch {
            preview.bytes.collect { jpeg -> if (jpeg != null) renderer.submitFrame(jpeg) }
        }
    }

    /**
     * 3f. Cancel the collect job and stop the transport. Idempotent —
     * called from onPause and from onDestroy's shutdown path.
     */
    private fun stopPreview() {
        collectJob?.cancel()
        collectJob = null
        if (::preview.isInitialized) preview.stop()
    }

    /**
     * #14. Subscribe to [MarkerStateBus] so the VR marker overlay updates
     * live when a re-solve completes while this activity is open. The
     * Intent-extras snapshot in [onCreate] seeds the first frame; this
     * collector takes over from there. StateFlow delivers the latest
     * emission to a new subscriber on collect, so any solve that
     * completed between onCreate and the first collect is captured.
     * No-op if already running (mirrors [startPreview]).
     */
    private fun startMarkerBus() {
        if (markerBusJob?.isActive == true) return
        markerBusJob = lifecycleScope.launch {
            MarkerStateBus.solve.collect { result -> renderer.setSolve(result) }
        }
    }

    /** #14. Idempotent; called from onPause to release the collector. */
    private fun stopMarkerBus() {
        markerBusJob?.cancel()
        markerBusJob = null
    }

    companion object {
        const val EXTRA_HOST = "dev.openpolaris.android.VR_HOST"
        const val EXTRA_PORT = "dev.openpolaris.android.VR_PORT"
        private const val TAG = "VRActivity"

        // Stream 7.4 — solve-target marker overlay (issue #11).
        // All seven are present only when the caller has a fresh solve
        // AND a target configured. Absent → marker is hidden.
        const val EXTRA_SOLVE_RA_DEG = "dev.openpolaris.android.VR_SOLVE_RA"
        const val EXTRA_SOLVE_DEC_DEG = "dev.openpolaris.android.VR_SOLVE_DEC"
        const val EXTRA_SOLVE_CONFIDENCE = "dev.openpolaris.android.VR_SOLVE_CONF"
        const val EXTRA_SOLVE_AGE_MS = "dev.openpolaris.android.VR_SOLVE_AGE_MS"
        const val EXTRA_TARGET_RA_DEG = "dev.openpolaris.android.VR_TARGET_RA"
        const val EXTRA_TARGET_DEC_DEG = "dev.openpolaris.android.VR_TARGET_DEC"
        const val EXTRA_FOV_X_DEG = "dev.openpolaris.android.VR_FOV_X"
        const val EXTRA_FOV_Y_DEG = "dev.openpolaris.android.VR_FOV_Y"
        const val DEFAULT_HOST = "192.168.43.1"
        const val DEFAULT_PORT = 8080
        // Marker dims after 5 min since the solve was recorded.
        const val MARKER_MAX_AGE_MS = 5L * 60L * 1000L
    }
}

/* ─────────── helpers ─────────── */

private fun qToYaw(q: FloatArray): Float {
    val x = q[0]; val y = q[1]; val z = q[2]; val w = q[3]
    val sinyCosp = 2f * (w * z + x * y)
    val cosyCosp = 1f - 2f * (y * y + z * z)
    return kotlin.math.atan2(sinyCosp, cosyCosp)
}

private fun qToPitch(q: FloatArray): Float {
    val x = q[0]; val y = q[1]; val z = q[2]; val w = q[3]
    val sinp = (2f * (w * x - y * z)).coerceIn(-1f, 1f)
    return kotlin.math.asin(sinp)
}

/* ─────────── GL renderer ─────────── */

/**
 * Draws the latest MJPEG frame twice (left/right) with Cardboard v1
 * barrel distortion. The vertex shader applies the lens warp; the
 * fragment shader just samples the texture.
 *
 * The "look around" effect is a small in-plane pan of the sampled
 * texture driven by yaw/pitch — cheap, dependency-free, and a credible
 * approximation of a true pose-driven view.
 */
class StereoRenderer : GLSurfaceView.Renderer {
    @Volatile private var latestJpeg: ByteArray? = null
    @Volatile private var yaw: Float = 0f
    @Volatile private var pitch: Float = 0f

    // FPS accounting: count frames every second, expose via [fps].
    // Used by the in-VR status HUD so the user can tell at a glance
    // whether the MJPEG stream is keeping up.
    @Volatile private var fps: Float = 0f
    private var fpsFrames: Int = 0
    private var fpsStartNs: Long = 0L

    /** Total number of MJPEG frames submitted to the renderer. */
    @Volatile var frameCount: Int = 0
        private set

    /** Read-only FPS (0–60 typical). Updated every second on the GL thread. */
    fun fps(): Float = fps

    /** Wall-clock millis of the last frame submission, or 0 if none yet. */
    @Volatile var lastFrameMs: Long = 0L
        private set

    private var program = 0
    private var lineProgram = 0
    private var textureId = 0
    private var width = 0
    private var height = 0

    private val mvpLeft = FloatArray(16)
    private val mvpRight = FloatArray(16)

    fun submitFrame(jpeg: ByteArray) {
        latestJpeg = jpeg
        frameCount++
        lastFrameMs = System.currentTimeMillis()
    }

    fun setYawPitch(y: Float, p: Float) {
        yaw = y
        pitch = p
    }

    // Stream 7.4 — plate-solve target marker overlay (issue #11).
    // All eight are written by [setSolveTarget] from the UI thread and
    // read by the GL thread inside [drawSolveMarker] once per frame.
    // @Volatile guarantees publication of each field individually;
    // the GL thread reads them in a stable order so any inconsistency
    // is at most one frame's worth, and the confidence/fov values are
    // coerced at the setter so out-of-range extras can't crash the
    // draw path.
    @Volatile private var solveFieldRaDeg: Double = 0.0
    @Volatile private var solveFieldDecDeg: Double = 0.0
    @Volatile private var solveTargetRaDeg: Double = 0.0
    @Volatile private var solveTargetDecDeg: Double = 0.0
    @Volatile private var solveConfidence: Float = 0f
    @Volatile private var solveFovXDeg: Float = 0f
    @Volatile private var solveFovYDeg: Float = 0f
    @Volatile private var solveRecordedAtMs: Long = 0L
    /** True when the launching Intent supplied a valid solve + target. */
    @Volatile private var hasSolveTarget: Boolean = false

    fun setSolveTarget(
        fieldRaDeg: Double,
        fieldDecDeg: Double,
        targetRaDeg: Double,
        targetDecDeg: Double,
        confidence: Float,
        ageMs: Long,
        fovXDeg: Float,
        fovYDeg: Float,
    ) {
        val c = confidence.coerceIn(0f, 1f)
        // Stream 15.2 (issue #15): log and clamp — don't silently
        // substitute a hard-coded 60°/45°. The minimum (1°) mirrors the
        // `require(fovXDeg > 0f)` validation in [CameraProfile] so a
        // bogus extra never reaches the projector, but the marker is
        // still drawn (badly positioned) rather than crashing. The
        // log is the audit trail.
        val fx = if (fovXDeg > 0f) fovXDeg else {
            Log.w(TAG, "setSolveTarget: fovXDeg=$fovXDeg <= 0, clamping to 1°")
            1f
        }
        val fy = if (fovYDeg > 0f) fovYDeg else {
            Log.w(TAG, "setSolveTarget: fovYDeg=$fovYDeg <= 0, clamping to 1°")
            1f
        }
        solveFieldRaDeg = fieldRaDeg
        solveFieldDecDeg = fieldDecDeg
        solveTargetRaDeg = targetRaDeg
        solveTargetDecDeg = targetDecDeg
        solveConfidence = c
        solveFovXDeg = fx
        solveFovYDeg = fy
        // The launch-time age, recorded against the system clock at
        // launch, lets [drawSolveMarker] age out cleanly without a
        // second source of truth.
        solveRecordedAtMs = System.currentTimeMillis() - ageMs
        hasSolveTarget = true
    }

    /**
     * Update only the four solve-side fields (RA/Dec/confidence/timestamp)
     * without touching the target, FoV, or `hasSolveTarget` flag. Used by
     * the live [MarkerStateBus] collector (issue #14) so a re-solve while
     * VR is open updates the marker within one frame, without clobbering
     * the launch-time seed.
     *
     * `null` hides the marker but keeps the target/FoV in place — the
     * next non-null emission restores it.
     */
    fun setSolve(result: dev.openpolaris.core.solver.SolveResult?) {
        if (result == null) {
            solveFieldRaDeg = 0.0
            solveFieldDecDeg = 0.0
            solveConfidence = 0f
            // Leave solveRecordedAtMs untouched; drawSolveMarker checks
            // confidence == 0f and short-circuits before reading age.
            return
        }
        solveFieldRaDeg = result.raDeg
        solveFieldDecDeg = result.decDeg
        solveConfidence = result.confidence.toFloat().coerceIn(0f, 1f)
        // The new solve's recorded-at timestamp, so the marker honestly
        // ages from the *new* solve (not the launch-time one). See #12.
        solveRecordedAtMs = if (result.timestampMs > 0L) result.timestampMs
            else System.currentTimeMillis()
    }

    override fun onSurfaceCreated(gl: GLES20?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        // Shader source lives in `commonMain` (`VrStereoShaders`) so its
        // constants can be cross-checked from a JVM unit test. The
        // corrected mapping is: flat quad per eye, pan lives in vUV.
        val vsh = VrStereoShaders.VERTEX_SHADER_SRC.trimIndent()
        val fsh = VrStereoShaders.FRAGMENT_SHADER_SRC.trimIndent()
        program = linkProgram(vsh, fsh)
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        textureId = tex[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        // Tiny "line" shader: positions in NDC, output a uniform colour.
        // Used for the crosshair overlay drawn per eye.
        lineProgram = linkProgram(
            "attribute vec2 aPos; varying vec2 vNdc; void main() { vNdc = aPos; gl_Position = vec4(aPos, 0.0, 1.0); }",
            "precision mediump float; uniform vec4 uColor; varying vec2 vNdc; void main() { gl_FragColor = uColor; }"
        )
    }

    override fun onSurfaceChanged(gl: GLES20?, w: Int, h: Int) {
        width = w
        height = h
        Matrix.setIdentityM(mvpLeft, 0)
        Matrix.setIdentityM(mvpRight, 0)
    }

    override fun onDrawFrame(gl: GLES20?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        uploadTexture()

        val halfW = width / 2
        val eyeHeight = height

        GLES20.glViewport(0, 0, halfW, eyeHeight)
        drawEye(-0.08f, halfW.toFloat(), eyeHeight.toFloat())
        drawCrosshair(halfW.toFloat(), eyeHeight.toFloat())
        drawSolveMarker(halfW.toFloat(), eyeHeight.toFloat())

        GLES20.glViewport(halfW, 0, halfW, eyeHeight)
        drawEye(0.08f, halfW.toFloat(), eyeHeight.toFloat())
        drawCrosshair(halfW.toFloat(), eyeHeight.toFloat())
        drawSolveMarker(halfW.toFloat(), eyeHeight.toFloat())

        // FPS accounting — once per second, take a sample.
        fpsFrames++
        if (fpsStartNs == 0L) fpsStartNs = System.nanoTime()
        val elapsed = System.nanoTime() - fpsStartNs
        if (elapsed >= 1_000_000_000L) {
            fps = fpsFrames * 1_000_000_000f / elapsed
            fpsFrames = 0
            fpsStartNs = System.nanoTime()
        }
    }

    /**
     * Draws a crosshair in the centre of the currently-bound viewport.
     * Two passes: a faint black drop-shadow underneath, then a thin
     * white reticle on top. Uses a small dedicated "line" shader so
     * it does not need a texture or UV coords.
     *
     * Coordinates are passed straight to `gl_Position` in NDC, so the
     * segments are interpreted in the [-1, 1] y / [-aspect, +aspect] x
     * space of the ortho projection set up by [drawEye]. A 0.015 gap
     * and 0.04 line length keep the reticle near the centre.
     */
    private fun drawCrosshair(viewW: Float, viewH: Float) {
        val aspect = viewW / viewH
        val gap = 0.015f
        val len = 0.04f

        fun horiz(y: Float, xMin: Float, xMax: Float) = floatArrayOf(xMin, y, xMax, y)
        fun vert(x: Float, yMin: Float, yMax: Float) = floatArrayOf(x, yMin, x, yMax)

        val segments = floatArrayOf(
            *horiz(0f, -gap, -(gap + len)),
            *horiz(0f,  gap,  (gap + len)),
            *vert(0f,  -gap, -(gap + len)),
            *vert(0f,   gap,  (gap + len))
        )

        // 1) Black drop-shadow nudged slightly to be visible.
        drawLines(segments, offsetX = 0.003f, offsetY = -0.003f,
            color = floatArrayOf(0f, 0f, 0f, 0.85f), lineWidth = 3f, aspect = aspect)
        // 2) White reticle on top.
        drawLines(segments, offsetX = 0f, offsetY = 0f,
            color = floatArrayOf(1f, 1f, 1f, 1f), lineWidth = 2f, aspect = aspect)
    }

    /**
     * Stream 7.4 — draws a small circle at the on-screen position of the
     * GoTo target, offset from the field centre by the angular separation
     * between the plate-solve result (field) and the configured target.
     *
     * Math lives in [SolveTargetScreenPos] (commonMain, JVM-tested). This
     * method is only the renderer: it asks the math for (x, y) in the
     * `[-0.5, +0.5]` visible-disc space, converts to per-eye NDC, draws a
     * 16-segment circle outline, and colours it by solve confidence.
     *
     * The marker dims and hides as the solve ages (default 5 min — see
     * [companion.MARKER_MAX_AGE_MS] in VRActivity), so an obsolete solve
     * doesn't lie to the user about where the target is.
     *
     * No-op when [setSolveTarget] was never called or the solve has
     * aged out. Cheap on purpose: 32 vertices, 1 draw call, reuses the
     * existing [lineProgram] shader so we don't add GL state.
     */
    private fun drawSolveMarker(viewW: Float, viewH: Float) {
        if (!hasSolveTarget) return
        val now = System.currentTimeMillis()
        val age = now - solveRecordedAtMs
        if (age < 0L || age > MARKER_MAX_AGE_MS) return

        // The math is a pure data class: it expects (field, target)
        // SolveResult snapshots that the UI thread captures at launch
        // time. matchedStars=4 is a safe lower bound (the data class
        // init requires >= 3) and signals "trustworthy enough" without
        // bringing in a synthetic timestamp.
        val fieldSolve = SolveResult(
            raDeg = solveFieldRaDeg,
            decDeg = solveFieldDecDeg,
            confidence = 1.0,
            matchedStars = 4,
        )
        val targetSolve = SolveResult(
            raDeg = solveTargetRaDeg,
            decDeg = solveTargetDecDeg,
            confidence = 1.0,
            matchedStars = 4,
        )
        val pos = SolveTargetProjector.project(
            target = targetSolve,
            field = fieldSolve,
            fovXDeg = solveFovXDeg.toDouble(),
            fovYDeg = solveFovYDeg.toDouble(),
        )
        val aspect = viewW / viewH

        // SolveTargetProjector returns the raw (x, y) in [-0.5, +0.5]
        // visible-disc space; anything outside is offScreen. When
        // offScreen we snap the magnitude to ~0.95 of the disc so the
        // user can see which way to slew.
        val (cx, cy) = if (pos.offScreen) {
            val e = pos.clampedToEdge()
            e.x to e.y
        } else {
            pos.x to pos.y
        }

        // Convert visible-disc coords to NDC for this eye.
        val ndcX = (2f * aspect * cx).toFloat()
        val ndcY = (2f * cy).toFloat()

        // Marker geometry: 16-segment circle outline in NDC. Radius
        // chosen to be readable at 60° FoV but not so big it overlaps
        // the crosshair. ~3% of the screen height.
        val radius = 0.03f
        val segments = 16
        val verts = FloatArray(segments * 4) // (x0, y0, x1, y1) per segment
        for (i in 0 until segments) {
            val theta0 = (2.0 * Math.PI * i / segments).toFloat()
            val theta1 = (2.0 * Math.PI * (i + 1) / segments).toFloat()
            val j = i * 4
            verts[j]     = ndcX + radius * kotlin.math.cos(theta0)
            verts[j + 1] = ndcY + radius * kotlin.math.sin(theta0)
            verts[j + 2] = ndcX + radius * kotlin.math.cos(theta1)
            verts[j + 3] = ndcY + radius * kotlin.math.sin(theta1)
        }

        // Confidence → colour and alpha. Spec from issue #11:
        //   conf ≥ 0.8 → green
        //   0.6 ≤ conf < 0.8 → yellow
        //   conf < 0.6 → white-dim
        // Alpha additionally fades with age so the marker visibly dies
        // instead of popping off at exactly 5 min.
        val conf = solveConfidence
        val ageFrac = (1f - age.toFloat() / MARKER_MAX_AGE_MS).coerceIn(0f, 1f)
        val baseAlpha = if (conf >= 0.8f) {
            floatArrayOf(0.2f, 1f, 0.2f, 0.9f)
        } else if (conf >= 0.6f) {
            floatArrayOf(1f, 0.85f, 0.2f, 0.85f)
        } else {
            floatArrayOf(0.85f, 0.85f, 0.85f, 0.6f)
        }
        val color = floatArrayOf(baseAlpha[0], baseAlpha[1], baseAlpha[2], baseAlpha[3] * ageFrac)

        // Drop shadow first for legibility on bright nebulosity.
        drawLines(verts, offsetX = 0.003f, offsetY = -0.003f,
            color = floatArrayOf(0f, 0f, 0f, 0.7f), lineWidth = 3f, aspect = aspect)
        drawLines(verts, offsetX = 0f, offsetY = 0f,
            color = color, lineWidth = 2f, aspect = aspect)
    }

    /**
     * Renders a set of line segments in NDC. Each segment is two
     * consecutive vertices (x0, y0, x1, y1). Offsets are applied to
     * each vertex; the `aspect` parameter is currently unused but is
     * accepted so the call site mirrors the eye's ortho projection
     * (and so future per-aspect math can be added without churn).
     */
    private fun drawLines(
        segments: FloatArray,
        offsetX: Float,
        offsetY: Float,
        color: FloatArray,
        lineWidth: Float,
        @Suppress("UNUSED_PARAMETER") aspect: Float
    ) {
        val ndc = FloatArray(segments.size)
        for (i in 0 until segments.size step 2) {
            ndc[i] = segments[i] + offsetX
            ndc[i + 1] = segments[i + 1] + offsetY
        }
        val buf = java.nio.ByteBuffer.allocateDirect(ndc.size * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(ndc).apply { position(0) }

        GLES20.glUseProgram(lineProgram)
        val posLoc = GLES20.glGetAttribLocation(lineProgram, "aPos")
        val colLoc = GLES20.glGetUniformLocation(lineProgram, "uColor")
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 8, buf)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glUniform4fv(colLoc, 1, color, 0)
        GLES20.glLineWidth(lineWidth)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, segments.size / 2)
        GLES20.glDisableVertexAttribArray(posLoc)
        // Reset line width for safety.
        GLES20.glLineWidth(1f)
    }

    private fun drawEye(eyeOffset: Float, viewW: Float, viewH: Float) {
        val aspect = viewW / viewH
        val left = -aspect
        val right = aspect
        val bottom = -1f
        val top = 1f
        val near = -1f
        val far = 1f
        val proj = FloatArray(16)
        val mvp = FloatArray(16)
        Matrix.orthoM(proj, 0, left, right, bottom, top, near, far)
        Matrix.multiplyMM(mvp, 0, proj, 0, if (eyeOffset < 0f) mvpLeft else mvpRight, 0)

        GLES20.glUseProgram(program)
        val posLoc = GLES20.glGetAttribLocation(program, "aPos")
        val uvLoc = GLES20.glGetAttribLocation(program, "aUV")
        val mvpLoc = GLES20.glGetUniformLocation(program, "uMVP")
        val eyeLoc = GLES20.glGetUniformLocation(program, "uEyeOffset")
        val yawLoc = GLES20.glGetUniformLocation(program, "uYawPan")
        val pitchLoc = GLES20.glGetUniformLocation(program, "uPitchPan")
        val texLoc = GLES20.glGetUniformLocation(program, "uTex")

        val verts = floatArrayOf(
            -1f, -1f, 0f, 1f,
             1f, -1f, 1f, 1f,
            -1f,  1f, 0f, 0f,
             1f,  1f, 1f, 0f
        )
        val buf = java.nio.ByteBuffer.allocateDirect(verts.size * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(verts).apply { position(0) }

        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 16, buf)
        buf.position(2)
        GLES20.glVertexAttribPointer(uvLoc, 2, GLES20.GL_FLOAT, false, 16, buf)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glEnableVertexAttribArray(uvLoc)

        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvp, 0)
        GLES20.glUniform2f(eyeLoc, eyeOffset, 0f)
        GLES20.glUniform1f(yawLoc, yaw.coerceIn(-0.6f, 0.6f))
        GLES20.glUniform1f(pitchLoc, pitch.coerceIn(-0.5f, 0.5f))
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(texLoc, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(uvLoc)
    }

    private fun uploadTexture() {
        val jpeg = latestJpeg ?: return
        val bmp = android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
    }

    private fun linkProgram(vsh: String, fsh: String): Int {
        val v = compile(GLES20.GL_VERTEX_SHADER, vsh)
        val f = compile(GLES20.GL_FRAGMENT_SHADER, fsh)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        return p
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        return s
    }
}
