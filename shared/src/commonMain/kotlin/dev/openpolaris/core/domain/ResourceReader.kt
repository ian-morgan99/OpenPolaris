package dev.openpolaris.core.domain

/**
 * Read a text resource that lives in the module's resources directory.
 *
 * On JVM and Android, the [path] is resolved against the classpath /
 * package resources (e.g. `"catalog.json"` resolves to the bundled
 * shard at `commonMain/resources/catalog.json`).
 *
 * Returns `null` if the resource is not present so the caller can
 * degrade gracefully (e.g. skip an optional catalog shard).
 */
expect fun readResourceText(path: String): String?

/**
 * Read a binary resource (e.g. an embedded PNG) from the module's
 * resources directory. See [readResourceText] for resolution rules.
 *
 * Returns `null` if the resource is not present.
 */
expect fun readResourceBytes(path: String): ByteArray?
