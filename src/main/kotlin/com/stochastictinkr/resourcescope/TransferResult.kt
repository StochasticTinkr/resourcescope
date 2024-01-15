package com.stochastictinkr.resourcescope

/**
 * The result of a resource transfer. This is used to indicate whether a resource was successfully
 * acquired or not.
 *
 * The result can be in one of four states:
 * <ul>
 *     <li>Uninitialized: The result has not yet been set.</li>
 *     <li>Accepted: The resource was successfully acquired.</li>
 *     <li>Rejected: The resource was not acquired, but the request was not an error.</li>
 *     <li>Failed: The resource was not acquired, and the request was an error.</li>
 * </ul>
 *
 * @param R The type of the resource.
 */
@Suppress("unused")
class TransferResult<R> {
    private sealed interface NotSuccess
    private data object Uninitialized : NotSuccess
    private data class Failure(val exception: Throwable) : NotSuccess
    private data class Rejected(val value: Any?) : NotSuccess

    private var valueField: Any? = Uninitialized

    /**
     * Returns the value of the result, or null if the result was rejected or failed.
     *
     * @throws IllegalStateException if the result has not yet been set.
     */
    fun valueOrNull(): R? {
        @Suppress("UNCHECKED_CAST")
        return when (val value = valueField) {
            Uninitialized -> uninitializedError()
            is Failure -> null
            is Rejected -> value.value
            else -> value
        } as R?
    }

    /**
     * Returns the value of the result if the transfer was accepted or rejected, or throws the
     * failure exception if the transfer failed.
     *
     * @throws IllegalStateException if the result has not yet been set.
     * @throws Throwable if the result was failure.
     */
    val value: R
        get() {
            @Suppress("UNCHECKED_CAST")
            return when (val value = valueField) {
                Uninitialized -> uninitializedError()
                is Failure -> throw value.exception
                is Rejected -> value.value
                else -> value
            } as R
        }

    private fun uninitializedError(): Nothing =
        error("Result not yet initialized")

    /**
     * Returns true if the transfer was accepted or false otherwise.
     */
    val isSuccess get() = valueField !is NotSuccess

    /**
     * Sets the transfer as successful with the given result.
     * @throws IllegalStateException if the result has already been set.
     */
    fun accepted(result: R) = initializeValue { result }

    /**
     * Sets the transfer as rejected with the given result.
     * @throws IllegalStateException if the result has already been set.
     */
    fun rejected(result: R) = initializeValue { Rejected(result) }

    /**
     * Sets the transfer as failed with the given exception.
     * @throws IllegalStateException if the result has already been set.
     */
    fun failed(failure: Throwable) = initializeValue { Failure(failure) }

    /**
     * Sets the result value if it has not yet been set.
     * @throws IllegalStateException if the result has already been set.
     */
    private inline fun initializeValue(value: () -> Any?) {
        check(valueField == Uninitialized) { "Can not change result once set." }
        valueField = value()
    }
}
