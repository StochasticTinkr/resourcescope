package com.stochastictinkr.resourcescope

import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResourceReceiverKtTest {
    @Test
    fun `ResourceReceiver or should return the value if available`() {
        val receiver: ResourceReceiver<Int, Int?> = ResourceReceiver<Int, Int> { value, _ -> accepted(value) } or null
        val transferResult = TransferResult<Int?>()
        with(receiver) { transferResult.receive(1) { } }
        assertTrue(transferResult.isSuccess)
        assertEquals(1, transferResult.value)
    }

    @Test
    fun `default ResourceReceiver fails if no value is available`() {
        val receiver: ResourceReceiver<Int, Int?> = ResourceReceiver<Int, Int> { value, _ -> accepted(value) }
        val transferResult = TransferResult<Int?>()
        with(receiver) { transferResult.noValue { "No value was found" } }
        assertFalse(transferResult.isSuccess)
        assertThrows<IllegalStateException> {
            assertNull(transferResult.value)
        }
    }


    @Test
    fun `ResourceReceiver or should return the alternative value if no value availiable`() {
        val receiver: ResourceReceiver<Int, Int?> = ResourceReceiver<Int, Int> { value, _ -> accepted(value) } or null
        val transferResult = TransferResult<Int?>()
        with(receiver) { transferResult.noValue { "No value was found" } }
        assertFalse(transferResult.isSuccess)
        assertNull(transferResult.value)
    }

    @Test
    fun `ResourceReceiver orElse should perform the alternative action if no value availiable`() {
        val receiver: ResourceReceiver<Int, Int?> =
            ResourceReceiver<Int, Int> { value, _ -> accepted(value) } orElse { rejected(-1) }
        val transferResult = TransferResult<Int?>()
        with(receiver) { transferResult.noValue { "No value was found" } }
        assertFalse { transferResult.isSuccess }
        assertEquals(-1, transferResult.value)
    }
}
