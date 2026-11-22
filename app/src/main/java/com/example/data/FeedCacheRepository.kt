package com.example.data

import android.content.Context
import com.example.model.ChannelPlatform
import com.example.model.ChannelPreviewInfo
import com.example.model.FeedPost
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class CachedFeedSnapshot(
  val basePosts: List<FeedPost>,
  val webExtractedPosts: Map<ChannelPlatform, List<FeedPost>>,
  val previews: Map<String, ChannelPreviewInfo>,
  val lastUpdatedMs: Long,
)

class FeedCacheRepository(private val context: Context) {

  private val cacheFile: File
    get() = File(context.applicationContext.filesDir, CACHE_FILE_NAME)

  suspend fun loadSnapshot(): CachedFeedSnapshot? =
    withContext(Dispatchers.IO) {
      try {
        val file = cacheFile
        if (!file.exists() || file.length() == 0L) return@withContext null
        val root = JSONObject(file.readText())
        val basePosts = decodePostList(root.optJSONArray("basePosts"))
        val webObj = root.optJSONObject("webExtractedPosts")
        val webMap = mutableMapOf<ChannelPlatform, List<FeedPost>>()
        if (webObj != null) {
          ChannelPlatform.entries.forEach { platform ->
            val arr = webObj.optJSONArray(platform.name)
            if (arr != null) {
              val list = decodePostList(arr)
              if (list.isNotEmpty()) webMap[platform] = list
            }
          }
        }
        val previewsObj = root.optJSONObject("previews")
        val previewMap = mutableMapOf<String, ChannelPreviewInfo>()
        if (previewsObj != null) {
          val keys = previewsObj.keys()
          while (keys.hasNext()) {
            val k = keys.next()
            val pObj = previewsObj.optJSONObject(k) ?: continue
            previewMap[k] =
              ChannelPreviewInfo(
                channelId = pObj.optString("channelId", k),
                liveTitle = pObj.optString("liveTitle").takeIf { it.isNotBlank() },
                liveDescription = pObj.optString("liveDescription").takeIf { it.isNotBlank() },
                subscriberCount = pObj.optString("subscriberCount").takeIf { it.isNotBlank() },
                imageUrl = pObj.optString("imageUrl").takeIf { it.isNotBlank() },
                resolvedUrl = pObj.optString("resolvedUrl").takeIf { it.isNotBlank() },
              )
          }
        }
        val updatedMs = root.optLong("lastUpdatedMs", 0L)
        if (basePosts.isEmpty() && webMap.isEmpty()) return@withContext null
        CachedFeedSnapshot(
          basePosts = basePosts,
          webExtractedPosts = webMap,
          previews = previewMap,
          lastUpdatedMs = updatedMs,
        )
      } catch (_: Exception) {
        null
      }
    }

  suspend fun saveSnapshot(
    basePosts: List<FeedPost>,
    webExtractedPosts: Map<ChannelPlatform, List<FeedPost>>,
    previews: Map<String, ChannelPreviewInfo>,
    lastUpdatedMs: Long,
  ) =
    withContext(Dispatchers.IO) {
      try {
        val root = JSONObject()
        root.put("lastUpdatedMs", lastUpdatedMs)
        root.put("basePosts", encodePostList(basePosts.take(60)))

        val webObj = JSONObject()
        webExtractedPosts.forEach { (platform, posts) ->
          if (posts.isNotEmpty()) {
            webObj.put(platform.name, encodePostList(posts.take(40)))
          }
        }
        root.put("webExtractedPosts", webObj)

        val previewsObj = JSONObject()
        previews.forEach { (key, info) ->
          val pObj = JSONObject()
          pObj.put("channelId", info.channelId)
          info.liveTitle?.let { pObj.put("liveTitle", it) }
          info.liveDescription?.let { pObj.put("liveDescription", it) }
          info.subscriberCount?.let { pObj.put("subscriberCount", it) }
          info.imageUrl?.let { pObj.put("imageUrl", it) }
          info.resolvedUrl?.let { pObj.put("resolvedUrl", it) }
          previewsObj.put(key, pObj)
        }
        root.put("previews", previewsObj)

        cacheFile.writeText(root.toString())
      } catch (_: Exception) {
        // Cache write failures are non-fatal
      }
    }

  private fun encodePostList(posts: List<FeedPost>): JSONArray {
    val arr = JSONArray()
    posts.forEach { post ->
      val obj = JSONObject()
      obj.put("id", post.id)
      obj.put("platforms", JSONArray(post.platforms.map { it.name }))
      val urlsObj = JSONObject()
      post.platformUrls.forEach { (p, u) -> urlsObj.put(p.name, u) }
      obj.put("platformUrls", urlsObj)
      obj.put("channelTitle", post.channelTitle)
      obj.put("text", post.text)
      obj.put("timestampLabel", post.timestampLabel)
      post.viewCount?.let { obj.put("viewCount", it) }
      post.imageUrl?.let { obj.put("imageUrl", it) }
      obj.put("primaryUrl", post.primaryUrl)
      obj.put("isLiveChannelCard", post.isLiveChannelCard)
      arr.put(obj)
    }
    return arr
  }

  private fun decodePostList(arr: JSONArray?): List<FeedPost> {
    if (arr == null) return emptyList()
    return buildList {
      for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val platformsArr = obj.optJSONArray("platforms") ?: continue
        val platforms =
          buildList {
            for (j in 0 until platformsArr.length()) {
              val name = platformsArr.optString(j)
              ChannelPlatform.entries.firstOrNull { it.name == name }?.let(::add)
            }
          }
        if (platforms.isEmpty()) continue
        val urlsObj = obj.optJSONObject("platformUrls")
        val platformUrls = buildMap {
          if (urlsObj != null) {
            ChannelPlatform.entries.forEach { p ->
              val u = urlsObj.optString(p.name)
              if (u.isNotBlank()) put(p, u)
            }
          }
        }
        val text = obj.optString("text")
        val imageUrl = obj.optString("imageUrl").takeIf { it.isNotBlank() }
        if (text.isBlank() && imageUrl == null) continue
        add(
          FeedPost(
            id = obj.optString("id", "cached_$i"),
            platforms = platforms,
            platformUrls = platformUrls,
            channelTitle = obj.optString("channelTitle", "Project π"),
            text = text,
            timestampLabel = obj.optString("timestampLabel", "Cached"),
            viewCount = obj.optString("viewCount").takeIf { it.isNotBlank() },
            imageUrl = imageUrl,
            primaryUrl = obj.optString("primaryUrl"),
            isLiveChannelCard = obj.optBoolean("isLiveChannelCard", false),
          )
        )
      }
    }
  }

  companion object {
    private const val CACHE_FILE_NAME = "project_pi_feed_cache.json"
  }
}
