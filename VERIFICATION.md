# Verification results

Date: 13 Oct 2026. Honest status of this version (English only, name personalisation,
continuous motivation, 2 warm-up + 2 beginner + 6 advanced cardio).

## Latest changes
- Name heard ~4× per exercise + rest; 20 named lines prerecorded for "Ramanan" with the
  owner-chosen pronunciation (Option 1, "RAA-muh-nun").
- Bug fixed: a name with initials ("Ramanan R") fell back to the robotic phone voice; any word
  of the name now matches the prerecorded clips. New switch to turn the phone voice off.
- Build fix: removed a stale `Level.HARDER` line (the only compile error in the last CI run).

## Build status
- An earlier version of the app **built successfully on GitHub Actions** (13 Oct 2026).
- **This version has not been built yet** — the workspace it was written in blocks Google's
  Android repositories, so the Android code is syntax-checked only. Build it with the included
  workflow; compile errors, if any, should be small — send the log.
- Nothing has been run on a phone yet (UI, audio, TTS for names, video player).

## Timer engine — compiled and run (Kotlin 2.0.21): 14 / 14 passed

| Test | Covers |
|---|---|
| sessionLengthsAreExact | 15/30/45/60 min = 18/36/54/72 intervals; rounds of 8; final round 8/2/4/6 |
| eachDurationFinishesAtExactActiveTimeWithJitteryTicks | ends at exactly the selected active time with random 5–140 ms ticks |
| countdownDisplayAndTransitions | 30…1 / 20…1, instant switch at 0 |
| warmupOnceThenCardioRoundsRepeat | warm-up once, then exercises 3–10 in order; numbering; short final round |
| cueSequenceIsCompleteOrderedAndSpokenOnce | every cue once, in order, ≤ 50 ms late; a name line in every exercise; 15 s and 10 s lines every exercise; "Up next" in every rest but the last; milestones once each |
| motivationSlotsAreDenseAndNeverOverlap | name line at display 28, motivation 22, 15, 10; praise 17; up-next 11 and names the right exercise; no two cues at the same moment; no gap > 7 s during an exercise |
| namedCuesFillInTheName | "{name}" filled in and trimmed; name-free fallback when blank |
| bannerShowsLatestMotivationOfThePhase | on-screen motivation text follows the voice |
| countdownCuesLineUpWithDisplayedNumber | "Five" when the display shows 5 … "One" at 1 |
| pauseResumePreservesExactRemainingTime | 1 h pause changes nothing; exact resume |
| pausedCueIsNotReplayedAfterResume | a cut-off line isn't repeated |
| repeatedStartDoesNotRestartOrDuplicate | extra Start presses ignored |
| staleCuesAreDroppedNotQueued | a stalled ticker never queues late lines |
| restoreFromSnapshotIsPausedAtSamePoint | process-death restore |

One test (stale cues) was updated because the new "Up next" line legitimately falls at 39 s;
the engine logic itself was not changed for it.

## Assets — `tools/check_assets.py`: ALL ASSETS OK
- 10 animations; jump lunges added and inspected frame by frame.
- 10 how-to videos (H.264 + AAC), frames inspected.
- 53 voice clips in `voice.pack`, every offset decodes; countdown 0.48–0.59 s; every
  in-workout line ≤ 2.8 s (slots are ≥ 5 s apart); every named line also prerecorded for
  "Ramanan". Not listened to by a person — please play `docs/voice_preview.mp3`.

## Please check on the phone
1. First launch asks your name; Home greets you; *Test voice* says your name.
2. In a workout the coach talks at ~2, 8, 15, 20 s and counts down 5…1; the banner matches.
3. Try a different name: it should be spoken by the phone voice (or a note on the home
   screen explains it can't be).
4. Pause on "Three" → resume → "Two" on time. Home button → paused banner. Airplane mode.

## Known limitations
- Names other than "Ramanan" use the phone's offline TTS voice, which sounds different from
  the coach. If the phone has no offline English voice, the coach cheers without the name.
- No background running: the workout pauses when the app leaves the screen.
- The coach is a 2D cartoon; mountain climbers use a slightly inclined plank in side view.
- Coach photo licence unknown — keep the repo private.
