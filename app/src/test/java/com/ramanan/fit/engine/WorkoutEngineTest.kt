package com.ramanan.fit.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FakeClock(var t: Long = 1_000_000L) : MonotonicClock {
    override fun nowMs() = t
    fun advance(ms: Long) { t += ms }
}

class WorkoutEngineTest {

    @Test
    fun sessionLengthsAreExact() {
        // minutes → total ms, cycles, cardio cycles, full rounds, total rounds, last round size
        data class E(val ms: Long, val cycles: Int, val main: Int, val full: Int, val total: Int, val last: Int)
        val expected = mapOf(
            SessionLength.MIN_15 to E(900_000L, 18, 16, 2, 2, 8),
            SessionLength.MIN_30 to E(1_800_000L, 36, 34, 4, 5, 2),
            SessionLength.MIN_45 to E(2_700_000L, 54, 52, 6, 7, 4),
            SessionLength.MIN_60 to E(3_600_000L, 72, 70, 8, 9, 6),
        )
        for ((len, e) in expected) {
            assertEquals(len.name, e.ms, len.totalMs)
            assertEquals(len.name, e.cycles, len.cycles)
            assertEquals(len.name, e.main, len.mainCycles)
            assertEquals(len.name, e.full, len.fullRounds)
            assertEquals(len.name, e.total, len.totalRounds)
            assertEquals(len.name, e.last, len.lastRoundSize)
            assertEquals(len.name, len.cycles * Timing.CYCLE_MS, len.totalMs)
            assertEquals(len.name, Timing.WARMUP_COUNT + (len.totalRounds - 1) * Timing.ROUND_SIZE + len.lastRoundSize, len.cycles)
        }
        assertEquals(SessionLength.MIN_30, SessionLength.DEFAULT)
    }

    @Test
    fun countdownDisplayAndTransitions() {
        val L = SessionLength.MIN_15
        val p0 = positionAt(L, 0)
        assertEquals(Phase.EXERCISE, p0.phase); assertEquals(30, p0.displaySeconds)
        assertEquals(30, positionAt(L, 999).displaySeconds)     // 30 shown for the whole first second
        assertEquals(29, positionAt(L, 1000).displaySeconds)
        assertEquals(1, positionAt(L, 29_999).displaySeconds)
        val r = positionAt(L, 30_000)                            // immediate switch at zero, no extra second
        assertEquals(Phase.REST, r.phase); assertEquals(20, r.displaySeconds)
        assertEquals(1, positionAt(L, 49_999).displaySeconds)
        val n = positionAt(L, 50_000)
        assertEquals(Phase.EXERCISE, n.phase); assertEquals(30, n.displaySeconds)
        assertEquals(1, n.exerciseIndex); assertEquals(2, n.exerciseNumber)
    }

    @Test
    fun warmupOnceThenCardioRoundsRepeat() {
        for (L in SessionLength.entries) {
            val order = ArrayList<Int>()
            for (c in 0 until L.cycles) {
                val p = positionAt(L, c * Timing.CYCLE_MS + 100)
                order += p.exerciseIndex
                if (c < 2) {
                    assertTrue(p.isWarmup); assertEquals(0, p.round)
                    assertEquals(c + 1, p.exerciseNumber); assertEquals(2, p.groupSize)
                } else {
                    val m = c - 2
                    val R = Timing.ROUND_SIZE
                    assertFalse(p.isWarmup)
                    assertEquals(m / R + 1, p.round)
                    assertEquals(m % R + 1, p.exerciseNumber)
                    assertEquals(if (p.round == L.totalRounds) L.lastRoundSize else R, p.groupSize)
                    assertEquals(2 + m % R, p.exerciseIndex)
                }
                val expectedNext = if (c == L.cycles - 1) null else exerciseForCycle(c + 1)
                assertEquals(expectedNext, p.nextExerciseIndex)
            }
            // warm-up exercises (0,1) appear exactly once, at the start
            assertEquals(listOf(0, 1), order.take(2))
            assertEquals(1, order.count { it == 0 }); assertEquals(1, order.count { it == 1 })
            // after warm-up, never repeats warm-ups and follows 2..9 in order
            assertEquals((0 until L.mainCycles).map { 2 + it % Timing.ROUND_SIZE }, order.drop(2))
            assertEquals(10, Timing.EXERCISE_COUNT)
            val last = positionAt(L, L.totalMs - 1)
            assertEquals(Phase.REST, last.phase); assertEquals(1, last.displaySeconds)
            assertEquals(L.totalRounds, last.round); assertEquals(L.lastRoundSize, last.exerciseNumber)
            val done = positionAt(L, L.totalMs)
            assertEquals(Phase.FINISHED, done.phase)
            assertEquals(L.cycles, done.completedExercises); assertEquals(L.fullRounds, done.completedRounds)
            assertEquals(0L, done.sessionRemainingMs)
        }
    }

    @Test
    fun eachDurationFinishesAtExactActiveTimeWithJitteryTicks() {
        val rnd = Random(42)
        for (L in SessionLength.entries) {
            val clock = FakeClock()
            val s = WorkoutSession(L, clock)
            assertTrue(s.start())
            var finishedAt = -1L
            while (finishedAt < 0) {
                clock.advance(rnd.nextLong(5, 140))
                val t = s.tick()
                if (s.state == RunState.FINISHED) finishedAt = t.position.activeElapsedMs
                else assertTrue(t.position.activeElapsedMs < L.totalMs)
            }
            assertEquals(L.totalMs, finishedAt)
            clock.advance(10_000)
            assertEquals(L.totalMs, s.activeElapsedMs())   // no running past the end
        }
    }

    @Test
    fun cueSequenceIsCompleteOrderedAndSpokenOnce() {
        for (L in SessionLength.entries) {
            val clock = FakeClock()
            val s = WorkoutSession(L, clock)
            s.start()
            val heard = ArrayList<Pair<Long, Cue>>()
            heard += s.tick().let { 0L to it.cue!! }
            while (s.state != RunState.FINISHED) {
                clock.advance(50)
                val t = s.tick()
                t.cue?.let { heard += t.position.activeElapsedMs to it }
            }
            val expected = cueSchedule(L)
            assertEquals(expected.map { it.cue }, heard.map { it.second })
            // each cue heard within one tick (50 ms) of its scheduled second
            expected.zip(heard).forEach { (e, h) -> assertTrue(h.first - e.atMs in 0..50) }
            assertEquals(Cue.LETS_START, heard.first().second)
            assertEquals(Cue.COMPLETED, heard.last().second)
            assertEquals(1, heard.count { it.second == Cue.COMPLETED })
            for (c in Cue.COUNTDOWN) assertEquals("$c in $L", 2 * L.cycles, heard.count { it.second == c })
            assertEquals(L.cycles, heard.count { it.second == Cue.TAKE_REST })
            assertEquals(L.cycles - 1, heard.count { it.second == Cue.NEXT_EXERCISE })
            val motivation = Cue.MOTIVATION_ROTATION + listOf(Cue.WARMUP, Cue.HALFWAY, Cue.LAST_ROUND, Cue.FINAL_PUSH)
            assertEquals(L.cycles, heard.count { it.second in motivation })
            assertEquals(L.cycles, heard.count { it.second in Cue.REST_ROTATION })
            assertEquals(2, heard.count { it.second == Cue.WARMUP })
            assertEquals(1, heard.count { it.second == Cue.HALFWAY })
            assertEquals(1, heard.count { it.second == Cue.LAST_ROUND })
            assertEquals(1, heard.count { it.second == Cue.FINAL_PUSH })
            // name spoken in every exercise
            assertEquals(L.cycles, heard.count { it.second == Cue.NAME_WARM || it.second in Cue.NAME_ROTATION })
            assertEquals(L.cycles, heard.count { it.second == Cue.FIFTEEN_LEFT || it.second == Cue.HALF_EXERCISE })
            assertEquals(L.cycles, heard.count { it.second == Cue.TEN_LEFT || it.second == Cue.LAST_TEN })
            assertEquals(L.cycles - 1, heard.count { it.second in Cue.UP_NEXT })
        }
    }

    @Test
    fun motivationSlotsAreDenseAndNeverOverlap() {
        for (L in SessionLength.entries) {
            val sched = cueSchedule(L)
            sched.zipWithNext().forEach { (a, b) -> assertTrue("$a vs $b", b.atMs > a.atMs) }
            for (tc in sched) {
                val p = positionAt(L, tc.atMs)
                when {
                    tc.cue == Cue.NAME_WARM || tc.cue in Cue.NAME_ROTATION -> { assertEquals(Phase.EXERCISE, p.phase); assertEquals(28, p.displaySeconds) }
                    tc.cue in Cue.MOTIVATION_ROTATION || tc.cue in Cue.MILESTONES -> { assertEquals(Phase.EXERCISE, p.phase); assertEquals(22, p.displaySeconds) }
                    tc.cue == Cue.FIFTEEN_LEFT || tc.cue == Cue.HALF_EXERCISE -> assertEquals(15, p.displaySeconds)
                    tc.cue == Cue.TEN_LEFT || tc.cue == Cue.LAST_TEN -> assertEquals(10, p.displaySeconds)
                    tc.cue in Cue.REST_ROTATION -> { assertEquals(Phase.REST, p.phase); assertEquals(17, p.displaySeconds) }
                    tc.cue in Cue.UP_NEXT -> {
                        assertEquals(Phase.REST, p.phase); assertEquals(11, p.displaySeconds)
                        assertEquals(Cue.UP_NEXT[p.nextExerciseIndex!!], tc.cue)
                    }
                }
            }
            // within every exercise, motivation keeps coming: no gap longer than 6 s before the countdown
            for (c in 0 until L.cycles) {
                val st = c * Timing.CYCLE_MS
                val times = sched.filter { it.atMs >= st && it.atMs < st + Timing.EXERCISE_MS }.map { it.atMs - st }
                times.zipWithNext().forEach { (a, b) -> assertTrue("gap $a→$b", b - a <= 7_000L) }
            }
            val firstOfLast = Timing.WARMUP_COUNT + (L.totalRounds - 1) * Timing.ROUND_SIZE
            assertEquals(Cue.LAST_ROUND, motivationFor(L, firstOfLast))
        }
    }

    @Test
    fun namedCuesFillInTheName() {
        assertEquals("Come on Priya, you've got this!", Cue.NAME_GO.say(" Priya "))
        assertEquals("Come on, you've got this!", Cue.NAME_GO.say(""))
        assertEquals("Come on, you've got this!", Cue.NAME_GO.say(null))
        assertEquals("Great job, Ramanan! Workout completed.", Cue.COMPLETED.say("Ramanan"))
        assertEquals("You've got this!", Cue.YOU_GOT_THIS.say("Ramanan"))
        assertEquals("Come on Ramanan, you've got this!", Cue.NAME_GO.say("Ramanan R"))   // first name only
        assertEquals("Ten more seconds, Ramanan! Give it everything!", Cue.TEN_LEFT.say("ramanan  r".replace("ramanan", "Ramanan")))
        // the name is heard several times in every exercise + rest
        val L = SessionLength.MIN_30
        val perCycle = cueSchedule(L).filter { it.atMs >= 2 * Timing.CYCLE_MS && it.atMs < 3 * Timing.CYCLE_MS && it.cue.named }
        assertTrue("named lines per cycle = ${perCycle.size}", perCycle.size >= 4)
        assertFalse(Cue.FIVE.named); assertTrue(Cue.NAME_STRONG.named)
        assertEquals(10, Cue.UP_NEXT.size)
    }

    @Test
    fun bannerShowsLatestMotivationOfThePhase() {
        val L = SessionLength.MIN_30
        assertNull(bannerCueAt(L, 1_000))                                  // before the first line
        assertEquals(Cue.NAME_WARM, bannerCueAt(L, 2_000))
        assertEquals(Cue.WARMUP, bannerCueAt(L, 9_000))
        assertEquals(Cue.TEN_LEFT, bannerCueAt(L, 29_000))
        assertNull(bannerCueAt(L, 30_500))                                 // rest just started
        assertEquals(Cue.REST_ROTATION[0], bannerCueAt(L, 33_000))
        assertEquals("Breathe deep, Ramanan. You're doing amazing.", bannerCueAt(L, 33_000)!!.say("Ramanan"))
        assertEquals(Cue.UP_STEP_JACKS, bannerCueAt(L, 40_000))
        assertNull(bannerCueAt(L, L.totalMs))
    }

    @Test
    fun countdownCuesLineUpWithDisplayedNumber() {
        val L = SessionLength.MIN_15
        for (tc in cueSchedule(L)) {
            val p = positionAt(L, tc.atMs)
            val idx = Cue.COUNTDOWN.indexOf(tc.cue)
            if (idx >= 0) assertEquals(5 - idx, p.displaySeconds)
        }
    }

    @Test
    fun pauseResumePreservesExactRemainingTime() {
        val clock = FakeClock()
        val s = WorkoutSession(SessionLength.MIN_30, clock)
        s.start()
        clock.advance(37_431); s.tick()
        assertTrue(s.pause())
        val before = s.position()
        clock.advance(3_600_000)                       // an hour paused
        val during = s.tick()
        assertNull(during.cue)
        assertEquals(before, during.position)
        assertFalse(s.pause())                         // already paused
        assertTrue(s.resume())
        assertEquals(before.phaseRemainingMs, s.position().phaseRemainingMs)
        assertEquals(before.sessionRemainingMs, s.position().sessionRemainingMs)
        clock.advance(1_000)
        assertEquals(37_431L + 1_000L, s.activeElapsedMs())
    }

    @Test
    fun pausedCueIsNotReplayedAfterResume() {
        val clock = FakeClock()
        val s = WorkoutSession(SessionLength.MIN_15, clock)
        s.start(); s.tick()
        clock.advance(25_000)
        assertEquals(Cue.FIVE, s.tick().cue)
        clock.advance(300); s.pause()
        clock.advance(60_000); s.resume()
        assertNull(s.tick().cue)
        clock.advance(700)
        assertEquals(Cue.FOUR, s.tick().cue)
    }

    @Test
    fun repeatedStartDoesNotRestartOrDuplicate() {
        val clock = FakeClock()
        val s = WorkoutSession(SessionLength.MIN_15, clock)
        assertTrue(s.start())
        clock.advance(5_000)
        assertFalse(s.start()); assertFalse(s.start())
        assertEquals(5_000L, s.activeElapsedMs())
        assertFalse(s.resume())                        // resume while running is a no-op
    }

    @Test
    fun staleCuesAreDroppedNotQueued() {
        val clock = FakeClock()
        val s = WorkoutSession(SessionLength.MIN_15, clock)
        s.start(); s.tick()
        clock.advance(29_500)                          // ticker stalled past Five..One
        assertEquals(Cue.ONE, s.tick().cue)            // only the newest, still fresh
        clock.advance(8_000)                           // stalled past Take a rest + praise (33 s) by 4.5 s
        assertNull(s.tick().cue)                       // too late: dropped, nothing queued
        clock.advance(7_600)                           // stalled past Up next (39 s) by 6.1 s
        assertEquals(Cue.FIVE, s.tick().cue)           // only the fresh countdown plays
    }

    @Test
    fun restoreFromSnapshotIsPausedAtSamePoint() {
        val clock = FakeClock()
        val s = WorkoutSession(SessionLength.MIN_45, clock)
        s.start(); s.tick()
        clock.advance(123_456); s.tick(); s.pause(PauseReason.BACKGROUND)
        val snap = s.snapshot()
        val clock2 = FakeClock(5L)
        val r = WorkoutSession.restorePaused(snap, clock2)
        assertEquals(RunState.PAUSED, r.state)
        assertEquals(SessionLength.MIN_45, r.length)
        assertEquals(s.position(), r.position())
        r.resume(); clock2.advance(1_000)
        assertEquals(124_456L, r.activeElapsedMs())
    }
}
