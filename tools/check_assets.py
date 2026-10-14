#!/usr/bin/env python3
"""Cross-checks that every exercise, voice cue (in assets/voice.pack) and how-to video referenced
in the Kotlin source is bundled."""
import json, re, os, sys, subprocess

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
src = os.path.join(root, "app/src/main/java/com/ramanan/fit")
assets = os.path.join(root, "app/src/main/assets")
ok = True


def probe(f, what):
    return subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", what, "-of", "csv=p=0", f]).decode().split()


ex_src = open(f"{src}/data/Exercises.kt").read()
ids = re.findall(r'Exercise\(\s*"([a-z_]+)", Level\.', ex_src)
poses = json.load(open(f"{assets}/poses/poses.json"))["exercises"]
print(f"exercises in code: {len(ids)}; animations bundled: {len(poses)}")
for i in ids:
    ex = poses.get(i)
    if not ex: print("MISSING animation:", i); ok = False; continue
    fr = ex["frames"]
    if len(fr) < 24 or any(len(f["p"]) != 36 for f in fr): print("BAD frames:", i); ok = False
    print(f"  {i:18s} view={ex['view']:5s} loop={ex['loopMs']}ms frames={len(fr)}")

vids = re.findall(r"R\.raw\.(howto_[a-z_]+)", ex_src)
print(f"how-to videos referenced: {len(vids)}")
for v in vids:
    f = os.path.join(root, f"app/src/main/res/raw/{v}.mp4")
    if not os.path.exists(f): print("MISSING video:", v); ok = False; continue
    d = float(probe(f, "format=duration")[0]); st = probe(f, "stream=codec_name")
    print(f"  {v:30s} {d:5.1f}s {os.path.getsize(f)//1024:5d} KB  {'+'.join(st)}")
    if st != ["h264", "aac"]: print("  unexpected codecs"); ok = False
if len(vids) != len(ids): print("video count mismatch"); ok = False

plan = open(f"{src}/engine/WorkoutPlan.kt").read()
cues = re.findall(r'\b[A-Z_]+\("([a-z_]+)", "((?:[^"\\]|\\.)*)"', plan)
print(f"voice cues in code: {len(cues)}")
index = json.load(open(f"{assets}/voice.json"))
pack = open(f"{assets}/voice.pack", "rb").read()
for key, (off, ln, ms) in index.items():
    if off + ln > len(pack) or pack[off:off + 4] != b"OggS": print(f"  {key}: bad offset in voice.pack"); ok = False
for asset, text in cues:
    e = index.get(asset)
    if not e: print("MISSING audio:", asset); ok = False; continue
    ms = e[2]
    if asset in ("five", "four", "three", "two", "one") and ms >= 950: print(f"  {asset} countdown too long: {ms} ms"); ok = False
    # every in-workout line must finish before the next slot (≥ 5 s apart; hello/completed are outside the timer)
    if asset not in ("hello", "completed") and ms > 4_800: print(f"  {asset} too long for its slot: {ms} ms"); ok = False
named = [a for a, t in cues if "{name}" in t]
for a in named:
    if f"{a}@ramanan" not in index: print("MISSING prerecorded name clip:", a); ok = False
print(f"  {len(index)} clips in voice.pack ({len(named)} named lines, each also prerecorded for 'Ramanan')")
print("ALL ASSETS OK" if ok else "PROBLEMS FOUND"); sys.exit(0 if ok else 1)
