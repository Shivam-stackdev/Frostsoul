package dev.vxs.frostsoul.overlay

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vxs.frostsoul.lyrics.LyricsEntry

/**
 * Compose UI for the floating desktop lyrics overlay.
 * QQ Music inspired design with frosted glass, smooth animations.
 */
@Composable
fun FloatingLyricsOverlay(
    currentEntry: LyricsEntry?,
    nextEntry: LyricsEntry?,
    settings: OverlaySettingsData,
    onToggleLock: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val isVisible = currentEntry != null && (settings.isPlaying || !settings.autoHideWhenPaused)

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
            animationSpec = tween(400),
            initialOffsetY = { it / 2 }
        ),
        exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(
            animationSpec = tween(400),
            targetOffsetY = { it / 2 }
        )
    ) {
        Box(
            modifier = Modifier
                .padding(16.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = Color.Black.copy(alpha = 0.3f),
                    spotColor = Color.Black.copy(alpha = 0.3f)
                )
                .clip(RoundedCornerShape(20.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = settings.transparency * 0.8f),
                            Color.Black.copy(alpha = settings.transparency)
                        )
                    )
                )
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Current lyric line
                AnimatedLyricLine(
                    entry = currentEntry,
                    isCurrent = true,
                    fontSize = settings.fontSize.sp,
                    showTranslation = settings.showTranslation,
                    showRomanized = settings.showRomanized
                )

                // Next lyric line (if two-line mode)
                if (settings.twoLineMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AnimatedLyricLine(
                        entry = nextEntry,
                        isCurrent = false,
                        fontSize = (settings.fontSize - 2).sp,
                        showTranslation = false,
                        showRomanized = false
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedLyricLine(
    entry: LyricsEntry?,
    isCurrent: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    showTranslation: Boolean,
    showRomanized: Boolean
) {
    val textColor = if (isCurrent) {
        Color(0xFF00D4D8) // Dark Cyan
    } else {
        Color.White.copy(alpha = 0.65f)
    }

    val fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal

    AnimatedContent(
        targetState = entry,
        transitionSpec = {
            (fadeIn(animationSpec = tween(250)) + slideInVertically(
                animationSpec = tween(350),
                initialOffsetY = { it / 3 }
            )).togetherWith(
                fadeOut(animationSpec = tween(200)) + slideOutVertically(
                    animationSpec = tween(300),
                    targetOffsetY = { -it / 3 }
                )
            )
        },
        label = "lyric_line"
    ) { targetEntry ->
        if (targetEntry == null || targetEntry.isEmpty) {
            Box(modifier = Modifier.height(fontSize.value.dp + 8.dp))
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Main text
                Text(
                    text = targetEntry.text,
                    color = textColor,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )

                // Translation
                if (showTranslation && !targetEntry.translation.isNullOrBlank()) {
                    Text(
                        text = targetEntry.translation,
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = (fontSize.value - 3).sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Romanized
                if (showRomanized && !targetEntry.romanized.isNullOrBlank()) {
                    Text(
                        text = targetEntry.romanized,
                        color = textColor.copy(alpha = 0.6f),
                        fontSize = (fontSize.value - 4).sp,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }
        }
    }
}

/**
 * Data class for overlay settings snapshot.
 */
data class OverlaySettingsData(
    val transparency: Float = 0.75f,
    val fontSize: Int = 18,
    val twoLineMode: Boolean = true,
    val autoHideWhenPaused: Boolean = true,
    val isPlaying: Boolean = true,
    val showTranslation: Boolean = false,
    val showRomanized: Boolean = false
)
