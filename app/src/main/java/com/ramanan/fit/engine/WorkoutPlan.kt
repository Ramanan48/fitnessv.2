package com.ramanan.fit.engine

/**
 * Pure-Kotlin workout schedule. No Android imports — unit-tested on the JVM.
 *
 * One cycle = 30 s exercise + 20 s rest = 50 s. Cardio HIIT structure:
 *  - Warm-up: exercises 0 and 1, once, at the start of the session.
 *  - Then cardio rounds of exercises 2..9 (8 per round) repeat until time is up.
 *    The last round may be shorter so the session ends exactly on time.
 * Sessions include the final rest, so a session lasts exactly its selected length.
 */
object Timing {
    const val EXERCISE_MS = 30_000L
    const val REST_MS = 20_000L
    const val CYCLE_MS = EXERCISE_MS + REST_MS
    const val WARMUP_COUNT = 2
    const val ROUND_SIZE = 8
    const val EXERCISE_COUNT = WARMUP_COUNT + ROUND_SIZE
    const val COUNTDOWN_FROM = 5
}

enum class SessionLength(val minutes: Int, val label: String) {
    MIN_15(15, "15 min"),
    MIN_30(30, "30 min"),
    MIN_45(45, "45 min"),
    MIN_60(60, "1 hour");

    val totalMs: Long get() = minutes * 60_000L
    val cycles: Int get() = (totalMs / Timing.CYCLE_MS).toInt()
    /** Cardio intervals after the warm-up. */
    val mainCycles: Int get() = cycles - Timing.WARMUP_COUNT
    val fullRounds: Int get() = mainCycles / Timing.ROUND_SIZE
    /** Rounds including a shorter final round, if any. */
    val totalRounds: Int get() = (mainCycles + Timing.ROUND_SIZE - 1) / Timing.ROUND_SIZE
    /** Exercises in the final round (= ROUND_SIZE when it is a full round). */
    val lastRoundSize: Int get() = mainCycles - (totalRounds - 1) * Timing.ROUND_SIZE

    init {
        require(totalMs % Timing.CYCLE_MS == 0L) { "Session must be a whole number of cycles" }
        require(mainCycles > 0)
    }

    companion object {
        val DEFAULT = MIN_30
        fun fromName(name: String?): SessionLength = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Which of the 8 exercises a given cycle uses. */
fun exerciseForCycle(cycle: Int): Int =
    if (cycle < Timing.WARMUP_COUNT) cycle
    else Timing.WARMUP_COUNT + (cycle - Timing.WARMUP_COUNT) % Timing.ROUND_SIZE

enum class Phase { EXERCISE, REST, FINISHED }

/** Snapshot of where the session is at a given active (un-paused) elapsed time. */
data class Position(
    val length: SessionLength,
    val activeElapsedMs: Long,
    val phase: Phase,
    /** 0-based cycle number across the whole session. */
    val cycleIndex: Int,
    /** 0..9 — which exercise this cycle uses (0-1 warm-up, 2-9 cardio round). */
    val exerciseIndex: Int,
    /** true during the two warm-up cycles. */
    val isWarmup: Boolean,
    /** 0 during warm-up, then 1-based cardio round number. */
    val round: Int,
    /** 1-based position within the warm-up or the current round. */
    val exerciseNumber: Int,
    /** Exercises in the warm-up (2) or in the current round (8, or fewer in a short final round). */
    val groupSize: Int,
    val phaseElapsedMs: Long,
    val phaseRemainingMs: Long,
    /** Exercise that follows the current cycle, or null on the final cycle. */
    val nextExerciseIndex: Int?,
) {
    val sessionRemainingMs: Long get() = (length.totalMs - activeElapsedMs).coerceAtLeast(0)
    val progress: Float get() = (activeElapsedMs.toDouble() / length.totalMs).toFloat().coerceIn(0f, 1f)

    /** Big countdown number: 30→1 (exercise), 20→1 (rest). Shows 0 only when finished. */
    val displaySeconds: Int get() = ceilDiv(phaseRemainingMs, 1000L).toInt()

    /** Exercises (warm-up included) whose 30 s work interval has fully elapsed. */
    val completedExercises: Int
        get() = when (phase) {
            Phase.FINISHED -> length.cycles
            Phase.REST -> cycleIndex + 1
            Phase.EXERCISE -> cycleIndex
        }

    /** Full 8-exercise cardio rounds whose last work interval has fully elapsed. */
    val completedRounds: Int
        get() = ((completedExercises - Timing.WARMUP_COUNT).coerceAtLeast(0)) / Timing.ROUND_SIZE
}

internal fun ceilDiv(a: Long, b: Long): Long = if (a <= 0) 0 else (a + b - 1) / b

private fun groupInfo(length: SessionLength, cycle: Int): Triple<Int, Int, Int> {
    // (round, exerciseNumber, groupSize)
    if (cycle < Timing.WARMUP_COUNT) return Triple(0, cycle + 1, Timing.WARMUP_COUNT)
    val m = cycle - Timing.WARMUP_COUNT
    val round = m / Timing.ROUND_SIZE + 1
    val size = if (round == length.totalRounds) length.lastRoundSize else Timing.ROUND_SIZE
    return Triple(round, m % Timing.ROUND_SIZE + 1, size)
}

fun positionAt(length: SessionLength, activeElapsedMs: Long): Position {
    val elapsed = activeElapsedMs.coerceIn(0L, length.totalMs)
    if (elapsed >= length.totalMs) {
        val last = length.cycles - 1
        val (r, n, g) = groupInfo(length, last)
        return Position(
            length, length.totalMs, Phase.FINISHED, last, exerciseForCycle(last), false,
            r, n, g, Timing.REST_MS, 0L, null,
        )
    }
    val cycle = (elapsed / Timing.CYCLE_MS).toInt()
    val inCycle = elapsed - cycle * Timing.CYCLE_MS
    val isExercise = inCycle < Timing.EXERCISE_MS
    val phaseElapsed = if (isExercise) inCycle else inCycle - Timing.EXERCISE_MS
    val phaseLen = if (isExercise) Timing.EXERCISE_MS else Timing.REST_MS
    val (round, number, size) = groupInfo(length, cycle)
    return Position(
        length = length,
        activeElapsedMs = elapsed,
        phase = if (isExercise) Phase.EXERCISE else Phase.REST,
        cycleIndex = cycle,
        exerciseIndex = exerciseForCycle(cycle),
        isWarmup = cycle < Timing.WARMUP_COUNT,
        round = round,
        exerciseNumber = number,
        groupSize = size,
        phaseElapsedMs = phaseElapsed,
        phaseRemainingMs = phaseLen - phaseElapsed,
        nextExerciseIndex = if (cycle + 1 < length.cycles) exerciseForCycle(cycle + 1) else null,
    )
}

/**
 * Voice cues. Each has one bundled English clip in assets/voice.pack (key = [asset]).
 * Named cues ([named] = true) say the user's name: [text] holds "{name}"; [plain] is the
 * name-free fallback that the bundled clip actually says. A clip "<asset>@<name>" may also be
 * bundled for a specific name (e.g. the owner's), otherwise the device's offline TTS speaks
 * [text] with the name filled in.
 */
enum class Cue(val asset: String, val text: String, val plain: String = text) {
    LETS_START("lets_start", "Let's start!"),
    FIVE("five", "Five."), FOUR("four", "Four."), THREE("three", "Three."), TWO("two", "Two."), ONE("one", "One."),
    TAKE_REST("take_rest", "Take a rest."),
    NEXT_EXERCISE("next_exercise", "Next exercise."),
    COMPLETED("completed", "Great job, {name}! Workout completed.", "Great job! Workout completed."),
    HELLO("hello", "Hi {name}! I'm your coach. Let's get fit together!", "Hi! I'm your coach. Let's get fit together!"),

    // Slot A — 2 s into every exercise: always says the name.
    NAME_WARM("name_warm", "Let's warm up, {name}!", "Let's warm up!"),
    NAME_GO("name_go", "Come on {name}, you've got this!", "Come on, you've got this!"),
    NAME_STRONG("name_strong", "{name}, you are stronger than you think!", "You are stronger than you think!"),
    NAME_PROUD("name_proud", "Keep going {name}, I'm proud of you!", "Keep going, I'm proud of you!"),
    NAME_PUSH("name_push", "Push it {name}, don't give up!", "Push it, don't give up!"),
    NAME_GREAT("name_great", "Looking great, {name}!", "Looking great!"),
    NAME_ENERGY("name_energy", "That's it {name}, keep that energy!", "That's it, keep that energy!"),
    NAME_BELIEVE("name_believe", "I believe in you, {name}!", "I believe in you!"),

    // Slot B — 8 s: a motivation line, or a session milestone.
    KEEP_GOING("keep_going", "Keep going, you're doing great!"),
    YOU_GOT_THIS("you_got_this", "You've got this!"),
    GREAT_FORM("great_form", "Great form, stay strong!"),
    PUSH_HARDER("push_harder", "Push a little harder!"),
    STRONGER("stronger", "Every rep makes you stronger!"),
    DONT_STOP("dont_stop", "Don't stop now!"),
    FEEL_BURN("feel_burn", "Feel that burn. It's working!"),
    BREATHE_MOVE("breathe_move", "Breathe and keep moving!"),
    WARMUP("warmup", "Nice and easy. Warm up your body."),
    HALFWAY("halfway", "Halfway through the workout, {name}! Amazing!", "Halfway through the workout! Amazing!"),
    LAST_ROUND("last_round", "Last round, {name}! Give it everything!", "Last round! Give it everything!"),
    FINAL_PUSH("final_push", "Final exercise, {name}! Finish strong!", "Final exercise! Finish strong!"),

    // Slot C — 15 s left.  Slot D — 10 s left.
    FIFTEEN_LEFT("fifteen_left", "Fifteen seconds to go, {name}! Keep moving!", "Fifteen seconds to go, keep moving!"),
    HALF_EXERCISE("half_exercise", "Halfway there, {name}! Stay strong!", "Halfway there, stay strong!"),
    TEN_LEFT("ten_left", "Ten more seconds, {name}! Give it everything!", "Ten more seconds, give it everything!"),
    LAST_TEN("last_ten", "Last ten seconds, {name}! Push!", "Last ten seconds, push!"),

    // Rest — 3 s in: praise. 9 s in: what comes next.
    BREATHE("breathe", "Breathe deep, {name}. You're doing amazing.", "Breathe deep. You're doing amazing."),
    GREAT_WORK("great_work", "Great work, {name}! Shake it out.", "Great work! Shake it out."),
    PROUD("proud", "Be proud of yourself, {name}. Keep it up!", "Be proud of yourself. Keep it up!"),
    UP_MARCH_ARMS("up_march_arms", "Up next: March with arm swings."),
    UP_STEP_JACKS("up_step_jacks", "Up next: Step jacks."),
    UP_HIGH_KNEES("up_high_knees", "Up next: High knees. Get ready!"),
    UP_BUTT_KICKS("up_butt_kicks", "Up next: Butt kicks. Get ready!"),
    UP_JUMPING_JACKS("up_jumping_jacks", "Up next: Jumping jacks. Get ready!"),
    UP_SKATERS("up_skaters", "Up next: Skaters. Get ready!"),
    UP_JUMP_LUNGES("up_jump_lunges", "Up next: Jump lunges. Get ready!"),
    UP_MOUNTAIN_CLIMBERS("up_mountain_climbers", "Up next: Mountain climbers. Get ready!"),
    UP_SQUAT_JUMPS("up_squat_jumps", "Up next: Squat jumps. Get ready!"),
    UP_BURPEES("up_burpees", "Up next: Burpees. Last one, get ready!");

    val named: Boolean get() = text.contains("{name}")

    /** What to show / speak, with the user's first name filled in (or the name-free version). */
    fun say(name: String?): String {
        val first = name?.trim()?.split(Regex("\\s+"))?.firstOrNull().orEmpty()
        return if (named && first.isNotEmpty()) text.replace("{name}", first) else plain
    }

    companion object {
        val COUNTDOWN = listOf(FIVE, FOUR, THREE, TWO, ONE)
        val NAME_ROTATION = listOf(NAME_GO, NAME_STRONG, NAME_ENERGY, NAME_PROUD, NAME_PUSH, NAME_BELIEVE, NAME_GREAT)
        val MOTIVATION_ROTATION = listOf(KEEP_GOING, YOU_GOT_THIS, GREAT_FORM, PUSH_HARDER, STRONGER, DONT_STOP, FEEL_BURN, BREATHE_MOVE)
        val MILESTONES = listOf(WARMUP, HALFWAY, LAST_ROUND, FINAL_PUSH)
        val REST_ROTATION = listOf(BREATHE, GREAT_WORK, PROUD)
        /** "Up next" cue per exercise, in exercise order (checked against Exercises.all). */
        val UP_NEXT = listOf(UP_MARCH_ARMS, UP_STEP_JACKS, UP_HIGH_KNEES, UP_BUTT_KICKS, UP_JUMPING_JACKS,
            UP_SKATERS, UP_JUMP_LUNGES, UP_MOUNTAIN_CLIMBERS, UP_SQUAT_JUMPS, UP_BURPEES)

        /** Cues that are just signals; everything else is shown as a motivation banner. */
        val SIGNALS = COUNTDOWN + listOf(LETS_START, NEXT_EXERCISE, TAKE_REST, COMPLETED, HELLO)
    }
}

/** Exercise-phase slots (ms into the 30 s exercise). Countdown follows at 25 s. */
const val SLOT_NAME_MS = 2_000L
const val SLOT_MOTIVATION_MS = 8_000L
const val SLOT_FIFTEEN_MS = 15_000L
const val SLOT_TEN_MS = 20_000L
/** Rest-phase slots (ms into the 20 s rest). Countdown follows at 15 s. */
const val REST_PRAISE_AT_MS = 3_000L
const val REST_UP_NEXT_AT_MS = 9_000L

/** Slot-A line: warm-up greeting during the warm-up, otherwise a rotating name line. */
fun nameLineFor(cycle: Int): Cue =
    if (cycle < Timing.WARMUP_COUNT) Cue.NAME_WARM else Cue.NAME_ROTATION[cycle % Cue.NAME_ROTATION.size]

/** Slot-B line: warm-up, final exercise, last round, session halfway, else a rotation. */
fun motivationFor(length: SessionLength, cycle: Int): Cue {
    val firstOfLastRound = Timing.WARMUP_COUNT + (length.totalRounds - 1) * Timing.ROUND_SIZE
    val halfwayCycle = ((length.totalMs / 2) / Timing.CYCLE_MS).toInt()
    return when {
        cycle < Timing.WARMUP_COUNT -> Cue.WARMUP
        cycle == length.cycles - 1 -> Cue.FINAL_PUSH
        length.totalRounds > 1 && cycle == firstOfLastRound -> Cue.LAST_ROUND
        cycle == halfwayCycle -> Cue.HALFWAY
        else -> Cue.MOTIVATION_ROTATION[cycle % Cue.MOTIVATION_ROTATION.size]
    }
}

/** The praise line spoken during a cycle's rest. */
fun restPraiseFor(length: SessionLength, cycle: Int): Cue =
    if (cycle == length.cycles - 1) Cue.PROUD else Cue.REST_ROTATION[cycle % Cue.REST_ROTATION.size]

data class TimedCue(val atMs: Long, val cue: Cue)

/**
 * Every cue in the session, sorted by time.
 * Exercise (30 s): 0 "Let's start!"/"Next exercise." · 2 s name line · 8 s motivation/milestone ·
 *   15 s "fifteen seconds" line · 20 s "ten seconds" line · 25–29 s Five…One.
 * Rest (20 s): 0 "Take a rest." · 3 s praise · 9 s "Up next: …" (not on the final rest) ·
 *   15–19 s Five…One.  End of session (after the final rest): "Great job, {name}! …"
 */
fun cueSchedule(length: SessionLength): List<TimedCue> {
    val out = ArrayList<TimedCue>(length.cycles * 18 + 1)
    for (c in 0 until length.cycles) {
        val start = c * Timing.CYCLE_MS
        out += TimedCue(start, if (c == 0) Cue.LETS_START else Cue.NEXT_EXERCISE)
        out += TimedCue(start + SLOT_NAME_MS, nameLineFor(c))
        out += TimedCue(start + SLOT_MOTIVATION_MS, motivationFor(length, c))
        out += TimedCue(start + SLOT_FIFTEEN_MS, if (c % 2 == 0) Cue.FIFTEEN_LEFT else Cue.HALF_EXERCISE)
        out += TimedCue(start + SLOT_TEN_MS, if (c % 2 == 0) Cue.TEN_LEFT else Cue.LAST_TEN)
        addCountdown(out, start + Timing.EXERCISE_MS)
        val rest = start + Timing.EXERCISE_MS
        out += TimedCue(rest, Cue.TAKE_REST)
        out += TimedCue(rest + REST_PRAISE_AT_MS, restPraiseFor(length, c))
        if (c + 1 < length.cycles) out += TimedCue(rest + REST_UP_NEXT_AT_MS, Cue.UP_NEXT[exerciseForCycle(c + 1)])
        addCountdown(out, start + Timing.CYCLE_MS)
    }
    out += TimedCue(length.totalMs, Cue.COMPLETED)
    return out
}

/** The most recent motivation line (non-signal cue) of the current phase, for the on-screen banner. */
fun bannerCueAt(length: SessionLength, activeElapsedMs: Long): Cue? {
    val p = positionAt(length, activeElapsedMs)
    if (p.phase == Phase.FINISHED) return null
    val phaseStart = activeElapsedMs - p.phaseElapsedMs
    val c = p.cycleIndex
    val slots: List<Pair<Long, Cue?>> = if (p.phase == Phase.EXERCISE) listOf(
        SLOT_NAME_MS to nameLineFor(c),
        SLOT_MOTIVATION_MS to motivationFor(length, c),
        SLOT_FIFTEEN_MS to (if (c % 2 == 0) Cue.FIFTEEN_LEFT else Cue.HALF_EXERCISE),
        SLOT_TEN_MS to (if (c % 2 == 0) Cue.TEN_LEFT else Cue.LAST_TEN),
    ) else listOf(
        REST_PRAISE_AT_MS to restPraiseFor(length, c),
        REST_UP_NEXT_AT_MS to (if (c + 1 < length.cycles) Cue.UP_NEXT[exerciseForCycle(c + 1)] else null),
    )
    return slots.lastOrNull { (at, cue) -> cue != null && phaseStart + at <= activeElapsedMs }?.second
}

private fun addCountdown(out: MutableList<TimedCue>, phaseEnd: Long) {
    Cue.COUNTDOWN.forEachIndexed { i, cue ->
        out += TimedCue(phaseEnd - (Timing.COUNTDOWN_FROM - i) * 1000L, cue)
    }
}
