package com.example.model

import androidx.annotation.Keep
import com.example.data.PostDeduplicator
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.security.MessageDigest

@Keep
@IgnoreExtraProperties
data class PostResponse(
  @DocumentId val id: String = "",
  val userId: String = "",
  val postId: String = "",
  val postKey: String = "",
  val questionExcerpt: String = "",
  val answerText: String = "",
  val platformSummary: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

object QuestionPostHelper {
  private val QUESTION_HINT_REGEX =
    Regex(
      """\b(question|quiz|solve|answer|find the|calculate|what is|which of|how many|prove|explain|reply|guess|challenge|puzzle)\b""",
      RegexOption.IGNORE_CASE,
    )

  fun looksLikeQuestion(text: String): Boolean {
    if (text.isBlank()) return false
    return text.contains('?') || QUESTION_HINT_REGEX.containsMatchIn(text)
  }

  fun computePostKey(post: FeedPost): String {
    val normalized = PostDeduplicator.normalizeForDedup(post.text)
    val seed = normalized.ifBlank { post.id }
    val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
    val hex = digest.take(12).joinToString("") { "%02x".format(it) }
    return "post_$hex"
  }
}
