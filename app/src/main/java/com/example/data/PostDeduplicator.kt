package com.example.data

import com.example.model.ChannelPlatform
import com.example.model.FeedPost

object PostDeduplicator {
  private val URL_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
  private val PUNCT_REGEX = Regex("""[^\p{L}\p{N}\s]""")
  private val MULTI_SPACE_REGEX = Regex("""\s+""")

  fun normalizeForDedup(text: String): String {
    if (text.isBlank()) return ""
    val withoutUrls = URL_REGEX.replace(text, " ")
    val cleaned = PUNCT_REGEX.replace(withoutUrls, " ")
    return MULTI_SPACE_REGEX.replace(cleaned, " ").trim().lowercase()
  }

  fun areDuplicates(textA: String, textB: String): Boolean {
    val normA = normalizeForDedup(textA)
    val normB = normalizeForDedup(textB)
    if (normA.isEmpty() || normB.isEmpty()) return false
    if (normA == normB) return true

    val shorter = if (normA.length <= normB.length) normA else normB
    val longer = if (normA.length > normB.length) normA else normB
    if (shorter.length >= 24 && longer.contains(shorter)) {
      if (shorter.length.toDouble() / longer.length.toDouble() >= 0.72) {
        return true
      }
    }

    val tokensA = normA.split(' ').filter { it.length >= 2 }.toSet()
    val tokensB = normB.split(' ').filter { it.length >= 2 }.toSet()
    if (tokensA.size < 3 || tokensB.size < 3) return false

    val intersection = tokensA.intersect(tokensB).size
    val union = tokensA.union(tokensB).size
    if (union == 0) return false
    val jaccard = intersection.toDouble() / union.toDouble()
    return jaccard >= 0.78
  }

  fun mergeAndDeduplicate(posts: List<FeedPost>): List<FeedPost> {
    if (posts.isEmpty()) return emptyList()
    val merged = mutableListOf<FeedPost>()

    for (incoming in posts) {
      val matchIndex =
        merged.indexOfFirst { existing ->
          val sameImage =
            !existing.imageUrl.isNullOrBlank() &&
              existing.imageUrl == incoming.imageUrl &&
              existing.text.isBlank() &&
              incoming.text.isBlank()

          val differentOrSameExact =
            existing.platforms.intersect(incoming.platforms.toSet()).isEmpty() ||
              normalizeForDedup(existing.text) == normalizeForDedup(incoming.text)

          differentOrSameExact && (sameImage || areDuplicates(existing.text, incoming.text))
        }

      if (matchIndex == -1) {
        merged.add(incoming)
      } else {
        merged[matchIndex] = mergePair(merged[matchIndex], incoming)
      }
    }
    return merged
  }

  private fun mergePair(existing: FeedPost, incoming: FeedPost): FeedPost {
    val combinedPlatforms =
      (existing.platforms + incoming.platforms)
        .distinct()
        .sortedBy { it.ordinal }
    val combinedUrls = existing.platformUrls + incoming.platformUrls
    val bestText = if (incoming.text.length > existing.text.length) incoming.text else existing.text
    val bestImage = existing.imageUrl ?: incoming.imageUrl
    val bestViews = existing.viewCount ?: incoming.viewCount
    val bestTimestamp =
      if (existing.timestampLabel == "Live Channel" || existing.timestampLabel == "Synced live") {
        incoming.timestampLabel
      } else {
        existing.timestampLabel
      }
    val bestTitle =
      if (combinedPlatforms.size > 1) {
        "Project π"
      } else {
        existing.channelTitle
      }

    return existing.copy(
      platforms = combinedPlatforms,
      platformUrls = combinedUrls,
      channelTitle = bestTitle,
      text = bestText,
      timestampLabel = bestTimestamp,
      viewCount = bestViews,
      imageUrl = bestImage,
      isLiveChannelCard = existing.isLiveChannelCard && incoming.isLiveChannelCard,
    )
  }
}
