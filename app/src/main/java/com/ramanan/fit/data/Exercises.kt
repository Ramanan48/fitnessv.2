package com.ramanan.fit.data

import androidx.annotation.RawRes
import com.ramanan.fit.R
import com.ramanan.fit.engine.Cue

enum class Level(val label: String) {
    WARMUP("Warm-up"), BEGINNER("Beginner"), ADVANCED("Advanced cardio")
}

/** Text for one exercise. */
data class ExText(
    val name: String,
    val steps: List<String>,      // start → movement → return
    val tips: List<String>,
    val easier: String?,          // no-jump / easier option
    val prep: String,             // shown during the preceding rest
)

/**
 * The 10 exercises, in session order. Index 0-1 = warm-up (once per session),
 * 2-9 = one cardio round (repeats): 2 beginner + 6 advanced cardio.
 */
data class Exercise(
    val id: String,               // matches assets/poses/poses.json
    val level: Level,
    val en: ExText,
    @RawRes val video: Int,
)

object Exercises {
    val all: List<Exercise> = listOf(
        Exercise(
            "march_arms", Level.WARMUP,
            en = ExText(
                "March with Arm Swings",
                listOf("Stand tall, feet hip-width.", "March on the spot, knees to mid height, arms swinging big.", "Keep a steady, comfortable rhythm."),
                listOf("Easy pace — this is your warm-up.", "Relaxed shoulders, breathe steadily."),
                null, "Warm-up first: march and swing your arms.",
            ),
            video = R.raw.howto_march_arms,
        ),
        Exercise(
            "step_jacks", Level.WARMUP,
            en = ExText(
                "Step Jacks",
                listOf("Stand tall, arms by your sides.", "Step one foot out as both arms sweep overhead.", "Step back in, arms down. Alternate sides."),
                listOf("Soft knees — don't lock them.", "Reach all the way up to warm your shoulders."),
                null, "Arms by your sides, get ready.",
            ),
            video = R.raw.howto_step_jacks,
        ),
        Exercise(
            "high_knees", Level.BEGINNER,
            en = ExText(
                "High Knees",
                listOf("Stand tall, arms bent.", "Drive one knee up to hip height, then the other — fast.", "Stay on the balls of your feet, pump the arms."),
                listOf("Chest up — don't hunch forward.", "Land softly, quiet feet."),
                "Fast march: keep one foot on the floor.", "Feet hip-width, arms bent and ready.",
            ),
            video = R.raw.howto_high_knees,
        ),
        Exercise(
            "butt_kicks", Level.BEGINNER,
            en = ExText(
                "Butt Kicks",
                listOf("Stand tall, slight forward lean.", "Kick one heel up towards the hip, then the other — quick rhythm.", "Arms pump like running."),
                listOf("Knees point down — don't swing the thigh forward.", "Keep your core lightly tight."),
                "Walk-pace heel curls, no hop.", "Lean slightly forward and get ready.",
            ),
            video = R.raw.howto_butt_kicks,
        ),
        Exercise(
            "jumping_jacks", Level.ADVANCED,
            en = ExText(
                "Jumping Jacks",
                listOf("Feet together, arms down.", "Jump feet wide as arms go overhead.", "Jump back together, arms down. Keep going."),
                listOf("Land on the front of the foot, soft knees.", "Keep a steady rhythm — good form beats speed."),
                "Step jacks — step to the side instead of jumping.", "Feet together, arms down. Ready to jump!",
            ),
            video = R.raw.howto_jumping_jacks,
        ),
        Exercise(
            "skaters", Level.ADVANCED,
            en = ExText(
                "Skaters",
                listOf("Stand on one leg, slight squat.", "Hop sideways onto the other leg; back foot sweeps behind.", "Swing the arms across, then hop back to the first side."),
                listOf("Land softly and bend the standing knee.", "Start with small hops, then go wider."),
                "Side step + tap the back foot behind, no hop.", "Small squat on one leg, ready to hop sideways.",
            ),
            video = R.raw.howto_skaters,
        ),
        Exercise(
            "jump_lunges", Level.ADVANCED,
            en = ExText(
                "Jump Lunges",
                listOf("Lunge: front knee bent at 90°, back knee near the floor.", "Jump up and switch legs in the air.", "Land softly in a lunge on the other side. Keep switching."),
                listOf("Front knee stays over the ankle, not past the toes.", "Chest up and core tight as you land."),
                "Alternating reverse lunges — no jump.", "Step into a lunge, chest up. Ready!",
            ),
            video = R.raw.howto_jump_lunges,
        ),
        Exercise(
            "mountain_climbers", Level.ADVANCED,
            en = ExText(
                "Mountain Climbers",
                listOf("High plank: hands under shoulders, body straight.", "Drive one knee towards the chest.", "Switch legs quickly, like running on the floor."),
                listOf("Keep your hips low — don't lift them up.", "Hands firm, shoulders over wrists."),
                "Hands on a chair or bed, slow alternating steps.", "Get into a plank on the floor.",
            ),
            video = R.raw.howto_mountain_climbers,
        ),
        Exercise(
            "squat_jumps", Level.ADVANCED,
            en = ExText(
                "Squat Jumps",
                listOf("Feet shoulder-width, squat down with arms back.", "Jump up explosively, arms swinging overhead.", "Land softly and go straight into the next squat."),
                listOf("Land quietly on the balls of your feet, soft knees.", "Knees follow your toes — don't let them cave in."),
                "Squat and rise onto your toes — no jump.", "Feet shoulder-width, ready to squat!",
            ),
            video = R.raw.howto_squat_jumps,
        ),
        Exercise(
            "burpees", Level.ADVANCED,
            en = ExText(
                "Burpees",
                listOf("Squat and place both hands on the floor.", "Jump feet back to a plank, then jump them back in.", "Stand and jump up, arms overhead. Repeat."),
                listOf("Keep your back straight in the plank — don't let the hips sag.", "Go at your own pace, steady and strong."),
                "Step back, step in, stand and reach — no jumps.", "Last one — Burpees! Full energy!",
            ),
            video = R.raw.howto_burpees,
        ),
    )

    init {
        require(all.size == 10)
        // the engine's "Up next" voice lines must follow the same order
        require(Cue.UP_NEXT.map { it.asset } == all.map { "up_${it.id}" })
    }
}
