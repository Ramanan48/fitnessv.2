# Ramanan Fit — Master Prompt

Use this prompt to rebuild, extend or hand over the app. It describes every feature of the
current version. Build a working app, not a mockup.

## 1. Goal
A complete **offline Android cardio HIIT app** "Ramanan Fit" (English only) with a personal
coach that asks the user's name and motivates them by name, continuously, through every
30-second exercise. Deliver APK (debug + release signed with the debug key), Gradle source,
build/install steps, asset/licence list, verification report. Everything works in airplane
mode; no login, ads, analytics, network calls or permissions. No guaranteed-result claims.
Keep the project **under ~75 files** (GitHub web upload limit is 100 per upload).

## 2. Tech
Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01), Material 3, AGP 8.7.3, Gradle 8.11.1,
compile/target SDK 35, min SDK 26, JVM 17, package `com.ramanan.fit`. Pure-Kotlin timer
engine with JUnit tests; one Activity + AndroidViewModel (SavedStateHandle).
CI `.github/workflows/build-apk.yml` (ubuntu-24.04): checkout@v5, setup-java@v5 (17),
setup-gradle@v4 with `gradle-version: 8.11.1` (works without gradlew), a step that finds the
folder containing `settings.gradle.kts` (works if files were uploaded inside a sub-folder),
`gradle testDebugUnitTest`, `gradle assembleDebug assembleRelease`, artifact **RamananFit-apk**.
No setup-android step (the runner has the SDK; setup-android@v3 fails on 'tools').

## 3. Workout
30 s exercise + 20 s rest. **Warm-up once** (exercises 1–2), then **rounds of 8** (3–10) repeat;
a shorter final round ends the session exactly on time; final rest included; completion after it.
15 min = warm-up + 2 rounds · 30 (default) = warm-up + 4 + 2 · 45 = + 6 + 4 · 60 = + 8 + 6.

Exercises (each with 3 steps, 2 form tips, no-jump option, get-ready line, animation, how-to video):
1 March with Arm Swings (warm-up) · 2 Step Jacks (warm-up) · 3 High Knees (beginner) ·
4 Butt Kicks (beginner) · 5 Jumping Jacks · 6 Skaters · 7 Jump Lunges · 8 Mountain Climbers ·
9 Squat Jumps · 10 Burpees (5–10 = advanced cardio).

## 4. Timer engine (unit-tested)
Monotonic clock (elapsedRealtime), recomputed every tick → no drift; paused time never counts.
Display = ceil(remaining): 30→1 / 20→1, instant switch at 0. Position: phase, exercise,
warm-up flag, round, number in group, group size, next exercise, time left, progress,
completed exercises/rounds. Start only from READY; one ~50 ms ticker; process-death restore
as PAUSED; each tick plays at most the newest cue and only if < 0.6 s late.

## 5. Coach voice (female, offline) — talks through every second, says the name often
Exercise: 0 s "Let's start!"/"Next exercise." · **2 s name line** (rotation of 7: "Come on {name},
you've got this!", "{name}, you are stronger than you think!", "That's it {name}, keep that
energy!", "Keep going {name}, I'm proud of you!", "Push it {name}, don't give up!", "I believe in
you, {name}!", "Looking great, {name}!"; warm-up: "Let's warm up, {name}!") · **8 s** motivation
rotation of 8 lines, or milestone ("Nice and easy. Warm up your body." in warm-up,
"Halfway through the workout, {name}!" at the session midpoint, "Last round, {name}!" on the
first exercise of the final round, "Final exercise, {name}! Finish strong!") ·
**15 s** "Fifteen seconds to go, {name}! Keep moving!" / "Halfway there, {name}! Stay strong!" ·
**20 s** "Ten more seconds, {name}! Give it everything!" / "Last ten seconds, {name}! Push!" ·
25–29 s Five…One. Rest: "Take a rest." · 3 s praise with name (Breathe deep / Great work /
Be proud of yourself) · 9 s "Up next: <exercise>. Get ready!" · 15–19 s Five…One.
End: "Great job, {name}! Workout completed." → the name is heard ~4× per exercise + rest.
Every line also shows on screen. Only the **first word** of the name is spoken.

Clips: Kokoro-82M (70 % af_heart + 30 % af_nicole). Every line has a name-free clip; every
named line (20) is also prerecorded for **Ramanan** with the owner-chosen pronunciation
"RAA-muh-nun" (IPA ɹˈɑːmənən — Kokoro's default "ram-AH-nun" is wrong). All packed into
`assets/voice.pack` + `voice.json` (`tools/make_voice.py "Ramanan=ɹˈɑːmənən"`, `tools/pack_voice.py`).
Name matching: any word of the typed name, case/initials ignored ("Ramanan R" → prerecorded).
Other names: Android TextToSpeech with an on-device voice only (prefer en-IN/en-US female) —
sounds robotic, so the home screen shows which voice is used and has a switch
"Use phone voice for my name" (off → name-free clips). One voice at a time; stop on pause;
audio focus (duck) while speaking; a call pauses the workout.
Online TTS (e.g. Google Cloud) was considered and rejected: needs billing + API key + internet.

## 6. Visuals
Original 2D cartoon coach (light-brown high ponytail, teal sports bra, teal leggings with
yellow stripe, white sneakers, wristband) drawn from shapes (`Avatar.kt` ≡ `tools/avatar.py`)
over joint keyframes (`tools/make_poses.py` → `poses.json`); stick-figure option. Animation
clock = phase time (freezes on pause). 10 portrait how-to MP4s (720×1280 H.264+AAC) with
slow-motion steps, captions and narration (`tools/make_howto_videos.py`).
Coach photo (`coach_photo.jpg`, owner-supplied) on name screen, Home card, Completed screen.

## 7. Screens
1 **Name** (first launch; also "Change name"): coach photo, "Hi! I'm your coach. What's your
name?", text field, "Let's go!" → saves name, greets by name. 2 **Home**: "Hi, {name} 👋",
Change name, coach card with daily quote using the name, duration cards, info card, voice card
(volume, mute, Test voice = named line, which voice says the name, phone-voice switch), guide-style and how-to
switches, 10 exercise rows (level tag, live animation, steps/tips/no-jump, Watch how-to),
recent history, disclaimer, START. 3 **How-to** walkthrough (Back/Next/START WORKOUT/Skip).
4 **Workout** (green) / **Rest** (blue): status, time left, progress, coach animation (next
exercise preview in rest), motivation banner (current line with name), huge countdown,
level, name, tip, no-jump, next, PAUSE/RESUME + STOP. 5 **Completed**: photo, "Your coach is
proud of you!", "Great job, {name}!", stats, cool-down, Back to home.

## 8. Storage & lifecycle
SharedPreferences: name, session length, volume, mute, guide style, how-to toggle, last 50
workouts. Screen on only while running; rotation doesn't pause; leaving the app auto-pauses
with a banner; Back = pause.

## 9. Verification
Engine tests: exact durations; warm-up once then rounds of 8; numbering; 30→1/20→1; cue order
and timing; name line at display 28, motivation 22, 15, 10, praise 17, up-next 11 and matches
the next exercise; no two cues at once; no gap > 7 s during exercise; name fill-in and
name-free fallback; banner text; pause/resume exactness; no replay; duplicate Start ignored;
stale cues dropped; restore. `tools/check_assets.py`: animations, videos, every cue clip and
every prerecorded name clip present, lengths fit their slots. Say plainly what was not built,
heard or run on a phone.
