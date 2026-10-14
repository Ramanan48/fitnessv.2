package com.ramanan.fit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.ramanan.fit.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramanan.fit.anim.StickFigure
import com.ramanan.fit.data.Exercises
import com.ramanan.fit.engine.PauseReason
import com.ramanan.fit.engine.Phase
import com.ramanan.fit.engine.Position
import com.ramanan.fit.engine.RunState
import com.ramanan.fit.engine.positionAt
import com.ramanan.fit.engine.bannerCueAt

@Composable
fun WorkoutScreen(state: UiState, vm: WorkoutViewModel) {
    val base = state.position ?: return
    val running = state.runState == RunState.RUNNING
    val elapsed = rememberLiveElapsed(vm, state.runState, base.cycleIndex to base.phase)
    val p: Position = positionAt(base.length, elapsed)
    var confirmStop by remember { mutableStateOf(false) }
    val s = S

    // Keep the screen awake only while the workout is actively running.
    val view = LocalView.current
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }
    // System back = Pause (never silently ends the session).
    BackHandler { if (running) vm.pause() else confirmStop = true }

    val isRest = p.phase == Phase.REST
    val accent = if (isRest) Palette.Rest else Palette.Exercise
    val deep = if (isRest) Palette.RestDeep else Palette.ExerciseDeep
    val shownEx = Exercises.all[if (isRest) (p.nextExerciseIndex ?: p.exerciseIndex) else p.exerciseIndex]

    Column(
        Modifier
            .fillMaxSize()
            .background(deep.copy(alpha = 0.55f))
    ) {
        TopStatus(p, accent, s)
        if (!running) PausedBanner(s.paused(state.pauseReason))

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val landscape = maxWidth > maxHeight
            val figure: @Composable (Modifier) -> Unit = { m ->
                Box(
                    m
                        .clip(RoundedCornerShape(20.dp))
                        .background(Palette.Bg.copy(alpha = 0.7f))
                ) {
                    StickFigure(shownEx.id, p.phaseElapsedMs, accent, Modifier.fillMaxSize().padding(8.dp), avatar = state.avatar)
                    if (isRest) {
                        Text(
                            if (p.nextExerciseIndex != null) s.nextUpPreview else s.lastRest,
                            color = Palette.Rest, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                        )
                    }
                }
            }
            if (landscape) {
                Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    figure(Modifier.weight(1f).fillMaxHeight())
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) { PhaseInfo(p, accent, isRest, state.name, s) }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    figure(Modifier.fillMaxWidth().weight(1f))
                    Spacer(Modifier.height(8.dp))
                    PhaseInfo(p, accent, isRest, state.name, s)
                }
            }
        }

        Controls(running, s, onPauseResume = { if (running) vm.pause() else vm.resume() }, onStop = { confirmStop = true })
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(s.stopTitle) },
            text = { Text(s.stopText) },
            confirmButton = {
                TextButton(onClick = { confirmStop = false; vm.stop() }) { Text(s.stop, color = Palette.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text(s.keepGoing) } },
        )
    }
}

@Composable
private fun TopStatus(p: Position, accent: Color, s: S) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (p.isWarmup) s.warmupStatus(p.exerciseNumber, p.groupSize)
                else s.roundStatus(p.round, p.length.totalRounds, p.exerciseNumber, p.groupSize),
                color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(s.left(formatMmSs(p.sessionRemainingMs)), color = Palette.TextDim, fontSize = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { p.progress },
            color = accent,
            trackColor = Palette.SurfaceHi,
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
        )
    }
}

@Composable
private fun PausedBanner(msg: String) {
    Text(
        msg,
        color = Color(0xFF241A00),
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Warn)
            .padding(12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun ColumnScope.PhaseInfo(p: Position, accent: Color, isRest: Boolean, name: String, s: S) {
    val currentEx = Exercises.all[p.exerciseIndex]
    val current = currentEx.en
    val next = p.nextExerciseIndex?.let { Exercises.all[it].en }

    // Motivation line, shown from the moment it is spoken
    val cheer = bannerCueAt(p.length, p.activeElapsedMs)?.say(name)
    if (cheer != null) {
        Text(
            "💪 $cheer",
            color = Palette.Bg, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(bottom = 6.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(accent)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    Text(if (isRest) s.restLabel else s.exercise, color = accent, fontSize = 18.sp, fontWeight = FontWeight.Black)
    Text(
        "${p.displaySeconds}",
        color = accent,
        fontSize = 108.sp,
        fontWeight = FontWeight.Black,
        lineHeight = 112.sp,
        modifier = Modifier.semantics { contentDescription = "${p.displaySeconds} seconds" },
    )
    if (!isRest) {
        Text(currentEx.level.label.uppercase(), color = levelColor(currentEx.level), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(current.name, color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(current.tips.first(), color = Palette.TextDim, fontSize = 15.sp, textAlign = TextAlign.Center)
        current.easier?.let { Text(s.noJump(it), color = Palette.Rest, fontSize = 14.sp, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(4.dp))
        Text(
            if (next != null) s.next(next.name) else s.lastExercise,
            color = Palette.TextDim, fontSize = 14.sp, textAlign = TextAlign.Center,
        )
    } else if (next != null) {
        Text(s.next(next.name), color = Palette.Text, fontSize = 21.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(next.prep, color = Palette.TextDim, fontSize = 15.sp, textAlign = TextAlign.Center)
    } else {
        Text(s.finalRest, color = Palette.Text, fontSize = 18.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Controls(running: Boolean, s: S, onPauseResume: () -> Unit, onStop: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onPauseResume,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (running) Palette.Warn else Palette.Exercise,
                contentColor = Color(0xFF1A1400),
            ),
            modifier = Modifier.weight(2f).heightIn(min = 72.dp),
        ) { Text(if (running) s.pause else s.resume, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        Button(
            onClick = onStop,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.SurfaceHi, contentColor = Palette.Danger),
            modifier = Modifier.weight(1f).heightIn(min = 72.dp),
        ) { Text(s.stop, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun DoneScreen(state: UiState, vm: WorkoutViewModel) {
    val p = state.position
    val s = S
    BackHandler { vm.stop() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.coach_photo),
            contentDescription = s.coachTitle,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.size(140.dp).clip(CircleShape).border(3.dp, Palette.Exercise, CircleShape),
        )
        Spacer(Modifier.height(10.dp))
        Text(s.coachProud, color = Palette.TextDim, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(s.greatJob(state.name), color = Palette.Exercise, fontSize = 40.sp, fontWeight = FontWeight.Black)
        Text(s.completed, color = Palette.Text, fontSize = 22.sp)
        Spacer(Modifier.height(24.dp))
        if (p != null) {
            InfoCard {
                DoneRow(s.session, s.dur(p.length))
                DoneRow(s.exercisesDone, "${p.completedExercises}")
                DoneRow(s.fullRounds, "${p.completedRounds}")
                DoneRow(s.activeTime, formatMmSs(p.activeElapsedMs))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(s.coolDown, color = Palette.TextDim, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = vm::stop,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Exercise, contentColor = Color(0xFF00210F)),
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        ) { Text(s.backHome, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun DoneRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = Palette.TextDim, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Text(value, color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}
