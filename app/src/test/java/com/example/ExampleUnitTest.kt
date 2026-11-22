package com.example

import com.example.data.ChannelFeedRepository
import com.example.data.PostDeduplicator
import com.example.model.ChannelPlatform
import com.example.model.FeedPost
import com.example.model.ProjectPiChannels
import com.example.model.QuestionPostHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun questionDetection_ignoresAnswerStatements_andDetectsOlympiadQuestions() {
    assertFalse(QuestionPostHelper.looksLikeQuestion("The answer will be 1.8751"))
    assertFalse(QuestionPostHelper.looksLikeQuestion("Answer: 42"))
    assertTrue(QuestionPostHelper.looksLikeQuestion("Russian math olympiad 2007"))
    assertTrue(QuestionPostHelper.looksLikeQuestion("Daily Question: What is 2 + 2?"))
    assertTrue(QuestionPostHelper.looksLikeQuestion("Solve for x in 3x = 12"))
  }

  @Test
  fun boilerplateFilter_catchesBengaliAndEnglishUiNoise() {
    assertTrue(ChannelFeedRepository.isGenericBoilerplate("কমেন্ট করুন"))
    assertTrue(ChannelFeedRepository.isGenericBoilerplate("লগ ইন করুন"))
    assertTrue(
      ChannelFeedRepository.isGenericBoilerplate(
        "Follow Project π's WhatsApp Channel. 33 followers for the latest updates."
      )
    )
    assertFalse(ChannelFeedRepository.isGenericBoilerplate("Russian math olympiad 2007"))
    assertEquals(
      "https://telegram.me/s/projectpitelegram",
      ProjectPiChannels.TELEGRAM_MIRROR_WEB_FEED_URL,
    )
  }

  @Test
  fun deduplicator_mergesCrossPlatformPostsEvenWithHashtagsAndEmojis() {
    val tg =
      FeedPost(
        id = "tg_10",
        platforms = listOf(ChannelPlatform.TELEGRAM),
        platformUrls = mapOf(ChannelPlatform.TELEGRAM to "https://t.me/projectpitelegram/10"),
        channelTitle = "Project π",
        text = "Russian math olympiad 2007 problem #ProjectPi",
        timestampLabel = "2026-11-21 • 12:00",
        viewCount = "220",
        primaryUrl = "https://t.me/projectpitelegram/10",
      )
    val fb =
      FeedPost(
        id = "fb_10",
        platforms = listOf(ChannelPlatform.FACEBOOK),
        platformUrls = mapOf(ChannelPlatform.FACEBOOK to ProjectPiChannels.FACEBOOK_URL),
        channelTitle = "Facebook",
        text = "Russian math olympiad 2007 problem 🔥",
        timestampLabel = "Facebook",
        imageUrl = "https://scontent.example.com/photo.jpg",
        primaryUrl = ProjectPiChannels.FACEBOOK_URL,
      )
    val wa =
      FeedPost(
        id = "wa_10",
        platforms = listOf(ChannelPlatform.WHATSAPP),
        platformUrls = mapOf(ChannelPlatform.WHATSAPP to ProjectPiChannels.WHATSAPP_URL),
        channelTitle = "WhatsApp Channel",
        text = "Russian math olympiad 2007 problem https://whatsapp.com/channel/0029VbDBwQp4Y9lhHdsmHG2D",
        timestampLabel = "WhatsApp Channel",
        primaryUrl = ProjectPiChannels.WHATSAPP_URL,
      )

    val merged = PostDeduplicator.mergeAndDeduplicate(listOf(fb, tg, wa))
    assertEquals(1, merged.size)
    assertEquals(
      listOf(ChannelPlatform.WHATSAPP, ChannelPlatform.TELEGRAM, ChannelPlatform.FACEBOOK),
      merged.first().platforms,
    )
    assertEquals("2026-11-21 • 12:00", merged.first().timestampLabel)
    assertEquals("220", merged.first().viewCount)
    assertEquals("https://scontent.example.com/photo.jpg", merged.first().imageUrl)
  }

  @Test
  fun telegramParser_extractsPollsAndVideoThumbs_andWhatsAppParserFiltersSeoBio() {
    val tgHtml =
      """
      <div class="tgme_widget_message_wrap js-widget_message_wrap">
        <div class="tgme_widget_message" data-post="projectpitelegram/99">
          <div class="tgme_widget_message_poll_question">Best olympiad topic?</div>
          <div class="tgme_widget_message_poll_option_text">Number Theory</div>
          <div class="tgme_widget_message_poll_option_text">Geometry</div>
          <div class="tgme_widget_message_video_thumb" style="background-image:url('https://cdn4.telegram-cdn.org/file/thumb.jpg')"></div>
          <time datetime="2026-11-22T08:30:00+00:00">08:30</time>
        </div>
      </div>
      """.trimIndent()
    val parsedTg = ChannelFeedRepository.parseTelegramChannelHtml(tgHtml)
    assertEquals(1, parsedTg.posts.size)
    assertTrue(parsedTg.posts.first().text.contains("Best olympiad topic?"))
    assertTrue(parsedTg.posts.first().text.contains("Number Theory"))
    assertEquals("https://cdn4.telegram-cdn.org/file/thumb.jpg", parsedTg.posts.first().imageUrl)

    val waSeoHtml =
      """
      <html><head>
        <meta property="og:title" content="Project π" />
        <meta property="og:description" content="Project π WhatsApp Channel. 33 followers for the latest updates." />
      </head></html>
      """.trimIndent()
    val parsedWa = ChannelFeedRepository.parseWhatsAppChannelHtml(waSeoHtml)
    assertEquals("33", parsedWa.preview.subscriberCount)
    assertEquals(0, parsedWa.posts.size)
  }
}

