package com.everydayta.app

/** Score positions in SQLite and JSON backups are deliberately 1-based.
 * The 0–10 preset is the only scale whose user-facing numerical score starts at zero.
 */
object ScaleSemantics {
    const val LIKERT = "likert"
    const val ZERO_TO_TEN = "zero_to_ten"
    const val CUSTOM = "custom"
    const val DONE_NOT_DONE = "done_not_done"

    val doneNotDoneLabels = listOf("Not done", "Done")
    val likertLabels = listOf("Very bad", "Bad", "Neutral", "Good", "Very good")
    val zeroToTenLabels = (0..10).map(Int::toString)

    fun inferLegacy(labels: List<String>): String = when (labels) {
        zeroToTenLabels -> ZERO_TO_TEN
        likertLabels -> LIKERT
        doneNotDoneLabels -> DONE_NOT_DONE
        else -> CUSTOM
    }

    fun valid(type: String, labels: List<String>): Boolean = when(type) {
        ZERO_TO_TEN -> labels == zeroToTenLabels
        LIKERT -> labels == likertLabels
        DONE_NOT_DONE -> labels == doneNotDoneLabels
        CUSTOM -> labels.size in 2..11
        else -> false
    }

    fun displayScore(type: String, levelIndex: Int): Int =
        if (type == ZERO_TO_TEN) levelIndex - 1 else levelIndex

    fun displayLabel(type: String, labels: List<String>, levelIndex: Int): String {
        require(levelIndex in 1..labels.size)
        return if (type == ZERO_TO_TEN) (levelIndex - 1).toString() else labels[levelIndex - 1]
    }

    /** Row schema v2: score is user-facing, level_index is reversible 1-based storage. */
    fun csvFields(type: String, levelIndex: Int, label: String): Triple<String, String, String> =
        Triple(displayScore(type, levelIndex).toString(), label, levelIndex.toString())
}
