package dev.openpolaris.android

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dev.openpolaris.ui.CompassHeadingSource
import kotlin.math.PI

/** Rotation-vector compass input used only to populate the Astro heading field. */
class AndroidCompassHeadingSource(context: Context) : CompassHeadingSource {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    override val supported: Boolean get() = rotationSensor != null

    private val listener = object : SensorEventListener {
        private val rotation = FloatArray(9)
        private val orientation = FloatArray(3)

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
            SensorManager.getRotationMatrixFromVector(rotation, event.values)
            SensorManager.getOrientation(rotation, orientation)
            val degrees = (orientation[0] * 180.0 / PI + 360.0) % 360.0
            onHeading?.invoke(degrees)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private var onHeading: ((Double) -> Unit)? = null

    override fun start(onHeading: (Double) -> Unit, onUnavailable: (String) -> Unit) {
        stop()
        if (sensorManager == null || rotationSensor == null) {
            onUnavailable("This device does not expose a rotation-vector compass sensor")
            return
        }
        this.onHeading = onHeading
        if (!sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)) {
            this.onHeading = null
            onUnavailable("Android refused the compass sensor registration")
        }
    }

    override fun stop() {
        sensorManager?.unregisterListener(listener)
        onHeading = null
    }
}
