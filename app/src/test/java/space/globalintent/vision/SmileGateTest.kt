package space.globalintent.vision

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmileGateTest {
    @Test fun onlyFiresWhenEveryoneSmilesThreeTimes() {
        val gate = SmileGate()
        assertFalse(gate.sample(emptyList(), 0))
        assertFalse(gate.sample(listOf(0.95f, 0.40f), 1000))
        assertFalse(gate.sample(listOf(0.90f, 0.90f), 2000))
        assertFalse(gate.sample(listOf(0.91f, 0.82f), 3000))
        assertTrue(gate.sample(listOf(0.93f, 0.95f), 4000))
        assertFalse(gate.sample(listOf(0.99f, 0.99f), 5000))
    }

    @Test fun resetsWhenFaceCountChanges() {
        val gate = SmileGate()
        assertFalse(gate.sample(listOf(0.95f), 1000))
        assertFalse(gate.sample(listOf(0.95f, 0.95f), 2000))
        assertFalse(gate.sample(listOf(0.95f, 0.95f), 3000))
        assertTrue(gate.sample(listOf(0.95f, 0.95f), 4000))
    }

    @Test fun missingConfidenceNeverCountsAsASmile() {
        val gate = SmileGate()
        assertFalse(gate.sample(listOf(null), 1000))
        assertFalse(gate.sample(listOf(0.8f, null), 2000))
    }
}
