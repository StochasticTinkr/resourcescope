package com.stochastictinkr.resourcescope

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransferResultTest {
    @Test
    fun `uninitialized result should behave as expected`() {
        val result = TransferResult<Int>()
        assertFalse(result.isSuccess)
        assertFailsWith<IllegalStateException> { result.value }
        assertFailsWith<IllegalStateException> { result.valueOrNull() }
    }

    @Test
    fun `accepted result should behave as expected`() {
        val result = TransferResult<Int>()
        result.accepted(1)
        assertTrue(result.isSuccess)
        assertEquals(1, result.value)
        assertEquals(1, result.valueOrNull())
    }

    @Test
    fun `rejected result should behave as expected`() {
        val result = TransferResult<Int>()
        result.rejected(1)
        assertFalse(result.isSuccess)
        assertEquals(1, result.value)
        assertEquals(1, result.valueOrNull())
    }

    @Test
    fun `failed result should behave as expected`() {
        val result = TransferResult<Int>()
        result.failed(RuntimeException())
        assertFalse(result.isSuccess)
        assertFailsWith<RuntimeException> { result.value }
        assertNull(result.valueOrNull())
    }
}