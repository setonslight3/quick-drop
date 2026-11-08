package com.quickdrop.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quickdrop.app.ui.theme.QuickDropColors

@Composable
fun GlassBadge(
    text: String,
    icon: ImageVector? = null,
    backgroundColor: Color = Color(0x26FFFFFF),
    textColor: Color = QuickDropColors.TextPrimary,
    borderColor: Color = Color(0x33FFFFFF),
    cornerRadius: Dp = 10.dp
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(cornerRadius))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun HeroReadinessCard(
    studentName: String,
    streakDays: Int,
    readinessPercent: Int,
    nextClassTitle: String?,
    nextClassCountdown: String?,
    nextClassLocation: String?,
    onLaunchPrepQuiz: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1E293B),
                        Color(0xFF0F172A),
                        Color(0xFF020617)
                    )
                )
            )
            .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Top Row: Greeting & Streak Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Good day, ${studentName.ifBlank { "Scholar" }} ✨",
                        style = MaterialTheme.typography.titleMedium,
                        color = QuickDropColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Your daily active recall companion",
                        style = MaterialTheme.typography.bodySmall,
                        color = QuickDropColors.TextSecondary
                    )
                }

                // Flame streak badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x33F59E0B))
                        .border(1.dp, QuickDropColors.Amber.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = "Streak",
                        tint = QuickDropColors.AmberLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "$streakDays-Day Streak",
                        color = QuickDropColors.AmberLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            // Middle: Readiness Progress & Next Class Callout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Readiness circular score
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                    CircularProgressIndicator(
                        progress = { (readinessPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.size(64.dp),
                        color = QuickDropColors.Emerald,
                        trackColor = Color(0x33FFFFFF),
                        strokeWidth = 6.dp
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$readinessPercent%",
                            color = QuickDropColors.TextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Recall",
                            color = QuickDropColors.TextMuted,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Next class details
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (nextClassTitle != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(QuickDropColors.Emerald)
                            )
                            Text(
                                text = "NEXT: $nextClassCountdown",
                                color = QuickDropColors.EmeraldLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            text = nextClassTitle,
                            color = QuickDropColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                        if (!nextClassLocation.isNullOrBlank()) {
                            Text(
                                text = "📍 $nextClassLocation",
                                color = QuickDropColors.TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    } else {
                        Text(
                            text = "No classes scheduled today",
                            color = QuickDropColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Great time for spaced repetition review",
                            color = QuickDropColors.TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // Action button
                Button(
                    onClick = onLaunchPrepQuiz,
                    colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Prep", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun InteractiveFlipCard(
    topicName: String,
    prompt: String,
    explanation: String,
    evidenceQuote: String,
    anchorText: String?,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    onRateConfidence: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFlipped by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        QuickDropColors.CosmicSurfaceElevated,
                        QuickDropColors.CosmicSurface
                    )
                )
            )
            .border(
                1.dp,
                if (isFlipped) QuickDropColors.Violet.copy(alpha = 0.6f) else QuickDropColors.CosmicBorder,
                RoundedCornerShape(18.dp)
            )
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header with Tag, Anchor & Save button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassBadge(
                        text = topicName,
                        icon = Icons.Default.MenuBook,
                        backgroundColor = Color(0x338B5CF6),
                        textColor = QuickDropColors.VioletLight,
                        borderColor = QuickDropColors.Violet.copy(alpha = 0.4f)
                    )
                    if (anchorText != null) {
                        GlassBadge(
                            text = anchorText,
                            backgroundColor = Color(0x1A06B6D4),
                            textColor = QuickDropColors.CyanLight,
                            borderColor = QuickDropColors.Cyan.copy(alpha = 0.3f)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleSave, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Save Drop",
                            tint = if (isSaved) QuickDropColors.AmberLight else QuickDropColors.TextMuted
                        )
                    }
                }
            }

            // Animated Card Face (Front / Back)
            AnimatedContent(
                targetState = isFlipped,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) }
            ) { flipped ->
                if (!flipped) {
                    // FRONT: Prompt / Core Takeaway
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isFlipped = true }
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = prompt,
                            color = QuickDropColors.TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 22.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x1AFFFFFF))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flip,
                                contentDescription = null,
                                tint = QuickDropColors.TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Tap card to flip & inspect verified citation",
                                color = QuickDropColors.TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                } else {
                    // BACK: Grounded Rationale & Evidence Quote
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isFlipped = false }
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = explanation,
                            color = QuickDropColors.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp
                        )

                        if (evidenceQuote.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0F172A))
                                    .border(1.dp, QuickDropColors.Emerald.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "VERBATIM LECTURE EVIDENCE:",
                                        color = QuickDropColors.EmeraldLight,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = "\"$evidenceQuote\"",
                                        color = QuickDropColors.TextSecondary,
                                        fontSize = 11.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }

                        Text(
                            text = "↺ Tap to flip back to question",
                            color = QuickDropColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Bottom Spaced Repetition Rating Buttons (Optimized touch targets & zero clipping)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { onRateConfidence("HARD") },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Text("Review", fontSize = 11.sp, color = QuickDropColors.RoseLight, maxLines = 1)
                }

                Button(
                    onClick = { onRateConfidence("GOOD") },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.weight(1.2f).height(38.dp)
                ) {
                    Text("Understood", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                Button(
                    onClick = { onRateConfidence("EASY") },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Violet),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Text("Mastered", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun TactileOptionCard(
    ordinalLabel: String,
    hotkeyHint: String,
    body: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) QuickDropColors.Emerald else QuickDropColors.CosmicBorder,
        animationSpec = tween(150)
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF0D3325) else QuickDropColors.CosmicSurface,
        animationSpec = tween(150)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Option letter pill
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) QuickDropColors.Emerald else Color(0x1FFFFFFF)
                )
                .border(
                    1.dp,
                    if (isSelected) QuickDropColors.EmeraldLight else Color(0x33FFFFFF),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = ordinalLabel,
                color = if (isSelected) Color.White else QuickDropColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        // Body text
        Text(
            text = body,
            color = QuickDropColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )

        if (hotkeyHint.isNotBlank()) {
            Text(
                text = hotkeyHint,
                color = QuickDropColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SegmentedProgressBar(
    total: Int,
    current: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until total) {
            val isPassed = i < current
            val isCurrent = i == current
            val segmentColor = when {
                isPassed -> QuickDropColors.Emerald
                isCurrent -> QuickDropColors.Violet
                else -> Color(0x26FFFFFF)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(if (isCurrent) 6.dp else 4.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(segmentColor)
            )
        }
    }
}

@Composable
fun StatMetricCard(
    label: String,
    value: String,
    subtext: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(QuickDropColors.CosmicSurfaceElevated)
            .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = QuickDropColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Text(
                text = value,
                color = QuickDropColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = subtext,
                color = QuickDropColors.TextMuted,
                fontSize = 10.sp
            )
        }
    }
}
