package dev.openpolaris.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class ConnectionModeContractTest {
    private fun source(path: String): String {
        var directory: java.io.File? = java.io.File(".").canonicalFile
        repeat(10) {
            val candidate = directory?.let { java.io.File(it, path) }
            if (candidate?.isFile == true) return candidate.readText(Charsets.UTF_8)
            directory = directory?.parentFile
        }
        error("Cannot read $path")
    }

    @Test
    fun connectionPaneExplainsAndGatesApOnlyActions() {
        val pane = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")
        assertTrue("connection-mode-direct-ap" in pane)
        assertTrue("connection-mode-home-lan" in pane)
        assertTrue("Station host/IP" in pane)
        assertTrue("No AP join or bridge is performed" in pane)
        assertTrue("vm.connectionMode == ConnectionMode.DIRECT_AP" in pane)
        assertTrue("Find & wake (Direct AP only)" in pane)
    }

    @Test
    fun endpointHealthKeepsHomeLanFailureClassesAndActionableGuidanceVisible() {
        val pane = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt")
        val model = source(
            "shared/src/commonMain/kotlin/dev/openpolaris/core/domain/EndpointHealth.kt",
        )
        assertTrue("Failure class:" in pane)
        assertTrue("endpoint-failure-kind" in pane)
        assertTrue("lastFailureKind" in model)
        assertTrue("classifyEndpointFailure" in model)
        assertTrue("TCP refused" in model)
        assertTrue("TCP timeout" in model)
        assertTrue("Network unreachable" in model)
        assertTrue("Protocol or handshake failure" in model)
    }

    @Test
    fun modeIsPersistedThroughTheExistingPreferenceLifecycle() {
        val shell = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/OpenPolarisApp.kt")
        val model = source("composeApp/src/commonMain/kotlin/dev/openpolaris/ui/AppViewModel.kt")
        val preferences = source(
            "shared/src/commonMain/kotlin/dev/openpolaris/core/session/UserPreferencesStore.kt",
        )
        assertTrue("vm.setConnectionModePersistence" in shell)
        assertTrue("vm.restoreConnectionMode(loaded.connectionMode)" in shell)
        assertTrue("fun selectConnectionMode(mode: ConnectionMode)" in model)
        assertTrue("val connectionMode: ConnectionMode" in preferences)
    }
}
