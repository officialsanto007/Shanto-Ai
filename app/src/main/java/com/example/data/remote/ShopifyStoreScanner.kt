package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

data class ShopifyScanResult(
    val normalizedUrl: String,
    val domain: String,
    val storeName: String,
    val isShopifyDetected: Boolean,
    val httpStatus: Int,
    val publicEmails: List<String>,
    val publicPhones: List<String>,
    val instagramUsername: String,
    val instagramProfileUrl: String,
    val whatsappNumber: String,
    val structuralSignals: List<String>,
    val productSampleCount: Int,
    val hasViewportMeta: Boolean,
    val h1Count: Int,
    val navLinkCount: Int,
    val missingAltImagesCount: Int,
    val totalImagesChecked: Int
)

class ShopifyStoreScanner(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    suspend fun scanStore(rawUrl: String): Result<ShopifyScanResult> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedUrl = normalizeUrl(rawUrl)
            val uri = URI(normalizedUrl)
            val domain = (uri.host ?: normalizedUrl).removePrefix("www.")

            val htmlRequest = Request.Builder()
                .url(normalizedUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
                )
                .get()
                .build()

            val (statusCode, html) = client.newCall(htmlRequest).execute().use { resp ->
                resp.code to (resp.body?.string().orEmpty().take(350_000))
            }

            if (statusCode >= 400 && html.isBlank()) {
                error("Website returned HTTP $statusCode")
            }

            // Probe public Shopify /products.json endpoint
            val productsJsonCount = probeProductsJsonCount(normalizedUrl)

            val isShopify = html.contains("cdn.shopify.com", ignoreCase = true) ||
                html.contains("Shopify.theme", ignoreCase = true) ||
                html.contains("shopify-section", ignoreCase = true) ||
                productsJsonCount > 0

            val title = extractMetaOrTitle(html, domain)
            val emails = extractPublicEmails(html)
            val phones = extractPublicPhones(html)
            val igHandle = extractInstagramHandle(html)
            val waNumber = extractWhatsAppNumber(html, phones)

            val hasViewport = Regex("""<meta[^>]*name=["']viewport["']""", RegexOption.IGNORE_CASE).containsMatchIn(html)
            val h1Matches = Regex("""<h1\b[^>]*>(.*?)</h1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .findAll(html)
                .map { stripHtml(it.groupValues[1]).trim() }
                .filter { it.isNotBlank() }
                .toList()

            val navBlock = Regex("""<nav\b[^>]*>(.*?)</nav>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)?.groupValues?.get(1).orEmpty()
            val navLinks = Regex("""<a\b""", RegexOption.IGNORE_CASE).findAll(navBlock).count()

            val imgTags = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(html).take(40).toList()
            val missingAltCount = imgTags.count { tag ->
                val hasAlt = Regex("""\balt=["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(tag.value)
                hasAlt == null || hasAlt.groupValues[1].isBlank()
            }

            val hasHeroSection = html.contains("hero", ignoreCase = true) ||
                html.contains("slideshow", ignoreCase = true) ||
                html.contains("banner", ignoreCase = true)
            val hasReviews = html.contains("judge.me", ignoreCase = true) ||
                html.contains("yotpo", ignoreCase = true) ||
                html.contains("loox", ignoreCase = true) ||
                html.contains("okendo", ignoreCase = true) ||
                html.contains("stamped", ignoreCase = true) ||
                html.contains("reviews", ignoreCase = true)
            val hasAnnouncementBar = html.contains("announcement", ignoreCase = true)
            val hasNewsletter = html.contains("newsletter", ignoreCase = true) ||
                html.contains("klaviyo", ignoreCase = true)
            val hasCollectionLinks = html.contains("/collections/", ignoreCase = true)
            val hasSearch = html.contains("/search", ignoreCase = true) ||
                html.contains("type=\"search\"", ignoreCase = true)

            val signals = buildList {
                add("HTTP status: $statusCode | Shopify detected: ${if (isShopify) "Yes" else "Unconfirmed"}")
                if (productsJsonCount > 0) add("Public Shopify catalog probe: $productsJsonCount products fetched from /products.json")
                add("Viewport meta tag for mobile scaling: ${if (hasViewport) "Present" else "Missing"}")
                add("H1 hero headings found: ${h1Matches.size}${if (h1Matches.isNotEmpty()) " (Primary: '${h1Matches.first().take(60)}')" else ""}")
                add("Hero/banner section markup detected: ${if (hasHeroSection) "Yes" else "No dedicated hero markup found"}")
                add("Top navigation links inside <nav>: $navLinks")
                add("Product/image tags checked: ${imgTags.size}, missing alt text: $missingAltCount")
                add("Collection page links (/collections/): ${if (hasCollectionLinks) "Present" else "Not detected on homepage"}")
                add("Social proof / review app integration: ${if (hasReviews) "Detected" else "Not detected on homepage"}")
                add("Header announcement/promo bar: ${if (hasAnnouncementBar) "Detected" else "Not detected"}")
                add("Email capture / Klaviyo form: ${if (hasNewsletter) "Detected" else "Not detected"}")
                add("Storefront search affordance: ${if (hasSearch) "Detected" else "Not detected"}")
            }

            ShopifyScanResult(
                normalizedUrl = normalizedUrl,
                domain = domain,
                storeName = title,
                isShopifyDetected = isShopify,
                httpStatus = statusCode,
                publicEmails = emails,
                publicPhones = phones,
                instagramUsername = igHandle,
                instagramProfileUrl = if (igHandle.isNotBlank()) "https://instagram.com/$igHandle" else "",
                whatsappNumber = waNumber,
                structuralSignals = signals,
                productSampleCount = productsJsonCount,
                hasViewportMeta = hasViewport,
                h1Count = h1Matches.size,
                navLinkCount = navLinks,
                missingAltImagesCount = missingAltCount,
                totalImagesChecked = imgTags.size
            )
        }
    }

    private fun probeProductsJsonCount(baseUrl: String): Int {
        return try {
            val cleanBase = baseUrl.trimEnd('/')
            val req = Request.Builder()
                .url("$cleanBase/products.json?limit=6")
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return 0
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                json.optJSONArray("products")?.length() ?: 0
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private fun extractMetaOrTitle(html: String, fallbackDomain: String): String {
        val ogSiteName = Regex(
            """<meta[^>]*property=["']og:site_name["'][^>]*content=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.get(1)
        if (!ogSiteName.isNullOrBlank()) return ogSiteName.trim()

        val title = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)
            ?.let { stripHtml(it).split("|", "–", "—", "-").firstOrNull()?.trim() }
        return if (!title.isNullOrBlank()) title else fallbackDomain.substringBefore('.').replaceFirstChar { it.uppercase() }
    }

    private fun extractPublicEmails(html: String): List<String> {
        val mailtoRegex = Regex("""mailto:([a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,})""", RegexOption.IGNORE_CASE)
        val plainEmailRegex = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,8}""")
        val ignored = listOf("shopify.com", "sentry.io", "w3.org", "example.com", ".png", ".jpg", ".webp")
        val combined = (mailtoRegex.findAll(html).map { it.groupValues[1] } +
            plainEmailRegex.findAll(html).map { it.value })
            .map { it.lowercase().trim() }
            .filter { email -> ignored.none { email.contains(it) } }
            .distinct()
            .take(3)
            .toList()
        return combined
    }

    private fun extractPublicPhones(html: String): List<String> {
        val telRegex = Regex("""tel:([+0-9()\-\s]{7,20})""", RegexOption.IGNORE_CASE)
        return telRegex.findAll(html)
            .map { it.groupValues[1].trim() }
            .filter { it.count(Char::isDigit) >= 10 }
            .distinct()
            .take(2)
            .toList()
    }

    private fun extractInstagramHandle(html: String): String {
        val igRegex = Regex("""https?://(?:www\.)?instagram\.com/([a-zA-Z0-9._]{2,30})""", RegexOption.IGNORE_CASE)
        val blacklist = setOf("p", "reel", "reels", "stories", "explore", "accounts", "shopify")
        return igRegex.findAll(html)
            .map { it.groupValues[1].trim('/') }
            .firstOrNull { handle -> handle.lowercase() !in blacklist }
            .orEmpty()
    }

    private fun extractWhatsAppNumber(html: String, phones: List<String>): String {
        val waRegex = Regex("""(?:wa\.me/|whatsapp\.com/send\?phone=)(\+?[0-9]{10,15})""", RegexOption.IGNORE_CASE)
        val direct = waRegex.find(html)?.groupValues?.get(1)
        if (!direct.isNullOrBlank()) {
            return if (direct.startsWith("+")) direct else "+$direct"
        }
        return phones.firstOrNull().orEmpty()
    }

    private fun stripHtml(text: String): String =
        text.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
}
