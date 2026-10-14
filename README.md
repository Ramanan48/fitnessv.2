# Ramanan Fit

Offline Android cardio HIIT app with a personal coach. Asks your name on first launch, then
talks you through every workout — by name — with a female voice, countdowns and constant
motivation. 10 exercises (2 warm-up, 2 beginner, 6 advanced cardio), 30 s work / 20 s rest,
15 / 30 / 45 / 60-minute sessions, animated coach guides and how-to videos.
No login, ads, internet or permissions. Full spec: `MASTER_PROMPT.md`.

## Build the APK (GitHub Actions — no Android Studio needed)
1. Create a new **private** GitHub repository (no README).
2. Extract the zip. Open the `RamananFit` folder, select everything inside (Ctrl+A) and drag it
   onto the repo's *uploading an existing file* page (the project is < 75 files, one upload).
   → **Commit changes**.
3. The hidden `.github` folder is usually skipped by the website. Create it: **Add file → Create
   new file**, name `.github/workflows/build-apk.yml`, paste the contents of that file from
   the zip → **Commit changes**.
4. **Actions** tab → wait for the green ✅ (≈ 8–12 min first time) → open the run →
   **Artifacts → RamananFit-apk**.
   The workflow finds the project even inside a sub-folder and installs Gradle itself.

Android Studio alternative: open the folder, let Gradle sync, *Build → Build APK(s)*.

## Install
Copy `app-release.apk` to the phone → tap → allow *Install unknown apps* → Install
(Play Protect: *More details → Install anyway*). Android 8.0+.

## Using it
- **First launch:** the coach asks your name (change it any time: Home → *Change name*).
- Pick 15 / 30 (default) / 45 min / 1 hour → **START**. How-to videos play first
  (Next / Back / *Skip videos & start workout*); turn that off on the home screen.
- **PAUSE / RESUME** freezes and continues countdown, animation and voice exactly.
  **STOP** (confirm) ends the session. Leaving the app auto-pauses with a banner.

### How a session runs
- Each interval = 30 s exercise + 20 s rest. Warm-up (March, Step jacks) runs **once**, then
  rounds of 8 repeat: High knees, Butt kicks (beginner) + Jumping jacks, Skaters, Jump lunges,
  Mountain climbers, Squat jumps, Burpees (advanced cardio). A shorter final round makes the
  session end exactly on time: 15 min = warm-up + 2 rounds · 30 = warm-up + 4 rounds + 2 ·
  45 = warm-up + 6 rounds + 4 · 60 = warm-up + 8 rounds + 6.
- **The coach talks the whole time.** Exercise: 0 s start · 2 s line with your name ·
  8 s motivation (or Halfway / Last round / Final exercise) · 15 s "fifteen seconds" ·
  20 s "ten seconds" · 25–29 s Five…One. Rest: "Take a rest" · 3 s praise ·
  9 s "Up next: …" · 15–19 s Five…One. Every line also appears on screen.
- **Your name** is heard ~4 times every exercise + rest (2 s, 15 s, 20 s and in the rest praise).
  "Ramanan" (any spelling with initials, e.g. "Ramanan R") is prerecorded in the coach's natural
  voice, pronounced "RAA-muh-nun". Other names use the phone's offline voice (more robotic —
  can be switched off on the home screen) or name-free lines.
  To prerecord another name: `python3 tools/make_voice.py <kokoro_dir> "Ramanan=ɹˈɑːmənən" "Priya"`.
- Timer: monotonic clock, no drift, paused time never counts; a late voice line is skipped,
  never queued.

## Project layout
```
app/src/main/java/com/ramanan/fit/
  engine/   WorkoutPlan.kt (schedule, voice lines, motivation slots), WorkoutSession.kt
  audio/    VoicePlayer.kt (voice.pack clips + offline TTS for names)
  anim/     PoseLibrary.kt, StickFigure.kt, Avatar.kt (coach)
  data/     Exercises.kt, Prefs.kt
  ui/       NameScreen, HomeScreen, HowToScreen, WorkoutScreen (+Done), Strings, ViewModel, Theme
app/src/main/assets/voice.pack + voice.json   all 53 voice clips in one file
app/src/main/assets/poses/poses.json          10 animations
app/src/main/res/raw/howto_*.mp4              10 how-to videos
app/src/main/res/drawable-nodpi/coach_photo.jpg  coach photo
app/src/test/                                 engine unit tests (14)
tools/  make_poses.py, avatar.py, make_voice.py, pack_voice.py, make_howto_videos.py, check_assets.py
docs/   sample screens, coach sheet, voice preview
```

## Regenerating assets (optional, needs Python + ffmpeg + Kokoro model files)
```
python3 tools/make_poses.py app/src/main/assets/poses/poses.json
python3 tools/make_voice.py <kokoro_dir> Ramanan          # add more names to prerecord them
python3 tools/make_howto_videos.py <kokoro_dir> app/src/main/res/raw
python3 tools/check_assets.py
```
