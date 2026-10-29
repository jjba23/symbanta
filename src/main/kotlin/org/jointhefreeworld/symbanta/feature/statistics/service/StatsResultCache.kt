/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.service

import org.jointhefreeworld.symbanta.shared.domain.PeriodSelection

/**
 * Identity of one cached stats read: the view asked for, the application and the period.
 *
 * Deliberately free of resolved instants: `LAST_7_DAYS` ends at "now", so a key built from the
 * query window would differ on every request and never hit. How old an answer may be is the
 * entry's own expiry, not part of its identity.
 */
internal data class StatsCacheKey(
    val view: String,
    val domain: String,
    val period: PeriodSelection,
)

/**
 * Small, bounded, read-through cache for stats results.
 *
 * An entry is reused for at most one TTL after it was computed, then the next read recomputes it.
 * Expired entries are dropped on the write path, and the map is capped with access-order eviction,
 * so a long-lived instance cannot grow with traffic or with period selections.
 *
 * Sections are short and the map is tiny: this is read-side only, and ingestion never touches it.
 */
internal class StatsResultCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private class Entry(
        val value: Any,
        val expiresAtMillis: Long,
    )

    private val entries =
        object : LinkedHashMap<StatsCacheKey, Entry>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<StatsCacheKey, Entry>): Boolean = size > maxEntries
        }

    /** The value for [key] if it was computed less than a TTL ago, otherwise `null`. */
    fun get(
        key: StatsCacheKey,
        nowMillis: Long,
    ): Any? =
        synchronized(entries) {
            val entry = entries[key] ?: return null
            if (entry.expiresAtMillis <= nowMillis) {
                entries.remove(key)
                return null
            }
            entry.value
        }

    fun put(
        key: StatsCacheKey,
        value: Any,
        nowMillis: Long,
        ttlMillis: Long,
    ) {
        synchronized(entries) {
            entries.entries.removeIf { it.value.expiresAtMillis <= nowMillis }
            entries[key] = Entry(value, nowMillis + ttlMillis)
        }
    }

    fun size(): Int = synchronized(entries) { entries.size }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 256
    }
}
