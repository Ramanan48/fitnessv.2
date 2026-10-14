package com.ramanan.fit.engine

/** Monotonic time source in milliseconds (Android: SystemClock.elapsedRealtime). */
fun interface MonotonicClock {
    fun nowMs(): Long
}

enum class RunState { READY, RUNNING, PAUSED, FINISHED }

enum class PauseReason { USER, BACKGROUND, AUDIO_INTERRUPTION }

data class Tick(val position: Position, val cue: Cue?)

/**
 * Session state machine. Not thread-safe: call from one thread (the main thread).
 *
 * Active elapsed time = time accumulated before the current run + (now − runStartedAt).
 * Because it is recomputed from a monotonic clock every tick (never by adding up
 * tick intervals), a slow or jittery ticker cannot cause drift, and paused time
 * never counts.
 */
class WorkoutSession(
    val length: SessionLength,
    private val clock: MonotonicClock,
    /** Cues older than this when finally observed are dropped instead of played late. */
    private val staleCueMs: Long = 600L,
) {
    private val schedule = cueSchedule(length)

    var state: RunState = RunState.READY
        private set
    var pauseReason: PauseReason? = null
        private set

    private var accumulatedMs = 0L
    private var runStartedAt = 0L

    /** Index of the first cue not yet handled (played or dropped). */
    private var nextCueIdx = 0

    fun activeElapsedMs(): Long {
        val raw = if (state == RunState.RUNNING) accumulatedMs + (clock.nowMs() - runStartedAt) else accumulatedMs
        return raw.coerceIn(0L, length.totalMs)
    }

    fun position(): Position = positionAt(length, activeElapsedMs())

    /** Returns false (and does nothing) unless the session is READY — guards duplicate Start presses. */
    fun start(): Boolean {
        if (state != RunState.READY) return false
        accumulatedMs = 0L
        runStartedAt = clock.nowMs()
        state = RunState.RUNNING
        pauseReason = null
        return true
    }

    fun pause(reason: PauseReason = PauseReason.USER): Boolean {
        if (state != RunState.RUNNING) return false
        accumulatedMs = activeElapsedMs()
        state = RunState.PAUSED
        pauseReason = reason
        return true
    }

    fun resume(): Boolean {
        if (state != RunState.PAUSED) return false
        runStartedAt = clock.nowMs()
        state = RunState.RUNNING
        pauseReason = null
        return true
    }

    /**
     * Advance and report the current position plus at most one cue to speak now.
     * If several cues were crossed since the last tick, only the newest is returned,
     * and only if it is not stale — so audio never queues up or plays late.
     */
    fun tick(): Tick {
        val now = activeElapsedMs()
        var cue: Cue? = null
        if (state == RunState.RUNNING || state == RunState.FINISHED) {
            var newest: TimedCue? = null
            while (nextCueIdx < schedule.size && schedule[nextCueIdx].atMs <= now) {
                newest = schedule[nextCueIdx]
                nextCueIdx++
            }
            if (newest != null) {
                val late = now - newest.atMs
                if (late <= staleCueMs || newest.cue == Cue.COMPLETED) cue = newest.cue
            }
            if (state == RunState.RUNNING && now >= length.totalMs) {
                accumulatedMs = length.totalMs
                state = RunState.FINISHED
            }
        }
        return Tick(positionAt(length, now), cue)
    }

    /** Snapshot for process-death restore. Only meaningful while PAUSED/RUNNING. */
    fun snapshot(): Snapshot = Snapshot(length.name, activeElapsedMs(), nextCueIdx)

    data class Snapshot(val lengthName: String, val activeElapsedMs: Long, val nextCueIdx: Int)

    companion object {
        /** Rebuild a session in the PAUSED state from a snapshot (e.g. after process death). */
        fun restorePaused(s: Snapshot, clock: MonotonicClock): WorkoutSession {
            val session = WorkoutSession(SessionLength.fromName(s.lengthName), clock)
            session.accumulatedMs = s.activeElapsedMs.coerceIn(0L, session.length.totalMs)
            session.nextCueIdx = s.nextCueIdx.coerceIn(0, session.schedule.size)
            session.state = RunState.PAUSED
            session.pauseReason = PauseReason.BACKGROUND
            return session
        }
    }
}
