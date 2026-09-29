package com.namai.goodalarm.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.namai.goodalarm.data.Alarm
import java.util.Calendar

data class SetupIssue(val key: String, val title: String, val body: String, val action: String)

@Composable
fun AlarmListScreen(
    alarms: List<Alarm>,
    snoozes: Map<Int, Long>,
    nextAlarmText: String?,
    issues: List<SetupIssue>,
    onFix: (SetupIssue) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Alarm) -> Unit,
    onToggle: (Alarm, Boolean) -> Unit,
    onDelete: (Alarm) -> Unit,
    onCancelSnooze: (Alarm) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 8.dp, bottom = bottom + 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Column(Modifier.padding(start = 4.dp, bottom = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    CircleButton(onSettings) { Icon(Icons.Rounded.Settings, "Settings", tint = Color.White) }
                    Spacer(Modifier.width(10.dp))
                    CircleButton(onAdd, accent = true) { Icon(Icons.Rounded.Add, "Add alarm", tint = Color.White) }
                }
                Text("Alarms", color = Palette.text, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                Text(
                    nextAlarmText ?: "No alarms on",
                    color = Palette.secondary, fontSize = 16.sp,
                )
            }
        }
        items(issues, key = { "issue-" + it.key }) { issue ->
            Row(
                Modifier
                    .animateItem()
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(22.dp), alpha = 0.10f, tint = Palette.orange)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.WarningAmber, null, tint = Palette.orange)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(issue.title, color = Palette.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(issue.body, color = Palette.secondary, fontSize = 14.sp)
                }
                GlassPill(issue.action, textColor = Palette.text) { onFix(issue) }
            }
        }
        if (alarms.isEmpty()) {
            item(key = "empty") { EmptyState(onAdd, Modifier.animateItem()) }
        }
        items(alarms, key = { it.id }) { alarm ->
            AlarmCard(
                alarm, snoozes[alarm.id],
                onClick = { onEdit(alarm) },
                onToggle = { onToggle(alarm, it) },
                onDelete = { onDelete(alarm) },
                onCancelSnooze = { onCancelSnooze(alarm) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
fun CircleButton(onClick: () -> Unit, accent: Boolean = false, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .bouncyClick(onClick = onClick)
            .then(if (accent) Modifier.clip(CircleShape).background(Palette.accent) else Modifier.glass(CircleShape, alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun AlarmCard(
    alarm: Alarm,
    snoozedUntil: Long?,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onCancelSnooze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val dim by animateFloatAsState(if (alarm.enabled) 1f else 0.45f, label = "dim")
    Column(
        modifier
            .fillMaxWidth()
            .bouncyClick(onClick = onClick)
            .glass()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).graphicsLayer { alpha = dim }) {
                Row(verticalAlignment = Alignment.Bottom) {
                    val h = if (is24) alarm.hour else (alarm.hour % 12).let { if (it == 0) 12 else it }
                    Text(
                        if (is24) "%02d:%02d".format(h, alarm.minute) else "%d:%02d".format(h, alarm.minute),
                        color = Palette.text, fontSize = 56.sp, fontWeight = FontWeight.Thin, lineHeight = 60.sp,
                    )
                    if (!is24) {
                        Text(
                            if (alarm.hour < 12) "AM" else "PM", color = Palette.text, fontSize = 22.sp,
                            fontWeight = FontWeight.Light, modifier = Modifier.padding(start = 4.dp, bottom = 9.dp),
                        )
                    }
                }
                Text(
                    listOfNotNull(alarm.label.ifBlank { null } ?: "Alarm", repeatText(alarm.days)).joinToString(", "),
                    color = Palette.secondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            GlassSwitch(alarm.enabled, onToggle)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
        val song = alarm.song
        if (song == null) Spacer(Modifier.weight(1f)) else {
            Row(
                Modifier.weight(1f).graphicsLayer { alpha = dim }.glass(RoundedCornerShape(14.dp), alpha = 0.06f).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    song.artwork(120), null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.08f)),
                )
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(song.title, color = Palette.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = Palette.secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.MusicNote, null, tint = Palette.pink, modifier = Modifier.size(18.dp).padding(end = 2.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        DeleteButton(onDelete)
        }
        snoozedUntil?.let { at ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Snooze, null, tint = Palette.orange, modifier = Modifier.size(18.dp))
                Text(
                    "Snoozed until " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(at)),
                    color = Palette.orange, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp).weight(1f),
                )
                TextButton("Dismiss", onClick = onCancelSnooze)
            }
        }
    }
}

/** Trash button that expands into a red "Delete" pill; a second tap deletes. */
@Composable
private fun DeleteButton(onDelete: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3_000)
            armed = false
        }
    }
    Row(
        Modifier
            .height(38.dp)
            .animateContentSize(spring(dampingRatio = 0.7f, stiffness = 500f))
            .bouncyClick { if (armed) onDelete() else armed = true }
            .then(
                if (armed) Modifier.clip(CircleShape).background(Palette.red)
                else Modifier.glass(CircleShape, alpha = 0.08f),
            )
            .padding(horizontal = if (armed) 14.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Delete, "Delete alarm", tint = if (armed) Color.White else Palette.secondary, modifier = Modifier.size(20.dp))
        if (armed) {
            Text("Delete", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(110.dp).glass(CircleShape, alpha = 0.10f), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Alarm, null, tint = Color.White, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("No Alarms", color = Palette.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Wake up to any song on Apple Music.", color = Palette.secondary, fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        GlassPill("Add Alarm", brush = Palette.accent, onClick = onAdd)
    }
}

fun repeatText(days: Set<Int>): String? {
    val weekdays = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
    val weekend = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
    return when {
        days.isEmpty() -> null
        days.size == 7 -> "every day"
        days == weekdays -> "weekdays"
        days == weekend -> "weekends"
        else -> {
            val names = mapOf(
                Calendar.MONDAY to "Mon", Calendar.TUESDAY to "Tue", Calendar.WEDNESDAY to "Wed",
                Calendar.THURSDAY to "Thu", Calendar.FRIDAY to "Fri", Calendar.SATURDAY to "Sat", Calendar.SUNDAY to "Sun",
            )
            listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)
                .filter { it in days }.joinToString(" ") { names.getValue(it) }
        }
    }
}
