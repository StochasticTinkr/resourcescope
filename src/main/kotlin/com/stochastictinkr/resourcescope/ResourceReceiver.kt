package com.stochastictinkr.resourcescope

/**
 * A ResourceReceiver is a function that receives a resource value and a destructor action.
 *
 * The receiver can accept the resource value and destructor, reject the resource value, or
 * reject the resource value with an exception.
 */
fun interface ResourceReceiver<V, out R> {
    /**
     * Receives the resource value and destructor.
     * Implementations of this method must call exactly one of the following methods exactly once:
     *  * [TransferResult.accepted] - The resource was successfully acquired. The ResourceReceiver is now responsible for closing the resource.
     *                 This should only be called when the receiver has successfully taken ownership of the resource.
     *  * [TransferResult.rejected] - The resource was not acquired, but the request was not an error. The ResourceReceiver does not take ownership of the resource.
     *  * [TransferResult.failed]   - The resource was not acquired, and the request was an error. The ResourceReceiver does not take ownership of the resource.
     */
    fun TransferResult<in R>.receive(value: V, destructor: V.() -> Unit)

    /**
     * Called when the resource value is not available.  This can happen if the resource was already closed, or if the resource was not initialized.
     * Implementations of this method must call exactly one of the following methods exactly once:
     *  * [TransferResult.rejected] - The result value to use when the resource is not available.
     *  * [TransferResult.failed]   - The exception to throw when the resource is not available.
     *
     * The default implementation throws an IllegalStateException.
     *
     * @param lazyMessage a function that returns the message which conveys why the resource is not available.
     */
    fun TransferResult<in R>.noValue(lazyMessage: () -> Any): Unit =
        failed(IllegalStateException(lazyMessage().toString()))
}

/**
 * Create a ResourceReceiver with the `noValue` function replaced with the rejection with the specified value.
 *
 * Example usage:
 * ```
 * val resource = constructor { 1 } finally { close(this) }
 * val result = resource.releaseTo(ResourceReceiver<Int, Int> { value, _ -> accepted(value) } or 0)
 * ```
 *
 */
infix fun <V, R> ResourceReceiver<V, R>.or(noValueResult: R) = orElse { rejected(noValueResult) }

/**
 * Create a ResourceReceiver with the `noValue` function replaced with the given function.
 *
 * Example usage:
 * ```
 * val resource = constructor { 1 } finally { close(this) }
 * val result = resource.releaseTo(ResourceReceiver<Int, Int> { value, _ -> accepted(value) } orElse { rejected(0) })
 * ```
 */
infix fun <V, R> ResourceReceiver<V, R>.orElse(noValueFun: TransferResult<in R>.(lazyMessage: () -> Any) -> Unit): ResourceReceiver<V, R> {
    val outer = this
    return object : ResourceReceiver<V, R> by (outer) {
        override fun TransferResult<in R>.noValue(lazyMessage: () -> Any) {
            noValueFun(lazyMessage)
        }
    }
}