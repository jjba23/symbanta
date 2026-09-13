package org.jointhefreeworld.symbanta

object UserAgentParser {
    fun parse(ua: String): String {
        if (ua.isBlank()) return "Unknown"

        val lowerUa = ua.lowercase()

        // Detect Crawlers & Bots
        if (lowerUa.contains("meta-externalagent") || lowerUa.contains("meta-webindexer")) return "Meta Crawler"
        if (lowerUa.contains("bingbot")) return "Bingbot"
        if (lowerUa.contains("chatgpt")) return "ChatGPT"
        if (lowerUa.contains("duckduckbot")) return "DuckDuckBot"
        if (lowerUa.contains("sogou web spider")) return "Sogou Web Spider"
        if (lowerUa.contains("googlebot")) return "Googlebot"
        if (lowerUa.contains("baiduspider")) return "Baidu Spider"
        if (lowerUa.contains("ahrefsbot")) return "Ahrefs Bot"

        // Detect Operating System
        val os =
            when {
                ua.contains("iPhone") -> "iOS / iPhone"
                ua.contains("iPad") -> "iOS / iPad"
                ua.contains("Android") -> "Android"
                ua.contains("Windows") -> "Windows"
                ua.contains("Macintosh") || ua.contains("Mac OS") -> "macOS"
                ua.contains("Ubuntu") -> "Linux"
                ua.contains("Linux") || ua.contains("X11") -> "Linux"
                else -> null
            }

        // Detect Browser (Order matters: check specific browsers before Safari/Chrome)
        val browser =
            when {
                ua.contains("Brave") -> "Brave"
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
}
