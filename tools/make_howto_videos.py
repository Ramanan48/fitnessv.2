#!/usr/bin/env python3
"""
Renders one vertical how-to video (720x1280, H.264 + AAC) per exercise:
  - coach renderer (tools/avatar.py) + animation data (assets/poses/poses.json)
  - exercise text parsed from app/.../data/Exercises.kt (single source of truth)
  - offline female narration: Kokoro-82M (same voice as the coach)

Usage:
  python3 tools/make_howto_videos.py <kokoro_dir> <out_dir> [exercise_id ...]
Output: <out_dir>/howto_<id>.mp4  (lives in app/src/main/res/raw/)
"""
import json, math, os, re, subprocess, sys, tempfile
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import avatar  # noqa: E402

W, H, FPS, SR = 720, 1280, 30, 24000
BG = (14, 21, 19); PANEL = (20, 30, 27); TEXT = (242, 247, 244); DIM = (180, 196, 189)
LEVEL_COLORS = {"WARMUP": (255, 197, 107), "BEGINNER": (90, 169, 255), "ADVANCED": (61, 220, 132), "HARDER": (255, 122, 110)}
FB = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FR = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
TAMIL_FONT = os.environ.get("TAMIL_FONT", "NotoSansTamil.ttf")
LANG = "en"
_fcache = {}
def F(sz, b=False):
    key = (LANG, sz, b)
    if key not in _fcache:
        if LANG == "ta":
            f = ImageFont.truetype(TAMIL_FONT, sz, layout_engine=ImageFont.Layout.RAQM)
            try: f.set_variation_by_name("Bold" if b else "Regular")
            except Exception: pass
        else:
            f = ImageFont.truetype(FB if b else FR, sz)
        _fcache[key] = f
    return _fcache[key]

L = {
    "en": dict(levels={"WARMUP": "WARM-UP", "BEGINNER": "BEGINNER", "ADVANCED": "ADVANCED CARDIO", "HARDER": "HARDER"},
               howto="HOW TO", slow="SLOW MOTION 0.5×", step="STEP {n}", full="FULL SPEED", tip="FORM TIP",
               easier="NO-JUMP / EASIER", ready="READY?",
               full_cap="Now at full speed — keep this rhythm for 30 seconds.",
               full_say="Now at full speed. Keep this rhythm for thirty seconds.",
               intro_say="{name}. {level} exercise. Watch the movement first.",
               easier_say="Easier option: {t}", outro_cap="Let's go!  30 s work · 20 s rest", outro_say="Ready? Let's go!"),
    "ta": dict(levels={"WARMUP": "வார்ம்-அப்", "BEGINNER": "தொடக்கம்", "ADVANCED": "மேம்பட்டது", "HARDER": "கடினம்"},
               howto="எப்படி செய்வது", slow="மெதுவாக 0.5×", step="படி {n}", full="முழு வேகம்", tip="சரியான முறை",
               easier="குதிக்காமல் / எளிதாக", ready="தயாரா?",
               full_cap="இப்போது முழு வேகத்தில் — இதே வேகத்தில் 30 விநாடி தொடருங்கள்.",
               full_say="இப்போது முழு வேகத்தில். இதே வேகத்தில் முப்பது விநாடிகள் தொடருங்கள்.",
               intro_say="{name}. {level} பயிற்சி. முதலில் அசைவை பாருங்கள்.",
               easier_say="எளிய முறை: {t}", outro_cap="தொடங்கலாம்!  30 வி பயிற்சி · 20 வி ஓய்வு", outro_say="தயாரா? தொடங்கலாம்!"),
}


# ---------------- exercise text (parsed from Exercises.kt) ----------------
_TOK = re.compile(r'"((?:[^"\\]|\\.)*)"|listOf\(|\)|\bnull\b')


def _extext(src, start):
    """Parse ExText(...) starting at index of 'ExText(' → (name, steps, tips, easier, prep)."""
    i = src.index("(", start) + 1; vals = []; cur_list = None
    for m in _TOK.finditer(src, i):
        tok = m.group(0)
        if tok == "listOf(": cur_list = []
        elif tok == ")":
            if cur_list is not None: vals.append(cur_list); cur_list = None
            else: break
        elif tok == "null": vals.append(None)
        else:
            (cur_list if cur_list is not None else vals).append(m.group(1))
    name, steps, tips, easier, prep = vals[:5]
    return dict(name=name, steps=steps, tips=tips, easier=easier, prep=prep)


def parse_exercises():
    src = open(os.path.join(ROOT, "app/src/main/java/com/ramanan/fit/data/Exercises.kt")).read()
    out = []
    for m in re.finditer(r'Exercise\(\s*"([a-z_]+)", Level\.(\w+),', src):
        en_at = src.index("en = ExText(", m.end())
        out.append(dict(id=m.group(1), level=m.group(2), en=_extext(src, en_at)))
    return out


def english_part(t):
    """Narrate only the English part of a mixed English/Tanglish line."""
    for sep in (" — ", ": "):
        if sep in t: t = t.split(sep)[0]
    return t.strip().rstrip(".") + "."


def say_text(t):
    """What the narrator speaks for a caption line in the current language."""
    if LANG == "en": return english_part(t)
    return t.replace("—", ",").replace("  ", " ")


# ---------------- narration ----------------
class Voice:
    def __init__(self, kdir, idir, lang):
        self.lang = lang
        if lang == "en":
            from kokoro_onnx import Kokoro
            self.k = Kokoro(f"{kdir}/kokoro-v1.0.onnx", f"{kdir}/voices-v1.0.bin")
            self.style = 0.7 * self.k.get_voice_style("af_heart") + 0.3 * self.k.get_voice_style("af_nicole")
        else:
            cwd = os.getcwd(); os.chdir(idir)
            from TTS.utils.synthesizer import Synthesizer
            self.syn = Synthesizer(tts_checkpoint="ta/fastpitch/best_model.pth", tts_config_path="ta/fastpitch/config.json",
                                   tts_speakers_file="ta/fastpitch/speakers.pth", vocoder_checkpoint="ta/hifigan/best_model.pth",
                                   vocoder_config="ta/hifigan/config.json", use_cuda=False)
            os.chdir(cwd)

    def say(self, text):
        if self.lang == "en":
            a, sr = self.k.create(text, voice=self.style, speed=0.95, lang="en-us")
            a = np.asarray(a, dtype=np.float32)
        else:
            import librosa
            a = np.asarray(self.syn.tts(text, speaker_name="female"), dtype=np.float32)
            a = librosa.resample(a, orig_sr=self.syn.output_sample_rate, target_sr=SR)
        nz = np.where(np.abs(a) > 0.01)[0]          # trim silence
        if len(nz): a = a[max(0, nz[0] - 600): nz[-1] + 1200]
        peak = np.max(np.abs(a)) or 1.0
        return a * (0.85 / peak)


# Which part of the animation loop each step shows (fractions of the loop).
# (a, a) = hold that pose still. Exercises not listed replay the whole loop from the start.
STEP_WINDOWS = {
    "burpees": [(0.0, 0.20), (0.20, 0.64), (0.64, 1.0)],
    "jumping_jacks": [(0.0, 0.0), (0.0, 0.5), (0.5, 1.0)],
    "mountain_climbers": [(0.0, 0.0), (0.0, 0.5), (0.0, 1.0)],
    "squat_jumps": [(0.0, 0.0), (0.0, 0.55), (0.55, 1.0)],
    "jump_lunges": [(0.0, 0.0), (0.0, 0.5), (0.5, 1.0)],
}


def build_segments(ex):
    """[(kind, caption_label, caption_text, narration, speed)]"""
    T = ex[LANG]; lab = L[LANG]
    lvl = lab["levels"][ex["level"]]
    lvl_say = lvl.replace("-", " ").lower().capitalize() if LANG == "en" else lvl
    segs = [("intro", lvl, T["name"], lab["intro_say"].format(name=T["name"], level=lvl_say), 0.5)]
    for i, st in enumerate(T["steps"]):
        segs.append(("step", lab["step"].format(n=i + 1), st, say_text(st) if LANG == "ta" else st, 0.5))
    segs.append(("full", lab["full"], lab["full_cap"], lab["full_say"], 1.0))
    for t in T["tips"]:
        segs.append(("tip", lab["tip"], t, say_text(t), 1.0))
    if T["easier"]:
        segs.append(("easier", lab["easier"], T["easier"], lab["easier_say"].format(t=say_text(T["easier"])), 1.0))
    segs.append(("outro", lab["ready"], lab["outro_cap"], lab["outro_say"], 1.0))
    return segs


# ---------------- drawing ----------------
def wrap(d, text, font, maxw):
    words, lines, cur = text.split(), [], ""
    for w_ in words:
        t = (cur + " " + w_).strip()
        if d.textlength(t, font=font) <= maxw: cur = t
        else: lines.append(cur); cur = w_
    if cur: lines.append(cur)
    return lines


def sample_joints(ex_anim, joints, t_ms):
    fr = ex_anim["frames"]; n = len(fr); L = ex_anim["loopMs"]
    pos = (t_ms % L) / L * n
    i0 = int(pos) % n; i1 = (i0 + 1) % n; f = pos - int(pos)
    a, b = fr[i0]["p"], fr[i1]["p"]
    pts = [a[k] + (b[k] - a[k]) * f for k in range(len(a))]
    far = fr[i0]["far"] if f < 0.5 else fr[i1]["far"]
    return {j: (pts[2 * i], pts[2 * i + 1]) for i, j in enumerate(joints)}, far


def draw_frame(ex, data, t_anim, seg, seg_i, n_segs, progress):
    im = Image.new("RGB", (W, H), BG); d = ImageDraw.Draw(im)
    col = LEVEL_COLORS[ex["level"]]; lab = L[LANG]; name = ex[LANG]["name"]
    # header
    d.text((40, 48), lab["levels"][ex["level"]], font=F(26, True), fill=col)
    nf = F(44, True)
    if d.textlength(name, font=nf) > 640: nf = F(34, True)
    d.text((40, 84), name, font=nf, fill=TEXT)
    d.text((680, 54), lab["howto"], font=F(22, True), fill=DIM, anchor="ra")
    # figure panel
    d.rounded_rectangle([30, 170, 690, 900], radius=28, fill=PANEL)
    an = data["exercises"][ex["id"]]
    J, far = sample_joints(an, data["joints"], t_anim)
    s = min(620 / 100, 700 / 120); ox = 360 - 50 * s; oy = 185
    d.line([(ox + 4 * s, oy + 112 * s), (ox + 96 * s, oy + 112 * s)], fill=(60, 82, 75), width=4)
    avatar.draw_avatar(avatar.Canvas(d, s, ox, oy), J, an["view"] == "side", far)
    if seg[4] < 1.0:
        d.rounded_rectangle([50, 190, 300, 236], radius=22, fill=(40, 58, 52))
        d.text((175, 213), lab["slow"], font=F(20, True), fill=(255, 210, 122), anchor="mm")
    # caption
    d.text((40, 930), seg[1], font=F(26, True), fill=col)
    big = seg[0] in ("intro", "outro")
    fnt = F((38 if big else 34) if LANG == "en" else (34 if big else 30), big)
    lh = 48 if LANG == "en" else 46
    for i, line in enumerate(wrap(d, seg[2], fnt, 640)[:5]):
        d.text((40, 972 + i * lh), line, font=fnt, fill=TEXT)
    # segment dots + progress
    for i in range(n_segs):
        x = 40 + i * 24
        d.ellipse([x, 1198, x + 12, 1210], fill=col if i <= seg_i else (55, 70, 65))
    d.rounded_rectangle([40, 1226, 680, 1234], radius=4, fill=(40, 55, 50))
    d.rounded_rectangle([40, 1226, 40 + 640 * progress, 1234], radius=4, fill=col)
    d.text((680, 1192), "Ramanan Fit", font=F(20), fill=DIM, anchor="ra")
    return im


def render(ex, data, voice, out_path):
    segs = build_segments(ex)
    clips = [voice.say(sg[3]) for sg in segs]
    durs = [max(2.6, len(c) / SR + 0.8) for c in clips]
    durs[0] = max(durs[0], 3.2)
    total = sum(durs)
    nframes = int(math.ceil(total * FPS))
    audio = np.zeros(int(total * SR) + SR, dtype=np.float32)
    t0 = 0.0
    for c, du in zip(clips, durs):
        st = int((t0 + 0.25) * SR); audio[st:st + len(c)] += c; t0 += du
    audio = audio[: int(nframes / FPS * SR)]
    with tempfile.TemporaryDirectory() as td:
        wav = os.path.join(td, "a.wav")
        import soundfile as sf
        sf.write(wav, audio, SR)
        cmd = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
               "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
               "-i", wav, "-c:v", "libx264", "-preset", "medium", "-crf", "27", "-pix_fmt", "yuv420p",
               "-profile:v", "main", "-c:a", "aac", "-b:a", "80k", "-ac", "1", "-movflags", "+faststart",
               "-shortest", out_path]
        p = subprocess.Popen(cmd, stdin=subprocess.PIPE)
        loop = data["exercises"][ex["id"]]["loopMs"]
        windows = STEP_WINDOWS.get(ex["id"])
        bounds = np.cumsum(durs); starts = np.concatenate([[0.0], bounds[:-1]])
        step_no = [None] * len(segs); k = 0
        for i, sg in enumerate(segs):
            if sg[0] == "step": step_no[i] = k; k += 1
        free_t = 0.0
        for f in range(nframes):
            t = f / FPS
            si = min(int(np.searchsorted(bounds, t, side="right")), len(segs) - 1)
            local = (t - starts[si]) * 1000.0 * segs[si][4]       # ms of animation into this segment
            if step_no[si] is not None:
                if windows:
                    a, b = windows[step_no[si]]
                    t_anim = a * loop if b <= a else a * loop + local % ((b - a) * loop)
                else:
                    t_anim = local                                 # each step replays from the start pose
            elif segs[si][0] == "intro":
                t_anim = local
            else:
                free_t += 1000.0 / FPS * segs[si][4]; t_anim = free_t
            img = draw_frame(ex, data, t_anim, segs[si], si, len(segs), t / total)
            p.stdin.write(img.tobytes())
        p.stdin.close(); p.wait()
        if p.returncode: raise SystemExit(f"ffmpeg failed for {ex['id']}")
    return total, segs


def main():
    global LANG
    kdir, out = sys.argv[1:3]; only = set(sys.argv[3:]); LANG = "en"; idir = None
    os.makedirs(out, exist_ok=True)
    data = json.load(open(os.path.join(ROOT, "app/src/main/assets/poses/poses.json")))
    voice = Voice(kdir, idir, LANG)
    for ex in parse_exercises():
        if only and ex["id"] not in only: continue
        path = os.path.join(out, f"howto_{ex['id']}.mp4")
        total, segs = render(ex, data, voice, path)
        print(f"{LANG} {ex['id']:18s} {total:5.1f}s  {len(segs)} segments  {os.path.getsize(path)//1024} KB", flush=True)


if __name__ == "__main__":
    main()
