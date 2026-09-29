package com.namai.goodalarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.namai.goodalarm.data.Song
import com.namai.goodalarm.music.AppleMusicSearch
import com.namai.goodalarm.music.TonePlayer
import kotlinx.coroutines.delay

private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Results(val songs: List<Song>) : SearchState
    data class Error(val message: String) : SearchState
}

@Composable
fun SongSearch(storefront: String, selectedId: Long?, onBack: () -> Unit, onPick: (Song) -> Unit) {
    val context = LocalContext.current
    val preview = remember { TonePlayer(context) }
    var previewing by remember { mutableStateOf<Long?>(null) }
    var query by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<SearchState>(SearchState.Idle) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    DisposableEffect(Unit) { onDispose { preview.stop() } }
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query) {
        if (query.isBlank()) {
            state = SearchState.Idle
            return@LaunchedEffect
        }
        delay(350)
        state = SearchState.Loading
        state = try {
            SearchState.Results(AppleMusicSearch.search(query, storefront))
        } catch (e: Exception) {
            SearchState.Error("Couldn't reach Apple Music. Check your connection.")
        }
    }

    GlassBackground {
        Column(Modifier.fillMaxSize().padding(top = 8.dp).navigationBarsPadding().imePadding()) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Palette.pink) }
                Text("Apple Music", color = Palette.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(14.dp), alpha = 0.12f)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = Palette.secondary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Songs, artists, albums", color = Palette.tertiary, fontSize = 17.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Palette.text, fontSize = 17.sp),
                        cursorBrush = SolidColor(Palette.pink),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            }

            AnimatedContent(
                state,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentKey = { it::class },
                label = "search",
                modifier = Modifier.weight(1f),
            ) { s ->
                when (s) {
                    SearchState.Idle -> Placeholder("Search the Apple Music catalogue", "The song plays in the Apple Music app when your alarm goes off.")
                    SearchState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Palette.pink, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                    is SearchState.Error -> Placeholder("Offline", s.message)
                    is SearchState.Results -> if (s.songs.isEmpty()) {
                        Placeholder("No results", "Try a different spelling or the artist's name.")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp)) {
                            items(s.songs, key = { it.id }) { song ->
                                SongRow(
                                    song = song,
                                    selected = song.id == selectedId,
                                    previewing = previewing == song.id,
                                    onPreview = {
                                        if (previewing == song.id) {
                                            preview.stop()
                                            previewing = null
                                        } else {
                                            previewing = song.id
                                            preview.playPreview(song.previewUrl) { previewing = null }
                                        }
                                    },
                                    onClick = {
                                        preview.stop()
                                        onPick(song)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongRow(song: Song, selected: Boolean, previewing: Boolean, onPreview: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .bouncyClick(onClick = onClick)
            .then(if (selected) Modifier.glass(RoundedCornerShape(16.dp), alpha = 0.14f) else Modifier)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            song.artwork(200), null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(song.title, color = Palette.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${song.artist} · ${song.album}", color = Palette.secondary, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(Icons.Rounded.CheckCircle, "Selected", tint = Palette.pink, modifier = Modifier.padding(end = 8.dp))
        }
        if (song.previewUrl.isNotBlank()) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onPreview),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (previewing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    if (previewing) "Stop preview" else "Preview",
                    tint = Color.White, modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun Placeholder(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(72.dp).glass(CircleShape, alpha = 0.10f),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.MusicNote, null, tint = Palette.pink, modifier = Modifier.size(34.dp)) }
        Spacer(Modifier.height(16.dp))
        Text(title, color = Palette.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Palette.secondary, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(80.dp))
    }
}
