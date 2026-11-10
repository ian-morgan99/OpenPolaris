package dev.openpolaris.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class AppDestinationTest {
    @Test
    fun primaryDestinationsFollowTheObservingWorkflow() {
        assertEquals(
            listOf(
                AppDestination.MISSION,
                AppDestination.CONTROL,
                AppDestination.CAMERA,
                AppDestination.ASTRO,
                AppDestination.GALLERY,
            ),
            primaryAppDestinations,
        )
    }

    @Test
    fun destinationsHaveReadableLabels() {
        assertEquals(
            listOf("Mission", "Control", "Camera", "Astro", "Gallery"),
            primaryAppDestinations.map { it.label },
        )
    }
}
