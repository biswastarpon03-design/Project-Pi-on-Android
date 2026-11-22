package com.example.model

import androidx.annotation.StringRes
import com.example.R

enum class ChannelPlatform(val shortLabel: String) {
  WHATSAPP("WhatsApp"),
  TELEGRAM("Telegram"),
  FACEBOOK("Facebook"),
}

enum class AppTab(@param:StringRes val labelRes: Int) {
  UPDATES(R.string.nav_updates),
  WHATSAPP(R.string.nav_whatsapp),
  TELEGRAM(R.string.nav_telegram),
  FACEBOOK(R.string.nav_facebook),
}

enum class FeedFilter(@param:StringRes val labelRes: Int, val platform: ChannelPlatform? = null) {
  ALL(R.string.filter_all_platforms, null),
  WHATSAPP(R.string.nav_whatsapp, ChannelPlatform.WHATSAPP),
  TELEGRAM(R.string.nav_telegram, ChannelPlatform.TELEGRAM),
  FACEBOOK(R.string.nav_facebook, ChannelPlatform.FACEBOOK),
}

data class CommunityChannel(
  val id: String,
  val platform: ChannelPlatform,
  val tab: AppTab,
  @param:StringRes val titleRes: Int,
  @param:StringRes val subtitleRes: Int,
  @param:StringRes val descriptionRes: Int,
  @param:StringRes val actionLabelRes: Int,
  val url: String,
  val inAppUrl: String,
  val displayUrl: String,
  val domain: String,
)

data class ChannelPreviewInfo(
  val channelId: String,
  val liveTitle: String? = null,
  val liveDescription: String? = null,
  val subscriberCount: String? = null,
  val imageUrl: String? = null,
  val resolvedUrl: String? = null,
)

data class ExtractedDomPost(
  val text: String,
  val imageUrl: String? = null,
)

data class FeedPost(
  val id: String,
  val platforms: List<ChannelPlatform>,
  val platformUrls: Map<ChannelPlatform, String>,
  val channelTitle: String,
  val text: String,
  val timestampLabel: String,
  val viewCount: String? = null,
  val imageUrl: String? = null,
  val primaryUrl: String,
  val isLiveChannelCard: Boolean = false,
) {
  val primaryPlatform: ChannelPlatform
    get() = platforms.first()
}

sealed interface FeedLoadState {
  data object Loading : FeedLoadState

  data class Ready(
    val posts: List<FeedPost>,
    val previews: Map<String, ChannelPreviewInfo>,
  ) : FeedLoadState

  data class Error(val message: String? = null) : FeedLoadState
}

object ProjectPiChannels {
  const val WHATSAPP_URL = "https://whatsapp.com/channel/0029VbDBwQp4Y9lhHdsmHG2D"
  const val FACEBOOK_URL = "https://www.facebook.com/share/1ddaQL72TH/"
  const val TELEGRAM_URL = "https://t.me/projectpitelegram"
  const val TELEGRAM_WEB_FEED_URL = "https://t.me/s/projectpitelegram"

  val all: List<CommunityChannel> =
    listOf(
      CommunityChannel(
        id = "whatsapp",
        platform = ChannelPlatform.WHATSAPP,
        tab = AppTab.WHATSAPP,
        titleRes = R.string.channel_whatsapp_title,
        subtitleRes = R.string.channel_whatsapp_subtitle,
        descriptionRes = R.string.channel_whatsapp_description,
        actionLabelRes = R.string.channel_whatsapp_cta,
        url = WHATSAPP_URL,
        inAppUrl = WHATSAPP_URL,
        displayUrl = "whatsapp.com/channel/0029VbDBwQp4Y9lhHdsmHG2D",
        domain = "whatsapp.com",
      ),
      CommunityChannel(
        id = "telegram",
        platform = ChannelPlatform.TELEGRAM,
        tab = AppTab.TELEGRAM,
        titleRes = R.string.channel_telegram_title,
        subtitleRes = R.string.channel_telegram_subtitle,
        descriptionRes = R.string.channel_telegram_description,
        actionLabelRes = R.string.channel_telegram_cta,
        url = TELEGRAM_URL,
        inAppUrl = TELEGRAM_WEB_FEED_URL,
        displayUrl = "t.me/projectpitelegram",
        domain = "t.me",
      ),
      CommunityChannel(
        id = "facebook",
        platform = ChannelPlatform.FACEBOOK,
        tab = AppTab.FACEBOOK,
        titleRes = R.string.channel_facebook_title,
        subtitleRes = R.string.channel_facebook_subtitle,
        descriptionRes = R.string.channel_facebook_description,
        actionLabelRes = R.string.channel_facebook_cta,
        url = FACEBOOK_URL,
        inAppUrl = FACEBOOK_URL,
        displayUrl = "facebook.com/share/1ddaQL72TH",
        domain = "facebook.com",
      ),
    )

  fun forTab(tab: AppTab): CommunityChannel? = all.firstOrNull { it.tab == tab }

  fun forPlatform(platform: ChannelPlatform): CommunityChannel =
    all.first { it.platform == platform }
}
