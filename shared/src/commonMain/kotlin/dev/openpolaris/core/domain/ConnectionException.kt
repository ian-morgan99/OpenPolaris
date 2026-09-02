package dev.openpolaris.core.domain

/**
 * KMP replacement for [java.io.IOException] in commonMain.
 *
 * The original [java.io.IOException] is JVM-only and doesn't compile in
 * the iOS metadata task (Kotlin 2.1.0 enforces commonMain strictness).
 * This class lives in commonMain, extends [RuntimeException] so it stays
 * in the "expected/checked-exception" bucket of the Kotlin exception
 * hierarchy on every target, and preserves the original message-and-cause
 * shape so call sites in [MountSession] can carry both a reason and an
 * underlying cause.
 *
 * Extending [RuntimeException] (not [Throwable] directly) means:
 *  - On JVM, this is still an `Exception` subclass, so `catch (e: Exception)`
 *    arms catch it as they used to catch `IOException`.
 *  - On Kotlin/Native, `RuntimeException` is the standard "expected"
 *    exception type and behaves identically to `Throwable` for catch
 *    purposes.
 *  - The compiler does NOT warn "is ConnectionException is always false"
 *    inside `catch (e: Exception)` blocks, because the type check is
 *    possible at runtime.
 *
 * Semantics are kept compatible with the previous [java.io.IOException]
 * usage:
 *  - `e is ConnectionException` still works as a catch arm
 *  - `e.message?.startsWith("handshake failed:")` still works
 *  - `completeExceptionally(ConnectionException("session closed"))` still
 *    works because [Throwable] is the [CompletionException] base in
 *    coroutines
 *
 * No I/O in the name because we want callers to use it for any
 * "connection lost / handshake failed / auth rejected" failure, not
 * just disk or socket errors.
 */
class ConnectionException(
    message: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
