package com.example.ui.components

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.ChannelFeedRepository
import com.example.model.ChannelPlatform
import com.example.model.CommunityChannel
import com.example.model.ExtractedDomPost
import com.example.model.ProjectPiChannels
import com.example.ui.theme.LocalPlatformColors
import kotlinx.coroutines.delay
import org.json.JSONArray

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BackgroundChannelSyncer(
  syncGeneration: Int,
  onPostsExtracted: (ChannelPlatform, List<ExtractedDomPost>) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (LocalInspectionMode.current) return

  val context = LocalContext.current
  val latestCallback by rememberUpdatedState(onPostsExtracted)
  val handler = remember { Handler(Looper.getMainLooper()) }
  val whatsappChannel = remember { ProjectPiChannels.forPlatform(ChannelPlatform.WHATSAPP) }
  val facebookChannel = remember { ProjectPiChannels.forPlatform(ChannelPlatform.FACEBOOK) }

  var activeChannel by remember { mutableStateOf<CommunityChannel?>(null) }
  var mountSyncWebView by remember { mutableStateOf(false) }

  // Defer WebView startup briefly so first Compose frames render smoothly without GPU contention
  LaunchedEffect(syncGeneration) {
    delay(1_200L)
    mountSyncWebView = true
  }

  if (!mountSyncWebView) return

  val webView =
    remember(context) {
      createSafeWebView(context)?.apply {
        visibility = View.INVISIBLE
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        layoutParams =
          ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
          )
        configureChannelWebView(this, ChannelPlatform.WHATSAPP)
        webViewClient =
          object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
              val target = activeChannel ?: return
              if (view == null) return
              runCatching { CookieManager.getInstance().flush() }
              val delays = listOf(1800L, 4200L)
              delays.forEachIndexed { idx, delayMs ->
                handler.postDelayed(
                  {
                    if (idx == 1) {
                      view.evaluateJavascript("window.scrollBy(0, 500);", null)
                    }
                    extractChannelPostsFromWebView(view, target.platform) { posts ->
                      if (posts.isNotEmpty()) {
                        latestCallback(target.platform, posts)
                      }
                    }
                  },
                  delayMs,
                )
              }
            }

            override fun shouldOverrideUrlLoading(
              view: WebView?,
              request: WebResourceRequest?,
            ): Boolean {
              val scheme = request?.url?.scheme?.lowercase().orEmpty()
              return scheme != "http" && scheme != "https"
            }

            override fun onRenderProcessGone(
              view: WebView?,
              detail: RenderProcessGoneDetail?,
            ): Boolean {
              handler.removeCallbacksAndMessages(null)
              return true
            }
          }
      }
    }

  if (webView == null) return

  // Sequentially sync WhatsApp then Facebook using a single lightweight background WebView
  LaunchedEffect(syncGeneration, webView) {
    handler.removeCallbacksAndMessages(null)
    activeChannel = whatsappChannel
    configureChannelUserAgent(webView, whatsappChannel.platform)
    webView.loadUrl(whatsappChannel.inAppUrl)

    delay(6_500L)

    handler.removeCallbacksAndMessages(null)
    activeChannel = facebookChannel
    configureChannelUserAgent(webView, facebookChannel.platform)
    webView.loadUrl(facebookChannel.inAppUrl)
  }

  DisposableEffect(webView) {
    onDispose {
      handler.removeCallbacksAndMessages(null)
      runCatching {
        webView.stopLoading()
        webView.destroy()
      }
    }
  }

  AndroidView(factory = { webView }, modifier = modifier.fillMaxSize())
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChannelWebViewReader(
  channel: CommunityChannel,
  isActive: Boolean,
  onOpenExternal: () -> Unit,
  onCopyLink: () -> Unit,
  onShareLink: () -> Unit,
  onPostsExtracted: (List<ExtractedDomPost>) -> Unit = {},
  onBackExitReader: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (LocalInspectionMode.current) return

  val context = LocalContext.current
  val palette = LocalPlatformColors.current
  val accent =
    when (channel.platform) {
      ChannelPlatform.WHATSAPP -> palette.whatsapp
      ChannelPlatform.FACEBOOK -> palette.facebook
      ChannelPlatform.TELEGRAM -> palette.telegram
    }

  val latestOnPostsExtracted by rememberUpdatedState(onPostsExtracted)

  var progress by remember { mutableIntStateOf(0) }
  var isLoading by remember { mutableStateOf(true) }
  var canGoBack by remember { mutableStateOf(false) }
  var canGoForward by remember { mutableStateOf(false) }
  var showMoreMenu by remember { mutableStateOf(false) }
  var renderCrashFallback by remember { mutableStateOf(false) }

  val mainHandler = remember { Handler(Looper.getMainLooper()) }

  val webView =
    remember(channel.id) {
      createSafeWebView(context)?.apply {
        layoutParams =
          ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
          )
        configureChannelWebView(this, channel.platform)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          settings.isAlgorithmicDarkeningAllowed = true
        }
        webChromeClient =
          object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
              progress = newProgress
              isLoading = newProgress < 100
            }
          }
        webViewClient =
          object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
              isLoading = true
              canGoBack = view?.canGoBack() == true
              canGoForward = view?.canGoForward() == true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
              isLoading = false
              canGoBack = view?.canGoBack() == true
              canGoForward = view?.canGoForward() == true
              runCatching { CookieManager.getInstance().flush() }

              if (view != null) {
                listOf(1500L, 3500L).forEach { delayMs ->
                  mainHandler.postDelayed(
                    {
                      extractChannelPostsFromWebView(view, channel.platform) { posts ->
                        if (posts.isNotEmpty()) {
                          latestOnPostsExtracted(posts)
                        }
                      }
                    },
                    delayMs,
                  )
                }
              }
            }

            override fun shouldOverrideUrlLoading(
              view: WebView?,
              request: WebResourceRequest?,
            ): Boolean {
              val uri = request?.url ?: return false
              val scheme = uri.scheme?.lowercase().orEmpty()
              if (scheme == "http" || scheme == "https") {
                return false
              }
              return if (request.hasGesture()) {
                handleExternalScheme(context, view, uri.toString())
              } else {
                true
              }
            }

            override fun onRenderProcessGone(
              view: WebView?,
              detail: RenderProcessGoneDetail?,
            ): Boolean {
              renderCrashFallback = true
              return true
            }
          }
        loadUrl(channel.inAppUrl)
      }
    }

  DisposableEffect(webView) {
    onDispose {
      mainHandler.removeCallbacksAndMessages(null)
      runCatching {
        webView?.stopLoading()
        webView?.destroy()
      }
    }
  }

  if (isActive) {
    BackHandler {
      if (webView != null && !renderCrashFallback && webView.canGoBack()) {
        webView.goBack()
      } else {
        onBackExitReader()
      }
    }
  }

  Column(
    modifier =
      (if (isActive) modifier.fillMaxSize() else modifier.size(0.dp))
        .background(MaterialTheme.colorScheme.background)
  ) {
    if (isActive) {
      Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Box(
              modifier =
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(accent.container),
              contentAlignment = Alignment.Center,
            ) {
              PlatformGlyphIcon(
                platform = channel.platform,
                tint = accent.foreground,
                modifier = Modifier.size(18.dp),
              )
            }

            Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
              Text(
                text = stringResource(channel.titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = channel.domain,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }

            IconButton(
              onClick = { if (webView?.canGoBack() == true) webView.goBack() },
              enabled = canGoBack && !renderCrashFallback,
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.webview_back),
              )
            }

            IconButton(
              onClick = {
                if (renderCrashFallback) {
                  renderCrashFallback = false
                }
                webView?.reload()
              },
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.webview_reload),
              )
            }

            FilledTonalButton(
              onClick = onOpenExternal,
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
              colors =
                ButtonDefaults.filledTonalButtonColors(
                  containerColor = accent.container,
                  contentColor = accent.foreground,
                ),
              modifier = Modifier.testTag("reader_open_native_${channel.id}"),
            ) {
              Text(
                text = stringResource(R.string.open_in_app),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
              )
              Spacer(Modifier.width(4.dp))
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
              )
            }

            Box {
              IconButton(
                onClick = { showMoreMenu = true },
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Icon(
                  imageVector = Icons.Rounded.MoreVert,
                  contentDescription = stringResource(R.string.action_channel_details),
                )
              }
              DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false },
              ) {
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.webview_forward)) },
                  leadingIcon = {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                  },
                  enabled = canGoForward,
                  onClick = {
                    showMoreMenu = false
                    if (webView?.canGoForward() == true) webView.goForward()
                  },
                )
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.action_copy_link)) },
                  leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                  onClick = {
                    showMoreMenu = false
                    onCopyLink()
                  },
                )
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.action_share_channel)) },
                  leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                  onClick = {
                    showMoreMenu = false
                    onShareLink()
                  },
                )
              }
            }
          }

          AnimatedVisibility(visible = isLoading && webView != null && !renderCrashFallback) {
            LinearProgressIndicator(
              progress = { (progress.coerceIn(5, 100)) / 100f },
              modifier = Modifier.fillMaxWidth().height(3.dp),
              color = accent.brand,
              trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
          }
          if (!isLoading || webView == null || renderCrashFallback) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          }
        }
      }
    }

    if (webView == null || renderCrashFallback) {
      if (isActive) {
        Box(
          modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Icon(
                imageVector = Icons.Rounded.Public,
                contentDescription = null,
                tint = accent.foreground,
                modifier = Modifier.size(36.dp),
              )
              Text(
                text = stringResource(channel.titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = stringResource(channel.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Button(
                onClick = onOpenExternal,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.small,
              ) {
                Text(stringResource(R.string.open_in_app))
              }
            }
          }
        }
      }
    } else {
      AndroidView(
        factory = { webView },
        update = { view -> view.visibility = if (isActive) View.VISIBLE else View.GONE },
        modifier =
          (if (isActive) Modifier.weight(1f).fillMaxWidth() else Modifier.size(0.dp))
            .testTag("webview_${channel.id}"),
      )
    }
  }
}

private fun createSafeWebView(context: Context): WebView? {
  return try {
    WebView(context)
  } catch (_: Throwable) {
    null
  }
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureChannelWebView(webView: WebView, platform: ChannelPlatform) {
  runCatching {
    CookieManager.getInstance().apply {
      setAcceptCookie(true)
      setAcceptThirdPartyCookies(webView, true)
    }
  }
  webView.settings.apply {
    javaScriptEnabled = true
    domStorageEnabled = true
    loadsImagesAutomatically = true
    allowFileAccess = false
    allowContentAccess = false
    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    useWideViewPort = true
    loadWithOverviewMode = true
    builtInZoomControls = true
    displayZoomControls = false
    configureChannelUserAgent(webView, platform)
  }
}

private fun configureChannelUserAgent(webView: WebView, platform: ChannelPlatform) {
  webView.settings.userAgentString =
    when (platform) {
      ChannelPlatform.WHATSAPP -> ChannelFeedRepository.DESKTOP_USER_AGENT
      ChannelPlatform.FACEBOOK -> ChannelFeedRepository.DESKTOP_USER_AGENT
      ChannelPlatform.TELEGRAM -> ChannelFeedRepository.MOBILE_USER_AGENT
    }
}

private fun extractChannelPostsFromWebView(
  webView: WebView,
  platform: ChannelPlatform,
  onResult: (List<ExtractedDomPost>) -> Unit,
) {
  val selector =
    when (platform) {
      ChannelPlatform.WHATSAPP ->
        "[data-testid='msg-container'], [data-testid='channel-update-message'], div[data-id], div.copyable-text, span.selectable-text, div[role='listitem'], div[role='main'] div[dir='auto'], div[role='main'] span[dir='ltr'], div[role='main'] span[dir='auto']"
      ChannelPlatform.FACEBOOK ->
        "div[data-ad-preview='message'], div[data-ad-comet-preview='message'], div[data-testid='post_message'], div[role='article'] div[dir='auto'], div[role='article'] span[dir='auto'], div.userContent"
      ChannelPlatform.TELEGRAM -> ".tgme_widget_message_text"
    }
  val script =
    """
    (function() {
      try {
        var closeBtn = document.querySelector("[role='dialog'] [aria-label='Close'], [aria-label='Close'], [aria-label='close']");
        if (closeBtn && closeBtn.click) { try { closeBtn.click(); } catch(e) {} }
        var nodes = document.querySelectorAll("$selector");
        var seen = {};
        var out = [];
        var blocked = /log in to facebook|log into facebook|create new account|forgotten password|allow all cookies|decline optional cookies|we use cookies|cookie policy|terms of service|privacy policy|download whatsapp|get whatsapp|whatsapp web|help center|end-to-end encrypted|scan the qr code|see more on facebook|view channel in whatsapp/i;
        for (var i = 0; i < nodes.length && out.length < 25; i++) {
          var el = nodes[i];
          var t = (el.innerText || "").trim();
          if (t.length < 10 || t.length > 2000) continue;
          if (blocked.test(t)) continue;
          var lower = t.toLowerCase();
          if (lower === "follow" || lower === "share" || lower === "like" || lower === "comment" || lower === "open app" || lower === "features" || lower === "download") continue;
          var duplicateSub = false;
          for (var j = 0; j < out.length; j++) {
            if (out[j].text.indexOf(t) !== -1 || t.indexOf(out[j].text) !== -1) {
              if (t.length > out[j].text.length) out[j].text = t;
              duplicateSub = true;
              break;
            }
          }
          if (duplicateSub || seen[t]) continue;
          seen[t] = true;
          var imgEl = el.querySelector ? el.querySelector("img[src^='https://']") : null;
          var imgUrl = (imgEl && imgEl.naturalWidth > 80) ? imgEl.src : null;
          out.push({ text: t, imageUrl: imgUrl });
        }
        return JSON.stringify(out);
      } catch (e) {
        return "[]";
      }
    })();
    """.trimIndent()

  runCatching {
    webView.evaluateJavascript(script) { raw ->
      onResult(parseJsPostObjects(raw))
    }
  }
}

private fun parseJsPostObjects(raw: String?): List<ExtractedDomPost> {
  if (raw.isNullOrBlank() || raw == "null") return emptyList()
  return try {
    val unquoted =
      if (raw.startsWith("\"") && raw.endsWith("\"")) {
        org.json.JSONTokener(raw).nextValue() as? String ?: return emptyList()
      } else {
        raw
      }
    val jsonArray = JSONArray(unquoted)
    buildList {
      for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.optJSONObject(i) ?: continue
        val text = obj.optString("text").trim()
        val img = obj.optString("imageUrl").takeIf { it.startsWith("https://") }
        if (text.isNotBlank()) add(ExtractedDomPost(text = text, imageUrl = img))
      }
    }
  } catch (_: Exception) {
    emptyList()
  }
}

private fun handleExternalScheme(context: Context, webView: WebView?, url: String): Boolean {
  return try {
    if (url.startsWith("intent://", ignoreCase = true)) {
      val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
      try {
        context.startActivity(intent)
      } catch (_: ActivityNotFoundException) {
        val fallbackUrl = intent.getStringExtra("browser_fallback_url")
        if (!fallbackUrl.isNullOrBlank() && fallbackUrl.startsWith("https://")) {
          webView?.loadUrl(fallbackUrl)
        }
      }
    } else {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
      context.startActivity(intent)
    }
    true
  } catch (_: Exception) {
    true
  }
}
