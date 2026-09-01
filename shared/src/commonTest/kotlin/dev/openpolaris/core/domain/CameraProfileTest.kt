package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class CameraProfileTest {
    @Test
    fun polarisEyepieceIsPerMountDefaultWith60By45() {
        val p = CameraProfile.PolarisEyepiece
        assertEquals(60f, p.fovXDeg, "eyepiece horizontal FoV must match the historical VRActivity constant")
        assertEquals(45f, p.fovYDeg, "eyepiece vertical FoV must match the historical VRActivity constant")
        assertEquals(CameraProfileSource.PER_MOUNT_DEFAULT, p.source)
    }

    @Test
    fun polarisEyepieceIsSingleton() {
        // Multiple reads should hand back the same instance so equality
        // checks anywhere in the codebase stay referentially stable.
        assertSame(CameraProfile.PolarisEyepiece, CameraProfile.PolarisEyepiece)
    }

    @Test
    fun rejectsZeroFovX() {
        val ex = assertFailsWith<IllegalArgumentException> {
            CameraProfile(fovXDeg = 0f, fovYDeg = 45f, source = CameraProfileSource.SENSOR)
        }
        assert(ex.message!!.contains("fovXDeg"))
    }

    @Test
    fun rejectsNegativeFovX() {
        val ex = assertFailsWith<IllegalArgumentException> {
            CameraProfile(fovXDeg = -10f, fovYDeg = 45f, source = CameraProfileSource.SENSOR)
        }
        assert(ex.message!!.contains("fovXDeg"))
    }

    @Test
    fun rejectsZeroFovY() {
        val ex = assertFailsWith<IllegalArgumentException> {
            CameraProfile(fovXDeg = 60f, fovYDeg = 0f, source = CameraProfileSource.SENSOR)
        }
        assert(ex.message!!.contains("fovYDeg"))
    }

    @Test
    fun rejectsNegativeFovY() {
        val ex = assertFailsWith<IllegalArgumentException> {
            CameraProfile(fovXDeg = 60f, fovYDeg = -1f, source = CameraProfileSource.SENSOR)
        }
        assert(ex.message!!.contains("fovYDeg"))
    }

    @Test
    fun acceptsOneDegreeAsTheMinimum() {
        // 1° is the smallest meaningful FoV; the validation must accept
        // it because VRActivity's clamp path emits exactly this value
        // when the Intent extra is missing or bogus.
        val p = CameraProfile(fovXDeg = 1f, fovYDeg = 1f, source = CameraProfileSource.OVERRIDE)
        assertEquals(1f, p.fovXDeg)
        assertEquals(1f, p.fovYDeg)
    }

    @Test
    fun sourceLabelIsPreserved() {
        val sensor = CameraProfile(70f, 50f, CameraProfileSource.SENSOR)
        val override = CameraProfile(90f, 70f, CameraProfileSource.OVERRIDE)
        val default = CameraProfile(60f, 45f, CameraProfileSource.PER_MOUNT_DEFAULT)
        assertEquals(CameraProfileSource.SENSOR, sensor.source)
        assertEquals(CameraProfileSource.OVERRIDE, override.source)
        assertEquals(CameraProfileSource.PER_MOUNT_DEFAULT, default.source)
    }

    @Test
    fun equalityIsByValue() {
        val a = CameraProfile(60f, 45f, CameraProfileSource.PER_MOUNT_DEFAULT)
        val b = CameraProfile(60f, 45f, CameraProfileSource.PER_MOUNT_DEFAULT)
        // data class — equality is structural, but the eyepiece is a
        // distinct instance. This test pins both behaviours so a future
        // refactor (e.g. turning it into a value class) has to update
        // the assertions explicitly.
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(CameraProfile.PolarisEyepiece, a)
    }
}
