package com.namai.goodalarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.namai.goodalarm.alarm.AlarmScheduler
import com.namai.goodalarm.alarm.AlarmService
import com.namai.goodalarm.alarm.SnoozeStore
import com.namai.goodalarm.data.Alarm
import com.namai.goodalarm.data.AlarmRepository
import com.namai.goodalarm.data.Settings
import com.namai.goodalarm.music.AppleMusic
import com.namai.goodalarm.music.MediaListenerService
import com.namai.goodalarm.ui.AlarmEditor
import com.namai.goodalarm.ui.AlarmListScreen
import com.namai.goodalarm.ui.GlassBackground
import com.namai.goodalarm.ui.Palette
import com.namai.goodalarm.ui.PermissionRow
import com.namai.goodalarm.ui.SettingsScreen
import com.namai.goodalarm.ui.SetupIssue
import com.namai.goodalarm.ui.glass
import kotlinx.coroutines.delay
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AlarmRepository.load(this)
        AlarmService.ensureChannels(this)
        AlarmScheduler.rescheduleAll(this)
        setContent { App() }
    }
}

private sealed interface Sheet {
    data class Edit(val alarm: Alarm, val isNew: Boolean) : Sheet
    data object Settings : Sheet
}

@Composable
private fun App() {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val alarms by AlarmRepository.alarms.collectAsState()
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var lastSheet by remember { mutableStateOf<Sheet?>(null) }
    if (sheet != null) lastSheet = sheet
    var tick by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }

    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            tick++
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2_800)
            toast = null
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val permissions = remember(tick) { permissionRows(context) }
    val issues = permissions.filter { !it.granted && it.key != "session" }.map {
        SetupIssue(it.key, it.title, it.body, "Allow")
    }
    val snoozes = remember(tick, alarms) { alarms.mapNotNull { a -> SnoozeStore.get(context, a.id)?.let { a.id to it } }.toMap() }
    val nextText = remember(tick, alarms) { nextAlarmText(alarms, snoozes) }

    fun grant(key: String) {
        val pkg = Uri.parse("package:${context.packageName}")
        when (key) {
            "notifications" -> if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            "fullscreen" -> if (Build.VERSION.SDK_INT >= 34) {
                context.startActivity(Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg))
            }
            "exact" -> context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
            "battery" -> context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
            "session" -> context.startActivity(
                Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                    AndroidSettings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(context, MediaListenerService::class.java).flattenToString(),
                ),
            )
        }
    }

    BackHandler(enabled = sheet != null) { sheet = null }

    val open = sheet != null
    val blur by animateDpAsState(if (open) 16.dp else 0.dp, tween(350), label = "blur")
    val scale by animateFloatAsState(if (open) 0.93f else 1f, spring(dampingRatio = 0.85f, stiffness = 300f), label = "scale")

    GlassBackground {
        AlarmListScreen(
            alarms = alarms,
            snoozes = snoozes,
            nextAlarmText = nextText,
            issues = issues,
            onFix = { grant(it.key) },
            onAdd = {
                val c = Calendar.getInstance().apply { add(Calendar.MINUTE, 1) }
                sheet = Sheet.Edit(Alarm(AlarmRepository.newId(context), c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)), true)
            },
            onEdit = { sheet = Sheet.Edit(it, false) },
            onToggle = { alarm, on ->
                val updated = alarm.copy(enabled = on)
                AlarmRepository.upsert(context, updated)
                AlarmScheduler.schedule(context, updated)
                if (on) toast = untilText(updated)
                tick++
            },
            onDelete = {
                AlarmScheduler.cancel(context, it.id)
                AlarmRepository.delete(context, it.id)
            },
            onCancelSnooze = {
                AlarmScheduler.cancelSnooze(context, it.id)
                context.getSystemService(NotificationManager::class.java).cancel(AlarmService.snoozeNotificationId(it.id))
                tick++
            },
            onSettings = { sheet = Sheet.Settings },
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .blur(blur),
        )

        AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(remember { MutableInteractionSource() }, indication = null) { sheet = null },
            )
        }

        AnimatedVisibility(
            open,
            enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 320f)) { it },
            exit = slideOutVertically(spring(dampingRatio = 1f, stiffness = 420f)) { it },
        ) {
            val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = top + 14.dp)
                    .clip(shape)
                    .background(Color(0xFF1C1C1E).copy(alpha = 0.94f))
                    .border(
                        1.dp,
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)),
                        shape,
                    ),
            ) {
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(width = 38.dp, height = 5.dp).background(Color.White.copy(alpha = 0.3f), CircleShape))
                    }
                    when (val s = lastSheet) {
                        is Sheet.Edit -> AlarmEditor(
                            initial = s.alarm,
                            isNew = s.isNew,
                            storefront = settings.storefront,
                            onCancel = { sheet = null },
                            onSave = { alarm ->
                                val saved = alarm.copy(enabled = true)
                                AlarmRepository.upsert(context, saved)
                                AlarmScheduler.schedule(context, saved)
                                sheet = null
                                toast = untilText(saved)
                                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.POST_NOTIFICATIONS,
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onDelete = {
                                AlarmScheduler.cancel(context, s.alarm.id)
                                AlarmRepository.delete(context, s.alarm.id)
                                sheet = null
                            },
                            onTest = { ContextCompat.startForegroundService(context, AlarmService.testIntent(context, it)) },
                        )
                        Sheet.Settings -> SettingsScreen(
                            settings = settings,
                            permissions = permissions,
                            appleMusicInstalled = remember { AppleMusic.isInstalled(context) },
                            onGrant = ::grant,
                            onClose = { sheet = null },
                        )
                        null -> {}
                    }
                }
            }
        }

        AnimatedVisibility(
            toast != null,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp),
            enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = 400f)) { -it * 2 } + fadeIn(),
            exit = slideOutVertically { -it * 2 } + fadeOut(),
        ) {
            var shown by remember { mutableStateOf("") }
            toast?.let { shown = it }
            Text(
                shown,
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .glass(CircleShape, alpha = 0.16f)
                    .background(Palette.base.copy(alpha = 0.4f))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

private fun permissionRows(context: Context): List<PermissionRow> {
    val rows = mutableListOf<PermissionRow>()
    if (Build.VERSION.SDK_INT >= 33) {
        rows += PermissionRow(
            "notifications", "Notifications", "Needed to show the alarm and its Snooze / Stop buttons.",
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    if (Build.VERSION.SDK_INT >= 34) {
        rows += PermissionRow(
            "fullscreen", "Full-screen alarms", "Lets the alarm take over the lock screen.",
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent(),
        )
    }
    rows += PermissionRow(
        "exact", "Exact alarms", "Rings at exactly the time you set.",
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
    )
    rows += PermissionRow(
        "battery", "Unrestricted battery", "Stops Samsung from putting the app to sleep.",
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
    )
    rows += PermissionRow(
        "session", "Media control (backup)", "Optional: another way to reach Apple Music if the direct connection fails.",
        AppleMusic.hasSessionAccess(context),
    )
    return rows
}

private fun untilText(alarm: Alarm): String {
    val mins = ((AlarmScheduler.nextTrigger(alarm) - System.currentTimeMillis()) / 60_000L).toInt() + 1
    val d = mins / (60 * 24)
    val h = (mins / 60) % 24
    val m = mins % 60
    val parts = listOfNotNull(
        d.takeIf { it > 0 }?.let { "$it d" },
        h.takeIf { it > 0 }?.let { "$it hr" },
        m.takeIf { it > 0 || (d == 0 && h == 0) }?.let { "$it min" },
    )
    return "Alarm set for " + parts.joinToString(" ") + " from now"
}

private fun nextAlarmText(alarms: List<Alarm>, snoozes: Map<Int, Long>): String? {
    val now = System.currentTimeMillis()
    val next = (alarms.filter { it.enabled }.map { AlarmScheduler.nextTrigger(it, now) } + snoozes.values).minOrNull()
        ?: return null
    val mins = ((next - now) / 60_000L).toInt() + 1
    return when {
        mins < 60 -> "Next alarm in $mins min"
        mins < 24 * 60 -> "Next alarm in ${mins / 60} hr ${mins % 60} min"
        else -> "Next alarm " + java.text.SimpleDateFormat("EEE 'at' h:mm a", java.util.Locale.getDefault()).format(java.util.Date(next))
    }
}
