package com.everydayta.app

import org.junit.Assert.*
import org.junit.Test

class ScaleRulesTest {
    @Test fun elevenPointScaleIncludesBothEndpoints() {
        val labels = presetLabels(1)
        assertEquals(11, labels.size)
        assertEquals("0", labels.first())
        assertEquals("10", labels.last())
        assertNull(trackerFormError("Energy", labels))
    }

    @Test fun emptyNameIsRejectedWithExplanation() {
        assertEquals("Give your tracker a name.", trackerFormError(" ", presetLabels(0)))
    }

    @Test fun duplicateLabelsAreRejected() {
        assertEquals("Each level needs a different label.", trackerFormError("Mood", listOf("High", "High")))
    }

    @Test fun customScaleCanUseTwoToElevenLevels() {
        assertNull(trackerFormError("Two", listOf("No", "Yes")))
        assertNotNull(trackerFormError("One", listOf("Only")))
        assertNotNull(trackerFormError("Twelve", (0..11).map(Int::toString)))
    }
}
