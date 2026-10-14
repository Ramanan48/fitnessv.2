#!/usr/bin/env python3
"""
Generates app/src/main/assets/poses/poses.json — original stick-figure animations
for the nine exercises — and (optionally) preview GIFs drawn from the same data.

Usage:  python3 tools/make_poses.py <out_json> [gif_dir]

Angles: degrees, 0 = pointing straight down, +90 = screen right, 180 = straight up.
Arm angles are relative to the torso (they tilt with the spine); leg angles are absolute.
Limb "a" is the screen-left limb (front view) or the far limb (side view, facing right).
"""
import json, math, sys

W, H, GROUND = 100.0, 120.0, 112.0
HEAD_R, NECK_LEN, TORSO = 6.0, 8.0, 30.0
UARM, FARM, THIGH, SHIN, FOOT = 15.0, 14.0, 20.0, 20.0, 5.0
SH_HALF, HIP_HALF = 9.0, 5.5
FRAMES = 48

NEUTRAL = dict(spine=180, spine_scale=1.0, rot=0,
               au=-8, af=-5, bu=8, bf=5, au_s=1, af_s=1, bu_s=1, bf_s=1,
               at=-3, as_=-3, bt=3, bs=3, at_s=1, bt_s=1,
               far=0, ground="low", lift=0, xoff=0)


def d(a):
    r = math.radians(a)
    return math.sin(r), math.cos(r)


def add(p, a, length):
    dx, dy = d(a)
    return (p[0] + dx * length, p[1] + dy * length)


def build(view, pose):
    p = dict(NEUTRAL); p.update(pose)
    pel = (0.0, 0.0)
    neck = add(pel, p["spine"], TORSO * p["spine_scale"])
    tilt = p["spine"] - 180
    side = view == "side"
    rot = math.radians(p["rot"])
    shw = 0 if side else SH_HALF * math.cos(rot)
    hpw = 0 if side else HIP_HALF
    # shoulder line perpendicular to the spine
    px, py = d(p["spine"] - 90)
    shA = (neck[0] - px * shw, neck[1] - py * shw)
    shB = (neck[0] + px * shw, neck[1] + py * shw)
    head = add(neck, p["spine"], NECK_LEN)
    hipA = (pel[0] - hpw, pel[1]); hipB = (pel[0] + hpw, pel[1])
    elA = add(shA, p["au"] + tilt, UARM * p["au_s"]); haA = add(elA, p["af"] + tilt, FARM * p["af_s"])
    elB = add(shB, p["bu"] + tilt, UARM * p["bu_s"]); haB = add(elB, p["bf"] + tilt, FARM * p["bf_s"])
    knA = add(hipA, p["at"], THIGH * p["at_s"]); anA = add(knA, p["as_"], SHIN)
    knB = add(hipB, p["bt"], THIGH * p["bt_s"]); anB = add(knB, p["bs"], SHIN)
    if side:
        ftA = add(anA, p["as_"] + 90, FOOT); ftB = add(anB, p["bs"] + 90, FOOT)
        nose = add(head, p["spine"] - 90, HEAD_R + 1.2)
    else:
        ftA = add(anA, -90 + p["as_"] * 0.3, FOOT * 0.7); ftB = add(anB, 90 + p["bs"] * 0.3, FOOT * 0.7)
        nose = (head[0] - math.sin(rot) * HEAD_R * 0.75, head[1] + 1.5)
    pts = [head, nose, neck, shA, shB, elA, elB, haA, haB, pel, hipA, hipB, knA, knB, anA, anB, ftA, ftB]
    # anchor: lowest ankle (or chosen one) sits on the ground
    g = p["ground"]
    if g == "a": gy, gx = anA[1], anA[0]
    elif g == "b": gy, gx = anB[1], anB[0]
    else:
        gy = max(anA[1], anB[1]); gx = anA[0] if anA[1] >= anB[1] else anB[0]
    dy = GROUND - 2.5 - gy - p["lift"]          # lift > 0 = airborne (jumps)
    dx = ((50.0 - gx) if side else 50.0) + p["xoff"]
    return [(round(x + dx, 2), round(y + dy, 2)) for x, y in pts], p["far"]


def ease(t):
    return t * t * (3 - 2 * t)


def lerp_pose(p0, p1, t):
    out = {}
    for k in NEUTRAL:
        a = p0.get(k, NEUTRAL[k]); b = p1.get(k, NEUTRAL[k])
        if isinstance(a, (int, float)) and isinstance(b, (int, float)):
            out[k] = a + (b - a) * t
        else:
            out[k] = a if t < 0.5 else b
    return out


def bake(ex):
    keys = ex["keys"]  # [(t, pose)], t in [0,1), loops back to keys[0] at t=1
    keys = keys + [(1.0, keys[0][1])]
    frames = []
    n = ex.get("frames", FRAMES)
    for i in range(n):
        t = i / n
        for (t0, p0), (t1, p1) in zip(keys, keys[1:]):
            if t0 <= t < t1:
                u = ease((t - t0) / (t1 - t0)) if t1 > t0 else 0
                pose = lerp_pose(p0, p1, u)
                break
        pts, far = build(ex["view"], pose)
        frames.append({"p": [c for pt in pts for c in pt], "far": int(round(far))})
    return frames


# ---------- arm / leg presets ----------
HANDS_HEAD = dict(au=-125, af=75, bu=125, bf=-75)        # elbows out, fingertips near ears
ARMS_UP = dict(au=-165, af=-175, bu=165, bf=175)
WIDE = dict(at=-12, as_=-12, bt=12, bs=12)

EXERCISES = [
    # ---- warm-up (once at the start) ----
    dict(id="march_arms", view="side", loopMs=1600, keys=[
        (0.00, dict(bt=55, bs=0, at=0, as_=0, au=75, af=95, bu=-45, bf=-35, ground="a")),
        (0.25, dict(at=0, as_=0, bt=0, bs=0, au=10, af=10, bu=10, bf=10)),
        (0.50, dict(at=55, as_=0, bt=0, bs=0, bu=75, bf=95, au=-45, af=-35, ground="b")),
        (0.75, dict(at=0, as_=0, bt=0, bs=0, au=10, af=10, bu=10, bf=10)),
    ]),
    dict(id="step_jacks", view="front", loopMs=2000, keys=[
        (0.00, dict()),
        (0.25, dict(**ARMS_UP, at=-3, as_=-3, bt=24, bs=24, ground="a")),
        (0.50, dict()),
        (0.75, dict(**ARMS_UP, at=-24, as_=-24, bt=3, bs=3, ground="b")),
    ]),
    # ---- beginner ----
    dict(id="high_knees", view="side", loopMs=900, keys=[
        (0.00, dict(bt=88, bs=8, at=0, as_=0, au=55, af=150, bu=-35, bf=40, ground="a")),
        (0.25, dict(at=0, as_=0, bt=0, bs=0, au=10, af=60, bu=10, bf=60)),
        (0.50, dict(at=88, as_=8, bt=0, bs=0, bu=55, bf=150, au=-35, af=40, ground="b")),
        (0.75, dict(at=0, as_=0, bt=0, bs=0, au=10, af=60, bu=10, bf=60)),
    ]),
    dict(id="butt_kicks", view="side", loopMs=900, keys=[
        (0.00, dict(at=0, as_=0, bt=-8, bs=-162, au=40, af=130, bu=-30, bf=30, ground="a", spine=176)),
        (0.25, dict(at=0, as_=0, bt=0, bs=0, au=5, af=60, bu=5, bf=60, spine=176)),
        (0.50, dict(at=-8, as_=-162, bt=0, bs=0, bu=40, bf=130, au=-30, af=30, ground="b", spine=176)),
        (0.75, dict(at=0, as_=0, bt=0, bs=0, au=5, af=60, bu=5, bf=60, spine=176)),
    ]),
    # ---- advanced ----
    dict(id="jumping_jacks", view="front", loopMs=1200, keys=[
        (0.00, dict(at=-2, as_=-2, bt=2, bs=2, au=-6, af=-4, bu=6, bf=4)),
        (0.25, dict(at=-11, as_=-11, bt=11, bs=11, au=-95, af=-100, bu=95, bf=100, lift=6)),
        (0.50, dict(at=-20, as_=-20, bt=20, bs=20, au=-165, af=-175, bu=165, bf=175)),
        (0.75, dict(at=-11, as_=-11, bt=11, bs=11, au=-95, af=-100, bu=95, bf=100, lift=6)),
    ]),
    dict(id="skaters", view="front", loopMs=1600, keys=[
        (0.00, dict(xoff=13, spine=170, bt=18, bs=-12, at=35, as_=55, au=40, af=55, bu=-35, bf=-30, ground="b")),
        (0.25, dict(xoff=0, lift=7, at=-6, as_=-2, bt=6, bs=2, au=-10, af=-5, bu=10, bf=5)),
        (0.50, dict(xoff=-13, spine=190, at=-18, as_=12, bt=-35, bs=-55, bu=-40, bf=-55, au=35, af=30, ground="a")),
        (0.75, dict(xoff=0, lift=7, at=-6, as_=-2, bt=6, bs=2, au=-10, af=-5, bu=10, bf=5)),
    ]),
    dict(id="jump_lunges", view="side", loopMs=1800, frames=72, keys=[
        (0.00, dict(spine=178, bt=70, bs=-15, at=-10, as_=-80, au=-40, af=-20, bu=50, bf=110, ground="b")),
        (0.25, dict(spine=180, at=25, as_=-30, bt=20, bs=-30, au=30, af=80, bu=30, bf=80, lift=7)),
        (0.50, dict(spine=178, at=70, as_=-15, bt=-10, bs=-80, bu=-40, bf=-20, au=50, af=110, ground="a")),
        (0.75, dict(spine=180, at=20, as_=-30, bt=25, bs=-30, au=30, af=80, bu=30, bf=80, lift=7)),
    ]),
    dict(id="mountain_climbers", view="side", loopMs=900, keys=[
        (0.00, dict(spine=122, at=-70, as_=-70, bt=35, bs=-110, au=58, af=58, bu=58, bf=58, ground="a", xoff=-30)),
        (0.50, dict(spine=122, bt=-70, bs=-70, at=35, as_=-110, au=58, af=58, bu=58, bf=58, ground="b", xoff=-30)),
    ]),
    # ---- harder ----
    dict(id="squat_jumps", view="side", loopMs=2000, frames=72, keys=[
        (0.00, dict(at=80, as_=-26, bt=80, bs=-26, spine=150, au=0, af=0, bu=0, bf=0)),
        (0.30, dict(at=0, as_=0, bt=0, bs=0, au=150, af=155, bu=150, bf=155, lift=7)),
        (0.55, dict(at=0, as_=0, bt=0, bs=0, au=60, af=60, bu=60, bf=60)),
        (0.80, dict(at=80, as_=-26, bt=80, bs=-26, spine=150, au=0, af=0, bu=0, bf=0)),
    ]),
    dict(id="burpees", view="side", loopMs=4000, frames=96, keys=[
        (0.00, dict(at=0, as_=0, bt=0, bs=0, au=5, af=5, bu=5, bf=5, xoff=12)),
        (0.18, dict(at=90, as_=-30, bt=90, bs=-30, spine=125, au=50, af=50, bu=50, bf=50, xoff=15)),
        (0.36, dict(spine=122, at=-70, as_=-70, bt=-70, bs=-70, au=58, af=58, bu=58, bf=58, xoff=-30)),
        (0.48, dict(spine=122, at=-70, as_=-70, bt=-70, bs=-70, au=58, af=58, bu=58, bf=58, xoff=-30)),
        (0.64, dict(at=90, as_=-30, bt=90, bs=-30, spine=125, au=50, af=50, bu=50, bf=50, xoff=15)),
        (0.80, dict(at=0, as_=0, bt=0, bs=0, au=160, af=165, bu=160, bf=165, lift=7, xoff=12)),
        (0.90, dict(at=0, as_=0, bt=0, bs=0, au=5, af=5, bu=5, bf=5, xoff=12)),
    ]),
]

JOINTS = ["head", "nose", "neck", "shA", "shB", "elA", "elB", "haA", "haB", "pelvis",
          "hipA", "hipB", "knA", "knB", "anA", "anB", "ftA", "ftB"]


def main():
    out = {"canvas": [W, H], "ground": GROUND, "headRadius": HEAD_R, "joints": JOINTS,
           "exercises": {}}
    for ex in EXERCISES:
        out["exercises"][ex["id"]] = {"view": ex["view"], "loopMs": ex["loopMs"], "frames": bake(ex)}
    with open(sys.argv[1], "w") as f:
        json.dump(out, f, separators=(",", ":"))
    if len(sys.argv) > 2:
        render_gifs(out, sys.argv[2])


def render_gifs(data, outdir):
    from PIL import Image, ImageDraw
    import os
    os.makedirs(outdir, exist_ok=True)
    S = 3
    sheet = []
    for name, ex in data["exercises"].items():
        imgs = []
        for fr in ex["frames"]:
            im = Image.new("RGB", (int(W * S), int(H * S)), (16, 24, 22))
            dr = ImageDraw.Draw(im)
            P = {j: (fr["p"][2 * i] * S, fr["p"][2 * i + 1] * S) for i, j in enumerate(JOINTS)}
            dr.line([(0, GROUND * S), (W * S, GROUND * S)], fill=(60, 80, 75), width=2)
            side = ex["view"] == "side"
            farA = side or fr["far"] == 1
            farB = (not side) and fr["far"] == 2
            near, far = (120, 230, 150), (60, 120, 80)
            def limb(chain, c):
                dr.line([P[k] for k in chain], fill=c, width=5 * S // 2, joint="curve")
            limb(["shA", "elA", "haA"], far if farA else near)
            limb(["hipA", "knA", "anA", "ftA"], far if (side) else near)
            limb(["shA", "shB"], near); limb(["hipA", "hipB"], near); limb(["neck", "pelvis"], near)
            limb(["hipB", "knB", "anB", "ftB"], near)
            limb(["shB", "elB", "haB"], far if farB else near)
            hx, hy = P["head"]; r = HEAD_R * S
            dr.ellipse([hx - r, hy - r, hx + r, hy + r], outline=near, width=5 * S // 2)
            nx, ny = P["nose"]; dr.ellipse([nx - 3, ny - 3, nx + 3, ny + 3], fill=(250, 220, 120))
            dr.text((6, 6), name, fill=(200, 200, 200))
            imgs.append(im)
        imgs[0].save(f"{outdir}/{name}.gif", save_all=True, append_images=imgs[1:],
                     duration=ex["loopMs"] // len(imgs), loop=0)
        sheet.append([imgs[i * len(imgs) // 4] for i in range(4)])
    # contact sheet: 4 key moments per exercise
    cw, ch = int(W * S), int(H * S)
    cs = Image.new("RGB", (cw * 4, ch * len(sheet)))
    for r, row in enumerate(sheet):
        for c, im in enumerate(row):
            cs.paste(im, (c * cw, r * ch))
    cs.save(f"{outdir}/contact_sheet.png")


if __name__ == "__main__":
    main()
