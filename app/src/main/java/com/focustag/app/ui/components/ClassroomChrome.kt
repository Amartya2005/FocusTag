package com.focustag.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun FunkyStage(content: @Composable BoxScope.() -> Unit) {
    val motion = rememberInfiniteTransition(label = "blobs")
    val drift by motion.animateFloat(
        initialValue = -18f,
        targetValue = 22f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift"
    )
    val lift by motion.animateFloat(
        initialValue = 10f,
        targetValue = -16f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Reverse),
        label = "lift"
    )
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            Modifier
                .size(180.dp)
                .offset(x = (-40).dp, y = 120.dp + drift.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), CircleShape)
        )
        Box(
            Modifier
                .size(140.dp)
                .align(Alignment.TopEnd)
                .offset(x = 28.dp, y = 40.dp + lift.dp)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f), CircleShape)
        )
        Box(
            Modifier
                .size(90.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (-12).dp, y = (-80).dp)
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f), CircleShape)
        )
        content()
    }
}

@Composable
fun ClassroomTopBar(
    eyebrow: String = "FOCUSTAG",
    title: String,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                eyebrow,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun InitialsAvatar(label: String, onClick: (() -> Unit)? = null) {
    val initials = label.filter { it.isLetter() }.take(2).uppercase().ifBlank { "FT" }
    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(initials, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ErrorBanner(message: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            message,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
fun PulseDot(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (active) 700 else 1300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(16.dp)) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .scale(scale)
                .background(
                    color = if (active) MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                    shape = CircleShape
                )
        )
    }
}

@Composable
fun QuietLinkRow(items: List<Pair<String, () -> Unit>>, enabled: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items.forEach { (label, onClick) ->
                Surface(
                    onClick = onClick,
                    enabled = enabled,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
