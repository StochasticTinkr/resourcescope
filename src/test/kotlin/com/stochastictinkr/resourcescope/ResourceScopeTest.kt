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
    fun `resource can be transferred via releaseTo and ownershipReceiver`() {
        val scope1 = ResourceScope()
        val scope2 = ResourceScope()
        val closed = mutableListOf<Int>()
        val resource = scope1.initializeResource({ 1 }, { closed.add(it) })
        val transferred = resource releaseTo scope2.ownershipReceiver()
        assertEquals(1, transferred.value)
        scope1.close()
        assertTrue(closed.isEmpty())
        scope2.close()
        assertEquals(listOf(1), closed)
    }

    @Test
    fun `releasing a closed resource should throw`() {
        val scope1 = ResourceScope()
        val scope2 = ResourceScope()
        val resource = scope1.initializeResource({ 1 }, {})
        resource.close()
        assertFailsWith<IllegalStateException> {
            resource releaseTo scope2.ownershipReceiver()
        }
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
    fun `finally can take a function reference`() {
        var closed = false
        fun close() {
            closed = true
        }
        resourceScope {
            construct { 1 } finally ::close
        }
        assertTrue(closed)
    }


    @Test
    fun `resource valueOrNull should be null if scope is closed`() {
        val resource = resourceScope {
            construct { 1 } finally { }
        }
        assertNull(resource.valueOrNull())
    }

    @Test
    fun `resource valueOrNull should be value if scope is not closed`() {
        resourceScope {
            val valueOrNull = (construct { 1 } finally { }).valueOrNull()
            assertEquals(1, valueOrNull)
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

    @Test
    fun `then can be chained`() {
        val configured = mutableListOf<Int>()
        val closed = mutableListOf<Int>()
        resourceScope {
            construct { 1 } then { configured.add(this) } then { configured.add(this) } finally { closed.add(this) }
            assertEquals(listOf(1, 1), configured)
            assertTrue(closed.isEmpty())
        }
        assertEquals(listOf(1), closed)
    }

    @Test
    fun `takeOwnershipOf works on foreign resources`() {
        val scope1 = ResourceScope()
        val scope2 = ResourceScope()
        val resource = scope1.initializeResource({ 1 }, {})
        // This bypasses the optimization of takeOwnershipOf in ResourceScopeImpl
        val resourceDelegate = object : Resource<Int> by resource {}
        val transferred = scope2.takeOwnershipOf(resourceDelegate)
        assertEquals(1, transferred.value)
        scope1.close()
        scope2.close()
    }

    @Test
    fun `destructuring of Resource works as expected`() {
        resourceScope {
            val (value, resource) = construct { 1 } finally { }
            assertEquals(1, value)
            assertEquals(1, resource.value)
        }
    }

}