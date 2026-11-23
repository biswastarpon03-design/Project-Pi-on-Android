package com.example.data

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

enum class OtpPurpose(val wireValue: String) {
  SIGNUP("signup"),
  RESET("reset"),
}

data class SendOtpResponse(
  val message: String,
  val retryAfterSeconds: Int? = null,
)

data class AccountAuthResponse(
  val customToken: String,
  val username: String? = null,
  val email: String? = null,
)

data class OtpServerException(
  override val message: String,
  val retryAfterSeconds: Int? = null,
) : Exception(message)

object OtpAuthValidator {
  private val EMAIL_REGEX = Regex("^[a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,}$")
  private val USERNAME_REGEX = Regex("^[\\p{L}\\p{N}._\\- ]{3,30}$")
  private val OTP_REGEX = Regex("^\\d{6}$")

  fun normalizeEmail(raw: String): String = raw.trim().lowercase()

  fun isValidEmail(raw: String): Boolean {
    val email = normalizeEmail(raw)
    return email.length <= 254 && EMAIL_REGEX.matches(email)
  }

  fun normalizeUsername(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

  fun isValidUsername(raw: String): Boolean {
    val username = normalizeUsername(raw)
    return USERNAME_REGEX.matches(username)
  }

  fun isValidPassword(password: String): Boolean = password.length in 8..128

  fun normalizeOtp(raw: String): String = raw.filter { it.isDigit() }.take(6)

  fun isValidOtp(raw: String): Boolean = OTP_REGEX.matches(raw.trim())
}

interface OtpAuthRepository {
  suspend fun warmUpServer()

  suspend fun sendEmailOtp(email: String, purpose: OtpPurpose): Result<SendOtpResponse>

  suspend fun registerWithEmailOtp(
    email: String,
    username: String,
    password: String,
    otp: String,
  ): Result<AccountAuthResponse>

  suspend fun loginWithEmailPassword(email: String, password: String): Result<AccountAuthResponse>

  suspend fun resetPasswordWithOtp(
    email: String,
    otp: String,
    newPassword: String,
  ): Result<AccountAuthResponse>

  companion object {
    const val DEFAULT_BASE_URL = "https://projectpi-otp-server3.onrender.com"
    val default: OtpAuthRepository by lazy { HttpOtpAuthRepository() }
  }
}

class HttpOtpAuthRepository(
  private val baseUrl: String = OtpAuthRepository.DEFAULT_BASE_URL,
  private val client: OkHttpClient = defaultHttpClient(),
  private val firebaseCustomTokenSignIn: suspend (String) -> Unit = { token ->
    Firebase.auth.signInWithCustomToken(token).await()
  },
) : OtpAuthRepository {

  init {
    require(baseUrl.startsWith("https://")) { "OTP server URL must use HTTPS." }
  }

  private val trimmedBaseUrl = baseUrl.trimEnd('/')

  override suspend fun warmUpServer() {
    withContext(Dispatchers.IO) {
      runCatching {
        val request = Request.Builder().url("$trimmedBaseUrl/").get().build()
        client.newCall(request).execute().close()
      }
    }
  }

  override suspend fun sendEmailOtp(email: String, purpose: OtpPurpose): Result<SendOtpResponse> {
    val normalizedEmail = OtpAuthValidator.normalizeEmail(email)
    val payload =
      JSONObject()
        .put("email", normalizedEmail)
        .put("purpose", purpose.wireValue)

    return postJson(path = "/auth/send-otp", payload = payload).map { json ->
      SendOtpResponse(
        message = json.optString("message").ifBlank { "OTP sent to $normalizedEmail" },
        retryAfterSeconds = json.optInt("retryAfterSeconds", 0).takeIf { it > 0 },
      )
    }
  }

  override suspend fun registerWithEmailOtp(
    email: String,
    username: String,
    password: String,
    otp: String,
  ): Result<AccountAuthResponse> {
    val normalizedEmail = OtpAuthValidator.normalizeEmail(email)
    val normalizedUsername = OtpAuthValidator.normalizeUsername(username)
    val payload =
      JSONObject()
        .put("email", normalizedEmail)
        .put("username", normalizedUsername)
        .put("password", password)
        .put("otp", otp.trim())

    return authenticateWithTokenEndpoint(path = "/auth/register", payload = payload)
  }

  override suspend fun loginWithEmailPassword(
    email: String,
    password: String,
  ): Result<AccountAuthResponse> {
    val normalizedEmail = OtpAuthValidator.normalizeEmail(email)
    val payload =
      JSONObject()
        .put("email", normalizedEmail)
        .put("password", password)

    return authenticateWithTokenEndpoint(path = "/auth/login", payload = payload)
  }

  override suspend fun resetPasswordWithOtp(
    email: String,
    otp: String,
    newPassword: String,
  ): Result<AccountAuthResponse> {
    val normalizedEmail = OtpAuthValidator.normalizeEmail(email)
    val payload =
      JSONObject()
        .put("email", normalizedEmail)
        .put("otp", otp.trim())
        .put("newPassword", newPassword)

    return authenticateWithTokenEndpoint(path = "/auth/reset-password", payload = payload)
  }

  private suspend fun authenticateWithTokenEndpoint(
    path: String,
    payload: JSONObject,
  ): Result<AccountAuthResponse> {
    val responseResult = postJson(path = path, payload = payload)
    val json = responseResult.getOrElse { return Result.failure(it) }
    val parsed = parseTokenResponse(json).getOrElse { return Result.failure(it) }

    return try {
      firebaseCustomTokenSignIn(parsed.customToken)
      Result.success(parsed)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  private suspend fun postJson(path: String, payload: JSONObject): Result<JSONObject> =
    withContext(Dispatchers.IO) {
      try {
        val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request =
          Request.Builder()
            .url("$trimmedBaseUrl$path")
            .post(requestBody)
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
          val rawBody = response.body?.string().orEmpty()
          val json = runCatching { JSONObject(rawBody) }.getOrNull() ?: JSONObject()

          if (!response.isSuccessful || json.optBoolean("ok", true).not()) {
            val serverMessage =
              json.optString("error").ifBlank {
                json.optString("message").ifBlank {
                  "Authentication server returned error (${response.code}). Please try again."
                }
              }
            val retryAfter = json.optInt("retryAfterSeconds", 0).takeIf { it > 0 }
            return@withContext Result.failure(
              OtpServerException(message = serverMessage, retryAfterSeconds = retryAfter)
            )
          }

          Result.success(json)
        }
      } catch (_: SocketTimeoutException) {
        Result.failure(
          OtpServerException(
            "Verification server took too long to respond while waking up. Please try again."
          )
        )
      } catch (_: UnknownHostException) {
        Result.failure(
          OtpServerException("No internet connection. Please check your network and try again.")
        )
      } catch (_: IOException) {
        Result.failure(
          OtpServerException(
            "Could not reach the verification server. Please check your connection and try again."
          )
        )
      } catch (e: Exception) {
        Result.failure(
          OtpServerException(e.localizedMessage ?: "Unexpected verification error. Please retry.")
        )
      }
    }

  companion object {
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    fun defaultHttpClient(): OkHttpClient =
      OkHttpClient.Builder()
        .connectTimeout(35, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(35, TimeUnit.SECONDS)
        .build()

    fun parseTokenResponse(json: JSONObject): Result<AccountAuthResponse> {
      val customToken = json.optString("customToken").trim()
      if (customToken.isEmpty()) {
        val err = json.optString("error").ifBlank { "Missing sign-in token from server." }
        return Result.failure(OtpServerException(err))
      }
      val username = json.optString("username").trim().ifEmpty { null }
      val email = json.optString("email").trim().ifEmpty { null }
      return Result.success(
        AccountAuthResponse(
          customToken = customToken,
          username = username,
          email = email,
        )
      )
    }
  }
}
