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
      """\b(question|quiz|solve|find\s+the|find\s+all|calculate|what\s+is|which\s+of|how\s+many|prove|explain|guess|challenge|puzzle|olympiad|problem)\b""",
      RegexOption.IGNORE_CASE,
    )

  private val ANSWER_STATEMENT_REGEX =
    Regex(
      """^\s*(the\s+)?(correct\s+)?(ans|answer|solution)\s*(is|will\s+be|was|:|=)""",
      RegexOption.IGNORE_CASE,
    )

  fun looksLikeQuestion(text: String): Boolean {
    if (text.isBlank()) return false
    if (text.contains('?')) return true
    if (ANSWER_STATEMENT_REGEX.containsMatchIn(text)) return false
    return QUESTION_HINT_REGEX.containsMatchIn(text) ||
      text.contains("প্রশ্ন") ||
      text.contains("সমাধান")
  }

  fun computePostKey(post: FeedPost): String {
    val normalized = PostDeduplicator.normalizeForDedup(post.text)
    val seed = normalized.ifBlank { post.id }
    val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
    val hex = digest.take(12).joinToString("") { "%02x".format(it) }
    return "post_$hex"
  }
}
