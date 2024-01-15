package com.stochastictinkr.resourcescope.internal

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ResourceImplTest {
    @Test
    fun `value on uninitialized resource should throw`() {
        // This test is the only way to get 100% coverage on ResourceImpl, since this case is not possible in practice.
        val resource = ResourceImpl<Int>(ResourceScopeImpl())
        assertFailsWith<IllegalStateException> {
            resource.value
        }.message?.contains("Access to uninitialized resource")?.let { assertTrue(it) }
    }
}