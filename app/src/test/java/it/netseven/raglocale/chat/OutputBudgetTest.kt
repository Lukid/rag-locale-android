package it.netseven.raglocale.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputBudgetTest {
    @Test
    fun `stima token su stringa vuota e' zero`() {
        assertEquals(0, OutputBudget.estimateTokens(""))
    }

    @Test
    fun `stima token arrotonda per eccesso a 4 caratteri per token`() {
        assertEquals(1, OutputBudget.estimateTokens("abcd"))
        assertEquals(2, OutputBudget.estimateTokens("abcde"))
        assertEquals(2, OutputBudget.estimateTokens("abcdefgh"))
    }

    @Test
    fun `reachedCap true quando la stima raggiunge il cap`() {
        // 8 caratteri ≈ 2 token, cap 2 → raggiunto
        assertTrue(OutputBudget.reachedCap("abcdefgh", maxTokens = 2))
    }

    @Test
    fun `reachedCap false sotto il cap`() {
        assertFalse(OutputBudget.reachedCap("abcd", maxTokens = 5))
    }

    @Test
    fun `cap non positivo disabilita il limite`() {
        assertFalse(OutputBudget.reachedCap("testo lungo a piacere", maxTokens = 0))
    }
}
