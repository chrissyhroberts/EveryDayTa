package com.everydayta.app

/** Single location for scale validation, including the eleven-point 0–10 case. */
internal fun trackerFormError(name: String, labels: List<String>): String? = when {
    name.isBlank() -> "Give your tracker a name."
    name.trim().length > 200 -> "Please use a shorter name (200 characters maximum)."
    labels.size !in 2..11 -> "Choose between 2 and 11 scoring levels."
    labels.any { it.isBlank() } -> "Each scoring level needs a label."
    labels.map { it.trim().lowercase() }.distinct().size != labels.size -> "Each level needs a different label."
    else -> null
}


internal fun presetLabels(index: Int): List<String> = when(index) {
    0 -> ScaleSemantics.likertLabels
    1 -> ScaleSemantics.zeroToTenLabels
    2 -> ScaleSemantics.doneNotDoneLabels
    else -> listOf("Very low", "Low", "Okay", "Good", "Excellent")
}
