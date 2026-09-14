/* symbanta
 *
 * Copyright © Josep Bigorra <jjbigorra@gmail.com>
 *
 * symbanta is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * symbanta is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with symbanta.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.jointhefreeworld.symbanta

import org.jointhefreeworld.symbanta.domain.ScreenSizeStat

/**
 * Maps raw screen dimensions into aggregated resolution buckets
 * to reduce metric fragmentation across desktop, tablet, and mobile viewports.
 */
object ScreenSizeMapper {
    data class ResolutionBucket(
        val label: String,
        val nominalWidth: Int,
        val nominalHeight: Int,
    )

    val BUCKET_ULTRA_HD = ResolutionBucket("> 1440p", 3840, 2160)
    val BUCKET_QHD = ResolutionBucket("1080p - 1440p", 2560, 1440)
    val BUCKET_FULL_HD = ResolutionBucket("900p - 1080p", 1920, 1080)
    val BUCKET_HD_PLUS = ResolutionBucket("720p - 900p", 1440, 900)
    val BUCKET_HD = ResolutionBucket("600p - 720p", 1280, 720)
    val BUCKET_LARGE_MOBILE = ResolutionBucket("480p - 600p", 414, 896)
    val BUCKET_SMALL_MOBILE = ResolutionBucket("≤ 480p", 360, 640)
    val BUCKET_UNKNOWN = ResolutionBucket("Unknown", 0, 0)

    fun mapToBucket(
        width: Int,
        height: Int,
    ): ResolutionBucket {
        if (width <= 0 || height <= 0) {
            return BUCKET_UNKNOWN
        }

        // Normalize dimensions to portrait vs. landscape independent effective axes
        val effectiveWidth = maxOf(width, height)
        val effectiveHeight = minOf(width, height)

        return when {
            effectiveHeight > 1440 || effectiveWidth > 2560 -> BUCKET_ULTRA_HD
            effectiveHeight > 1080 || effectiveWidth > 1920 -> BUCKET_QHD
            effectiveHeight > 900 || effectiveWidth > 1600 -> BUCKET_FULL_HD
            effectiveHeight > 720 || effectiveWidth > 1280 -> BUCKET_HD_PLUS
            effectiveHeight > 600 || effectiveWidth > 1024 -> BUCKET_HD
            effectiveHeight > 480 || effectiveWidth > 640 -> BUCKET_LARGE_MOBILE
            else -> BUCKET_SMALL_MOBILE
        }
    }

    fun aggregate(stats: List<ScreenSizeStat>): List<ScreenSizeStat> =
        stats
            .groupBy { mapToBucket(it.width, it.height) }
            .map { (bucket, group) ->
                ScreenSizeStat(
                    width = bucket.nominalWidth,
                    height = bucket.nominalHeight,
                    pageViews = group.sumOf { it.pageViews },
                    visitors = group.sumOf { it.visitors },
                )
            }.sortedByDescending { it.pageViews }
}
