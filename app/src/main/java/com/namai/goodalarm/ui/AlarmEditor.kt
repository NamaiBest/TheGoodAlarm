package com.namai.goodalarm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.namai.goodalarm.data.Alarm
import com.namai.goodalarm.data.Song
import java.util.Calendar

@Composable
fun AlarmEditor(
    initial: Alarm,
    isNew: Boolean,
    storefront: String,
    onCancel: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: () -> Unit,
    onTest: (Alarm) -> Unit,
) {
    var alarm by remember(initial) { mutableStateOf(initial) }
    var searching by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 24.dp),
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton("Cancel", onClick = onCancel)
                Text(
                    if (isNew) "Add Alarm" else "Edit Alarm",
                    color = Palette.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
                TextButton("Save", bold = true) { onSave(alarm) }
            }

            TimeWheel(
                hour = alarm.hour, minute = alarm.minute,
                onChange = { h, m -> alarm = alarm.copy(hour = h, minute = m) },
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = 16.dp).glass(RoundedCornerShape(22.dp))) {
                Text(
                    "Repeat", color = Palette.text, fontSize = 17.sp,
                    modifier = Modifier.padding(start = 18.dp, top = 14.dp),
                )
                DayPicker(alarm.days, onChange = { alarm = alarm.copy(days = it) })
                Hairline()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Label", color = Palette.text, fontSize = 17.sp)
                    Spacer(Modifier.width(16.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        if (alarm.label.isEmpty()) Text("Alarm", color = Palette.tertiary, fontSize = 17.sp)
                        BasicTextField(
                            value = alarm.label,
                            onValueChange = { alarm = alarm.copy(label = it.take(40)) },
                            singleLine = true,
                            textStyle = TextStyle(color = Palette.secondary, fontSize = 17.sp, textAlign = TextAlign.End),
                            cursorBrush = SolidColor(Palette.pink),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            SectionTitle("Sound")
            SoundCard(
                song = alarm.song,
                onPick = { searching = true },
                onClear = { alarm = alarm.copy(song = null) },
            )

            Spacer(Modifier.height(20.dp))
            Column(Modifier.padding(horizontal = 16.dp).glass(RoundedCornerShape(22.dp))) {
                GroupRow("Snooze") {
                    GlassSwitch(alarm.snoozeEnabled, { alarm = alarm.copy(snoozeEnabled = it) })
                }
                AnimatedVisibility(
                    alarm.snoozeEnabled,
                    enter = expandVertically(spring(stiffness = 500f)) + fadeIn(),
                    exit = shrinkVertically(spring(stiffness = 500f)) + fadeOut(),
                ) {
                    Column {
                        Hairline()
                        GroupRow("Snooze for") {
                            Stepper(
                                value = alarm.snoozeMinutes,
                                label = "${alarm.snoozeMinutes} min",
                                onChange = { alarm = alarm.copy(snoozeMinutes = it.coerceIn(1, 30)) },
                            )
                        }
                    }
                }
                Hairline()
                GroupRow("Vibrate") {
                    GlassSwitch(alarm.vibrate, { alarm = alarm.copy(vibrate = it) })
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassPill("Test alarm now", Modifier.weight(1f)) { onTest(alarm) }
            }
            if (!isNew) {
                Spacer(Modifier.height(12.dp))
                GlassPill(
                    "Delete Alarm", Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    textColor = Palette.red, onClick = onDelete,
                )
            }
        }

        AnimatedVisibility(
            searching,
            enter = slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 380f)) { it } + fadeIn(),
            exit = slideOutHorizontally(spring(dampingRatio = 0.9f, stiffness = 380f)) { it } + fadeOut(),
        ) {
            SongSearch(
                storefront = storefront,
                selectedId = alarm.song?.id,
                onBack = { searching = false },
                onPick = {
                    alarm = alarm.copy(song = it)
                    searching = false
                },
            )
        }
    }
}

@Composable
fun TextButton(text: String, bold: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        color = Palette.pink,
        fontSize = 17.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).bouncyClick(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(), color = Palette.tertiary, fontSize = 13.sp, letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 34.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
fun TimeWheel(hour: Int, minute: Int, onChange: (Int, Int) -> Unit, modifier: Modifier = Modifier) {
    val is24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    var h by remember { mutableIntStateOf(hour) }
    var m by remember { mutableIntStateOf(minute) }
    val itemHeight = 48.dp
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Selection band behind the centre row.
        Box(
            Modifier.fillMaxWidth().height(itemHeight).glass(RoundedCornerShape(14.dp), alpha = 0.10f),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            val style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Light, color = Palette.text)
            if (is24) {
                WheelPicker(24, h, { "%02d".format(it) }, { h = it; onChange(h, m) }, Modifier.width(84.dp), itemHeight = itemHeight, textStyle = style)
            } else {
                WheelPicker(
                    12, h % 12, { if (it == 0) "12" else it.toString() },
                    { h = it + if (h >= 12) 12 else 0; onChange(h, m) },
                    Modifier.width(84.dp), itemHeight = itemHeight, textStyle = style,
                )
            }
            Text(":", style = style, modifier = Modifier.padding(bottom = 4.dp))
            WheelPicker(60, m, { "%02d".format(it) }, { m = it; onChange(h, m) }, Modifier.width(84.dp), itemHeight = itemHeight, textStyle = style)
            if (!is24) {
                WheelPicker(
                    2, if (h >= 12) 1 else 0, { if (it == 0) "AM" else "PM" },
                    { h = (h % 12) + if (it == 1) 12 else 0; onChange(h, m) },
                    Modifier.width(76.dp), loop = false, itemHeight = itemHeight,
                    textStyle = style.copy(fontSize = 22.sp, fontWeight = FontWeight.Normal),
                )
            }
        }
    }
}

private val dayOrder = listOf(
    Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
    Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY,
)
private val dayLetters = listOf("S", "M", "T", "W", "T", "F", "S")

@Composable
fun DayPicker(days: Set<Int>, onChange: (Set<Int>) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        dayOrder.forEachIndexed { i, day ->
            val on = day in days
            val bg by animateColorAsState(if (on) Palette.pink else Color.White.copy(alpha = 0.08f), label = "day")
            Box(
                Modifier
                    .size(38.dp)
                    .bouncyClick { onChange(if (on) days - day else days + day) }
                    .clip(CircleShape)
                    .background(bg)
                    .border(1.dp, Color.White.copy(alpha = if (on) 0.3f else 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    dayLetters[i], color = if (on) Color.White else Palette.secondary,
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
fun SoundCard(song: Song?, onPick: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .bouncyClick(onClick = onPick)
            .glass(RoundedCornerShape(22.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(Palette.accent),
            contentAlignment = Alignment.Center,
        ) {
            if (song != null) {
                AsyncImage(song.artwork(200), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
                song?.title ?: "Choose from Apple Music",
                color = Palette.text, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                song?.artist ?: "Default alarm tone until you pick a song",
                color = Palette.secondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (song != null) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, "Remove song", tint = Palette.secondary, modifier = Modifier.size(18.dp))
            }
        } else {
            Icon(Icons.Rounded.PlayArrow, null, tint = Palette.tertiary)
        }
    }
}

@Composable
fun Stepper(value: Int, label: String, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.secondary, fontSize = 16.sp, modifier = Modifier.padding(end = 12.dp))
        Row(Modifier.glass(RoundedCornerShape(10.dp), alpha = 0.12f)) {
            listOf("−" to -1, "+" to 1).forEachIndexed { i, (sym, d) ->
                if (i == 1) Box(Modifier.width(0.5.dp).height(32.dp).background(Palette.hairline))
                Box(
                    Modifier.size(width = 46.dp, height = 32.dp).clickable { onChange(value + d) },
                    contentAlignment = Alignment.Center,
                ) { Text(sym, color = Palette.text, fontSize = 20.sp) }
            }
        }
    }
}
