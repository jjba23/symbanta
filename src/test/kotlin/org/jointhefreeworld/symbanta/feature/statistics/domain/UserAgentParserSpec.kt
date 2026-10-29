/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/
package org.jointhefreeworld.symbanta.feature.statistics.domain

import io.kotest.core.names.DuplicateTestNameMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class UserAgentParserSpec :
    FunSpec({

        duplicateTestNameMode = DuplicateTestNameMode.Silent

        data class TestCase(
            val ua: String,
            val expected: String,
        )

        context("Empty and Blank Strings") {
            withData(
                nameFn = { "should return '${it.expected}' for '${it.ua}'" },
                TestCase("", "???"),
                TestCase(" ", "???"),
                TestCase("   ", "???"),
                TestCase("\t", "???"),
                TestCase("\n", "???"),
            ) { (ua, expected) ->
                UserAgentParser.parse(ua) shouldBe expected
            }
        }

        context("AI Crawlers & LLM Bots") {
            withData(
                nameFn = { "should parse ${it.ua} to ${it.expected}" },
                TestCase(
                    "Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko); compatible; GPTBot/1.0",
                    "ChatGPT / OpenAI",
                ),
                TestCase("OAI-SearchBot/1.0", "ChatGPT / OpenAI"),
                TestCase("chatgpt-user-agent", "ChatGPT / OpenAI"),
                TestCase("Mozilla/5.0 (compatible; ClaudeBot/1.0; +claudebot@anthropic.com)", "Claude / Anthropic"),
                TestCase("claude-web-scraper", "Claude / Anthropic"),
                TestCase("anthropic-ai-bot", "Claude / Anthropic"),
                TestCase("PerplexityBot/1.0 (+http://www.perplexity.ai/bot.html)", "Perplexity"),
                TestCase("Mozilla/5.0 (compatible; Google-Extended/2.1)", "Google Gemini AI"),
                TestCase("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Bytespider", "ByteDance AI"),
                TestCase("CCBot/2.0 (https://commoncrawl.org/faq/)", "Common Crawl"),
            ) { (ua, expected) ->
                UserAgentParser.parse(ua) shouldBe expected
            }
        }

        context("Search Engine & Web Crawlers") {
            withData(
                nameFn = { "should parse ${it.expected}" },
                TestCase("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)", "Googlebot"),
                TestCase("Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)", "Bingbot"),
                TestCase(
                    "Mozilla/5.0 (compatible; Baiduspider/2.0; +http://www.baidu.com/search/spider.html)",
                    "Baidu Spider",
                ),
                TestCase("Mozilla/5.0 (compatible; YandexBot/3.0; +http://yandex.com/bots)", "Yandex Bot"),
                TestCase("DuckDuckBot/1.0; (+http://duckduckgo.com/duckduckbot.html)", "DuckDuckBot"),
                TestCase("Sogou web spider/4.0", "Sogou Web Spider"),
                TestCase(
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_1) AppleWebKit/600.2.5 (Applebot/0.1)",
                    "Applebot",
                ),
                TestCase("facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)", "Meta Crawler"),
                TestCase("meta-externalagent/1.1", "Meta Crawler"),
                TestCase("meta-webindexer/1.0", "Meta Crawler"),
                TestCase("Twitterbot/1.0", "Twitter / X Bot"),
                TestCase("LinkedInBot/1.0 (compatible; Mozilla/5.0;)", "LinkedIn Bot"),
                TestCase("Mozilla/5.0 (compatible; AhrefsBot/7.0; +http://ahrefs.com/robot/)", "Ahrefs Bot"),
                TestCase("Mozilla/5.0 (compatible; SemrushBot/7~bl; +http://www.semrush.com/bot.html)", "Semrush Bot"),
            ) { (ua, expected) ->
                UserAgentParser.parse(ua) shouldBe expected
            }
        }

        context("Real Browsers (OS & Browser Combinations)") {
            withData(
                nameFn = { "should parse ${it.expected} correctly" },
                // Windows
                TestCase(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
                    "Windows, Chrome",
                ),
                TestCase(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/119.0",
                    "Windows, Firefox",
                ),
                TestCase(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0",
                    "Windows, Edge",
                ),
                TestCase(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36 OPR/105.0.0.0",
                    "Windows, Opera",
                ),
                TestCase(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/118.0.0.0 Safari/537.36 Vivaldi/6.4",
                    "Windows, Vivaldi",
                ),
                TestCase(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/118.0 YaBrowser/23.11.0.0 Safari/537.36",
                    "Windows, Yandex Browser",
                ),
                TestCase(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36 Brave/1.61",
                    "Windows, Brave",
                ),
                // macOS
                TestCase(
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Version/17.1 Safari/605.1.15",
                    "macOS, Safari",
                ),
                TestCase(
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:109.0) Gecko/20100101 Firefox/119.0",
                    "macOS, Firefox",
                ),
                TestCase(
                    "Mozilla/5.0 (Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
                    "macOS, Chrome",
                ),
                // iOS / iPhone / iPad
                TestCase(
                    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_1 like Mac OS X) AppleWebKit/605.1.15 Version/17.1 Mobile/15E148 Safari/604.1",
                    "iOS / iPhone, Safari",
                ),
                TestCase(
                    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_1 like Mac OS X) AppleWebKit/605.1.15 CriOS/120.0.0.0 Mobile/15E148 Safari/604.1",
                    "iOS / iPhone, Chrome",
                ),
                TestCase(
                    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_1 like Mac OS X) AppleWebKit/605.1.15 FxiOS/119.0 Mobile/15E148 Safari/605.1.15",
                    "iOS / iPhone, Firefox",
                ),
                TestCase(
                    "Mozilla/5.0 (iPad; CPU OS 17_1 like Mac OS X) AppleWebKit/605.1.15 Version/17.1 Mobile/15E148 Safari/604.1",
                    "iOS / iPad, Safari",
                ),
                // Android
                TestCase(
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36",
                    "Android, Chrome",
                ),
                TestCase("Mozilla/5.0 (Android 14; Mobile; rv:109.0) Gecko/119.0 Firefox/119.0", "Android, Firefox"),
                TestCase(
                    "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 SamsungBrowser/23.0 Chrome/115.0 Mobile Safari/537.36",
                    "Android, Samsung Internet",
                ),
                TestCase(
                    "Mozilla/5.0 (Linux; U; Android 14) AppleWebKit/537.36 Chrome/78.0.3904.108 UCBrowser/13.4.0 Mobile Safari/537.36",
                    "Android, UC Browser",
                ),
                TestCase(
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36 Opera/105.0.0.0",
                    "Android, Opera",
                ), // Opera Mobile
                // Linux / ChromeOS
                TestCase(
                    "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/119.0",
                    "GNU/Linux, Firefox",
                ),
                TestCase(
                    "Mozilla/5.0 (X11; Ubuntu; Linux x86_64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
                    "GNU/Linux, Chrome",
                ),
                TestCase(
                    "Mozilla/5.0 (X11; CrOS x86_64 14541.0.0) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
                    "ChromeOS, Chrome",
                ),
            ) { (ua, expected) ->
                UserAgentParser.parse(ua) shouldBe expected
            }
        }

        context("Fallbacks and Edge Cases") {
            withData(
                nameFn = { "should parse ${it.expected} accurately when data is incomplete" },
                // Missing Browser
                TestCase("Mozilla/5.0 (Windows NT 10.0; Win64; x64) CustomApp/1.0", "Windows"),
                TestCase("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15) CustomClient/2.0", "macOS"),
                // Missing OS
                TestCase("CustomDevice/1.0 Firefox/119.0", "Firefox"),
                TestCase("WeirdApp/3.0 Safari/605.1.15", "Safari"),
                // Completely unidentifiable string returns the raw UA
                TestCase("curl/7.68.0", "curl/7.68.0"),
                TestCase("PostmanRuntime/7.35.0", "PostmanRuntime/7.35.0"),
                TestCase("SuperCustomInternalBot/v2.1", "SuperCustomInternalBot/v2.1"),
            ) { (ua, expected) ->
                UserAgentParser.parse(ua) shouldBe expected
            }
        }
    })
