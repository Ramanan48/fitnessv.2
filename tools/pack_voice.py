#!/usr/bin/env python3
"""
Packs every voice clip into ONE asset file so the project stays small (GitHub web upload
allows < 100 files at a time).

  input : <clips_dir>/<key>.ogg             (made by tools/make_voice.py; key = cue asset,
                                             or "<asset>@<name>" for a prerecorded name)
  output: app/src/main/assets/voice.pack    (all .ogg files back-to-back, stored uncompressed)
          app/src/main/assets/voice.json    {"five": [offset, length, durationMs], ...}

The app opens voice.pack once and loads each clip with SoundPool.load(fd, offset, length).

Usage: python3 tools/pack_voice.py <clips_dir>
"""
import json, os, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
clips = sys.argv[1]
out_dir = os.path.join(ROOT, "app/src/main/assets")
index, blob = {}, bytearray()
for f in sorted(os.listdir(clips)):
    if not f.endswith(".ogg"): continue
    path = os.path.join(clips, f)
    data = open(path, "rb").read()
    dur = float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                                         "-of", "csv=p=0", path]))
    index[f[:-4]] = [len(blob), len(data), int(round(dur * 1000))]
    blob += data
open(os.path.join(out_dir, "voice.pack"), "wb").write(blob)
json.dump(index, open(os.path.join(out_dir, "voice.json"), "w"), separators=(",", ":"), sort_keys=True)
print(f"packed {len(index)} clips, {len(blob) // 1024} KB")
