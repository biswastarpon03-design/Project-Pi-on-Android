package com.example.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AppTab
import com.example.model.ChannelPlatform
import com.example.model.ChannelPreviewInfo
import com.example.model.CommunityChannel
import com.example.model.ExtractedDomPost
import com.example.model.FeedFilter
import com.example.model.FeedLoadState
import com.example.model.FeedPost
import com.example.model.PostResponse
import com.example.model.ProjectPiChannels
import com.example.model.QuestionPostHelper
import com.example.ui.components.AnimatedPostEntry
import com.example.ui.components.BackgroundChannelSyncer
import com.example.ui.components.ChannelDetailSheetContent
import com.example.ui.components.ChannelSyncHintCard
import com.example.ui.components.ChannelWebViewReader
import com.example.ui.components.FeedFallbackCard
import com.example.ui.components.FeedLoadingCard
import com.example.ui.components.FeedPostCard
import com.example.ui.components.PlatformGlyphIcon
import com.example.ui.components.VerificationFooterCard
import com.example.ui.components.platformAccentFor
import com.example.ui.theme.LocalPlatformColors
import com.example.ui.theme.ProjectPiTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectPiScreen(
  uiState: ProjectPiUiState,
  onSelectTab: (AppTab) -> Unit,
  onSelectFeedFilter: (FeedFilter) -> Unit,
  onRefreshFeed: () -> Unit,
  onWebViewPostsExtracted: (ChannelPlatform, List<ExtractedDomPost>) -> Unit,
  onSaveResponse: (FeedPost, String, String, (Boolean) -> Unit) -> Unit,
  onUpdateResponse: (String, String, String, (Boolean) -> Unit) -> Unit,
  onDeleteResponse: (String, String, (Boolean) -> Unit) -> Unit,
  onClearResponseError: () -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onToggleJoined: (String) -> Unit,
  onOpenDetails: (CommunityChannel) -> Unit,
  onDismissDetails: () -> Unit,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()
  var isSearchExpanded by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(uiState.responseError) {
    val err = uiState.responseError
    if (!err.isNullOrBlank()) {
      snackbarHostState.showSnackbar(err)
      onClearResponseError()
    }
  }

  val openExternalUrl: (String, String) -> Unit = { url, label ->
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    try {
      context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
      coroutineScope.launch {
        snackbarHostState.showSnackbar(context.getString(R.string.snackbar_no_browser, label))
      }
    }
  }

  val copyText: (String, String) -> Unit = { text, label ->
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    coroutineScope.launch {
      snackbarHostState.showSnackbar(context.getString(R.string.snackbar_copied, label))
    }
  }

  val shareText: (String) -> Unit = { text ->
    val sendIntent =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
      }
    context.startActivity(
      Intent.createChooser(sendIntent, context.getString(R.string.share_chooser_title))
    )
  }

  val shareSingleChannel: (CommunityChannel) -> Unit = { channel ->
    val title = context.getString(channel.titleRes)
    val subtitle = context.getString(channel.subtitleRes)
    shareText(context.getString(R.string.share_single_message, title, subtitle, channel.url))
  }

  val shareAllChannels: () -> Unit = {
    shareText(
      context.getString(
        R.string.share_all_message,
        ProjectPiChannels.WHATSAPP_URL,
        ProjectPiChannels.FACEBOOK_URL,
        ProjectPiChannels.TELEGRAM_URL,
      )
    )
  }

  BackHandler(enabled = uiState.selectedChannelForDetail != null) { onDismissDetails() }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val useNavRail = maxWidth >= 680.dp

    BackgroundChannelSyncer(
      syncGeneration = uiState.syncGeneration,
      onPostsExtracted = onWebViewPostsExtracted,
      modifier = Modifier.fillMaxSize(),
    )

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      contentWindowInsets = WindowInsets.safeDrawing,
      containerColor = MaterialTheme.colorScheme.background,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      topBar = {
        if (uiState.selectedTab == AppTab.UPDATES) {
          TopAppBar(
            colors =
              TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
              ),
            title = {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Image(
                  painter = painterResource(id = R.drawable.project_pi_logo),
                  contentDescription = stringResource(R.string.cd_project_pi_logo),
                  contentScale = ContentScale.Crop,
                  modifier =
                    Modifier.size(32.dp)
                      .clip(CircleShape)
                      .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
                Column {
                  Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                  )
                  Text(
                    text =
                      stringResource(
                        if (uiState.isRefreshing) R.string.sync_status_syncing
                        else R.string.sync_status_live
                      ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                  )
                }
              }
            },
            actions = {
              IconButton(
                onClick = {
                  if (isSearchExpanded && uiState.searchQuery.isNotEmpty()) {
                    onSearchQueryChange("")
                  }
                  isSearchExpanded = !isSearchExpanded
                },
                modifier = Modifier.testTag("toggle_search_button").minimumInteractiveComponentSize(),
              ) {
                Icon(
                  imageVector =
                    if (isSearchExpanded) Icons.Rounded.Close else Icons.Rounded.Search,
                  contentDescription = stringResource(R.string.toggle_search),
                  tint = MaterialTheme.colorScheme.onBackground,
                )
              }
              IconButton(
                onClick = onRefreshFeed,
                modifier =
                  Modifier.testTag("refresh_feed_button").minimumInteractiveComponentSize(),
              ) {
                if (uiState.isRefreshing) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onBackground,
                  )
                } else {
                  Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.cd_refresh_feed),
                    tint = MaterialTheme.colorScheme.onBackground,
                  )
                }
              }
              IconButton(
                onClick = shareAllChannels,
                modifier = Modifier.testTag("share_all_button").minimumInteractiveComponentSize(),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Share,
                  contentDescription = stringResource(R.string.cd_share_all),
                  tint = MaterialTheme.colorScheme.onBackground,
                )
              }
              IconButton(
                onClick = onSignOut,
                modifier = Modifier.testTag("sign_out_button").minimumInteractiveComponentSize(),
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Rounded.Logout,
                  contentDescription = stringResource(R.string.cd_sign_out),
                  tint = MaterialTheme.colorScheme.onBackground,
                )
              }
            },
          )
        }
      },
      bottomBar = {
        if (!useNavRail) {
          NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            AppTab.entries.forEach { tab ->
              val selected = uiState.selectedTab == tab
              NavigationBarItem(
                selected = selected,
                onClick = { onSelectTab(tab) },
                icon = { TabNavIcon(tab = tab, selected = selected) },
                label = {
                  Text(
                    text = stringResource(tab.labelRes),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useNavRail) {
          NavigationRail(
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxHeight(),
          ) {
            Spacer(Modifier.height(12.dp))
            AppTab.entries.forEach { tab ->
              val selected = uiState.selectedTab == tab
              NavigationRailItem(
                selected = selected,
                onClick = { onSelectTab(tab) },
                icon = { TabNavIcon(tab = tab, selected = selected) },
                label = { Text(text = stringResource(tab.labelRes), maxLines = 1) },
                modifier = Modifier.testTag("nav_rail_${tab.name.lowercase()}"),
              )
            }
          }
          HorizontalDivider(
            modifier = Modifier.width(1.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.outlineVariant,
          )
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
          if (uiState.selectedTab == AppTab.UPDATES) {
            val savedMsg = stringResource(R.string.answer_saved_snackbar)
            val updatedMsg = stringResource(R.string.answer_updated_snackbar)
            val deletedMsg = stringResource(R.string.answer_deleted_snackbar)

            UpdatesFeedHub(
              uiState = uiState,
              isWideLayout = useNavRail,
              isSearchExpanded = isSearchExpanded || uiState.searchQuery.isNotEmpty(),
              onSelectTab = onSelectTab,
              onSelectFeedFilter = onSelectFeedFilter,
              onRefreshFeed = onRefreshFeed,
              onSearchQueryChange = onSearchQueryChange,
              onOpenDetails = onOpenDetails,
              onOpenExternal = openExternalUrl,
              onCopyText = copyText,
              onShareText = shareText,
              onSaveResponse = { post, postKey, answer, onSuccess ->
                onSaveResponse(post, postKey, answer) { ok ->
                  if (ok) {
                    onSuccess()
                    coroutineScope.launch { snackbarHostState.showSnackbar(savedMsg) }
                  }
                }
              },
              onUpdateResponse = { postKey, responseId, newText, onSuccess ->
                onUpdateResponse(postKey, responseId, newText) { ok ->
                  if (ok) {
                    onSuccess()
                    coroutineScope.launch { snackbarHostState.showSnackbar(updatedMsg) }
                  }
                }
              },
              onDeleteResponse = { postKey, responseId ->
                onDeleteResponse(postKey, responseId) { ok ->
                  if (ok) {
                    coroutineScope.launch { snackbarHostState.showSnackbar(deletedMsg) }
                  }
                }
              },
            )
          }

          uiState.visitedReaderTabs.forEach { tab ->
            val channel = ProjectPiChannels.forTab(tab)
            if (channel != null) {
              val title = stringResource(channel.titleRes)
              ChannelWebViewReader(
                channel = channel,
                isActive = uiState.selectedTab == tab,
                onOpenExternal = { openExternalUrl(channel.url, title) },
                onCopyLink = { copyText(channel.url, "$title link") },
                onShareLink = { shareSingleChannel(channel) },
                onPostsExtracted = { extracted ->
                  onWebViewPostsExtracted(channel.platform, extracted)
                },
                onBackExitReader = { onSelectTab(AppTab.UPDATES) },
              )
            }
          }
        }
      }
    }
  }

  val detailChannel = uiState.selectedChannelForDetail
  if (detailChannel != null) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val title = stringResource(detailChannel.titleRes)
    val preview = (uiState.feedState as? FeedLoadState.Ready)?.previews?.get(detailChannel.id)
    ModalBottomSheet(
      onDismissRequest = onDismissDetails,
      sheetState = sheetState,
      containerColor = MaterialTheme.colorScheme.surface,
      modifier = Modifier.testTag("detail_bottom_sheet"),
    ) {
      ChannelDetailSheetContent(
        channel = detailChannel,
        preview = preview,
        isJoined = detailChannel.id in uiState.joinedChannelIds,
        onOpenInsideApp = {
          onDismissDetails()
          onSelectTab(detailChannel.tab)
        },
        onOpenNative = {
          onDismissDetails()
          openExternalUrl(detailChannel.url, title)
        },
        onCopy = { copyText(detailChannel.url, "$title link") },
        onShare = { shareSingleChannel(detailChannel) },
        onToggleJoined = { onToggleJoined(detailChannel.id) },
        onClose = onDismissDetails,
      )
    }
  }
}

@Composable
private fun TabNavIcon(tab: AppTab, selected: Boolean) {
  val palette = LocalPlatformColors.current
  val defaultTint =
    if (selected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

  when (tab) {
    AppTab.UPDATES ->
      Icon(
        imageVector = Icons.Rounded.DynamicFeed,
        contentDescription = stringResource(tab.labelRes),
        tint = defaultTint,
      )
    AppTab.WHATSAPP ->
      PlatformGlyphIcon(
        platform = ChannelPlatform.WHATSAPP,
        tint = if (selected) palette.whatsapp.foreground else defaultTint,
        modifier = Modifier.size(22.dp),
      )
    AppTab.TELEGRAM ->
      PlatformGlyphIcon(
        platform = ChannelPlatform.TELEGRAM,
        tint = if (selected) palette.telegram.foreground else defaultTint,
        modifier = Modifier.size(22.dp),
      )
    AppTab.FACEBOOK ->
      PlatformGlyphIcon(
        platform = ChannelPlatform.FACEBOOK,
        tint = if (selected) palette.facebook.foreground else defaultTint,
        modifier = Modifier.size(22.dp),
      )
  }
}

@Composable
private fun UpdatesFeedHub(
  uiState: ProjectPiUiState,
  isWideLayout: Boolean,
  isSearchExpanded: Boolean,
  onSelectTab: (AppTab) -> Unit,
  onSelectFeedFilter: (FeedFilter) -> Unit,
  onRefreshFeed: () -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onOpenDetails: (CommunityChannel) -> Unit,
  onOpenExternal: (String, String) -> Unit,
  onCopyText: (String, String) -> Unit,
  onShareText: (String) -> Unit,
  onSaveResponse: (FeedPost, String, String, () -> Unit) -> Unit,
  onUpdateResponse: (String, String, String, () -> Unit) -> Unit,
  onDeleteResponse: (String, String) -> Unit,
) {
  val readyState = uiState.feedState as? FeedLoadState.Ready
  val previews = readyState?.previews.orEmpty()
  val missingPlatforms =
    remember(uiState.platformPostCounts) {
      ChannelPlatform.entries.filter { (uiState.platformPostCounts[it] ?: 0) == 0 }
    }

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    if (isWideLayout) {
      Row(
        modifier = Modifier.widthIn(max = 1080.dp).fillMaxSize().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
      ) {
        LazyColumn(
          modifier = Modifier.weight(0.38f).fillMaxSize(),
          contentPadding = PaddingValues(vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item(key = "channels_strip") {
            ChannelQuickStrip(
              channels = uiState.allChannels,
              previews = previews,
              platformCounts = uiState.platformPostCounts,
              joinedIds = uiState.joinedChannelIds,
              onOpenTab = onSelectTab,
              onOpenDetails = onOpenDetails,
            )
          }
          if (!uiState.isRefreshing && missingPlatforms.isNotEmpty()) {
            item(key = "sync_hint") {
              ChannelSyncHintCard(missingPlatforms = missingPlatforms, onOpenTab = onSelectTab)
            }
          }
          item(key = "verification") { VerificationFooterCard() }
        }

        LazyColumn(
          modifier = Modifier.weight(0.62f).fillMaxSize(),
          contentPadding = PaddingValues(vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item(key = "feed_controls") {
            FeedFilterAndSearchBar(
              selectedFilter = uiState.selectedFeedFilter,
              platformCounts = uiState.platformPostCounts,
              isSearchExpanded = isSearchExpanded,
              searchQuery = uiState.searchQuery,
              onSelectFilter = onSelectFeedFilter,
              onSearchQueryChange = onSearchQueryChange,
            )
          }
          feedBodyItems(
            feedState = uiState.feedState,
            posts = uiState.unifiedPosts,
            responsesByPostKey = uiState.responsesByPostKey,
            submittingPostKeys = uiState.submittingPostKeys,
            searchQuery = uiState.searchQuery,
            onRetry = onRefreshFeed,
            onSelectTab = onSelectTab,
            onCopyPost = { text -> onCopyText(text, "Post") },
            onCopyAnswer = { text -> onCopyText(text, "Answer") },
            onSharePost = onShareText,
            onOpenLiveTab = { platform -> onSelectTab(ProjectPiChannels.forPlatform(platform).tab) },
            onOpenPost = { post -> onOpenExternal(post.primaryUrl, post.channelTitle) },
            onSaveResponse = onSaveResponse,
            onUpdateResponse = onUpdateResponse,
            onDeleteResponse = onDeleteResponse,
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        item(key = "channels_strip") {
          ChannelQuickStrip(
            channels = uiState.allChannels,
            previews = previews,
            platformCounts = uiState.platformPostCounts,
            joinedIds = uiState.joinedChannelIds,
            onOpenTab = onSelectTab,
            onOpenDetails = onOpenDetails,
          )
        }

        item(key = "feed_controls") {
          FeedFilterAndSearchBar(
            selectedFilter = uiState.selectedFeedFilter,
            platformCounts = uiState.platformPostCounts,
            isSearchExpanded = isSearchExpanded,
            searchQuery = uiState.searchQuery,
            onSelectFilter = onSelectFeedFilter,
            onSearchQueryChange = onSearchQueryChange,
          )
        }

        feedBodyItems(
          feedState = uiState.feedState,
          posts = uiState.unifiedPosts,
          responsesByPostKey = uiState.responsesByPostKey,
          submittingPostKeys = uiState.submittingPostKeys,
          searchQuery = uiState.searchQuery,
          onRetry = onRefreshFeed,
          onSelectTab = onSelectTab,
          onCopyPost = { text -> onCopyText(text, "Post") },
          onCopyAnswer = { text -> onCopyText(text, "Answer") },
          onSharePost = onShareText,
          onOpenLiveTab = { platform -> onSelectTab(ProjectPiChannels.forPlatform(platform).tab) },
          onOpenPost = { post -> onOpenExternal(post.primaryUrl, post.channelTitle) },
          onSaveResponse = onSaveResponse,
          onUpdateResponse = onUpdateResponse,
          onDeleteResponse = onDeleteResponse,
        )

        if (!uiState.isRefreshing && missingPlatforms.isNotEmpty() && uiState.unifiedPosts.isNotEmpty()) {
          item(key = "sync_hint") {
            ChannelSyncHintCard(missingPlatforms = missingPlatforms, onOpenTab = onSelectTab)
          }
        }

        item(key = "verification") {
          VerificationFooterCard(modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        }
      }
    }
  }
}

@Composable
private fun ChannelQuickStrip(
  channels: List<CommunityChannel>,
  previews: Map<String, ChannelPreviewInfo>,
  platformCounts: Map<ChannelPlatform, Int>,
  joinedIds: Set<String>,
  onOpenTab: (AppTab) -> Unit,
  onOpenDetails: (CommunityChannel) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    channels.forEach { channel ->
      val accent = platformAccentFor(channel.platform)
      val isJoined = channel.id in joinedIds
      val count = platformCounts[channel.platform] ?: 0
      val subs = previews[channel.id]?.subscriberCount
      val statusText =
        when {
          count > 0 -> "$count synced"
          subs != null -> "$subs subs"
          else -> "Live tab"
        }

      Surface(
        modifier =
          Modifier.weight(1f)
            .heightIn(min = 68.dp)
            .testTag("channel_quick_${channel.id}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border =
          BorderStroke(
            width = if (isJoined) 1.5.dp else 1.dp,
            color =
              if (isJoined) accent.foreground.copy(alpha = 0.65f)
              else MaterialTheme.colorScheme.outline,
          ),
      ) {
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .clickable { onOpenTab(channel.tab) }
              .padding(horizontal = 10.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier =
                Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(accent.container),
              contentAlignment = Alignment.Center,
            ) {
              PlatformGlyphIcon(
                platform = channel.platform,
                tint = accent.foreground,
                modifier = Modifier.size(15.dp),
              )
            }
            IconButton(
              onClick = { onOpenDetails(channel) },
              modifier = Modifier.size(28.dp).testTag("details_${channel.id}"),
            ) {
              Icon(
                imageVector =
                  if (isJoined) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                contentDescription = stringResource(R.string.action_channel_details),
                tint =
                  if (isJoined) accent.foreground else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
              )
            }
          }

          Column {
            Text(
              text = channel.platform.shortLabel,
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = statusText,
              style = MaterialTheme.typography.labelSmall,
              color =
                if (count > 0) accent.foreground else MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun FeedFilterAndSearchBar(
  selectedFilter: FeedFilter,
  platformCounts: Map<ChannelPlatform, Int>,
  isSearchExpanded: Boolean,
  searchQuery: String,
  onSelectFilter: (FeedFilter) -> Unit,
  onSearchQueryChange: (String) -> Unit,
) {
  val palette = LocalPlatformColors.current

  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      itemsIndexed(FeedFilter.entries) { _, filter ->
        val selected = selectedFilter == filter
        val chipAccent =
          when (filter.platform) {
            ChannelPlatform.WHATSAPP -> palette.whatsapp.foreground
            ChannelPlatform.TELEGRAM -> palette.telegram.foreground
            ChannelPlatform.FACEBOOK -> palette.facebook.foreground
            null -> MaterialTheme.colorScheme.primary
          }
        val countSuffix =
          filter.platform?.let { platform ->
            val count = platformCounts[platform] ?: 0
            if (count > 0) " ($count)" else ""
          }.orEmpty()

        FilterChip(
          selected = selected,
          onClick = { onSelectFilter(filter) },
          leadingIcon =
            if (filter.platform != null) {
              {
                PlatformGlyphIcon(
                  platform = filter.platform,
                  tint = if (selected) MaterialTheme.colorScheme.onPrimary else chipAccent,
                  modifier = Modifier.size(16.dp),
                )
              }
            } else null,
          label = { Text(stringResource(filter.labelRes) + countSuffix) },
          modifier =
            Modifier.heightIn(min = 48.dp).testTag("feed_filter_${filter.name.lowercase()}"),
          colors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primary,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
      }
    }

    AnimatedVisibility(visible = isSearchExpanded) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        singleLine = true,
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { onSearchQueryChange("") }) {
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.action_close),
              )
            }
          }
        },
        placeholder = { Text(stringResource(R.string.feed_search_placeholder)) },
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag("feed_search_input"),
      )
    }
  }
}

private fun LazyListScope.feedBodyItems(
  feedState: FeedLoadState,
  posts: List<FeedPost>,
  responsesByPostKey: Map<String, List<PostResponse>>,
  submittingPostKeys: Set<String>,
  searchQuery: String,
  onRetry: () -> Unit,
  onSelectTab: (AppTab) -> Unit,
  onCopyPost: (String) -> Unit,
  onCopyAnswer: (String) -> Unit,
  onSharePost: (String) -> Unit,
  onOpenLiveTab: (ChannelPlatform) -> Unit,
  onOpenPost: (FeedPost) -> Unit,
  onSaveResponse: (FeedPost, String, String, () -> Unit) -> Unit,
  onUpdateResponse: (String, String, String, () -> Unit) -> Unit,
  onDeleteResponse: (String, String) -> Unit,
) {
  if (feedState is FeedLoadState.Loading && posts.isEmpty()) {
    item(key = "feed_loading") { FeedLoadingCard() }
    return
  }

  if (posts.isEmpty()) {
    item(key = "feed_empty") {
      if (searchQuery.isNotBlank()) {
        Surface(
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = stringResource(R.string.feed_no_search_results, searchQuery),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp),
          )
        }
      } else {
        FeedFallbackCard(
          title = stringResource(R.string.feed_empty_title),
          body = stringResource(R.string.feed_empty_body),
          showRetry = true,
          onRetry = onRetry,
          onSelectTab = onSelectTab,
        )
      }
    }
    return
  }

  itemsIndexed(posts, key = { _, post -> post.id }) { index, post ->
    val postKey = remember(post.id, post.text) { QuestionPostHelper.computePostKey(post) }
    val postResponses = responsesByPostKey[postKey].orEmpty()
    val isSubmitting = postKey in submittingPostKeys

    AnimatedPostEntry(index = index) {
      FeedPostCard(
        post = post,
        responses = postResponses,
        isSubmittingResponse = isSubmitting,
        onSaveResponse = { text, onSuccess -> onSaveResponse(post, postKey, text, onSuccess) },
        onUpdateResponse = { responseId, newText, onSuccess ->
          onUpdateResponse(postKey, responseId, newText, onSuccess)
        },
        onDeleteResponse = { responseId -> onDeleteResponse(postKey, responseId) },
        onCopyAnswer = onCopyAnswer,
        onCopy = { onCopyPost(post.text.ifBlank { post.primaryUrl }) },
        onShare = {
          val shareBody =
            if (post.text.isNotBlank()) "${post.text}\n\n${post.primaryUrl}" else post.primaryUrl
          onSharePost(shareBody)
        },
        onOpenLive = { onOpenLiveTab(post.primaryPlatform) },
        onOpenPlatformTab = onOpenLiveTab,
        onOpenExternal = { onOpenPost(post) },
      )
    }
  }
}

@Preview(showBackground = true, name = "Project π Light")
@Preview(
  showBackground = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
  name = "Project π Dark",
)
@Composable
private fun ProjectPiScreenPreview() {
  val samplePosts =
    listOf(
      FeedPost(
        id = "preview_merged",
        platforms =
          listOf(ChannelPlatform.WHATSAPP, ChannelPlatform.TELEGRAM, ChannelPlatform.FACEBOOK),
        platformUrls =
          mapOf(
            ChannelPlatform.WHATSAPP to ProjectPiChannels.WHATSAPP_URL,
            ChannelPlatform.TELEGRAM to ProjectPiChannels.TELEGRAM_URL,
            ChannelPlatform.FACEBOOK to ProjectPiChannels.FACEBOOK_URL,
          ),
        channelTitle = "Project π",
        text = "Daily Question: What is the approximate value of π to 4 decimal places?",
        timestampLabel = "2026-11-21 • 09:00",
        viewCount = "510",
        primaryUrl = ProjectPiChannels.TELEGRAM_URL,
      )
    )
  ProjectPiTheme {
    ProjectPiScreen(
      uiState =
        ProjectPiUiState(
          feedState = FeedLoadState.Ready(posts = samplePosts, previews = emptyMap()),
          unifiedPosts = samplePosts,
          platformPostCounts =
            mapOf(
              ChannelPlatform.WHATSAPP to 1,
              ChannelPlatform.TELEGRAM to 1,
              ChannelPlatform.FACEBOOK to 1,
            ),
          joinedChannelIds = setOf("whatsapp", "telegram"),
        ),
      onSelectTab = {},
      onSelectFeedFilter = {},
      onRefreshFeed = {},
      onWebViewPostsExtracted = { _, _ -> },
      onSaveResponse = { _, _, _, _ -> },
      onUpdateResponse = { _, _, _, _ -> },
      onDeleteResponse = { _, _, _ -> },
      onClearResponseError = {},
      onSearchQueryChange = {},
      onToggleJoined = {},
      onOpenDetails = {},
      onDismissDetails = {},
      onSignOut = {},
    )
  }
}
