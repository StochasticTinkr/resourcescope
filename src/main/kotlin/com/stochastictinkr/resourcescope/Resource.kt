package com.stochastictinkr.resourcescope

/**
 * A resource that can managed by a [ResourceScope].
 *
 * @param V the type of the resource
 */
interface Resource<V> : AutoCloseable {
    /**
     * The scope that manages this resource.
     */
    val scope: ResourceScope?

    /**
     * The value of the resource.
     */
    val value: V

    /**
     * The value of the resource, or `null` if the resource is closed.
     */
    fun valueOrNull(): V?

    /**
     * Closes this resource.  Calling this method more than once has no effect.
     */
    override fun close()

    /**
     * Releases this resource to the specified [target] receiver.
     * The [target] receiver will receive the value of this resource, and will be responsible for closing it.
     *
     * @param target the target receiver
     * @return the result of the [target] receiver after receiving the value of this resource
     */
    infix fun <R> releaseTo(target: ResourceReceiver<V, R>): R

    operator fun component1() = value
    operator fun component2() = this
}

