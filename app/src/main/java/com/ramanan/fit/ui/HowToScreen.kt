package com.ramanan.fit.ui

import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ramanan.fit.data.Exercises

/**
 * How-to video player. Two modes:
 *  - walkthrough: shown after START — watch each of the 10 videos, Next / Back, or skip and start.
 *  - single: opened from an exercise on the home screen.
 * Videos are bundled in res/raw, so this works offline. Narration follows the app's voice
 * volume and mute settings.
 */
@Composable
fun HowToScreen(state: UiState, vm: WorkoutViewModel) {
    val ex = Exercises.all[state.howtoIndex]
    val s = S
    val t = ex.en
    val count = Exercises.all.size
    val last = state.howtoIndex == count - 1
    var finished by remember(state.howtoIndex) { mutableStateOf(false) }
    var replayTick by remember(state.howtoIndex) { mutableStateOf(0) }
    BackHandler { vm.closeHowTo() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.howtoWalkthrough) "${s.howto}  ·  ${state.howtoIndex + 1}/$count" else s.howto,
                    color = Palette.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
                Text(ex.level.label.uppercase(), color = levelColor(ex.level), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(t.name, color = Palette.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = vm::closeHowTo) { Text(s.close, color = Palette.TextDim, fontSize = 16.sp) }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black)
            ) {
                key(state.howtoIndex, replayTick) {
                    HowToVideo(ex.video, s.videoMissing, if (state.muted) 0f else state.volume) { finished = true }
                }
            }
        }

        if (finished) {
            OutlinedButton(
                onClick = { finished = false; replayTick++ },
                border = BorderStroke(1.dp, Palette.TextDim),
                modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp),
            ) { Text(s.replay, color = Palette.Text) }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.howtoWalkthrough) {
                Button(
                    onClick = vm::howtoBack,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.SurfaceHi, contentColor = Palette.Text),
                    modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                ) { Text(if (state.howtoIndex == 0) s.home else s.back, fontSize = 18.sp) }
                Button(
                    onClick = vm::howtoNext,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Exercise, contentColor = Color(0xFF00210F)),
                    modifier = Modifier.weight(2f).heightIn(min = 64.dp),
                ) {
                    Text(
                        if (last) s.startWorkout else s.nextBtn,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Button(
                    onClick = vm::closeHowTo,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Exercise, contentColor = Color(0xFF00210F)),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                ) { Text(s.gotIt, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            }
        }
        if (state.howtoWalkthrough && !last) {
            TextButton(
                onClick = vm::start,
                modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp),
            ) { Text(s.skipVideos, color = Palette.TextDim, fontSize = 15.sp) }
        }
    }
}

@Composable
private fun HowToVideo(rawRes: Int, missingText: String, volume: Float, onFinished: () -> Unit) {
    if (rawRes == 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(missingText, color = Palette.Danger)
        }
        return
    }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoURI(Uri.parse("android.resource://${ctx.packageName}/$rawRes"))
                setOnPreparedListener { mp ->
                    player = mp
                    mp.setVolume(volume, volume)
                    start()
                }
                setOnCompletionListener { onFinished() }
                setOnErrorListener { _, _, _ -> onFinished(); true }
            }
        },
        update = { player?.let { p -> try { p.setVolume(volume, volume) } catch (_: IllegalStateException) {} } },
        onRelease = { it.stopPlayback() },
    )
    DisposableEffect(Unit) { onDispose { player = null } }
}
