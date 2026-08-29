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

        val host = intent.getStringExtra(EXTRA_HOST) ?: DEFAULT_HOST

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
            // hard to find. The GL view is the click target; the hint
            // TextView is not (it would dismiss the hint the moment the
            // user tried to tap-to-exit).
            isClickable = true
            setOnClickListener { finish() }
        }
        val hint = TextView(this).apply {
            text = "Tilt your head to recenter — tap to exit"
            setTextColor(Color.WHITE)
            setShadowLayer(6f, 0f, 0f, Color.BLACK)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            // Translucent black background so the hint is legible over
            // arbitrary camera content (e.g. a bright sky).
            setBackgroundColor(0x66000000.toInt())
            setPadding(48, 24, 48, 24)
        }
        root.addView(
            hint,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        setContentView(root)

        // Fade the hint out after 2 seconds. We don't remove it from the
        // view tree — the user can still see it briefly if they re-enter
        // the activity. A delayed alpha animation is enough.
        Handler(Looper.getMainLooper()).postDelayed({
            hint.animate().alpha(0f).setDuration(400L).withEndAction {
                hint.visibility = View.GONE
            }.start()
        }, 2_000L)

        preview = PreviewController(parent = lifecycleScope.coroutineContext[Job])
        preview.start(host, 8080)
        collectJob = lifecycleScope.launch {
            preview.bytes.collect { jpeg -> if (jpeg != null) renderer.submitFrame(jpeg) }
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        rotationVector = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
        rotationVector?.let {
            sensorManager?.registerListener(headListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        sensorManager?.unregisterListener(headListener)
        glView.onPause()
        collectJob?.cancel()
        preview.stop()
        super.onPause()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        finish()
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

    private var program = 0
    private var textureId = 0
    private var width = 0
    private var height = 0

    private val mvpLeft = FloatArray(16)
    private val mvpRight = FloatArray(16)

    fun submitFrame(jpeg: ByteArray) {
        latestJpeg = jpeg
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

        GLES20.glViewport(halfW, 0, halfW, eyeHeight)
        drawEye(0.08f, halfW.toFloat(), eyeHeight.toFloat())
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
