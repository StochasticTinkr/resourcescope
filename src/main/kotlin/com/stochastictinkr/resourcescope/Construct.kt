package com.stochastictinkr.resourcescope

/**
 * Defines a resource constructor. This is the first step in creating a resource.
 *
 * Once a resource constructor is defined, it can be configured with an optional [then] and added to
 * a resource scope with [ResourceScope.finally].
 *
 * example:
 * ```
 * class MyResource(var value: Long = 0) {
 *    companion object {
 *        fun acquireMyResource() { ... }
 *    }
 *    fun destroy() { ... }
 * }
 *
 * resourceScope {
 *    val resource = construct { MyResource() } then { value = acquireMyResource() } finally { destroy() }
 *    // use resource
 *    // resource is destroyed when resourceScope is closed
 * }
 * ```
 */
@ResourceBuilderDsl
fun <V> construct(constructor: () -> V) = ResourceConstructor(constructor)
