package com.unjunkify.utils

import com.unjunkify.data.JunkSnapshot

/** Removes exempt items from scan results. Pure — unit-testable. */
fun filterExempt(items: List<JunkSnapshot>, exemptIds: Set<String>): List<JunkSnapshot> {
    if (exemptIds.isEmpty()) return items
    return items.filter { it.pathOrKey !in exemptIds }
}
