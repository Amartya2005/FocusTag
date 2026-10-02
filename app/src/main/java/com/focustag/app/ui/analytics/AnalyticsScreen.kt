package com.focustag.app.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focustag.app.ui.history.HistoryViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Ink = Color(0xFF0B1F2A)
private val Card = Color(0xFF16343A)
private val Teal = Color(0xFF8FE3DC)
private val Sand = Color(0xFFF6F1E8)
private val Zone = ZoneId.of("Asia/Kolkata")

@Composable
fun AnalyticsScreen(viewModel: HistoryViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val today = LocalDate.now(Zone)
    val byDay = state.sessions.groupBy { Instant.ofEpochMilli(it.record.startAt).atZone(Zone).toLocalDate() }
    val todayItems = byDay[today].orEmpty()
    val yesterdayItems = byDay[today.minusDays(1)].orEmpty()
    val todayMs = todayItems.sumOf { it.durationMillis ?: 0L }
    val yesterdayMs = yesterdayItems.sumOf { it.durationMillis ?: 0L }
    val todayCaught = todayItems.sumOf { it.interceptionCount }
    val yesterdayCaught = yesterdayItems.sumOf { it.interceptionCount }
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val dayMs = days.map { day -> byDay[day].orEmpty().sumOf { it.durationMillis ?: 0L } }
    val delta = todayMs - yesterdayMs

    Column(
        Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Analytics", color = Sand, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onBack) { Text("Back", color = Teal) }
        }
        Text(today.format(DateTimeFormatter.ofPattern("d MMM yyyy")), color = Teal)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("In class today", formatDuration(todayMs), Modifier.weight(1f))
            StatCard("Sessions", todayItems.size.toString(), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Yesterday", formatDuration(yesterdayMs), Modifier.weight(1f))
            StatCard("Caught today", todayCaught.toString(), Modifier.weight(1f))
        }
        Surface(shape = RoundedCornerShape(20.dp), color = Card, modifier = Modifier.fillMaxWidth()) {
            Text(
                improvement(delta, todayCaught, yesterdayCaught),
                color = Sand,
                modifier = Modifier.padding(16.dp)
            )
        }
        Text("Last 7 days", color = Sand, fontWeight = FontWeight.SemiBold)
        Surface(shape = RoundedCornerShape(20.dp), color = Card, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                DayBars(dayMs)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    days.forEach { Text(it.dayOfWeek.name.take(1), color = Sand.copy(alpha = 0.7f)) }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(20.dp), color = Card, modifier = modifier.height(108.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Sand.copy(alpha = 0.72f))
            Text(value, color = Teal, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DayBars(values: List<Long>) {
    val max = (values.maxOrNull() ?: 1L).coerceAtLeast(1L)
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val gap = 10.dp.toPx()
        val barW = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, value ->
            val h = size.height * (value.toFloat() / max.toFloat())
            drawRoundRect(
                color = Teal,
                topLeft = Offset(i * (barW + gap), size.height - h),
                size = Size(barW, h.coerceAtLeast(4f)),
                cornerRadius = CornerRadius(8f, 8f)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val minutes = ms / 60000
    val hours = minutes / 60
    val rem = minutes % 60
    return if (hours > 0) "${hours}h ${rem}m" else "${rem}m"
}

private fun improvement(deltaMs: Long, caught: Int, yesterdayCaught: Int): String {
    val time = when {
        deltaMs > 60_000 -> "In class ${formatDuration(deltaMs)} more than yesterday."
        deltaMs < -60_000 -> "In class ${formatDuration(-deltaMs)} less than yesterday."
        else -> "About the same class time as yesterday."
    }
    val slips = when {
        caught < yesterdayCaught -> " Fewer blocked-app attempts than yesterday."
        caught > yesterdayCaught -> " More blocked-app attempts than yesterday."
        else -> ""
    }
    return time + slips
}
