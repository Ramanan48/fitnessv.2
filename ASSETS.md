# Bundled assets — origins and licensing

Everything ships inside the APK. Nothing is streamed or downloaded at runtime.

## Voice — `app/src/main/assets/voice.pack` + `voice.json`

67 English clips packed back-to-back in one uncompressed file (≈ 1.1 MB); `voice.json` maps
each key to [offset, length, durationMs]; the app loads them with SoundPool at byte offsets.
Made by `tools/make_voice.py` (text read from the `Cue` enum) + `tools/pack_voice.py`.

- 47 lines: start, Five…One, take a rest, next exercise, completion, greeting, 8 name lines,
  12 motivation/milestone lines, 4 "seconds left" lines, 3 rest-praise lines, 10 "Up next" lines.
- 20 of them say the user's name; each is also prerecorded as "<line>@ramanan" with the name
  **Ramanan** pronounced "RAA-muh-nun" (IPA ɹˈɑːmənən, chosen by the owner) — so the name is
  heard ~4 times per exercise + rest in the coach's natural voice.
- Countdown clips 0.48–0.59 s; every in-workout line ≤ 3.0 s (slots are ≥ 5 s apart).
- **Voice:** Kokoro-82M TTS (Apache-2.0) via `kokoro-onnx` (MIT), 70 % `af_heart` + 30 %
  `af_nicole` (warm adult female). Not a human recording; not yet listened to by a person —
  play `docs/voice_preview.mp3`.
- **Other names:** spoken at runtime by the phone's built-in Android text-to-speech, using an
  on-device (offline) English voice only — never a network voice. If none is installed, the
  name-free clip plays instead (also switchable off on the home screen). This voice sounds
  more robotic than the coach's prerecorded voice. Names are matched on any word, ignoring
  case/initials, so "Ramanan R" uses the prerecorded clips.

## How-to videos — `app/src/main/res/raw/howto_<id>.mp4`

10 videos, 720×1280 portrait, H.264 Main + AAC mono, 30 fps. Made by
`tools/make_howto_videos.py` from the coach renderer, animation data and `Exercises.kt`
text, narrated with the Kokoro voice. Structure: title → each step in slow motion → full
speed → form tips → no-jump option → "Ready? Let's go!".

## Coach (animation guide style)

Original 2D cartoon coach drawn entirely from shapes in code: light-brown high ponytail,
teal sports bra, teal leggings with a yellow side stripe, white sneakers, dark wristband.
`app/.../anim/Avatar.kt` (app) and `tools/avatar.py` (same renderer, used for previews and
videos). Not a realistic AI video. Preview: `docs/avatar_exercises.png`.

## Coach photo — `app/src/main/res/drawable-nodpi/coach_photo.jpg`

Shown on the name screen, the Home "Your Coach" card and the Completed screen. An AI-generated
image supplied by the app owner; creator and licence unknown. Fine for a personal sideloaded
app — **keep the repository private** and do not publish the app with it unless you have the
rights. Replace the file (same name) to change it.

## Exercise animations — `app/src/main/assets/poses/poses.json`

Original joint-angle keyframes (`tools/make_poses.py`), 48–96 frames per loop.

| # | Exercise | Level | No-jump option | View |
|---|---|---|---|---|
| 1 | March with arm swings | Warm-up | — | side |
| 2 | Step jacks | Warm-up | — | front |
| 3 | High knees | Beginner | fast march | side |
| 4 | Butt kicks | Beginner | walk-pace heel curls | side |
| 5 | Jumping jacks | Advanced cardio | step jacks | front |
| 6 | Skaters | Advanced cardio | side step + back tap | front |
| 7 | Jump lunges | Advanced cardio | alternating reverse lunges | side |
| 8 | Mountain climbers | Advanced cardio | hands on chair/bed | side |
| 9 | Squat jumps | Advanced cardio | squat + rise to toes | side |
| 10 | Burpees | Advanced cardio | step back / in / reach | side |

## Code dependencies (build time)
AndroidX Core, Activity Compose, Lifecycle, Jetpack Compose (BOM 2024.12.01), Material 3 —
Apache 2.0. JUnit 4.13.2 (tests only) — EPL 1.0.
