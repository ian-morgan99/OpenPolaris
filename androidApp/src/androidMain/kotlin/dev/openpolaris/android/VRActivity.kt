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
import dev.openpolaris.core.domain.PreviewController
import dev.openpolaris.core.domain.VrStereoShaders
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

    // Overlay widgets (set in onCreate; read by the HUD tick).
    private lateinit var hudText: TextView
    private lateinit var connLoss: TextView
    private var hudHost: String = DEFAULT_HOST
    private var hudTickHandler: Handler? = null
    private val hudTick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val last = renderer.lastFrameMs
            val stale = last == 0L || now - last > 2_000L
            val fps = renderer.fps()
            val frames = renderer.frameCount
            hudText.text = "host=$hudHost  fps=${"%.1f".format(fps)}  frames=$frames"
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

        renderer = StereoRenderer()
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
        preview.start(hudHost, 8080)
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

    companion object {
        const val EXTRA_HOST = "dev.openpolaris.android.VR_HOST"
        const val DEFAULT_HOST = "192.168.43.1"
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

        GLES20.glViewport(halfW, 0, halfW, eyeHeight)
        drawEye(0.08f, halfW.toFloat(), eyeHeight.toFloat())
        drawCrosshair(halfW.toFloat(), eyeHeight.toFloat())

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
