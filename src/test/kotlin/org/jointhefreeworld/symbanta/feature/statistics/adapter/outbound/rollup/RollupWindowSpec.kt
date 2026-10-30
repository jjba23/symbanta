/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.adapter.outbound.rollup

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jointhefreeworld.symbanta.shared.domain.DateRange
import java.time.Instant
import java.time.ZoneId

/**
 * Pins how a dashboard window is divided between rollup rows and the event log.
 *
 * The slices have to be disjoint and cover the window exactly once: an overlap counts data twice,
 * a gap loses it. These tests assert both the shape and that invariant, with the reporting time
 * zone's midnight (UTC+1 in January) as the day boundary.
 */
class RollupWindowSpec :
    FunSpec({
        val zone = ZoneId.of("Europe/Amsterdam")

        fun instant(value: String): Instant = Instant.parse(value)

        test("reads the event log for the whole window when nothing is rolled up") {
            val range = DateRange(instant("2026-01-10T10:37:00Z"), instant("2026-01-12T15:00:00Z"))

            val window = RollupWindow.of(range, coveredThrough = null, zone = zone, wholeDays = true)

            window.rawAfter shouldBe range
            window.rawBefore shouldBe emptyAt(range.from)
            window.hourRows shouldBe emptyAt(range.from)
            window.dayRows shouldBe emptyAt(range.from)
        }

        test("reads the event log when the rollups stop before the window starts") {
            val range = DateRange(instant("2026-01-10T10:37:00Z"), instant("2026-01-12T15:00:00Z"))

            val window =
                RollupWindow.of(range, coveredThrough = instant("2026-01-10T10:37:00Z"), zone = zone, wholeDays = true)

            window.rawAfter shouldBe range
            window.hourRows shouldBe emptyAt(range.from)
        }

        test("answers a whole-day window from day rows only") {
            val range = DateRange(instant("2026-01-06T23:00:00Z"), instant("2026-01-09T23:00:00Z"))

            val window =
                RollupWindow.of(range, coveredThrough = instant("2026-01-20T00:00:00Z"), zone = zone, wholeDays = true)

            window.dayRows shouldBe range
            window.hourRows shouldBe DateRange(range.from, range.to)
            window.rawBefore shouldBe emptyAt(range.from)
            window.rawAfter shouldBe emptyAt(range.to)
        }

        test("reads the minutes before the first whole hour from the event log") {
            val range = DateRange(instant("2026-01-10T10:37:00Z"), instant("2026-01-13T15:00:00Z"))

            val window =
                RollupWindow.of(range, coveredThrough = instant("2026-01-20T00:00:00Z"), zone = zone, wholeDays = true)

            window.rawBefore shouldBe DateRange(instant("2026-01-10T10:37:00Z"), instant("2026-01-10T11:00:00Z"))
            window.hourRows shouldBe
                DateRange(instant("2026-01-10T11:00:00Z"), instant("2026-01-13T15:00:00Z"))
            // Local midnights: whole days are 11 January and 12 January.
            window.dayRows shouldBe
                DateRange(instant("2026-01-10T23:00:00Z"), instant("2026-01-12T23:00:00Z"))
            window.rawAfter shouldBe emptyAt(range.to)
        }

        test("reads what the rollups have not reached yet from the event log") {
            val range = DateRange(instant("2026-01-10T10:37:00Z"), instant("2026-01-13T15:00:00Z"))

            val window =
                RollupWindow.of(range, coveredThrough = instant("2026-01-12T09:30:00Z"), zone = zone, wholeDays = true)

            window.rawBefore shouldBe DateRange(instant("2026-01-10T10:37:00Z"), instant("2026-01-10T11:00:00Z"))
            window.hourRows shouldBe
                DateRange(instant("2026-01-10T11:00:00Z"), instant("2026-01-12T09:30:00Z"))
            window.dayRows shouldBe
                DateRange(instant("2026-01-10T23:00:00Z"), instant("2026-01-11T23:00:00Z"))
            window.rawAfter shouldBe
                DateRange(instant("2026-01-12T09:30:00Z"), instant("2026-01-13T15:00:00Z"))
        }

        test("keeps hour rows for an hourly series") {
            val range = DateRange(instant("2026-01-10T10:37:00Z"), instant("2026-01-13T15:00:00Z"))

            val window =
                RollupWindow.of(range, coveredThrough = instant("2026-01-20T00:00:00Z"), zone = zone, wholeDays = false)

            window.dayRows shouldBe emptyAt(instant("2026-01-10T11:00:00Z"))
            window.hourRows shouldBe
                DateRange(instant("2026-01-10T11:00:00Z"), instant("2026-01-13T15:00:00Z"))
        }

        test("reads a window shorter than its first whole hour entirely from the event log") {
            val range = DateRange(instant("2026-01-10T10:37:00Z"), instant("2026-01-10T10:50:00Z"))

            val window =
                RollupWindow.of(range, coveredThrough = instant("2026-01-20T00:00:00Z"), zone = zone, wholeDays = true)

            window.rawAfter shouldBe range
            window.hourRows shouldBe emptyAt(range.from)
        }

        test("slices cover the window once, without overlaps or holes") {
            val windows =
                listOf(
                    DateRange(instant("2026-01-10T10:37:00Z"), instant("2026-01-13T15:00:00Z")),
                    DateRange(instant("2026-01-06T23:00:00Z"), instant("2026-01-09T23:00:00Z")),
                    DateRange(instant("2026-01-10T23:00:00Z"), instant("2026-01-11T02:00:00Z")),
                    DateRange(instant("2025-12-01T00:00:00Z"), instant("2026-01-13T15:00:00Z")),
                )
            val coverages =
                listOf(
                    instant("2026-01-20T00:00:00Z"),
                    instant("2026-01-12T09:30:00Z"),
                    instant("2026-01-10T11:00:00Z"),
                )

            windows.forEach { range ->
                coverages.forEach { covered ->
                    val window = RollupWindow.of(range, covered, zone, wholeDays = true)

                    window.rawBefore.from shouldBe range.from
                    window.hourRows.from shouldBe window.rawBefore.to
                    window.rawAfter.from shouldBe window.hourRows.to
                    window.rawAfter.to shouldBe range.to
                    (window.dayRows.from >= window.hourRows.from) shouldBe true
                    (window.dayRows.to <= window.hourRows.to) shouldBe true
                    (window.rawBefore.to <= window.hourRows.from) shouldBe true
                    (window.hourRows.to <= window.rawAfter.from) shouldBe true
                }
            }
        }

        test("an empty slice is not confused with a real one") {
            val range = DateRange(instant("2026-01-10T10:00:00Z"), instant("2026-01-10T11:00:00Z"))

            val window =
                RollupWindow.of(range, coveredThrough = instant("2026-01-20T00:00:00Z"), zone = zone, wholeDays = true)

            window.rawBefore shouldBe emptyAt(range.from)
            window.rawAfter shouldBe emptyAt(range.to)
            window.hourRows shouldNotBe emptyAt(range.from)
        }
    })

private fun emptyAt(instant: Instant): DateRange = DateRange(instant, instant)
