package com.everydayta.app

import java.util.Locale

/** Identical names are not allowed even if case or surrounding whitespace differs. */
object TrackerNameRules {
    fun key(name: String): String = name.trim().lowercase(Locale.ROOT)

    fun duplicates(names: Collection<String>): Boolean =
        names.map(::key).distinct().size != names.size

    fun isUnique(name: String, existing: Collection<Pair<Long, String>>, id: Long = 0L): Boolean =
        existing.none { (otherId, otherName) -> otherId != id && key(otherName) == key(name) }
}
