package com.example.liveai.agent.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UtterancePriorityTest {

    @Test
    fun `IMMEDIATE has lowest ordinal`() {
        assertEquals(0, UtterancePriority.IMMEDIATE.ordinal)
    }

    @Test
    fun `priority ordering is IMMEDIATE then MAIN then NOTIFICATION then LOW`() {
        val sorted = UtterancePriority.entries.sortedBy { it.ordinal }
        assertEquals(
            listOf(
                UtterancePriority.IMMEDIATE,
                UtterancePriority.MAIN,
                UtterancePriority.NOTIFICATION,
                UtterancePriority.LOW
            ),
            sorted
        )
    }

    @Test
    fun `IMMEDIATE ordinal less than MAIN`() {
        assertTrue(UtterancePriority.IMMEDIATE.ordinal < UtterancePriority.MAIN.ordinal)
    }

    @Test
    fun `MAIN ordinal less than NOTIFICATION`() {
        assertTrue(UtterancePriority.MAIN.ordinal < UtterancePriority.NOTIFICATION.ordinal)
    }

    @Test
    fun `NOTIFICATION ordinal less than LOW`() {
        assertTrue(UtterancePriority.NOTIFICATION.ordinal < UtterancePriority.LOW.ordinal)
    }
}
