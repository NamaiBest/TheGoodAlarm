package com.namai.goodalarm.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

object Palette {
    val base = Color.Black
    val text = Color.White
    val secondary = Color.White.copy(alpha = 0.62f)
    val tertiary = Color.White.copy(alpha = 0.38f)
    val hairline = Color.White.copy(alpha = 0.12f)
    /** Apple Music red. */
    val pink = Color(0xFFFA2D48)
    val purple = Color(0xFFBF5AF2)
    val indigo = Color(0xFF5E5CE6)
    val green = Color(0xFF34C759)
    val orange = Color(0xFFFF9F0A)
    val red = Color(0xFFFF453A)
    val accent: Brush = SolidColor(pink)
}

/** Plain black backdrop the glass surfaces sit on. */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(Palette.base), content = content)
}

/** Frosted-glass surface: translucent fill with a light-catching edge. */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(28.dp),
    alpha: Float = 0.09f,
    tint: Color = Color.White,
): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(tint.copy(alpha = alpha + 0.05f), tint.copy(alpha = alpha))))
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.16f)),
        ),
        shape,
    )

/** Clickable that springs down slightly while pressed, instead of a ripple. */
fun Modifier.bouncyClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(dampingRatio = 0.45f, stiffness = 700f),
        label = "press",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(source, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun GlassSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val track by animateColorAsState(
        if (checked) Palette.green else Color.White.copy(alpha = 0.16f),
        tween(220), label = "track",
    )
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "thumb")
    Box(
        modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(CircleShape)
            .background(track)
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(!checked)
            },
    ) {
        Box(
            Modifier
                .offset(x = x, y = 2.dp)
                .size(27.dp)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

@Composable
fun GlassPill(
    text: String,
    modifier: Modifier = Modifier,
    brush: Brush? = null,
    textColor: Color = Palette.text,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .bouncyClick(onClick = onClick)
            .then(
                if (brush != null) Modifier.clip(CircleShape).background(brush)
                else Modifier.glass(CircleShape, alpha = 0.12f),
            )
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = textColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A single iOS-style "grouped list" row. */
@Composable
fun GroupRow(
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, color = Palette.text, fontSize = 17.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                Text(
                    value, color = Palette.secondary, fontSize = 17.sp, maxLines = 1,
                    modifier = Modifier.padding(start = 16.dp).width(IntrinsicWidthCap),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            if (chevron) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                    tint = Palette.tertiary, modifier = Modifier.size(22.dp),
                )
            }
            trailing?.invoke()
        }
    }
}

private val IntrinsicWidthCap = 190.dp

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(start = 18.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Palette.hairline),
    )
}

/**
 * iOS-style wheel: items curve away on a cylinder, snap to the centre row and tick with
 * haptics as they pass.
 */
@Composable
fun WheelPicker(
    count: Int,
    selected: Int,
    label: (Int) -> String,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    loop: Boolean = true,
    itemHeight: Dp = 46.dp,
    visibleItems: Int = 5,
    textStyle: TextStyle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Light, color = Palette.text),
) {
    val repeats = if (loop) 1000 else 1
    val start = if (loop) count * (repeats / 2) + selected else selected
    val state = rememberLazyListState(start)
    val haptics = LocalHapticFeedback.current
    val itemPx = with(LocalDensity.current) { itemHeight.toPx() }
    val onSel by rememberUpdatedState(onSelected)
    val centre by remember {
        derivedStateOf {
            state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0
        }
    }
    LaunchedEffect(state) {
        var first = true
        snapshotFlow { centre }.collect {
            if (!first) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            first = false
            onSel(it % count)
        }
    }
    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = PaddingValues(vertical = itemHeight * (visibleItems / 2)),
        modifier = modifier.height(itemHeight * visibleItems),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(count * repeats) { index ->
            Box(
                Modifier
                    .height(itemHeight)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val pos = state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / itemPx
                        val d = index - pos
                        val ad = abs(d).coerceAtMost(3f)
                        rotationX = (-d * 24f).coerceIn(-80f, 80f)
                        alpha = (1f - ad * 0.3f).coerceAtLeast(0.08f)
                        scaleX = 1f - ad * 0.06f
                        scaleY = scaleX
                        cameraDistance = 12f * density
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(label(index % count), style = textStyle)
            }
        }
    }
}

/** "Slide to stop" track with a draggable thumb, like the old iPhone lock screen. */
@Composable
fun SlideToStop(text: String, onComplete: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val offset = remember { Animatable(0f) }
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val sx by shimmer.animateFloat(
        -300f, 900f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "sx",
    )
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(76.dp)
            .glass(CircleShape, alpha = 0.12f),
        contentAlignment = Alignment.CenterStart,
    ) {
        val thumb = 64.dp
        val density = LocalDensity.current
        val maxPx = with(density) { (maxWidth - thumb - 12.dp).toPx() }
        val progress = if (maxPx > 0) offset.value / maxPx else 0f
        Text(
            text,
            modifier = Modifier.fillMaxWidth().padding(start = 64.dp).graphicsLayer { alpha = 1f - progress * 1.6f },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = TextStyle(
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.35f), Color.White, Color.White.copy(alpha = 0.35f)),
                    start = Offset(sx, 0f), end = Offset(sx + 260f, 0f),
                ),
            ),
        )
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .padding(6.dp)
                .size(thumb)
                .shadow(8.dp, CircleShape)
                .background(Color.White, CircleShape)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, maxPx)) }
                    },
                    onDragStopped = { velocity ->
                        if (offset.value > maxPx * 0.65f || (velocity > 2500f && offset.value > maxPx * 0.25f)) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            offset.animateTo(maxPx, tween(120))
                            onComplete()
                        } else {
                            offset.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 300f))
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                tint = Palette.pink, modifier = Modifier.size(34.dp),
            )
        }
    }
}
