package com.stochastictinkr.resourcescope

import com.stochastictinkr.resourcescope.internal.ResourceScopeImpl
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KFunction0

/**
 * A scope of managed resources.
 *
 * Resources can be created by calling [initializeResource], or by using the [ResourceConstructor] DSL.
 * Resources are destroyed when the scope is closed, in the reverse order of when they were added to the scope.
 *
 * Resources can be transferred between scopes by calling [takeOwnershipOf] or by using the [ownershipReceiver].
 *
 * Resources can be removed from the scope by calling [remove].  This will prevent the resource from being closed when the scope is closed.
 *
 * @see Resource
 */
interface ResourceScope : AutoCloseable {
    /**
     * Creates a resource that is managed by this scope.
     *
     * @param constructor the resource constructor
     * @return the created resource
     */
    fun <V> initializeResource(constructor: () -> V, destructor: (V) -> Unit): Resource<V>

    /**
     * Creates a resource that is managed by this scope. The resource will be closed by calling [AutoCloseable.close].
     *
     * @param constructor the resource constructor
     * @return the created resource
     */
    fun <V : AutoCloseable> constructClosable(constructor: () -> V): Resource<V> =
        initializeResource(constructor) { it.close() }

    /**
     * Creates a resource that is managed by this scope. The resource will be closed by calling the [destructor].
     *
     * @see [construct] for example usage.
     * @receiver the resource constructor created by [construct]
     * @return the created resource
     */
    @ResourceBuilderDsl
    infix fun <V> ResourceConstructor<V>.finally(destructor: V.() -> Unit): Resource<V> =
        initializeResource(constructor, destructor).also { configure?.invoke(it.value) }

    /**
     * Creates a resource that is managed by this scope. The resource will be closed by calling the [destructor].
     * This overload is useful when the destructor is a function reference that is global.
     *
     * @see [construct] for example usage.
     * @receiver the resource constructor created by [construct]
     * @return the created resource
     */
    @ResourceBuilderDsl
    infix fun <V> ResourceConstructor<V>.finally(destructor: KFunction0<Unit>): Resource<V> =
        finally { destructor() }

    /**
     * Takes ownership of the specified resource.
     * The resource will be removed from its current scope, and added to this scope.
     * If the resource is already managed by this scope, it will be returned as-is, and no changes will be made.
     *
     * @param resource the resource to be transferred.
     * @return a potentially new resource that is managed by this scope.
     * @throws IllegalStateException if this scope is already closed.
     *
     */
    infix fun <V> takeOwnershipOf(resource: Resource<V>): Resource<V> =
        resource.releaseTo(ownershipReceiver())

    /**
     * Removes the specified resource from this scope. The resource will need to be closed manually.
     *
     * @param resource the resource to be removed.
     * @throws IllegalArgumentException if the resource is not managed by this scope.
     * @throws IllegalStateException if this scope is already closed.
     */
    fun remove(resource: Resource<*>)

    /**
     * A receiver that takes ownership of a resource and returns a new resource.
     */
    fun <V> ownershipReceiver(): ResourceReceiver<V, Resource<V>>

    /**
     * Closes this scope, and all resources managed by this scope.
     */
    override fun close()
}

/**
 * Creates and uses a [ResourceScope], and closes it when the block completes.
 */
@OptIn(ExperimentalContracts::class)
inline fun <R> resourceScope(block: ResourceScope.() -> R): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return ResourceScope().use(block)
}

/**
 * Creates a [ResourceScope]. The caller is responsible for closing the scope.
 */
fun ResourceScope(): ResourceScope = ResourceScopeImpl()
