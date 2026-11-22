package com.example

import com.example.data.ChannelFeedRepository
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
    assertFalse(ChannelFeedRepository.isGenericBoilerplate("Russian math olympiad 2007"))
    assertEquals(
      "https://telegram.me/s/projectpitelegram",
      ProjectPiChannels.TELEGRAM_MIRROR_WEB_FEED_URL,
    )
  }
}
