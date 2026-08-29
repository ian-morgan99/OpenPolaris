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

    private lateinit var harness: MountHarness
    private var harnessFailure: Throwable? = null

    /**
     * Each test should call this first (or access [h] instead) so a
     * failed `setUp` (e.g. real mount unreachable) surfaces as the real
     * cause instead of `lateinit property harness has not been initialized`.
     */
    private val h: MountHarness
        get() = harnessFailure?.let { throw it } ?: harness

    @BeforeTest
    fun setUp() {
        // Capture startup failures (e.g. real mount unreachable) so every
        // test reports the real cause rather than the downstream
        // `lateinit property harness has not been initialized` symptom.
        try {
            harness = newHarness().also { it.start() }
        } catch (t: Throwable) {
            harnessFailure = t
        }
    }

    @AfterTest
    fun tearDown() {
        if (this::harness.isInitialized) {
            harness.stop()
        }
    }

    /**
     * Returns a [FakeMountHarness] (default) or a [RealMountHarness] when
     * `-Popenpolaris.realMount=true` is passed. The real-mount path is
     * exercised by `./gradlew smokeReal` and requires a physical Polaris
     * reachable at `openpolaris.realMount.host:openpolaris.realMount.port`
     * (defaults `192.168.0.1:9090`).
     */
    private fun newHarness(): MountHarness {
        val real = System.getProperty("openpolaris.realMount")?.toBoolean() == true
        return if (real) {
            val host = System.getProperty("openpolaris.realMount.host") ?: "192.168.0.1"
            val port = (System.getProperty("openpolaris.realMount.port") ?: "9090").toInt()
            RealMountHarness(host, port)
        } else {
            FakeMountHarness()
        }
    }

    @Test
    fun mount_session_connect_succeeds() = runTest {
        val ok = h.session.connect()
        assertTrue(ok, "MountSession.connect() should succeed against FakeMount")
        assertTrue(h.session.state.value.connected, "state should report connected")
    }

    @Test
    fun tracking_round_trip() = runTest {
        h.session.connect()
        val tracking = TrackingController(h.session)
        tracking.start(speed = 2) // 2 == solar-like index
        tracking.setHalfSpeed(on = false)
        tracking.gotoAzAlt(azimuthDeg = 180.0, altitudeDeg = 45.0)
        // No exceptions thrown ⇒ round-trip ok; verify state still healthy.
        assertTrue(h.session.state.value.connected)
    }

    @Test
    fun auto_level_toggle_persists() = runTest {
        h.session.connect()
        val autoLevel = AutoLevelController(h.session)
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
        h.session.connect()
        val autoLevel = AutoLevelController(h.session)
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
        h.session.connect()
        // GoToController requires a TrackingController; harness wires one.
        h.goto.goToRaDec(
            raDeg = 0.0,
            decDeg = 0.0,
            latDeg = 0.0,
            lngEastDeg = 0.0,
            jdUtc = 0.0,
            toleranceDeg = 1.0,
            timeoutMs = 1_000,
        )
        // cancel() sends 519; FakeMount acks with `1&519&2&ack:1;#`.
        h.goto.cancel()
        assertTrue(h.session.state.value.connected)
    }

    @Test
    fun jog_acknowledged() = runTest {
        h.session.connect()
        // Code 513 = X jog; FakeMount echoes dir:X;lvl:1; ack.
        h.tracking.jog(code = 513, durationMs = 100)
        assertTrue(h.session.state.value.connected)
    }

    @Test
    fun alignment_controller_constructs() = runTest {
        h.session.connect()
        assertNotNull(h.alignment, "AlignmentController should be constructable against a live session")
        assertEquals(0, h.alignment.starCount, "no stars submitted yet")
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
 * Per-test fixture: spins up a `MountSession` wired to *something* on
 * `127.0.0.1:<port>` and exposes the five controllers used by the
 * smoke tests. Two implementations:
 *  - [FakeMountHarness]: in-process [FakeMount] on an ephemeral port.
 *  - [RealMountHarness]: a real Polaris on `host:port`. Used by
 *    `./gradlew smokeReal` after the user turns the mount on and
 *    installs the polkit rule.
 *
 * `stop()` tears the whole stack down. Backed by a [SupervisorJob] so
 * one failed controller doesn't poison the rest.
 */
abstract class MountHarness {
    abstract val session: MountSession
    abstract val tracking: TrackingController
    abstract val goto: GoToController
    abstract val autoLevel: AutoLevelController
    abstract val alignment: AlignmentController
    abstract val preview: PreviewController
    abstract fun start()
    abstract fun stop()
}

/**
 * Per-test fixture: starts [FakeMount] on an ephemeral port, opens a real
 * [JvmConnection] via [MountSession], and exposes all five controllers. Tears
 * the whole stack down in [stop]. Run on a [SupervisorJob] so one failed
 * controller doesn't poison the rest.
 */
class FakeMountHarness : MountHarness() {
    private lateinit var mount: FakeMount
    private lateinit var scope: CoroutineScope
    private lateinit var connection: JvmConnection

    override lateinit var session: MountSession
        private set

    override lateinit var tracking: TrackingController
        private set
    override lateinit var goto: GoToController
        private set
    override lateinit var autoLevel: AutoLevelController
        private set
    override lateinit var alignment: AlignmentController
        private set
    override lateinit var preview: PreviewController
        private set

    override fun start() {
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

    override fun stop() {
        runCatching { session.disconnect() }
        runCatching { connection.close() }
        runCatching { mount.stop() }
        runCatching { scope.cancel() }
    }
}

/**
 * Per-test fixture: connects to a **real** Polaris at [host]:[port]. Same
 * controller surface as [FakeMountHarness] so the smoke tests can run
 * unchanged. The real mount is left in whatever state the tests leave it
 * (e.g. tracking on, half-speed off) — callers are expected to follow up
 * with a manual reset.
 *
 * Used by `./gradlew smokeReal -Popenpolaris.realMount=true`.
 */
class RealMountHarness(
    private val host: String,
    private val port: Int,
) : MountHarness() {
    private lateinit var scope: CoroutineScope
    private lateinit var connection: JvmConnection

    override lateinit var session: MountSession
        private set

    override lateinit var tracking: TrackingController
        private set
    override lateinit var goto: GoToController
        private set
    override lateinit var autoLevel: AutoLevelController
        private set
    override lateinit var alignment: AlignmentController
        private set
    override lateinit var preview: PreviewController
        private set

    override fun start() {
        connection = JvmConnection()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        session = MountSession(
            connectionFactory = { connection },
            host = host,
            port = port,
        )
        // Connect up front. `connect()` issues the 519 lifecycle handshake
        // and surfaces connection failures before any test fires a command.
        runBlocking(Dispatchers.IO) {
            val ok = session.connect()
            check(ok) { "real mount at $host:$port is unreachable" }
        }
        tracking = TrackingController(session)
        goto = GoToController(session, tracking)
        autoLevel = AutoLevelController(session)
        alignment = AlignmentController(session)
        preview = PreviewController()
    }

    override fun stop() {
        runCatching { session.disconnect() }
        runCatching { connection.close() }
        runCatching { scope.cancel() }
    }
}
