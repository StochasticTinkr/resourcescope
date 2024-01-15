package com.stochastictinkr.resourcescope.internal

import com.stochastictinkr.resourcescope.Resource
import com.stochastictinkr.resourcescope.ResourceReceiver
import com.stochastictinkr.resourcescope.ResourceScope
import java.util.LinkedList
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A thread safe implementation of [ResourceScope].
 */
internal class ResourceScopeImpl : ResourceScope {
    /**
     * A lock used to synchronize access to the state of this scope.
     */
    private val lock = ReentrantLock()

    /**
     * Possible states of this scope.
     */
    private sealed interface State

    /**
     * Represents an open scope, with a list of resources.
     */
    private class Open : State {
        val resources = LinkedList<ResourceImpl<*>>()
    }

    /**
     * Represents a closed scope.
     */
    private data object Closed : State

    /**
     * The current state of this scope. Initialized to [Open].
     */
    private var state: State = Open()

    override fun <V> initializeResource(constructor: () -> V, destructor: (V) -> Unit): Resource<V> {
        // Ordering is important here.  We want to push the resource to the stack before initializing it.
        // This ensures that there is no window where the resource is initialized but not yet pushed to the stack.
        val resource = ResourceImpl<V>(this)
        push(resource)
        val initialize = try {
            resource.initialize(constructor, destructor)
        } catch (ex: Throwable) {
            // Remove from the stack since initialization failed.
            remove(resource)
            throw ex
        }
        if (!initialize) {
            // Remove from the stack since initialization failed.
            remove(resource)
        }
        return resource
    }

    /**
     * Pushes a cleanup to the top of the stack.
     */
    private fun <V> push(resource: ResourceImpl<V>) {
        lock.withLock {
            val currentState = state
            check(currentState is Open) { "Cleanup Scope is closed." }
            currentState.resources.addFirst(resource)
        }
    }

    override fun remove(resource: Resource<*>) {
        lock.withLock {
            val currentState = state
            check(currentState is Open) { "Cleanup Scope is closed." }
            check(currentState.resources.remove(resource)) { "Cleanup is not in this scope" }
            (resource as ResourceImpl).releasedFrom(this)
        }
    }

    override fun close() {
        val currentState: State?
        lock.withLock {
            currentState = this.state
            this.state = Closed
        }
        if (currentState is Open) {
            var exception: Throwable? = null

            val cleanups = currentState.resources
            while (true) {
                try {
                    cleanups.pollFirst()?.scopeClosing(this) ?: break
                } catch (ex: Throwable) {
                    try {
                        exception?.let(ex::addSuppressed)
                        exception = ex
                    } catch (yikes: Throwable) {
                        yikes.printStackTrace()
                    }
                }
            }
            exception?.let { throw it }
        }
    }

    internal fun <V> resourceClosing(cleanup: ResourceImpl<V>) {
        lock.withLock { (state as? Open)?.resources?.remove(cleanup) }
    }

    override fun <V> takeOwnershipOf(resource: Resource<V>): Resource<V> {
        // already own
        if (resource.scope === this) return resource
        // in-place
        if (resource is ResourceImpl) {
            lock.withLock {
                val currentState = state
                check(currentState is Open) { "Cleanup Scope is closed." }
                currentState.resources.addFirst(resource)
                resource.movingScope(this)
            }
            return resource
        }
        // allocate a new resource impl, and attempt to move it over.
        return resource.releaseTo(ownershipReceiver())
    }

    override fun <V> ownershipReceiver() = ResourceReceiver<V, Resource<V>> { value, destructor ->
        val resource = ResourceImpl(this@ResourceScopeImpl, value, destructor)
        push(resource)
        accepted(resource)
    }
}