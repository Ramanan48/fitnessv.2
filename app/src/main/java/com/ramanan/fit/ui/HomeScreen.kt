package com.ramanan.fit.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.ramanan.fit.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramanan.fit.anim.StickFigure
import com.ramanan.fit.data.Exercise
import com.ramanan.fit.data.Exercises
import com.ramanan.fit.data.Level
import com.ramanan.fit.engine.SessionLength
import com.ramanan.fit.engine.Timing
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(state: UiState, vm: WorkoutViewModel) {
    val s = S
    val clock = rememberPreviewClock()
    var expanded by remember { mutableIntStateOf(-1) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ramanan Fit", color = Palette.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text(s.hello(state.name), color = Palette.Exercise, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(s.tagline, color = Palette.TextDim, fontSize = 13.sp)
                    }
                    TextButton(onClick = vm::editName, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(s.changeName, color = Palette.TextDim, fontSize = 14.sp)
                    }
                }
            }
            item { CoachCard(state.name) }

            item { SectionTitle(s.sessionLength) }
            item { DurationGrid(state.selected, vm::select) }
            item {
                val len = state.selected
                InfoCard {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Stat(s.workout, "${Timing.EXERCISE_MS / 1000} s", Palette.Exercise)
                        Stat(s.rest, "${Timing.REST_MS / 1000} s", Palette.Rest)
                        Stat(s.rounds, "${len.fullRounds}+", Palette.Text)
                        Stat(s.intervals, "${len.cycles}", Palette.Text)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(s.info(len), color = Palette.TextDim, fontSize = 13.sp)
                }
            }

            item { SectionTitle(s.voiceCoach) }
            item { VoiceCard(state, vm) }

            item { SectionTitle(s.exercisesTitle) }
            item { SettingRow(s.guideStyle(state.avatar), state.avatar, vm::setAvatar) }
            item { SettingRow(s.howtoBefore, state.howtoBefore, vm::setHowtoBefore) }
            itemsIndexed(Exercises.all) { i, ex ->
                ExerciseRow(i, ex, clock, expanded == i, state.avatar, onHowTo = { vm.openHowTo(i) }) {
                    expanded = if (expanded == i) -1 else i
                }
            }

            if (state.history.isNotEmpty()) {
                item { SectionTitle(s.recent) }
                item {
                    InfoCard {
                        val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        state.history.take(5).forEach { h ->
                            Text(
                                "${fmt.format(Date(h.finishedAtEpochMs))} — ${s.history(h.minutes, h.exercises, h.rounds)}",
                                color = Palette.TextDim, fontSize = 14.sp, modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                    }
                }
            }
            item { Text(s.disclaimer, color = Palette.TextDim, fontSize = 12.sp) }
        }

        Button(
            onClick = vm::onStartPressed,
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Exercise, contentColor = Color(0xFF00210F)),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(min = 72.dp)
                .semantics { contentDescription = s.start(s.dur(state.selected)) },
        ) {
            Text(s.start(s.dur(state.selected)), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** "Your Coach" card: the owner's chosen coach photo + a daily motivation line. */
@Composable
private fun CoachCard(name: String) {
    val s = S
    val quote = s.coachQuote(name, java.time.LocalDate.now().dayOfYear)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.coach_photo),
            contentDescription = s.coachTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 104.dp, height = 156.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(s.coachTitle.uppercase(), color = Palette.Exercise, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("“$quote”", color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Palette.TextDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.Exercise),
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, color = Palette.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
}

@Composable
fun InfoCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Surface)
            .padding(16.dp)
    ) { content() }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Palette.TextDim, fontSize = 13.sp)
    }
}

@Composable
private fun DurationGrid(selected: SessionLength, onSelect: (SessionLength) -> Unit) {
    val s = S
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SessionLength.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { len ->
                    val sel = len == selected
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 76.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (sel) Palette.ExerciseDeep else Palette.Surface)
                            .border(
                                width = if (sel) 2.dp else 1.dp,
                                color = if (sel) Palette.Exercise else Palette.SurfaceHi,
                                shape = RoundedCornerShape(16.dp),
                            )
                            .clickable(role = Role.RadioButton) { onSelect(len) }
                            .semantics { this.selected = sel }
                            .padding(14.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            s.dur(len) + if (len == SessionLength.DEFAULT) s.defaultTag else "",
                            color = if (sel) Palette.Exercise else Palette.Text,
                            fontSize = 19.sp, fontWeight = FontWeight.Bold,
                        )
                        Text(s.structure(len), color = Palette.TextDim, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceCard(state: UiState, vm: WorkoutViewModel) {
    val s = S
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.femaleVoice, color = Palette.Text, fontSize = 16.sp)
                Text(if (state.muted) s.muted else s.volume((state.volume * 100).toInt()), color = Palette.TextDim, fontSize = 13.sp)
            }
            Text(s.mute, color = Palette.TextDim, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = state.muted,
                onCheckedChange = vm::setMuted,
                colors = SwitchDefaults.colors(checkedTrackColor = Palette.Warn),
                modifier = Modifier.semantics { contentDescription = s.mute },
            )
        }
        Slider(
            value = state.volume,
            onValueChange = vm::setVolume,
            enabled = !state.muted,
            colors = SliderDefaults.colors(thumbColor = Palette.Exercise, activeTrackColor = Palette.Exercise),
            modifier = Modifier.semantics { contentDescription = s.voiceCoach },
        )
        OutlinedButton(
            onClick = vm::testVoice,
            enabled = !state.muted,
            border = BorderStroke(1.dp, Palette.TextDim),
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(s.testVoice, color = Palette.Text) }
        Spacer(Modifier.height(8.dp))
        when {
            state.namePrerecorded -> Text(s.namePrerecorded(state.name), color = Palette.Exercise, fontSize = 13.sp)
            state.canSayName -> Text(s.namePhoneVoice, color = Palette.TextDim, fontSize = 13.sp)
            else -> Text(s.nameVoiceNote, color = Palette.Warn, fontSize = 13.sp)
        }
        if (!state.namePrerecorded) SettingRow(s.phoneVoiceSwitch, state.phoneVoiceNames, vm::setPhoneVoiceNames)
        if (state.missingClips.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(s.missingClips + state.missingClips.joinToString(), color = Palette.Danger, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ExerciseRow(
    index: Int, ex: Exercise, clock: Long, open: Boolean, avatar: Boolean,
    onHowTo: () -> Unit, onToggle: () -> Unit,
) {
    val s = S
    val t = ex.en
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Surface)
            .clickable(onClick = onToggle)
            .animateContentSize()
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(if (open) 140.dp else 72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Palette.Bg)
            ) {
                StickFigure(ex.id, clock, Palette.Exercise, Modifier.fillMaxSize().padding(4.dp), avatar = avatar)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ex.level.label.uppercase(), color = levelColor(ex.level), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("${index + 1}. ${t.name}", color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                if (!open) Text(t.steps[1], color = Palette.TextDim, fontSize = 13.sp, maxLines = 2)
            }
        }
        if (open) {
            Spacer(Modifier.height(10.dp))
            t.steps.forEachIndexed { i, st -> Bullet("${i + 1}. $st", Palette.Text) }
            Spacer(Modifier.height(6.dp))
            t.tips.forEach { Bullet("• $it", Palette.TextDim) }
            t.easier?.let { Bullet(s.easier + it, Palette.Rest) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onHowTo,
                border = BorderStroke(1.dp, Palette.Exercise),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(s.watchHowto, color = Palette.Exercise) }
        }
    }
}

fun levelColor(l: Level): Color = when (l) {
    Level.WARMUP -> Palette.Warn
    Level.BEGINNER -> Palette.Rest
    Level.ADVANCED -> Palette.Exercise
}

@Composable
private fun Bullet(text: String, color: Color) {
    Text(text, color = color, fontSize = 15.sp, modifier = Modifier.padding(vertical = 2.dp))
}
