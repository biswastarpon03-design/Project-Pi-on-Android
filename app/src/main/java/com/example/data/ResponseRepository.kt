package com.example.data

import android.content.Context
import com.example.R
import com.example.model.FeedPost
import com.example.model.PostResponse
import com.example.model.QuestionPostHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ResponseRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(
    context: Context
  ) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  fun observeResponses(): Flow<List<PostResponse>> = flow {
    val uid = requireUserId()
    val path = "users/$uid/responses"
    emitAll(
      db.collection("users")
        .document(uid)
        .collection("responses")
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(PostResponse::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun saveResponse(post: FeedPost, answerText: String): Result<Unit> {
    var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/responses"
    return try {
      val uid = requireUserId()
      val cleanAnswer = answerText.trim()
      require(cleanAnswer.isNotEmpty() && cleanAnswer.length <= MAX_ANSWER_LENGTH) {
        "Answer must be between 1 and $MAX_ANSWER_LENGTH characters."
      }
      val postId = post.id.take(200).ifBlank { "post" }
      val postKey = QuestionPostHelper.computePostKey(post)
      val questionExcerpt = post.text.trim().take(300)
      val platformSummary =
        post.platforms.joinToString(", ") { it.shortLabel }.take(100).ifBlank { "Project π" }

      val docRef = db.collection("users").document(uid).collection("responses").document()
      docPath = docRef.path
      val payload =
        mapOf(
          "userId" to uid,
          "postId" to postId,
          "postKey" to postKey,
          "questionExcerpt" to questionExcerpt,
          "answerText" to cleanAnswer,
          "platformSummary" to platformSummary,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      docRef.set(payload).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun updateResponse(responseId: String, newAnswerText: String): Result<Unit> {
    var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/responses/$responseId"
    return try {
      val uid = requireUserId()
      require(responseId.isNotBlank() && responseId.length <= 128) {
        "Invalid response ID."
      }
      val cleanAnswer = newAnswerText.trim()
      require(cleanAnswer.isNotEmpty() && cleanAnswer.length <= MAX_ANSWER_LENGTH) {
        "Answer must be between 1 and $MAX_ANSWER_LENGTH characters."
      }
      val docRef =
        db.collection("users").document(uid).collection("responses").document(responseId)
      docPath = docRef.path
      docRef
        .update(
          mapOf(
            "answerText" to cleanAnswer,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun deleteResponse(responseId: String): Result<Unit> {
    var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/responses/$responseId"
    return try {
      val uid = requireUserId()
      require(responseId.isNotBlank() && responseId.length <= 128) {
        "Invalid response ID."
      }
      val docRef =
        db.collection("users").document(uid).collection("responses").document(responseId)
      docPath = docRef.path
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docPath)
      Result.failure(e)
    }
  }

  companion object {
    const val MAX_ANSWER_LENGTH = 2000
  }
}
