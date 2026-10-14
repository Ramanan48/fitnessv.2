package com.ramanan.fit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramanan.fit.engine.RunState

@Composable
fun App(vm: WorkoutViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .safeDrawingPadding()
    ) {
        when (state.screen) {
            Screen.NAME -> NameScreen(state, vm)
            Screen.HOME -> HomeScreen(state, vm)
            Screen.HOWTO -> HowToScreen(state, vm)
            Screen.WORKOUT -> WorkoutScreen(state, vm)
            Screen.DONE -> DoneScreen(state, vm)
        }
    }
}

/**
 * Active elapsed time refreshed every display frame while running, straight from the
 * session's monotonic clock. When paused it stops changing, freezing the countdown and
 * animation at exactly the same point.
 */
@Composable
fun rememberLiveElapsed(vm: WorkoutViewModel, runState: RunState, key: Any?): Long {
    var t by remember { mutableLongStateOf(vm.liveElapsedMs()) }
    LaunchedEffect(runState, key) {
        t = vm.liveElapsedMs()
        if (runState == RunState.RUNNING) {
            while (true) withFrameMillis { t = vm.liveElapsedMs() }
        }
    }
    return t
}

/** A free-running loop clock for the home-screen exercise previews. */
@Composable
fun rememberPreviewClock(): Long {
    var t by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { t = it - start }
    }
    return t
}

fun formatMmSs(ms: Long): String {
    val total = ((ms + 999) / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
