package com.stochastictinkr.resourcescope.internal

import com.stochastictinkr.resourcescope.Resource
import com.stochastictinkr.resourcescope.ResourceReceiver
import com.stochastictinkr.resourcescope.TransferResult
import com.stochastictinkr.resourcescope.or

/**
 * A thread safe implementation of [Resource] that is managed by a [ResourceScopeImpl].
 *
 * @param V the type of the resource
 * @param scope the scope that manages this resource
 * @param state the current state of the resource, or the resource itself.
 * @param destructor the action to be performed when the resource is closed.
 */
internal class ResourceImpl<V>(
    scope: ResourceScopeImpl,
    private var state: Any? = Uninitialized,
    private var destructor: ((V) -> Unit)? = null,
) : Resource<V> {
    /**
     * Represents an uninitialized resource.
     */
    private data object Uninitialized

    /**
     * Represents a closed resource.
     */
    private data object Closed

    /**
     * A lock used to synchronize access to the resource value.
     */
    private inline fun <R> withLock(action: () -> R): R = synchronized(this, action)

    /**
     * Initializes the state of this resource.
     *
     * @param constructor the function that produces the resource value.
     * @param destructor the action to be performed if the resource fails to initialize.
     * @return true if the resource was successfully initialized, or false otherwise.
     * @throws Throwable if the resource fails to initialize.
     * @throws IllegalStateException if the resource is already initialized.
     */
    internal fun initialize(constructor: () -> V, destructor: (V) -> Unit) = withLock {
        check(state == Uninitialized) { "Attempt to reinitialize resource" }
        this.destructor = destructor
        try {
            state = constructor()
        } catch (ex: Throwable) {
            withActiveResource(state) { destructor(it) }
            state = Closed
            throw ex
        }
        state != Closed
    }

    override var scope: ResourceScopeImpl? = scope
        private set

    override val value: V
        get() = sendState(state) { value, _ -> accepted(value) }.value

    override fun valueOrNull(): V? =
        sendState(state, ResourceReceiver<V, V?> { value, _ -> accepted(value) } or null).value

    /**
     * Executes the specified action with the given resource value if it is active.
     */
    private inline fun <R> withActiveResource(state: Any?, crossinline action: (V) -> R): R? =
        sendState(state, (ResourceReceiver { value, _ -> accepted(action(value)) } or null)).value

    /**
     * Closes this resource, and removes it from its owning scope.
     */
    override fun close(): Unit = withLock {
        try {
            // Remove the resource from the scope before closing it.
            scope?.resourceClosing(this)
        } finally {
            scope = null
            // Close the resource.
            val previousState = state
            state = Closed

            // Perform the destructor action if the resource was active.
            destroy(previousState)
        }
    }

    private fun destroy(state: Any?) {
        withActiveResource(state) { destructor?.let { destroy -> destroy(it) } }
    }

    /**
     * Removes this resource from its owning scope.
     */
    private fun removeFromScope() {
        try {
            scope?.remove(this)
        } finally {
            scope = null
        }
    }

    internal fun releasedFrom(parentScope: ResourceScopeImpl) = withLock {
        check(scope === parentScope) { "Releasing resource from the wrong scope" }
        scope = null
    }

    /**
     * Releases this resource to the specified receiver, changing the state of this resource to [Closed] if the
     * transfer is successful.
     */
    override fun <R> releaseTo(target: ResourceReceiver<V, R>): R = withLock {
        val result = sendState(state, target)
        if (result.isSuccess) {
            state = Closed
            removeFromScope()
        }
        result.value
    }

    /**
     * Sends the current state of this resource to the specified receiver.
     */
    private fun <R> sendState(state: Any?, receiver: ResourceReceiver<V, R>): TransferResult<R> =
        TransferResult<R>()
            .also { result ->
                receiver.run {
                    when (state) {
                        Uninitialized -> result.noValue { "Access to uninitialized resource" }
                        Closed -> result.noValue { "Access to closed resource" }
                        else -> @Suppress("UNCHECKED_CAST")
                        (result.receive(state as V, destructor!!))
                    }
                }
            }

    /**
     * Close this resource due to an owner scope closing.
     */
    internal fun scopeClosing(closingScope: ResourceScopeImpl) = withLock {
        check(scope === closingScope) { "Closing Cleanup from the wrong CleanupScope" }
        scope = null
        val previousState = state
        state = Closed
        destroy(previousState)
    }

    /**
     * Moves this resource to a new owning scope.
     */
    internal fun movingScope(newScope: ResourceScopeImpl) = withLock {
        removeFromScope()
        scope = newScope
    }
}