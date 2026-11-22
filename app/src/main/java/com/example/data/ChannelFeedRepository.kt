package com.example.data

import androidx.core.text.HtmlCompat
import com.example.model.ChannelPlatform
import com.example.model.ChannelPreviewInfo
import com.example.model.FeedPost
import com.example.model.ProjectPiChannels
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

data class LiveChannelBundle(
  val posts: List<FeedPost>,
  val previews: Map<String, ChannelPreviewInfo>,
  val hadAnyNetworkSuccess: Boolean,
)

private class InMemoryCookieJar : CookieJar {
  private val store = ConcurrentHashMap<String, List<Cookie>>()

  override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
    if (cookies.isEmpty()) return
    val existing = store[url.host].orEmpty().associateBy { it.name }.toMutableMap()
    cookies.forEach { existing[it.name] = it }
    store[url.host] = existing.values.toList()
  }

  override fun loadForRequest(url: HttpUrl): List<Cookie> {
    val now = System.currentTimeMillis()
    return store.entries
      .filter { (host, _) -> url.host == host || url.host.endsWith(".$host") }
      .flatMap { it.value }
      .filter { it.expiresAt >= now }
  }
}

class ChannelFeedRepository(
  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .cookieJar(InMemoryCookieJar())
      .connectTimeout(12, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .followRedirects(true)
      .followSslRedirects(true)
      .build()
) {

  private data class FetchedPage(val html: String, val resolvedUrl: String)

  suspend fun fetchLiveContent(): LiveChannelBundle = coroutineScope {
    val telegramDeferred =
      async { fetchHtml(ProjectPiChannels.TELEGRAM_WEB_FEED_URL, MOBILE_USER_AGENT) }
    val whatsappDeferred =
      async { fetchHtml(ProjectPiChannels.WHATSAPP_URL, DESKTOP_USER_AGENT) }
    val facebookDeferred =
      async { fetchHtml(ProjectPiChannels.FACEBOOK_URL, DESKTOP_USER_AGENT) }

    var telegramPage = telegramDeferred.await()
    if (telegramPage == null || parseTelegramChannelHtml(telegramPage.html).posts.isEmpty()) {
      val mirrorPage =
        fetchHtml(ProjectPiChannels.TELEGRAM_MIRROR_WEB_FEED_URL, MOBILE_USER_AGENT)
      if (mirrorPage != null) {
        val mirrorParsed = parseTelegramChannelHtml(mirrorPage.html)
        if (telegramPage == null || mirrorParsed.posts.isNotEmpty()) {
          telegramPage = mirrorPage
        }
      }
    }
    val whatsappPage = whatsappDeferred.await()
    val facebookPage = facebookDeferred.await()

    val previews = mutableMapOf<String, ChannelPreviewInfo>()
    val whatsappPosts = mutableListOf<FeedPost>()
    val telegramPosts = mutableListOf<FeedPost>()
    val facebookPosts = mutableListOf<FeedPost>()

    if (whatsappPage != null) {
      val parsedWa = parseWhatsAppChannelHtml(whatsappPage.html, whatsappPage.resolvedUrl)
      previews["whatsapp"] = parsedWa.preview
      whatsappPosts.addAll(parsedWa.posts)
    }

    if (telegramPage != null) {
      val parsedTg = parseTelegramChannelHtml(telegramPage.html)
      previews["telegram"] = parsedTg.preview
      telegramPosts.addAll(parsedTg.posts)
    }

    if (facebookPage != null) {
      val parsedFb = parseFacebookChannelHtml(facebookPage.html, facebookPage.resolvedUrl)
      previews["facebook"] = parsedFb.preview
      facebookPosts.addAll(parsedFb.posts)
    }

    val combinedPosts = interleaveChannelPosts(whatsappPosts, telegramPosts, facebookPosts)

    LiveChannelBundle(
      posts = PostDeduplicator.mergeAndDeduplicate(combinedPosts),
      previews = previews,
      hadAnyNetworkSuccess =
        telegramPage != null || whatsappPage != null || facebookPage != null,
    )
  }

  private suspend fun fetchHtml(url: String, userAgent: String): FetchedPage? =
    withContext(Dispatchers.IO) {
      val request =
        Request.Builder()
          .url(url)
          .header("User-Agent", userAgent)
          .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
          .header("Accept-Language", "en-US,en;q=0.9")
          .build()
      try {
        client.newCall(request).execute().use { response ->
          if (!response.isSuccessful) return@withContext null
          val body = response.body?.string() ?: return@withContext null
          FetchedPage(html = body, resolvedUrl = response.request.url.toString())
        }
      } catch (_: IOException) {
        null
      }
    }

  data class ParsedChannelResult(
    val preview: ChannelPreviewInfo,
    val posts: List<FeedPost>,
  )

  companion object {
    const val MOBILE_USER_AGENT =
      "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    const val DESKTOP_USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    private val TELEGRAM_TITLE_REGEX =
      Regex("""class="tgme_channel_info_header_title"[^>]*>.*?<span[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
    private val TELEGRAM_DESC_REGEX =
      Regex("""class="tgme_channel_info_description"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
    private val TELEGRAM_SUBS_REGEX =
      Regex("""class="counter_value">([^<]+)</span>\s*<span class="counter_type">subscribers</span>""", RegexOption.DOT_MATCHES_ALL)
    private val TELEGRAM_POST_SPLIT = "<div class=\"tgme_widget_message_wrap"
    private val DATA_POST_REGEX = Regex("""data-post="([^"]+)"""")
    private val MESSAGE_TEXT_REGEX =
      Regex("""class="tgme_widget_message_text[^"]*"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
    private val MESSAGE_TIME_REGEX =
      Regex("""<time[^>]*datetime="([^"]+)"[^>]*>([^<]*)</time>""", RegexOption.DOT_MATCHES_ALL)
    private val MESSAGE_VIEWS_REGEX =
      Regex("""class="tgme_widget_message_views">([^<]+)</span>""")
    private val MESSAGE_PHOTO_REGEX =
      Regex("""tgme_widget_message_photo_wrap[^>]*style="[^"]*background-image:url\(['"]?([^'")]+)['"]?\)""")

    private val META_TAG_REGEX = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val ATTR_PROPERTY_REGEX =
      Regex("""(?:property|name)=["'](og:title|og:description|og:image|description|twitter:title|twitter:description)["']""", RegexOption.IGNORE_CASE)
    private val ATTR_CONTENT_REGEX =
      Regex("""content=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val JSON_LD_REGEX =
      Regex("""<script[^>]*type=["']application/ld\+json["'][^>]*>(.*?)</script>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val JSON_MESSAGE_TEXT_REGEX =
      Regex(
        """"(?:articleBody|headline|description|message|caption|body|text)"\s*:\s*(?:\{\s*"text"\s*:\s*)?"((?:\\.|[^"\\]){16,1500})""""
      )

    private val GENERIC_BOILERPLATE_PHRASES =
      listOf(
        "log in or sign up",
        "log into facebook",
        "log in to facebook",
        "create an account",
        "see posts, photos and more",
        "whatsapp.com",
        "use whatsapp",
        "download whatsapp",
        "whatsapp channel invite",
        "follow this channel on whatsapp",
        "open app",
        "terms of service",
        "privacy policy",
        "cookie policy",
        "end-to-end encrypted",
        "allow all cookies",
        "decline optional cookies",
        "scan the qr code",
        "কমেন্ট করুন",
        "লগ ইন করুন",
        "নতুন অ্যাকাউন্ট তৈরি করুন",
        "পাসওয়ার্ড ভুলে গেছেন",
        "থেকে আরও দেখুন",
        "মন্তব্য করুন",
      )

    fun interleaveChannelPosts(
      whatsapp: List<FeedPost>,
      telegram: List<FeedPost>,
      facebook: List<FeedPost>,
    ): List<FeedPost> {
      val result = mutableListOf<FeedPost>()
      val maxLen = maxOf(whatsapp.size, telegram.size, facebook.size)
      for (i in 0 until maxLen) {
        if (i < whatsapp.size) result.add(whatsapp[i])
        if (i < telegram.size) result.add(telegram[i])
        if (i < facebook.size) result.add(facebook[i])
      }
      return result
    }

    fun parseTelegramChannelHtml(html: String): ParsedChannelResult {
      val channelTitle =
        TELEGRAM_TITLE_REGEX.find(html)?.groupValues?.getOrNull(1)?.let(::cleanHtmlText)
          ?.takeIf { it.isNotBlank() } ?: "Project π"
      val channelDesc =
        TELEGRAM_DESC_REGEX.find(html)?.groupValues?.getOrNull(1)?.let(::cleanHtmlText)
          ?.takeIf { it.isNotBlank() }
      val subscriberCount =
        TELEGRAM_SUBS_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim()

      val chunks = html.split(TELEGRAM_POST_SPLIT)
      val posts =
        chunks.drop(1).mapNotNull { chunk ->
          val dataPost = DATA_POST_REGEX.find(chunk)?.groupValues?.getOrNull(1) ?: return@mapNotNull null
          val rawText = MESSAGE_TEXT_REGEX.find(chunk)?.groupValues?.getOrNull(1)
          val text = rawText?.let(::cleanHtmlText).orEmpty()
          val imageUrl = MESSAGE_PHOTO_REGEX.find(chunk)?.groupValues?.getOrNull(1)

          if (text.isBlank() && imageUrl == null) return@mapNotNull null

          val timeMatch = MESSAGE_TIME_REGEX.find(chunk)
          val isoDate = timeMatch?.groupValues?.getOrNull(1)
          val displayTime = timeMatch?.groupValues?.getOrNull(2)?.trim().orEmpty()
          val timestampLabel = formatTimestampLabel(isoDate, displayTime)
          val views = MESSAGE_VIEWS_REGEX.find(chunk)?.groupValues?.getOrNull(1)?.trim()
          val postUrl = "https://t.me/$dataPost"

          FeedPost(
            id = "tg_$dataPost",
            platforms = listOf(ChannelPlatform.TELEGRAM),
            platformUrls = mapOf(ChannelPlatform.TELEGRAM to postUrl),
            channelTitle = channelTitle,
            text = text,
            timestampLabel = timestampLabel,
            viewCount = views,
            imageUrl = imageUrl,
            primaryUrl = postUrl,
          )
        }.reversed()

      return ParsedChannelResult(
        preview =
          ChannelPreviewInfo(
            channelId = "telegram",
            liveTitle = channelTitle,
            liveDescription = channelDesc,
            subscriberCount = subscriberCount,
          ),
        posts = posts,
      )
    }

    fun parseWhatsAppChannelHtml(
      html: String,
      resolvedUrl: String = ProjectPiChannels.WHATSAPP_URL,
    ): ParsedChannelResult {
      val og = parseOpenGraphMetadata("whatsapp", html, resolvedUrl)
      val embeddedTexts = extractEmbeddedJsonTexts(html)
      val usefulDesc = og.liveDescription?.takeUnless(::isGenericBoilerplate)
      val usefulTitle = og.liveTitle?.takeUnless(::isGenericBoilerplate) ?: "WhatsApp Channel"

      val posts = mutableListOf<FeedPost>()
      if (embeddedTexts.isNotEmpty()) {
        embeddedTexts.take(10).forEachIndexed { idx, body ->
          posts.add(
            FeedPost(
              id = "wa_http_$idx",
              platforms = listOf(ChannelPlatform.WHATSAPP),
              platformUrls = mapOf(ChannelPlatform.WHATSAPP to resolvedUrl),
              channelTitle = usefulTitle,
              text = body,
              timestampLabel = "WhatsApp Channel",
              imageUrl = if (idx == 0) og.imageUrl else null,
              primaryUrl = resolvedUrl,
              isLiveChannelCard = false,
            )
          )
        }
      } else if (!usefulDesc.isNullOrBlank()) {
        posts.add(
          FeedPost(
            id = "wa_live_snapshot",
            platforms = listOf(ChannelPlatform.WHATSAPP),
            platformUrls = mapOf(ChannelPlatform.WHATSAPP to resolvedUrl),
            channelTitle = usefulTitle,
            text = usefulDesc,
            timestampLabel = "WhatsApp Channel",
            imageUrl = og.imageUrl,
            primaryUrl = resolvedUrl,
            isLiveChannelCard = true,
          )
        )
      }
      return ParsedChannelResult(preview = og, posts = posts)
    }

    fun parseFacebookChannelHtml(
      html: String,
      resolvedUrl: String = ProjectPiChannels.FACEBOOK_URL,
    ): ParsedChannelResult {
      val og = parseOpenGraphMetadata("facebook", html, resolvedUrl)
      val embeddedTexts = extractEmbeddedJsonTexts(html)
      val usefulDesc = og.liveDescription?.takeUnless(::isGenericBoilerplate)
      val usefulTitle = og.liveTitle?.takeUnless(::isGenericBoilerplate) ?: "Facebook"

      val posts = mutableListOf<FeedPost>()
      if (embeddedTexts.isNotEmpty()) {
        embeddedTexts.take(10).forEachIndexed { idx, body ->
          posts.add(
            FeedPost(
              id = "fb_http_$idx",
              platforms = listOf(ChannelPlatform.FACEBOOK),
              platformUrls = mapOf(ChannelPlatform.FACEBOOK to resolvedUrl),
              channelTitle = usefulTitle,
              text = body,
              timestampLabel = "Facebook",
              imageUrl = if (idx == 0) og.imageUrl else null,
              primaryUrl = resolvedUrl,
              isLiveChannelCard = false,
            )
          )
        }
      } else if (!usefulDesc.isNullOrBlank()) {
        posts.add(
          FeedPost(
            id = "fb_live_snapshot",
            platforms = listOf(ChannelPlatform.FACEBOOK),
            platformUrls = mapOf(ChannelPlatform.FACEBOOK to resolvedUrl),
            channelTitle = usefulTitle,
            text = usefulDesc,
            timestampLabel = "Facebook",
            imageUrl = og.imageUrl,
            primaryUrl = resolvedUrl,
            isLiveChannelCard = true,
          )
        )
      }
      return ParsedChannelResult(preview = og, posts = posts)
    }

    fun parseOpenGraphMetadata(
      channelId: String,
      html: String,
      resolvedUrl: String? = null,
    ): ChannelPreviewInfo {
      var title: String? = null
      var description: String? = null
      var image: String? = null

      META_TAG_REGEX.findAll(html).forEach { match ->
        val tag = match.value
        val prop = ATTR_PROPERTY_REGEX.find(tag)?.groupValues?.getOrNull(1)?.lowercase() ?: return@forEach
        val content =
          ATTR_CONTENT_REGEX.find(tag)?.groupValues?.getOrNull(1)?.let(::decodeEntities)?.trim()
            ?.takeIf { it.isNotBlank() } ?: return@forEach

        when (prop) {
          "og:title", "twitter:title" -> if (title == null) title = content
          "og:description", "twitter:description" -> if (description == null) description = content
          "description" -> if (description == null) description = content
          "og:image" -> if (image == null) image = content
        }
      }

      return ChannelPreviewInfo(
        channelId = channelId,
        liveTitle = title?.takeUnless(::isGenericBoilerplate),
        liveDescription = description?.takeUnless(::isGenericBoilerplate),
        imageUrl = image,
        resolvedUrl = resolvedUrl,
      )
    }

    private fun extractEmbeddedJsonTexts(html: String): List<String> {
      val results = mutableListOf<String>()
      val sources =
        buildList {
          JSON_LD_REGEX.findAll(html).forEach { add(it.groupValues[1]) }
          add(html)
        }
      for (src in sources) {
        JSON_MESSAGE_TEXT_REGEX.findAll(src).forEach { fieldMatch ->
          val unescaped =
            fieldMatch.groupValues[1]
              .replace("\\n", "\n")
              .replace("\\\"", "\"")
              .replace("\\/", "/")
              .replace("\\u0026", "&")
              .trim()
          if (unescaped.length >= 25 &&
            !unescaped.startsWith("http") &&
            !unescaped.contains("function(") &&
            !unescaped.contains("window.") &&
            !unescaped.contains("{") &&
            !unescaped.contains("</") &&
            unescaped.count { it == ' ' } >= 3 &&
            !isGenericBoilerplate(unescaped)
          ) {
            results.add(unescaped)
          }
        }
        if (results.size >= 10) break
      }
      return results.distinct()
    }

    fun isGenericBoilerplate(text: String): Boolean {
      val lower = text.trim().lowercase()
      return GENERIC_BOILERPLATE_PHRASES.any { lower.contains(it) } ||
        lower == "facebook" ||
        lower == "whatsapp" ||
        lower == "কমেন্ট করুন" ||
        lower == "লাইক" ||
        lower == "শেয়ার করুন"
    }

    private fun cleanHtmlText(rawHtml: String): String {
      return HtmlCompat.fromHtml(rawHtml, HtmlCompat.FROM_HTML_MODE_COMPACT)
        .toString()
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
    }

    private fun decodeEntities(raw: String): String =
      HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()

    private fun formatTimestampLabel(isoDate: String?, shortTime: String): String {
      if (isoDate != null && isoDate.length >= 10) {
        val datePart = isoDate.substring(0, 10)
        return if (shortTime.isNotBlank()) "$datePart • $shortTime" else datePart
      }
      return shortTime.ifBlank { "Recent" }
    }
  }
}
