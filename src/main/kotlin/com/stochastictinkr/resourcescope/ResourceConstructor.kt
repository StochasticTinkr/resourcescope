package com.stochastictinkr.resourcescope

/**
 * Defines a resource constructor. See [construct] for more details.
 */
@ResourceBuilderDsl
data class ResourceConstructor<V>(val constructor: () -> V, val configure: (V.() -> Unit)? = null) {

    /**
     * Configures the resource after it is constructed.
     *
     * @param configure the configuration function
     * @return a new [ResourceConstructor] with the specified configuration
     */
    infix fun then(configure: V.() -> Unit) = copy(
        configure = this.configure?.let { existing ->
            {
                existing()
                configure()
            }
        } ?: { configure() }
    )
}


