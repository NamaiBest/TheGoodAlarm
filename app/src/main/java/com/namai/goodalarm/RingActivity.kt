package com.namai.goodalarm

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.namai.goodalarm.alarm.AlarmService
import com.namai.goodalarm.alarm.Ringing
import com.namai.goodalarm.alarm.RingingState
import com.namai.goodalarm.alarm.SoundSource
import com.namai.goodalarm.ui.GlassBackground
import com.namai.goodalarm.ui.GlassPill
import com.namai.goodalarm.ui.Palette
import com.namai.goodalarm.ui.SlideToStop
import com.namai.goodalarm.ui.glass
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            val current by RingingState.current.collectAsState()
            var shown by remember { mutableStateOf(current) }
            current?.let { shown = it }
            LaunchedEffect(current) {
                if (current == null) {
                    delay(500)
                    finish()
                }
            }
            BackHandler { /* an alarm has to be snoozed or stopped */ }
            shown?.let {
                RingScreen(
                    it,
                    onSnooze = {
                        startService(AlarmService.action(this, AlarmService.ACTION_SNOOZE))
                        finish()
                    },
                    onStop = {
                        startService(AlarmService.action(this, AlarmService.ACTION_DISMISS))
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun RingScreen(r: Ringing, onSnooze: () -> Unit, onStop: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val song = r.alarm.song
    val is24 = android.text.format.DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current)
    val breathe = rememberInfiniteTransition(label = "breathe")
    val pulse by breathe.animateFloat(
        1f, 1.045f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )

    GlassBackground {
        if (song != null) {
            AsyncImage(
                song.artwork(600), null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.4f; scaleY = 1.4f }.blur(90.dp),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.65f))),
                ),
            )
        }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            Text(
                SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)),
                color = Palette.secondary, fontSize = 18.sp, fontWeight = FontWeight.Medium,
            )
            Text(
                SimpleDateFormat(if (is24) "H:mm" else "h:mm", Locale.getDefault()).format(Date(now)),
                color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Thin, lineHeight = 100.sp,
            )
            Text(
                r.alarm.label.ifBlank { "Alarm" },
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium,
            )

            Spacer(Modifier.weight(1f))
            if (song != null) {
                AsyncImage(
                    song.artwork(600), null, contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(250.dp)
                        .graphicsLayer { scaleX = pulse; scaleY = pulse }
                        .shadow(30.dp, RoundedCornerShape(28.dp))
                        .clip(RoundedCornerShape(28.dp)),
                )
                Spacer(Modifier.height(22.dp))
                Text(
                    song.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
                Text(song.artist, color = Palette.secondary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Box(
                    Modifier.size(170.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }.glass(CircleShape, alpha = 0.12f),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Alarm, null, tint = Color.White, modifier = Modifier.size(80.dp)) }
            }
            Spacer(Modifier.height(14.dp))
            SourceChip(r.source)
            Spacer(Modifier.weight(1f))

            if (r.alarm.snoozeEnabled) {
                GlassPill(
                    "Snooze · ${r.alarm.snoozeMinutes} min",
                    Modifier.fillMaxWidth().height(64.dp),
                    onClick = onSnooze,
                )
                Spacer(Modifier.height(14.dp))
            }
            SlideToStop("slide to stop", onComplete = onStop)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SourceChip(source: SoundSource) {
    AnimatedContent(source, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "source") { s ->
        Row(
            Modifier.glass(CircleShape, alpha = 0.10f).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (s != SoundSource.Connecting) {
                Equalizer()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                when (s) {
                    SoundSource.Connecting -> "Starting Apple Music…"
                    SoundSource.AppleMusic -> "Playing on Apple Music"
                    SoundSource.Tone -> "Playing alarm sound"
                },
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun Equalizer() {
    val t = rememberInfiniteTransition(label = "eq")
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(14.dp)) {
        listOf(420, 560, 360).forEach { ms ->
            val h by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(ms), RepeatMode.Reverse), label = "bar")
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeightFraction(h)
                    .background(Palette.pink, RoundedCornerShape(2.dp)),
            )
        }
    }
}

private fun Modifier.fillMaxHeightFraction(f: Float) = this.then(Modifier.height((14 * f).dp))
