/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.domain

import org.jointhefreeworld.symbanta.feature.statistics.domain.UserAgentGroupStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.UserAgentStat

object UserAgentParser {
    fun parse(ua: String): String {
        if (ua.isBlank()) return "???"

        val lowerUa = ua.lowercase()

        // Detect AI Crawlers & LLM Bots
        val aiBot =
            when {
                lowerUa.contains("gptbot") || lowerUa.contains("oai-searchbot") || lowerUa.contains("chatgpt") -> "ChatGPT / OpenAI"
                lowerUa.contains("claudebot") || lowerUa.contains("claude-") || lowerUa.contains("anthropic-ai") -> "Claude / Anthropic"
                lowerUa.contains("perplexity") -> "Perplexity"
                lowerUa.contains("google-extended") -> "Google Gemini AI"
                lowerUa.contains("bytespider") -> "ByteDance AI"
                lowerUa.contains("ccbot") -> "Common Crawl"
                else -> null
            }
        if (aiBot != null) return aiBot

        // Detect Web & Social Crawlers
        val crawler =
            when {
                lowerUa.contains("googlebot") -> "Googlebot"

                lowerUa.contains("bingbot") -> "Bingbot"

                lowerUa.contains("baiduspider") -> "Baidu Spider"

                lowerUa.contains("yandex") -> "Yandex Bot"

                lowerUa.contains("duckduckbot") -> "DuckDuckBot"

                lowerUa.contains("sogou") -> "Sogou Web Spider"

                lowerUa.contains("applebot") -> "Applebot"

                lowerUa.contains(
                    "meta-externalagent",
                ) ||
                    lowerUa.contains("meta-webindexer") ||
                    lowerUa.contains("facebookexternalhit") -> "Meta Crawler"

                lowerUa.contains("twitterbot") -> "Twitter / X Bot"

                lowerUa.contains("linkedinbot") -> "LinkedIn Bot"

                lowerUa.contains("ahrefsbot") -> "Ahrefs Bot"

                lowerUa.contains("semrushbot") -> "Semrush Bot"

                else -> null
            }
        if (crawler != null) return crawler

        // Detect Operating System
        val os =
            when {
                ua.contains("iPhone") -> "iOS / iPhone"
                ua.contains("iPad") -> "iOS / iPad"
                ua.contains("Android") -> "Android"
                ua.contains("Windows") -> "Windows"
                ua.contains("Macintosh") || ua.contains("Mac OS") -> "macOS"
                ua.contains("CrOS") -> "ChromeOS"
                ua.contains("Ubuntu") -> "GNU/Linux"
                ua.contains("Linux") || ua.contains("X11") -> "GNU/Linux"
                else -> null
            }

        // Detect Browser (Order matters: check niche browsers before Safari/Chrome)
        val browser =
            when {
                // Niche, FOSS, and Privacy-focused Gecko forks
                ua.contains("LibreWolf/") -> "LibreWolf"

                ua.contains("Waterfox/") -> "Waterfox"

                ua.contains("PaleMoon/") -> "Pale Moon"

                ua.contains("Iceweasel/") -> "Iceweasel"

                ua.contains("MullvadBrowser/") -> "Mullvad Browser"

                ua.contains("TorBrowser/") ||
                    (ua.contains("Firefox/") && ua.contains("Android") && ua.contains("Orbot")) -> "Tor Browser"

                ua.contains("Fennec/") -> "Fennec"

                // Niche & FOSS WebKitGTK / Qt / GNU/Linux-native Browsers
                ua.contains("Epiphany/") -> "GNOME Web (Epiphany)"

                ua.contains("Falkon/") -> "Falkon"

                ua.contains("Luakit/") -> "Luakit"

                ua.contains("Qutebrowser") -> "qutebrowser"

                ua.contains("Midori/") -> "Midori"

                ua.contains("Min/") -> "Min"

                ua.contains("Nyxt/") -> "Nyxt"

                // Niche Chromium forks & Keyboard/Productivity Browsers
                ua.contains("Arc/") -> "Arc"

                ua.contains("Orion/") -> "Orion"

                ua.contains("Brave") -> "Brave"

                ua.contains("Vivaldi/") -> "Vivaldi"

                ua.contains("Ungoogled") -> "Ungoogled Chromium"

                ua.contains("Chromium/") -> "Chromium"

                // Mobile & Other Region/Vendor Niche Browsers
                ua.contains("SamsungBrowser/") -> "Samsung Internet"

                ua.contains("OPR/") || ua.contains("Opera/") || ua.contains("OPT/") -> "Opera"

                ua.contains("YaBrowser/") -> "Yandex Browser"

                ua.contains("UCBrowser/") -> "UC Browser"

                ua.contains("DuckDuckGo/") -> "DuckDuckGo"

                ua.contains("Focus/") -> "Firefox Focus"

                // Major Browsers
                ua.contains("Edg/") || ua.contains("Edge/") -> "Edge"

                ua.contains("Firefox/") || ua.contains("FxiOS/") -> "Firefox"

                ua.contains("Chrome/") || ua.contains("CriOS/") -> "Chrome"

                ua.contains("Safari/") || ua.contains("Version/") || ua.contains("Mobile/") -> "Safari"

                else -> null
            }

        return when {
            os != null && browser != null -> "$os, $browser"
            os != null -> os
            browser != null -> browser
            else -> ua
        }
    }

    /** Collapses raw user agent strings into readable labels for dashboards. */
    fun aggregate(stats: List<UserAgentStat>): List<UserAgentGroupStat> =
        stats
            .groupBy { parse(it.userAgent) }
            .map { (label, group) ->
                UserAgentGroupStat(
                    label = label,
                    pageViews = group.sumOf { it.pageViews },
                    visitors = group.sumOf { it.visitors },
                )
            }.sortedByDescending { it.pageViews }
}
