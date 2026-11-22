package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.ResponseRepository
import com.example.model.AppTab
import com.example.model.ChannelPlatform
import com.example.model.FeedPost
import com.example.model.PostResponse
import com.example.model.QuestionPostHelper
import com.example.ui.theme.LocalPlatformColors
import com.example.ui.theme.PlatformAccent

@Composable
fun platformAccentFor(platform: ChannelPlatform): PlatformAccent {
  val palette = LocalPlatformColors.current
  return when (platform) {
    ChannelPlatform.WHATSAPP -> palette.whatsapp
    ChannelPlatform.FACEBOOK -> palette.facebook
    ChannelPlatform.TELEGRAM -> palette.telegram
  }
}

@Composable
fun AnimatedPostEntry(index: Int, content: @Composable () -> Unit) {
  var visible by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { visible = true }

  AnimatedVisibility(
    visible = visible,
    enter =
      fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
        slideInVertically(
          animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
          initialOffsetY = { ((index.coerceAtMost(6)) + 1) * 14 },
        ),
  ) {
    content()
  }
}

@Composable
fun FeedPostCard(
  post: FeedPost,
  responses: List<PostResponse>,
  isSubmittingResponse: Boolean,
  onSaveResponse: (String, onSuccess: () -> Unit) -> Unit,
  onUpdateResponse: (responseId: String, newText: String, onSuccess: () -> Unit) -> Unit,
  onDeleteResponse: (responseId: String) -> Unit,
  onCopyAnswer: (String) -> Unit,
  onCopy: () -> Unit,
  onShare: () -> Unit,
  onOpenLive: () -> Unit,
  onOpenPlatformTab: (ChannelPlatform) -> Unit,
  onOpenExternal: () -> Unit,
) {
  val primaryAccent = platformAccentFor(post.primaryPlatform)
  val isQuestion = remember(post.text) { QuestionPostHelper.looksLikeQuestion(post.text) }
  var isResponseExpanded by rememberSaveable(post.id) { mutableStateOf(isQuestion) }
  var draftAnswer by rememberSaveable(post.id) { mutableStateOf("") }
  var editingResponseId by rememberSaveable(post.id) { mutableStateOf<String?>(null) }

  val showResponseSection = isResponseExpanded || responses.isNotEmpty()

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    tonalElevation = 1.dp,
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onOpenLive)
        .testTag("feed_post_${post.id}"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Box(
          modifier =
            Modifier.size(34.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(primaryAccent.container),
          contentAlignment = Alignment.Center,
        ) {
          PlatformGlyphIcon(
            platform = post.primaryPlatform,
            tint = primaryAccent.foreground,
            modifier = Modifier.size(18.dp),
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = post.channelTitle,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = post.timestampLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (!post.viewCount.isNullOrBlank()) {
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Visibility,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = post.viewCount,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(post.platforms) { _, platform ->
          val accent = platformAccentFor(platform)
          Surface(
            onClick = { onOpenPlatformTab(platform) },
            shape = RoundedCornerShape(999.dp),
            color = accent.container,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              PlatformGlyphIcon(
                platform = platform,
                tint = accent.foreground,
                modifier = Modifier.size(12.dp),
              )
              Text(
                text = platform.shortLabel,
                style = MaterialTheme.typography.labelSmall,
                color = accent.foreground,
              )
            }
          }
        }
        if (isQuestion) {
          item(key = "question_badge") {
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onTertiaryContainer,
                  modifier = Modifier.size(12.dp),
                )
                Text(
                  text = stringResource(R.string.badge_question),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
              }
            }
          }
        }
      }

      if (!post.imageUrl.isNullOrBlank()) {
        AsyncImage(
          model = post.imageUrl,
          contentDescription = stringResource(R.string.cd_post_image),
          contentScale = ContentScale.Crop,
          modifier =
            Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(MaterialTheme.shapes.small),
        )
      }

      if (post.text.isNotBlank()) {
        Text(
          text = post.text,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }

      // Saved user answers + inline Answer/Response field
      AnimatedVisibility(visible = showResponseSection) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            if (responses.isNotEmpty()) {
              Text(
                text = stringResource(R.string.answer_saved_header, responses.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
              )
              responses.forEach { saved ->
                Surface(
                  shape = MaterialTheme.shapes.small,
                  color = MaterialTheme.colorScheme.surface,
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Text(
                      text = saved.answerText,
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.weight(1f),
                    )
                    IconButton(
                      onClick = { onCopyAnswer(saved.answerText) },
                      modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                      Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.cd_copy_answer),
                        modifier = Modifier.size(16.dp),
                      )
                    }
                    IconButton(
                      onClick = {
                        editingResponseId = saved.id
                        draftAnswer = saved.answerText
                        isResponseExpanded = true
                      },
                      modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                      Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.cd_edit_answer),
                        modifier = Modifier.size(16.dp),
                      )
                    }
                    IconButton(
                      onClick = { onDeleteResponse(saved.id) },
                      modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                      Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_delete_answer),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                      )
                    }
                  }
                }
              }
            }

            if (isResponseExpanded) {
              OutlinedTextField(
                value = draftAnswer,
                onValueChange = { incoming ->
                  if (incoming.length <= ResponseRepository.MAX_ANSWER_LENGTH) {
                    draftAnswer = incoming
                  }
                },
                label = { Text(stringResource(R.string.answer_field_label)) },
                placeholder = { Text(stringResource(R.string.answer_field_placeholder)) },
                minLines = 2,
                maxLines = 5,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().testTag("answer_input_${post.id}"),
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                if (editingResponseId != null) {
                  TextButton(
                    onClick = {
                      editingResponseId = null
                      draftAnswer = ""
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                  ) {
                    Text(stringResource(R.string.answer_cancel_edit))
                  }
                }
                Spacer(Modifier.weight(1f))
                Button(
                  onClick = {
                    val activeEditId = editingResponseId
                    if (activeEditId != null) {
                      onUpdateResponse(activeEditId, draftAnswer) {
                        editingResponseId = null
                        draftAnswer = ""
                      }
                    } else {
                      onSaveResponse(draftAnswer) {
                        draftAnswer = ""
                      }
                    }
                  },
                  enabled = draftAnswer.isNotBlank() && !isSubmittingResponse,
                  shape = MaterialTheme.shapes.small,
                  modifier =
                    Modifier.heightIn(min = 48.dp).testTag("submit_answer_${post.id}"),
                ) {
                  if (isSubmittingResponse) {
                    CircularProgressIndicator(
                      modifier = Modifier.size(16.dp),
                      strokeWidth = 2.dp,
                      color = MaterialTheme.colorScheme.onPrimary,
                    )
                  } else {
                    Icon(
                      imageVector = Icons.AutoMirrored.Rounded.Send,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                  }
                  Spacer(Modifier.width(6.dp))
                  Text(
                    text =
                      stringResource(
                        if (editingResponseId != null) R.string.answer_update
                        else R.string.answer_submit
                      )
                  )
                }
              }
            }
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        FilledTonalButton(
          onClick = { isResponseExpanded = !isResponseExpanded },
          modifier =
            Modifier.weight(1f).heightIn(min = 48.dp).testTag("toggle_response_${post.id}"),
          shape = MaterialTheme.shapes.small,
          contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.ChatBubbleOutline,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(5.dp))
          Text(
            text =
              stringResource(
                if (isResponseExpanded) R.string.action_hide_respond else R.string.action_respond
              ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        if (post.isLiveChannelCard) {
          Button(
            onClick = onOpenLive,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.small,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = primaryAccent.brand,
                contentColor = MaterialTheme.colorScheme.surface,
              ),
          ) {
            Text(stringResource(R.string.view_live_channel), maxLines = 1)
          }
        } else {
          OutlinedButton(
            onClick = onCopy,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.ContentCopy,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
              text = stringResource(R.string.action_copy_post),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        OutlinedIconButton(
          onClick = onShare,
          modifier = Modifier.size(48.dp),
          shape = MaterialTheme.shapes.small,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Icon(
            imageVector = Icons.Rounded.Share,
            contentDescription = stringResource(R.string.action_share_channel),
          )
        }

        OutlinedIconButton(
          onClick = onOpenExternal,
          modifier = Modifier.size(48.dp),
          shape = MaterialTheme.shapes.small,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = stringResource(R.string.open_in_app),
          )
        }
      }
    }
  }
}

@Composable
fun FeedLoadingCard() {
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(18.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      CircularProgressIndicator(
        modifier = Modifier.size(20.dp),
        strokeWidth = 2.5.dp,
        color = MaterialTheme.colorScheme.tertiary,
      )
      Text(
        text = stringResource(R.string.loading_updates),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun ChannelSyncHintCard(
  missingPlatforms: List<ChannelPlatform>,
  onOpenTab: (AppTab) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (missingPlatforms.isEmpty()) return
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = modifier.fillMaxWidth().testTag("channel_sync_hint_card"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.Sync,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.tertiary,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = stringResource(R.string.channel_sync_hint_title),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
      Text(
        text = stringResource(R.string.channel_sync_hint_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        missingPlatforms.forEach { platform ->
          val accent = platformAccentFor(platform)
          val tab =
            when (platform) {
              ChannelPlatform.WHATSAPP -> AppTab.WHATSAPP
              ChannelPlatform.TELEGRAM -> AppTab.TELEGRAM
              ChannelPlatform.FACEBOOK -> AppTab.FACEBOOK
            }
          FilledTonalButton(
            onClick = { onOpenTab(tab) },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            colors =
              ButtonDefaults.filledTonalButtonColors(
                containerColor = accent.container,
                contentColor = accent.foreground,
              ),
          ) {
            PlatformGlyphIcon(
              platform = platform,
              tint = accent.foreground,
              modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(platform.shortLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
    }
  }
}

@Composable
fun FeedFallbackCard(
  title: String,
  body: String,
  showRetry: Boolean,
  onRetry: () -> Unit,
  onSelectTab: (AppTab) -> Unit,
) {
  val palette = LocalPlatformColors.current
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilledTonalButton(
          onClick = { onSelectTab(AppTab.WHATSAPP) },
          modifier = Modifier.weight(1f).heightIn(min = 48.dp),
          colors =
            ButtonDefaults.filledTonalButtonColors(
              containerColor = palette.whatsapp.container,
              contentColor = palette.whatsapp.foreground,
            ),
        ) {
          Text(stringResource(R.string.nav_whatsapp), maxLines = 1)
        }
        FilledTonalButton(
          onClick = { onSelectTab(AppTab.TELEGRAM) },
          modifier = Modifier.weight(1f).heightIn(min = 48.dp),
          colors =
            ButtonDefaults.filledTonalButtonColors(
              containerColor = palette.telegram.container,
              contentColor = palette.telegram.foreground,
            ),
        ) {
          Text(stringResource(R.string.nav_telegram), maxLines = 1)
        }
        FilledTonalButton(
          onClick = { onSelectTab(AppTab.FACEBOOK) },
          modifier = Modifier.weight(1f).heightIn(min = 48.dp),
          colors =
            ButtonDefaults.filledTonalButtonColors(
              containerColor = palette.facebook.container,
              contentColor = palette.facebook.foreground,
            ),
        ) {
          Text(stringResource(R.string.nav_facebook), maxLines = 1)
        }
      }
      if (showRetry) {
        OutlinedButton(
          onClick = onRetry,
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
          shape = MaterialTheme.shapes.small,
        ) {
          Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(stringResource(R.string.retry))
        }
      }
    }
  }
}
