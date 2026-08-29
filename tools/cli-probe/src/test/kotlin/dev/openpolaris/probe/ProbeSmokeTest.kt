package dev.openpolaris.probe

import dev.openpolaris.core.domain.AlignmentController
import dev.openpolaris.core.domain.AutoLevelController
import dev.openpolaris.core.domain.GoToController
import dev.openpolaris.core.domain.JvmConnection
import dev.openpolaris.core.domain.MountSession
import dev.openpolaris.core.domain.PreviewController
import dev.openpolaris.core.domain.TrackingController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end smoke tests for the cli-probe stack.
 *
 * Wires a real socket against [FakeMount] (port 0 → ephemeral), then drives
 * every controller through the production `MountSession` API. The goal is to
 * catch wire-format / session-handshake / controller-marshalling regressions
 * that the in-memory `FakeConnection` tests in `shared/commonTest` can't see.
 */
class ProbeSmokeTest {

    private lateinit var harness: FakeMountHarness

    @BeforeTest
    fun setUp() {
        harness = FakeMountHarness().also { it.start() }
    }

    @AfterTest
    fun tearDown() {
        harness.stop()
    }

    @Test
    fun mount_session_connect_succeeds() = runTest {
        val ok = harness.session.connect()
        assertTrue(ok, "MountSession.connect() should succeed against FakeMount")
        assertTrue(harness.session.state.value.connected, "state should report connected")
    }

    @Test
    fun tracking_round_trip() = runTest {
        harness.session.connect()
        val tracking = TrackingController(harness.session)
        tracking.start(speed = 2) // 2 == solar-like index
        tracking.setHalfSpeed(on = false)
        tracking.gotoAzAlt(azimuthDeg = 180.0, altitudeDeg = 45.0)
        // No exceptions thrown ⇒ round-trip ok; verify state still healthy.
        assertTrue(harness.session.state.value.connected)
    }

    @Test
    fun auto_level_toggle_persists() = runTest {
        harness.session.connect()
        val autoLevel = AutoLevelController(harness.session)
        autoLevel.start(this)
        try {
            autoLevel.setEnabled(true)
            assertEquals(true, autoLevel.isEnabled.value, "setEnabled(true) should reflect immediately")
            autoLevel.setEnabled(false)
            assertEquals(false, autoLevel.isEnabled.value, "setEnabled(false) should reflect immediately")
        } finally {
            autoLevel.stop()
        }
    }

    @Test
    fun auto_level_read_tilt_round_trip() = runBlocking {
        harness.session.connect()
        val autoLevel = AutoLevelController(harness.session)
        val scope = CoroutineScope(Dispatchers.Default)
        autoLevel.start(scope)
        try {
            val tilt = withTimeout(2_000) { autoLevel.readTilt() }
            assertNotNull(tilt, "AutoLevelController should observe the FakeMount 537 reply")
            assertEquals(0.10, tilt!!.pitchDeg, 1e-6, "pitch should match FakeMount script")
            assertEquals(-0.05, tilt.rollDeg, 1e-6, "roll should match FakeMount script")
        } finally {
            autoLevel.stop()
            scope.cancel()
        }
    }

    @Test
    fun goto_acknowledged() = runTest {
        harness.session.connect()
        // GoToController requires a TrackingController; harness wires one.
        harness.goto.goToRaDec(
            raDeg = 0.0,
            decDeg = 0.0,
            latDeg = 0.0,
            lngEastDeg = 0.0,
            jdUtc = 0.0,
            toleranceDeg = 1.0,
            timeoutMs = 1_000,
        )
        // cancel() sends 519; FakeMount acks with `1&519&2&ack:1;#`.
        harness.goto.cancel()
        assertTrue(harness.session.state.value.connected)
    }

    @Test
    fun jog_acknowledged() = runTest {
        harness.session.connect()
        // Code 513 = X jog; FakeMount echoes dir:X;lvl:1; ack.
        harness.tracking.jog(code = 513, durationMs = 100)
        assertTrue(harness.session.state.value.connected)
    }

    @Test
    fun alignment_controller_constructs() = runTest {
        harness.session.connect()
        assertNotNull(harness.alignment, "AlignmentController should be constructable against a live session")
        assertEquals(0, harness.alignment.starCount, "no stars submitted yet")
    }

    @Test
    fun preview_controller_constructs() = runTest {
        // PreviewController is decoupled from MountSession — it just wraps a
        // transportFactory. Smoke-test the no-arg path.
        val preview = PreviewController()
        assertEquals(PreviewController.State.Idle, preview.state.value)
    }
}

/**
 * Per-test fixture: starts [FakeMount] on an ephemeral port, opens a real
 * [JvmConnection] via [MountSession], and exposes all five controllers. Tears
 * the whole stack down in [stop]. Run on a [SupervisorJob] so one failed
 * controller doesn't poison the rest.
 */
class FakeMountHarness {
    private lateinit var mount: FakeMount
    private lateinit var scope: CoroutineScope
    private lateinit var connection: JvmConnection

    lateinit var session: MountSession
        private set

    lateinit var tracking: TrackingController
        private set
    lateinit var goto: GoToController
        private set
    lateinit var autoLevel: AutoLevelController
        private set
    lateinit var alignment: AlignmentController
        private set
    lateinit var preview: PreviewController
        private set

    fun start() {
        // 0 → ephemeral port assigned by the OS; the real port is exposed via
        // `mount.localPort` after start().
        mount = FakeMount(port = 0)
        mount.start()
        val port = mount.localPort
        connection = JvmConnection()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        session = MountSession(
            connectionFactory = { connection },
            host = "127.0.0.1",
            port = port,
        )
        tracking = TrackingController(session)
        // GoToController requires a TrackingController — it reuses it for the
        // slew-rate lookup and to halt tracking on cancel.
        goto = GoToController(session, tracking)
        autoLevel = AutoLevelController(session)
        alignment = AlignmentController(session)
        // PreviewController is independent of the control socket; the harness
        // exposes it for symmetry but tests may also construct their own.
        preview = PreviewController()
    }

    fun stop() {
        runCatching { session.disconnect() }
        runCatching { connection.close() }
        runCatching { mount.stop() }
        runCatching { scope.cancel() }
    }
}
