package com.namai.goodalarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.namai.goodalarm.data.Settings

data class PermissionRow(val key: String, val title: String, val body: String, val granted: Boolean)

@Composable
fun SettingsScreen(
    settings: Settings,
    permissions: List<PermissionRow>,
    appleMusicInstalled: Boolean,
    onGrant: (String) -> Unit,
    onClose: () -> Unit,
) {
    var volume by remember { mutableFloatStateOf(settings.volume) }
    var fadeIn by remember { mutableStateOf(settings.fadeIn) }
    var ringMinutes by remember { mutableIntStateOf(settings.ringMinutes) }
    var storefront by remember { mutableStateOf(settings.storefront) }
    BackHandler(onBack = onClose)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(70.dp))
            Text(
                "Settings", color = Palette.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            TextButton("Done", bold = true, onClick = onClose)
        }

        SectionTitle("Apple Music")
        Column(Modifier.padding(horizontal = 16.dp).glass(RoundedCornerShape(22.dp))) {
            GroupRow("Apple Music app", value = if (appleMusicInstalled) "Installed" else "Not installed")
            Hairline()
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Row {
                    Text("Alarm volume", color = Palette.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Text("${(volume * 100).toInt()}%", color = Palette.secondary, fontSize = 17.sp)
                }
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    onValueChangeFinished = { settings.volume = volume },
                    valueRange = 0.1f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Palette.pink,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                    ),
                )
            }
            Hairline()
            GroupRow("Gradually increase volume") {
                GlassSwitch(fadeIn, { fadeIn = it; settings.fadeIn = it })
            }
            Hairline()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Store region", color = Palette.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
                BasicTextField(
                    value = storefront.uppercase(),
                    onValueChange = { v ->
                        val code = v.filter { it.isLetter() }.take(2).lowercase()
                        storefront = code
                        if (code.length == 2) settings.storefront = code
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = Palette.secondary, fontSize = 17.sp, textAlign = TextAlign.End),
                    cursorBrush = SolidColor(Palette.pink),
                    modifier = Modifier.width(60.dp),
                )
            }
        }
        Text(
            "Your song plays inside the Apple Music app, signed in with your own account. " +
                "If it can't start, the alarm falls back to the song's preview, then the system alarm tone.",
            color = Palette.tertiary, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 34.dp, vertical = 8.dp),
        )

        SectionTitle("Ringing")
        Column(Modifier.padding(horizontal = 16.dp).glass(RoundedCornerShape(22.dp))) {
            GroupRow("Ring for") {
                Stepper(ringMinutes, "$ringMinutes min", onChange = {
                    ringMinutes = it.coerceIn(1, 30)
                    settings.ringMinutes = ringMinutes
                })
            }
        }

        SectionTitle("Permissions")
        Column(Modifier.padding(horizontal = 16.dp).glass(RoundedCornerShape(22.dp))) {
            permissions.forEachIndexed { i, p ->
                if (i > 0) Hairline()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(10.dp).background(if (p.granted) Palette.green else Palette.orange, CircleShape),
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(p.title, color = Palette.text, fontSize = 16.sp)
                        Text(p.body, color = Palette.secondary, fontSize = 13.sp)
                    }
                    if (!p.granted) GlassPill("Allow") { onGrant(p.key) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
