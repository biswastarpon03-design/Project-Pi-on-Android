package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChannelFeedRepository
import com.example.data.PostDeduplicator
import com.example.data.ResponseRepository
import com.example.data.UserPreferencesRepository
import com.example.model.AppTab
import com.example.model.ChannelPlatform
import com.example.model.CommunityChannel
import com.example.model.ExtractedDomPost
import com.example.model.FeedFilter
import com.example.model.FeedLoadState
import com.example.model.FeedPost
import com.example.model.PostResponse
import com.example.model.ProjectPiChannels
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ProjectPiUiState(
  val selectedTab: AppTab = AppTab.UPDATES,
  val visitedReaderTabs: Set<AppTab> = emptySet(),
  val allChannels: List<CommunityChannel> = ProjectPiChannels.all,
  val joinedChannelIds: Set<String> = emptySet(),
  val feedState: FeedLoadState = FeedLoadState.Loading,
  val selectedFeedFilter: FeedFilter = FeedFilter.ALL,
  val webExtractedPosts: Map<ChannelPlatform, List<FeedPost>> = emptyMap(),
  val unifiedPosts: List<FeedPost> = emptyList(),
  val platformPostCounts: Map<ChannelPlatform, Int> = emptyMap(),
  val responsesByPostKey: Map<String, List<PostResponse>> = emptyMap(),
  val submittingPostKeys: Set<String> = emptySet(),
  val responseError: String? = null,
  val isRefreshing: Boolean = false,
  val syncGeneration: Int = 1,
  val searchQuery: String = "",
  val selectedChannelForDetail: CommunityChannel? = null,
) {
  val joinedCount: Int
    get() = allChannels.count { it.id in joinedChannelIds }
}

class ProjectPiViewModel(
  private val preferencesRepository: UserPreferencesRepository,
  private val responseRepository: ResponseRepository,
  private val currentUserId: String,
  private val feedRepository: ChannelFeedRepository = ChannelFeedRepository(),
) : ViewModel() {

  private val selectedTab = MutableStateFlow(AppTab.UPDATES)
  private val visitedReaderTabs = MutableStateFlow(emptySet<AppTab>())
  private val feedState = MutableStateFlow<FeedLoadState>(FeedLoadState.Loading)
  private val selectedFeedFilter = MutableStateFlow(FeedFilter.ALL)
  private val webExtractedPosts = MutableStateFlow<Map<ChannelPlatform, List<FeedPost>>>(emptyMap())
  private val isRefreshing = MutableStateFlow(false)
  private val syncGeneration = MutableStateFlow(1)
  private val searchQuery = MutableStateFlow("")
  private val selectedChannelForDetail = MutableStateFlow<CommunityChannel?>(null)
  private val submittingPostKeys = MutableStateFlow(emptySet<String>())
  private val responseError = MutableStateFlow<String?>(null)

  private var autoSyncJob: Job? = null
  private var lastSyncTimeMs: Long = 0L

  private data class FeedSyncMeta(
    val feedState: FeedLoadState,
    val selectedFeedFilter: FeedFilter,
    val webExtractedPosts: Map<ChannelPlatform, List<FeedPost>>,
    val isRefreshing: Boolean,
    val syncGeneration: Int,
  )

  private data class ResponsesMeta(
    val grouped: Map<String, List<PostResponse>>,
    val submitting: Set<String>,
    val error: String?,
  )

  private val feedSyncFlow =
    combine(
      feedState,
      selectedFeedFilter,
      webExtractedPosts,
      isRefreshing,
      syncGeneration,
    ) { state, filter, extracted, refreshing, generation ->
      FeedSyncMeta(state, filter, extracted, refreshing, generation)
    }

  private val responsesStreamFlow: StateFlow<ResponsesMeta> =
    combine(
        responseRepository
          .observeResponses()
          .map<List<PostResponse>, Result<Map<String, List<PostResponse>>>> { list ->
            Result.success(list.groupBy { it.postKey })
          }
          .catch { error ->
            val firestoreError =
              generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing responses for $currentUserId", rootError)
            emit(Result.failure(rootError))
          },
        submittingPostKeys,
        responseError,
      ) { result, submitting, mutationError ->
        val grouped = result.getOrNull().orEmpty()
        val streamErr = result.exceptionOrNull()?.localizedMessage
        ResponsesMeta(
          grouped = grouped,
          submitting = submitting,
          error = mutationError ?: streamErr,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ResponsesMeta(emptyMap(), emptySet(), null),
      )

  val uiState: StateFlow<ProjectPiUiState> =
    combine(
        combine(selectedTab, visitedReaderTabs, preferencesRepository.joinedChannelIds) {
          tab,
          visited,
          joinedIds ->
          Triple(tab, visited, joinedIds)
        },
        feedSyncFlow,
        combine(searchQuery, selectedChannelForDetail) { q, d -> q to d },
        responsesStreamFlow,
      ) { (tab, visited, joinedIds), syncMeta, (query, detail), respMeta ->
        val readyPosts = (syncMeta.feedState as? FeedLoadState.Ready)?.posts.orEmpty()
        val computed =
          computeUnifiedFeed(
            basePosts = readyPosts,
            webExtractedPosts = syncMeta.webExtractedPosts,
            selectedFilter = syncMeta.selectedFeedFilter,
            searchQuery = query,
          )
        ProjectPiUiState(
          selectedTab = tab,
          visitedReaderTabs = visited,
          allChannels = ProjectPiChannels.all,
          joinedChannelIds = joinedIds,
          feedState = syncMeta.feedState,
          selectedFeedFilter = syncMeta.selectedFeedFilter,
          webExtractedPosts = syncMeta.webExtractedPosts,
          unifiedPosts = computed.filteredPosts,
          platformPostCounts = computed.platformCounts,
          responsesByPostKey = respMeta.grouped,
          submittingPostKeys = respMeta.submitting,
          responseError = respMeta.error,
          isRefreshing = syncMeta.isRefreshing,
          syncGeneration = syncMeta.syncGeneration,
          searchQuery = query,
          selectedChannelForDetail = detail,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ProjectPiUiState(),
      )

  init {
    refreshFeed(incrementGeneration = false)
  }

  fun setForeground(isForeground: Boolean) {
    if (isForeground) {
      val elapsed = System.currentTimeMillis() - lastSyncTimeMs
      if (elapsed >= 30_000L) {
        refreshFeed(incrementGeneration = true)
      }
      startAutoSyncLoop()
    } else {
      autoSyncJob?.cancel()
      autoSyncJob = null
    }
  }

  private fun startAutoSyncLoop() {
    if (autoSyncJob?.isActive == true) return
    autoSyncJob =
      viewModelScope.launch {
        while (isActive) {
          delay(AUTO_SYNC_INTERVAL_MS)
          refreshFeed(incrementGeneration = true)
        }
      }
  }

  fun selectTab(tab: AppTab) {
    selectedTab.value = tab
    if (tab != AppTab.UPDATES) {
      visitedReaderTabs.value = visitedReaderTabs.value + tab
    }
  }

  fun selectFeedFilter(filter: FeedFilter) {
    selectedFeedFilter.value = filter
  }

  fun refreshFeed(incrementGeneration: Boolean = true) {
    if (isRefreshing.value) return
    viewModelScope.launch {
      isRefreshing.value = true
      if (incrementGeneration) {
        syncGeneration.value += 1
      }
      if (feedState.value !is FeedLoadState.Ready) {
        feedState.value = FeedLoadState.Loading
      }
      val bundle = feedRepository.fetchLiveContent()
      lastSyncTimeMs = System.currentTimeMillis()
      feedState.value =
        if (bundle.hadAnyNetworkSuccess) {
          FeedLoadState.Ready(posts = bundle.posts, previews = bundle.previews)
        } else {
          FeedLoadState.Error()
        }
      isRefreshing.value = false
    }
  }

  fun mergeWebViewPosts(platform: ChannelPlatform, extracted: List<ExtractedDomPost>) {
    if (extracted.isEmpty()) return
    val channel = ProjectPiChannels.forPlatform(platform)
    val label = platform.shortLabel
    val mapped =
      extracted.mapIndexed { index, item ->
        FeedPost(
          id = "dom_${platform.name.lowercase()}_$index",
          platforms = listOf(platform),
          platformUrls = mapOf(platform to channel.url),
          channelTitle = "Project π • $label",
          text = item.text,
          timestampLabel = "Synced live",
          imageUrl = item.imageUrl,
          primaryUrl = channel.url,
          isLiveChannelCard = false,
        )
      }
    val dedupedForPlatform = PostDeduplicator.mergeAndDeduplicate(mapped)
    webExtractedPosts.value = webExtractedPosts.value + (platform to dedupedForPlatform)
  }

  fun saveResponse(
    post: FeedPost,
    postKey: String,
    answerText: String,
    onResult: (Boolean) -> Unit = {},
  ) {
    val trimmed = answerText.trim()
    if (trimmed.isEmpty()) return
    viewModelScope.launch {
      responseError.value = null
      submittingPostKeys.value = submittingPostKeys.value + postKey
      val result = responseRepository.saveResponse(post, trimmed)
      submittingPostKeys.value = submittingPostKeys.value - postKey
      result.onFailure { err ->
        responseError.value = err.localizedMessage ?: "Failed to save answer"
      }
      onResult(result.isSuccess)
    }
  }

  fun updateResponse(
    postKey: String,
    responseId: String,
    newAnswerText: String,
    onResult: (Boolean) -> Unit = {},
  ) {
    val trimmed = newAnswerText.trim()
    if (trimmed.isEmpty()) return
    viewModelScope.launch {
      responseError.value = null
      submittingPostKeys.value = submittingPostKeys.value + postKey
      val result = responseRepository.updateResponse(responseId, trimmed)
      submittingPostKeys.value = submittingPostKeys.value - postKey
      result.onFailure { err ->
        responseError.value = err.localizedMessage ?: "Failed to update answer"
      }
      onResult(result.isSuccess)
    }
  }

  fun deleteResponse(
    postKey: String,
    responseId: String,
    onResult: (Boolean) -> Unit = {},
  ) {
    viewModelScope.launch {
      responseError.value = null
      submittingPostKeys.value = submittingPostKeys.value + postKey
      val result = responseRepository.deleteResponse(responseId)
      submittingPostKeys.value = submittingPostKeys.value - postKey
      result.onFailure { err ->
        responseError.value = err.localizedMessage ?: "Failed to delete answer"
      }
      onResult(result.isSuccess)
    }
  }

  fun clearResponseError() {
    responseError.value = null
  }

  fun updateSearchQuery(query: String) {
    searchQuery.value = query
  }

  fun toggleChannelJoined(channelId: String) {
    viewModelScope.launch { preferencesRepository.toggleChannelJoined(channelId) }
  }

  fun openChannelDetails(channel: CommunityChannel) {
    selectedChannelForDetail.value = channel
  }

  fun dismissChannelDetails() {
    selectedChannelForDetail.value = null
  }

  data class UnifiedComputation(
    val filteredPosts: List<FeedPost>,
    val platformCounts: Map<ChannelPlatform, Int>,
  )

  companion object {
    private const val TAG = "ProjectPiVM"
    private const val AUTO_SYNC_INTERVAL_MS = 60_000L

    fun computeUnifiedFeed(
      basePosts: List<FeedPost>,
      webExtractedPosts: Map<ChannelPlatform, List<FeedPost>>,
      selectedFilter: FeedFilter,
      searchQuery: String,
    ): UnifiedComputation {
      val waPosts =
        webExtractedPosts[ChannelPlatform.WHATSAPP]?.takeIf { it.isNotEmpty() }
          ?: basePosts.filter { ChannelPlatform.WHATSAPP in it.platforms }
      val tgPosts =
        webExtractedPosts[ChannelPlatform.TELEGRAM]?.takeIf { it.isNotEmpty() }
          ?: basePosts.filter { ChannelPlatform.TELEGRAM in it.platforms }
      val fbPosts =
        webExtractedPosts[ChannelPlatform.FACEBOOK]?.takeIf { it.isNotEmpty() }
          ?: basePosts.filter { ChannelPlatform.FACEBOOK in it.platforms }

      val interleaved = ChannelFeedRepository.interleaveChannelPosts(waPosts, tgPosts, fbPosts)
      val deduplicatedAll = PostDeduplicator.mergeAndDeduplicate(interleaved)

      val counts =
        mapOf(
          ChannelPlatform.WHATSAPP to
            deduplicatedAll.count { ChannelPlatform.WHATSAPP in it.platforms },
          ChannelPlatform.TELEGRAM to
            deduplicatedAll.count { ChannelPlatform.TELEGRAM in it.platforms },
          ChannelPlatform.FACEBOOK to
            deduplicatedAll.count { ChannelPlatform.FACEBOOK in it.platforms },
        )

      val platformFiltered =
        when (selectedFilter) {
          FeedFilter.ALL -> deduplicatedAll
          FeedFilter.WHATSAPP ->
            deduplicatedAll.filter { ChannelPlatform.WHATSAPP in it.platforms }
          FeedFilter.TELEGRAM ->
            deduplicatedAll.filter { ChannelPlatform.TELEGRAM in it.platforms }
          FeedFilter.FACEBOOK ->
            deduplicatedAll.filter { ChannelPlatform.FACEBOOK in it.platforms }
        }

      val q = searchQuery.trim()
      val finalPosts =
        if (q.isEmpty()) {
          platformFiltered
        } else {
          platformFiltered.filter {
            it.text.contains(q, ignoreCase = true) ||
              it.channelTitle.contains(q, ignoreCase = true)
          }
        }

      return UnifiedComputation(filteredPosts = finalPosts, platformCounts = counts)
    }
  }
}
