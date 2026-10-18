package com.everydayta.app

import org.junit.Assert.*
import org.junit.Test

class ScaleSemanticsTest {
    @Test fun zeroToTenRoundTripsAllLevels() {
        assertEquals(11, ScaleSemantics.zeroToTenLabels.size)
        for (index in 1..11) {
            assertEquals((index - 1).toString(), ScaleSemantics.displayLabel(
                ScaleSemantics.ZERO_TO_TEN, ScaleSemantics.zeroToTenLabels, index))
            assertEquals(index - 1, ScaleSemantics.displayScore(ScaleSemantics.ZERO_TO_TEN, index))
        }
    }
    @Test fun likertIsTheFiveChoiceDefault() {
        assertEquals(listOf("Very bad", "Bad", "Neutral", "Good", "Very good"), presetLabels(0))
        assertEquals(ScaleSemantics.LIKERT, ScaleSemantics.inferLegacy(presetLabels(0)))
        assertEquals(ScaleSemantics.ZERO_TO_TEN, ScaleSemantics.inferLegacy(presetLabels(1)))
    }
    @Test fun existingZeroToTenLabelsInferWithoutMigratingScores() {
        assertTrue(ScaleSemantics.valid(ScaleSemantics.ZERO_TO_TEN, (0..10).map(Int::toString)))
        assertEquals(0, ScaleSemantics.displayScore(ScaleSemantics.ZERO_TO_TEN, 1))
        assertEquals(10, ScaleSemantics.displayScore(ScaleSemantics.ZERO_TO_TEN, 11))
    }
}
