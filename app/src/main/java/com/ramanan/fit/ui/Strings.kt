package com.ramanan.fit.ui

import com.ramanan.fit.engine.PauseReason
import com.ramanan.fit.engine.SessionLength

/** Every user-visible string (English). Voice-line text lives in engine Cue. */
object S {
    const val tagline = "Cardio HIIT at home · works fully offline"
    fun hello(name: String) = "Hi, $name 👋"
    const val changeName = "Change name"
    // name screen
    const val nameTitle = "Hi! I'm your coach."
    const val nameQuestion = "What's your name?"
    const val nameHint = "Your name"
    const val nameWhy = "I'll cheer you on by name while you work out."
    const val nameContinue = "Let's go!"
    const val cancel = "Cancel"
    const val nameVoiceNote = "The coach will cheer without your name (no phone voice available or switched off)."
    fun namePrerecorded(name: String) = "✓ \"${name.trim().split(" ").first()}\" is spoken in the coach's natural voice."
    const val namePhoneVoice = "Your name is spoken by the phone's voice (sounds more robotic). Turn it off below to hear name-free lines instead."
    const val phoneVoiceSwitch = "Use phone voice for my name"

    const val sessionLength = "Session length"
    const val defaultTag = "  (default)"
    const val workout = "Workout"; const val rest = "Rest"; const val rounds = "Rounds"; const val intervals = "Intervals"
    const val voiceCoach = "Voice coach"
    const val femaleVoice = "Female voice coach"
    const val muted = "Muted"
    fun volume(p: Int) = "Volume $p%"
    const val mute = "Mute"
    const val testVoice = "Test voice"
    const val missingClips = "Missing voice clips: "
    const val exercisesTitle = "The 10 exercises (tap for form tips)"
    fun guideStyle(coach: Boolean) = if (coach) "Guide style: Coach" else "Guide style: Stick figure"
    const val howtoBefore = "Show how-to videos before each workout"
    const val recent = "Recent workouts"
    const val disclaimer = "General fitness guide only — results vary person to person. Stop if you feel pain, " +
        "dizziness or chest discomfort, and check with a doctor if you have a health condition."
    fun start(d: String) = "START  ·  $d"
    fun dur(len: SessionLength) = len.label
    fun structure(len: SessionLength) =
        "Warm-up + ${len.fullRounds} rounds" + if (len.lastRoundSize < 8) " + ${len.lastRoundSize}" else ""
    fun info(len: SessionLength) =
        "Cardio HIIT: a 2-exercise warm-up once, then rounds of 8 (2 beginner + 6 advanced cardio) " +
            "repeat until ${len.label} is up. Your coach talks you through every second. Stretch 3–5 min after.\n\n" +
            "For best progress: 4–5 days a week + healthy food. Stamina usually improves within 4 weeks; " +
            "weight change depends mostly on diet and varies person to person."
    fun history(m: Int, e: Int, r: Int) = "$m min · $e exercises · $r full rounds"
    const val easier = "No-jump / easier: "
    const val watchHowto = "▶  Watch how-to video"

    // workout
    const val exercise = "EXERCISE"; const val restLabel = "REST"
    fun warmupStatus(n: Int, g: Int) = "Warm-up  ·  $n/$g"
    fun roundStatus(r: Int, rr: Int, n: Int, g: Int) = "Round $r/$rr  ·  Exercise $n/$g"
    fun left(t: String) = "$t left"
    const val nextUpPreview = "NEXT UP — preview"
    const val lastRest = "LAST REST"
    fun next(n: String) = "Next: $n"
    const val lastExercise = "Last exercise!"
    const val finalRest = "Final rest — breathe slowly. Almost done!"
    fun noJump(t: String) = "No-jump: $t"
    const val pause = "PAUSE"; const val resume = "RESUME"; const val stop = "STOP"
    const val stopTitle = "Stop workout?"
    const val stopText = "This ends the session and resets the timer."
    const val keepGoing = "Keep going"
    fun paused(r: PauseReason?) = when (r) {
        PauseReason.BACKGROUND -> "Paused — the app left the screen. Tap Resume to continue."
        PauseReason.AUDIO_INTERRUPTION -> "Paused — audio interruption (e.g. a call). Tap Resume to continue."
        else -> "Paused"
    }

    // done
    fun greatJob(name: String) = if (name.isBlank()) "Great job!" else "Great job, $name!"
    const val completed = "Workout completed"
    const val session = "Session"; const val exercisesDone = "Exercises completed"; const val fullRounds = "Full cardio rounds"
    const val activeTime = "Active time"
    const val coolDown = "Cool down: 3–5 min easy walking and gentle stretches. Drink water!"
    const val backHome = "Back to home"
    const val coachTitle = "Your Coach"
    const val coachProud = "Your coach is proud of you!"
    fun coachQuote(name: String, day: Int): String {
        val n = if (name.isBlank()) "" else ", $name"
        val q = listOf(
            "Ready to sweat$n? Let's crush today's cardio together!",
            "Small steps every day = big results. Let's go$n!",
            "Your only competition is yesterday's you$n.",
            "30 seconds at a time — you can do anything$n!",
            "Show up today$n. Future you will say thanks!",
        )
        return q[day % q.size]
    }

    // how-to
    const val howto = "HOW TO"; const val close = "Close"; const val replay = "↻  Replay"
    const val back = "◀ Back"; const val home = "Home"; const val nextBtn = "Next ▶"
    const val startWorkout = "START WORKOUT"; const val skipVideos = "Skip videos & start workout"
    const val gotIt = "Got it"; const val videoMissing = "How-to video missing"
}
