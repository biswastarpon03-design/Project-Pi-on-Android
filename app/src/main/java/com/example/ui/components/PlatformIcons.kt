package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import com.example.model.ChannelPlatform

@Composable
fun PlatformGlyphIcon(
  platform: ChannelPlatform,
  tint: Color,
  modifier: Modifier = Modifier,
) {
  when (platform) {
    ChannelPlatform.WHATSAPP -> WhatsAppGlyph(tint = tint, modifier = modifier)
    ChannelPlatform.TELEGRAM -> TelegramGlyph(tint = tint, modifier = modifier)
    ChannelPlatform.FACEBOOK -> FacebookGlyph(tint = tint, modifier = modifier)
  }
}

@Composable
private fun WhatsAppGlyph(tint: Color, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val s = size.minDimension / 24f
    scale(scaleX = s, scaleY = s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
      // Speech bubble ring with bottom-left tail
      val bubble =
        Path().apply {
          moveTo(12f, 3f)
          cubicTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
          cubicTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
          cubicTo(10.37f, 21f, 8.84f, 20.56f, 7.52f, 19.8f)
          lineTo(3.5f, 20.85f)
          lineTo(4.6f, 16.95f)
          cubicTo(3.59f, 15.54f, 3f, 13.84f, 3f, 12f)
          cubicTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
          close()
        }
      drawPath(
        path = bubble,
        color = tint,
        style = Stroke(width = 2.1f, cap = StrokeCap.Round, join = StrokeJoin.Round),
      )

      // Stylized telephone receiver inside bubble
      val phone =
        Path().apply {
          moveTo(9.1f, 8.2f)
          cubicTo(8.7f, 8.2f, 8.1f, 8.7f, 8.0f, 9.4f)
          cubicTo(7.8f, 11.2f, 9.0f, 13.3f, 10.6f, 14.7f)
          cubicTo(12.2f, 16.0f, 14.0f, 16.4f, 15.2f, 15.8f)
          cubicTo(15.8f, 15.5f, 16.1f, 14.8f, 16.0f, 14.3f)
          lineTo(14.5f, 13.4f)
          cubicTo(14.2f, 13.2f, 13.8f, 13.3f, 13.5f, 13.6f)
          lineTo(13.0f, 14.1f)
          cubicTo(11.9f, 13.6f, 10.7f, 12.4f, 10.2f, 11.3f)
          lineTo(10.7f, 10.8f)
          cubicTo(11.0f, 10.5f, 11.1f, 10.1f, 10.9f, 9.8f)
          lineTo(10.0f, 8.3f)
          cubicTo(9.8f, 8.2f, 9.4f, 8.2f, 9.1f, 8.2f)
          close()
        }
      drawPath(path = phone, color = tint)
    }
  }
}

@Composable
private fun TelegramGlyph(tint: Color, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val s = size.minDimension / 24f
    scale(scaleX = s, scaleY = s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
      val plane =
        Path().apply {
          moveTo(21.2f, 4.3f)
          lineTo(2.9f, 11.4f)
          cubicTo(2.1f, 11.7f, 2.15f, 12.8f, 2.95f, 13.05f)
          lineTo(7.6f, 14.5f)
          lineTo(17.9f, 7.9f)
          lineTo(10.1f, 15.4f)
          lineTo(9.8f, 19.6f)
          cubicTo(10.35f, 19.6f, 10.6f, 19.35f, 10.95f, 19.0f)
          lineTo(13.3f, 16.75f)
          lineTo(17.3f, 19.7f)
          cubicTo(18.05f, 20.12f, 18.6f, 19.9f, 18.8f, 19.0f)
          lineTo(21.8f, 5.1f)
          cubicTo(22.0f, 4.3f, 21.6f, 4.1f, 21.2f, 4.3f)
          close()
        }
      drawPath(path = plane, color = tint)
    }
  }
}

@Composable
private fun FacebookGlyph(tint: Color, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val s = size.minDimension / 24f
    scale(scaleX = s, scaleY = s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
      val fLetter =
        Path().apply {
          moveTo(14.6f, 21.0f)
          lineTo(14.6f, 13.2f)
          lineTo(17.2f, 13.2f)
          lineTo(17.6f, 10.1f)
          lineTo(14.6f, 10.1f)
          lineTo(14.6f, 8.2f)
          cubicTo(14.6f, 7.3f, 14.85f, 6.7f, 16.1f, 6.7f)
          lineTo(17.75f, 6.7f)
          lineTo(17.75f, 3.9f)
          cubicTo(17.45f, 3.85f, 16.45f, 3.75f, 15.3f, 3.75f)
          cubicTo(12.85f, 3.75f, 11.2f, 5.25f, 11.2f, 7.95f)
          lineTo(11.2f, 10.1f)
          lineTo(8.5f, 10.1f)
          lineTo(8.5f, 13.2f)
          lineTo(11.2f, 13.2f)
          lineTo(11.2f, 21.0f)
          close()
        }
      drawPath(path = fLetter, color = tint)
    }
  }
}
