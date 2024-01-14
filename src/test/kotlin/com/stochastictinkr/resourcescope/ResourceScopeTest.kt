package com.stochastictinkr.resourcescope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResourceScopeTest {

    @Test
    fun `initializeResource should return a Resource`() {
        val scope = ResourceScope()
        val resource = scope.initializeResource({ 1 }, {})
        assertNotNull(resource)
    }

    @Test
    fun `constructCloseable should return a Resource`() {
        val scope = ResourceScope()
        val resource = scope.constructClosable { AutoCloseable { } }
        assertNotNull(resource)
    }

    @Test
    fun `constructCloseable should call close in destructor`() {
        val scope = ResourceScope()
        var closed = false
        val resource = scope.constructClosable { AutoCloseable { closed = true } }
        resource.close()
        assertTrue(closed)
    }

    @Test
    fun `resource value should be from the constructor`() {
        val scope = ResourceScope()
        val resource = scope.initializeResource({ 1 }, {})
        assertEquals(1, resource.value)
    }

    @Test
    fun `resource can be manually closed`() {
        val scope = ResourceScope()
        val resource = scope.initializeResource({ 1 }, {})
        resource.close()
        assertNull(resource.valueOrNull())
    }

    @Test
    fun `initializeResource should throw if scope is closed`() {
        val scope = ResourceScope()
        scope.close()
        assertFailsWith<IllegalStateException> {
            scope.initializeResource({ 1 }, {})
        }
    }

    @Test
    fun `initializeResource should throw if constructor throws`() {
        val scope = ResourceScope()
        assertFailsWith<RuntimeException> {
            scope.initializeResource({ throw RuntimeException() }, {})
        }
    }

    @Test
    fun `resources should be closed when scope is closed`() {
        val scope = ResourceScope()
        val closed = mutableListOf<Int>()
        scope.initializeResource({ 1 }, { closed.add(it) })
        scope.close()
        assertEquals(listOf(1), closed)
    }

    @Test
    fun `resources should be closed in reverse order of initialization`() {
        val scope = ResourceScope()
        val closed = mutableListOf<Int>()
        scope.initializeResource({ 1 }, { closed.add(it) })
        scope.initializeResource({ 2 }, { closed.add(it) })
        scope.initializeResource({ 3 }, { closed.add(it) })
        scope.close()
        assertEquals(listOf(3, 2, 1), closed)
    }

    @Test
    fun `resources should be closed in reverse order of initialization even if some fail`() {
        val scope = ResourceScope()
        val closed = mutableListOf<Int>()
        scope.initializeResource({ 1 }, { closed.add(it) })
        scope.initializeResource({ 2 }, { throw RuntimeException() })
        scope.initializeResource({ 3 }, { closed.add(it) })
        assertFailsWith<RuntimeException> {
            scope.close()
        }
        assertEquals(listOf(3, 1), closed)
    }

    @Test
    fun `resources can be transferred`() {
        val scope1 = ResourceScope()
        val scope2 = ResourceScope()
        val resource = scope1.initializeResource({ 1 }, {})
        val transferred = scope2 takeOwnershipOf resource
        assertEquals(1, transferred.value)
        scope1.close()
        scope2.close()
    }

    @Test
    fun `transferred resources should only be closed by the new scope`() {
        val scope1 = ResourceScope()
        val scope2 = ResourceScope()
        val closed = mutableListOf<Int>()
        val resource = scope1.initializeResource({ 1 }, { closed.add(it) })
        val transferred = scope2 takeOwnershipOf resource
        assertEquals(1, transferred.value)
        scope1.close()
        assertTrue(closed.isEmpty())
        scope2.close()
        assertEquals(listOf(1), closed)
    }

    @Test
    fun `resources can be removed`() {
        val scope = ResourceScope()
        val closed = mutableListOf<Int>()
        val resource = scope.initializeResource({ 1 }, { closed.add(it) })
        scope.remove(resource)
        scope.close()
        assertTrue(closed.isEmpty())
    }

    @Test
    fun `removed resources should not be closed`() {
        val scope = ResourceScope()
        val closed = mutableListOf<Int>()
        val resource = scope.initializeResource({ 1 }, { closed.add(it) })
        scope.remove(resource)
        scope.close()
        assertTrue(closed.isEmpty())
    }

    @Test
    fun `removed resources can be added back`() {
        val scope = ResourceScope()
        val closed = mutableListOf<Int>()
        val resource = scope.initializeResource({ 1 }, { closed.add(it) })
        scope.remove(resource)
        scope.takeOwnershipOf(resource)
        scope.close()
        assertEquals(listOf(1), closed)
    }

    // DSL tests
    @Test
    fun `resourceScope should define usable scope`() {
        resourceScope {
            val resource = construct { 1 } finally { }
            assertEquals(1, resource.value)
        }
    }

    @Test
    fun `resourceScope should close resources`() {
        val closed = mutableListOf<Int>()
        resourceScope {
            construct { 1 } finally { closed.add(this) }
            construct { 2 } finally { closed.add(this) }
            construct { 3 } finally { closed.add(this) }
        }
        assertEquals(listOf(3, 2, 1), closed)
    }

    @Test
    fun `then should act on resource`() {
        val configured = mutableListOf<Int>()
        val closed = mutableListOf<Int>()
        resourceScope {
            construct { 1 } then { configured.add(this) } finally { closed.add(this) }
            assertEquals(listOf(1), configured)
            assertTrue(closed.isEmpty())
        }
        assertEquals(listOf(1), closed)
    }
}