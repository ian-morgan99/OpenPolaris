package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The bundled shards live in `shared/src/commonMain/resources/` and
 * should be visible to the JVM classpath (gradle copies them into
 * `processResources` for the `jvmTest` source set). Verifies the
 * expect/actual plumbing is wired up correctly before the UI layer
 * depends on it.
 */
class ResourceReaderTest {
    @Test
    fun `catalog shard is readable`() {
        val text = readResourceText("catalog.json")
        assertNotNull(text, "catalog.json should be on the JVM classpath")
        assertTrue(text.startsWith("{"), "catalog.json should be a JSON object")
    }

    @Test
    fun `stars shard is readable`() {
        val text = readResourceText("stars.json")
        assertNotNull(text, "stars.json should be on the JVM classpath")
        assertTrue(text.startsWith("{"), "stars.json should be a JSON object")
    }

    @Test
    fun `ngc shard is readable`() {
        val text = readResourceText("ngc.json")
        assertNotNull(text, "ngc.json should be on the JVM classpath")
        assertTrue(text.startsWith("{"), "ngc.json should be a JSON object")
    }

    @Test
    fun `comets shard is readable`() {
        val text = readResourceText("comets.json")
        assertNotNull(text, "comets.json should be on the JVM classpath")
        assertTrue(text.startsWith("{"), "comets.json should be a JSON object")
    }

    @Test
    fun `missing resource returns null`() {
        val text = readResourceText("does-not-exist.json")
        assertNull(text, "missing resources should return null, not throw")
    }
}
