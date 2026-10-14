#!/usr/bin/env python3
"""
Cartoon fitness-coach renderer — reference implementation of app/.../anim/Avatar.kt.
Original 2D coach styled after the owner's preferred look: light-brown hair in a high
ponytail, teal sports bra, teal leggings with a yellow side stripe, white sneakers, dark
wristband, athletic build. Drawn entirely from shapes — no photos or third-party art.

Usage: python3 tools/avatar.py <poses.json> <out_dir>   → GIF per exercise + contact sheet
Keep the geometry/colour constants in sync with Avatar.kt.
"""
import json, math, sys

SKIN = (238, 196, 168); SKIN_FAR = (206, 164, 138); SKIN_SHADE = (222, 176, 148)
HAIR = (150, 104, 66); HAIR_HI = (196, 150, 98); HAIR_FAR = (120, 82, 52)
TEAL = (24, 150, 160); TEAL_FAR = (16, 108, 116); TEAL_DARK = (14, 92, 102)
STRIPE = (242, 200, 40); BAND = (30, 40, 70)
SHOE = (245, 245, 248); SOLE = (200, 205, 212)
EYE = (40, 60, 70); EYE_HI = (255, 255, 255); LASH = (40, 26, 20); LIP = (214, 92, 100)
BLUSH = (240, 150, 140); BROW = (120, 82, 52); WRIST = (35, 35, 40); TEETH = (255, 255, 255)

HEAD_R = 6.2
PONY_FRONT = [(3.0, -7.0, 2.0), (5.4, -6.6, 1.8), (6.9, -4.6, 1.6), (7.5, -1.8, 1.4), (7.4, 1.0, 1.2), (7.0, 3.6, 0.9)]
PONY_SIDE = [(-2.6, -7.2, 1.9), (-5.0, -7.2, 1.8), (-7.0, -5.8, 1.6), (-8.2, -3.4, 1.4), (-8.6, -0.6, 1.2), (-8.4, 2.2, 1.0), (-7.8, 4.4, 0.8)]
FRINGE_FRONT = [(6.2, -0.6), (4.6, -2.4), (2.0, -3.2), (-0.6, -3.5), (-3.2, -2.9), (-5.4, -1.6), (-6.2, -0.4)]
FRINGE_SIDE = [(5.3, -2.6), (3.2, -3.4), (1.0, -2.6), (-1.0, -0.6), (-3.2, 1.6), (-5.6, 2.6), (-6.6, 1.4)]
W_THIGH, W_SHIN, W_SHOE, SHOE_R = 6.2, 5.0, 4.0, 2.8
W_UARM, W_FARM, HAND_R = 3.9, 3.3, 1.9


class Canvas:
    """Minimal drawing API mirrored 1:1 by Kotlin DrawScope calls."""
    def __init__(self, draw, scale, ox, oy):
        self.d, self.s, self.ox, self.oy = draw, scale, ox, oy
    def P(self, p): return (self.ox + p[0] * self.s, self.oy + p[1] * self.s)
    def line(self, a, b, w, c):
        A, B = self.P(a), self.P(b); r = w * self.s / 2
        self.d.line([A, B], fill=c, width=max(1, round(w * self.s)))
        for X in (A, B): self.d.ellipse([X[0] - r, X[1] - r, X[0] + r, X[1] + r], fill=c)
    def circle(self, p, r, c):
        X = self.P(p); R = r * self.s
        self.d.ellipse([X[0] - R, X[1] - R, X[0] + R, X[1] + R], fill=c)
    def poly(self, pts, c):
        self.d.polygon([self.P(p) for p in pts], fill=c)


def sub(a, b): return (a[0] - b[0], a[1] - b[1])
def add(a, b): return (a[0] + b[0], a[1] + b[1])
def mul(a, k): return (a[0] * k, a[1] * k)
def lerp(a, b, t): return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
def norm(v):
    L = math.hypot(*v); return (v[0] / L, v[1] / L) if L > 1e-6 else (0.0, 0.0)


def perp(a, b):
    d = norm(sub(b, a)); return (-d[1], d[0])


def draw_avatar(cv, J, side, far):
    head, neck, pel = J["head"], J["neck"], J["pelvis"]
    up = norm(sub(neck, pel)); down = mul(up, -1)
    theta = math.atan2(head[0] - neck[0], -(head[1] - neck[1]))
    ct, st = math.cos(theta), math.sin(theta)
    def H(x, y): return (head[0] + x * ct - y * st, head[1] + x * st + y * ct)
    nx, ny = sub(J["nose"], head)
    turn = nx * ct + ny * st
    facing = 1 if turn >= 0 else -1

    def leg(h, k, a, f, isFar, outward):
        c = TEAL_FAR if isFar else TEAL
        cv.line(h, k, W_THIGH, c); cv.line(k, a, W_SHIN, c)
        # yellow side stripe (outer seam in front view, centre seam in side view)
        for (p0, p1, w) in ((h, k, W_THIGH), (k, a, W_SHIN)):
            if side:
                off = (0.0, 0.0)
            else:
                pp = perp(p0, p1); sgn = 1 if pp[0] * outward > 0 else -1
                off = mul(pp, sgn * (w / 2 - 0.7))
            cv.line(add(p0, off), add(p1, off), 0.7, STRIPE if not isFar else (190, 158, 30))
        cv.circle(a, SHOE_R, SHOE); cv.line(a, f, W_SHOE, SHOE)
        if side: cv.line(add(a, (0, SHOE_R * 0.75)), add(f, (0.8, W_SHOE * 0.45)), 1.0, SOLE)
        else: cv.line(add(a, (-1.4, SHOE_R * 0.8)), add(a, (1.4, SHOE_R * 0.8)), 1.0, SOLE)

    def arm(s, e, h, isFar, band):
        sk = SKIN_FAR if isFar else SKIN
        cv.line(s, e, W_UARM, sk); cv.line(e, h, W_FARM, sk)
        if band: cv.line(lerp(e, h, 0.80), lerp(e, h, 0.88), W_FARM + 0.5, WRIST)
        cv.circle(h, HAND_R, sk)

    def torso():
        if side:
            top = add(neck, mul(down, 1.6)); hip = add(pel, mul(down, 0.2))
            cv.line(top, hip, 9.4, SKIN)                                  # midriff + back
            cv.line(top, lerp(top, hip, 0.42), 9.8, TEAL)                 # sports bra
            cv.line(lerp(top, hip, 0.88), hip, 9.8, TEAL)                 # leggings waistband
            return
        u = norm(sub(J["shB"], J["shA"]))
        if u == (0.0, 0.0): u = (-up[1], up[0])
        hu = norm(sub(J["hipB"], J["hipA"]))
        shw = math.hypot(*sub(J["shB"], J["shA"])) / 2
        waist = lerp(neck, pel, 0.66)
        ww = max(shw * 0.58, 4.3) if shw > 2 else 4.3
        hw = max(shw * 0.80, 5.9) if shw > 2 else 5.7
        hem = add(pel, mul(down, 0.6))
        chest = lerp(neck, pel, 0.42)
        cw = max(shw * 0.82, 5.0) if shw > 2 else 5.0
        # skin torso
        cv.poly([add(J["shA"], mul(u, -0.4)), add(J["shB"], mul(u, 0.4)), add(waist, mul(hu, ww)),
                 add(hem, mul(hu, hw)), add(hem, mul(hu, -hw)), add(waist, mul(hu, -ww))], SKIN)
        # subtle ab line
        cv.line(lerp(chest, waist, 0.35), lerp(waist, hem, 0.4), 0.35, SKIN_SHADE)
        # sports bra: straps + cups
        cv.poly([add(lerp(neck, J["shA"], 0.45), mul(down, 0.6)), add(lerp(neck, J["shB"], 0.45), mul(down, 0.6)),
                 add(chest, mul(hu, cw)), add(chest, mul(hu, -cw))], TEAL)
        cv.line(add(chest, mul(hu, -cw)), add(chest, mul(hu, cw)), 1.1, TEAL_DARK)
        cv.poly([add(neck, mul(u, -1.6)), add(neck, mul(u, 1.6)), add(neck, mul(down, 2.4))], SKIN)
        # leggings high waistband
        cv.poly([add(lerp(waist, hem, 0.45), mul(hu, (ww + hw) / 2)), add(hem, mul(hu, hw)),
                 add(hem, mul(hu, -hw)), add(lerp(waist, hem, 0.45), mul(hu, -(ww + hw) / 2))], TEAL)

    def face_front(f):
        for sx in (-2.3, 2.3):
            cx = sx + f
            cv.circle(H(cx, -0.2), 0.95, EYE)
            cv.circle(H(cx + 0.3, -0.55), 0.32, EYE_HI)
            cv.line(H(cx - 1.0, -1.0), H(cx + 1.1, -1.25), 0.4, LASH)
            cv.line(H(cx - 1.1, -2.2), H(cx + 1.0, -2.45), 0.45, BROW)
        cv.line(H(f, 0.6), H(f + 0.2, 1.4), 0.35, SKIN_SHADE)                 # nose
        cv.circle(H(-3.1 + f, 1.7), 1.0, BLUSH); cv.circle(H(3.1 + f, 1.7), 1.0, BLUSH)
        cv.poly([H(-1.5 + f, 2.7), H(1.5 + f, 2.7), H(f, 3.8)], LIP)         # smile
        cv.line(H(-1.1 + f, 2.85), H(1.1 + f, 2.85), 0.45, TEETH)

    def tail(spec, mirror, shift, col):
        """Smooth tapered ponytail: thick round-capped strokes between successive points."""
        for (x0, y0, r0), (x1, y1, _) in zip(spec, spec[1:]):
            cv.line(H(x0 * mirror + shift, y0), H(x1 * mirror + shift, y1), 2 * r0, col)

    def cap(mirror, shift, front_pts, a0, a1):
        """Sleek hair cap: arc over the top of the head + a fringe line back across the forehead."""
        pts = []
        steps = 14
        for i in range(steps + 1):
            ang = math.radians(a0 + (a1 - a0) * i / steps)
            pts.append(H((math.cos(ang) * 6.75) * mirror + shift, math.sin(ang) * 6.75 - 0.3))
        pts += [H(x * mirror + shift, y) for (x, y) in front_pts]
        cv.poly(pts, HAIR)

    def head_front():
        f = max(-4.0, min(4.0, turn)) * 0.85
        tail(PONY_FRONT, 1, -f * 0.3, HAIR_FAR)                                 # ponytail behind
        cv.circle(head, HEAD_R, SKIN)
        cap(1, f * 0.1, FRINGE_FRONT, 175, 365)
        cv.circle(H(-5.9 + f * 0.1, 0.9), 1.2, HAIR); cv.circle(H(5.9 + f * 0.1, 0.9), 1.2, HAIR)   # side strands
        cv.line(H(-3.8 + f * 0.1, -5.0), H(0.4 + f * 0.1, -6.3), 0.9, HAIR_HI)   # shine
        cv.circle(H(2.6 + f * 0.1, -7.0), 1.5, HAIR)                            # ponytail tie
        cv.circle(H(2.6 + f * 0.1, -7.0), 0.7, TEAL)
        face_front(f)

    def head_side():
        s = facing
        tail(PONY_SIDE, s, 0.0, HAIR)                                           # high ponytail
        cv.circle(head, HEAD_R, SKIN)
        cv.circle(H(6.0 * s, 0.7), 1.0, SKIN)                                    # nose
        cap(s, 0.0, FRINGE_SIDE, 160, 345)
        cv.circle(H(-2.4 * s, -7.0), 1.4, HAIR); cv.circle(H(-2.4 * s, -7.0), 0.65, TEAL)
        cv.line(H(-2.6 * s, -5.6), H(1.6 * s, -6.4), 0.9, HAIR_HI)
        cv.circle(H(3.8 * s, -0.2), 0.9, EYE); cv.circle(H(4.1 * s, -0.55), 0.3, EYE_HI)
        cv.line(H(3.0 * s, -1.0), H(4.9 * s, -1.2), 0.4, LASH)
        cv.line(H(2.9 * s, -2.3), H(4.9 * s, -2.5), 0.45, BROW)
        cv.circle(H(3.2 * s, 1.9), 0.9, BLUSH)
        cv.poly([H(4.1 * s, 2.9), H(5.7 * s, 2.7), H(5.0 * s, 3.7)], LIP)

    neck_top = lerp(neck, head, 0.55)
    if side:
        arm(J["shA"], J["elA"], J["haA"], True, False)
        leg(J["hipA"], J["knA"], J["anA"], J["ftA"], True, 0)
        leg(J["hipB"], J["knB"], J["anB"], J["ftB"], False, 0)
        torso()
        cv.line(neck, neck_top, 3.6, SKIN)
        head_side()
        arm(J["shB"], J["elB"], J["haB"], False, True)
    else:
        leg(J["hipA"], J["knA"], J["anA"], J["ftA"], False, -1)
        leg(J["hipB"], J["knB"], J["anB"], J["ftB"], False, 1)
        cv.line(J["hipA"], J["hipB"], W_THIGH, TEAL)
        if far == 1: arm(J["shA"], J["elA"], J["haA"], True, False)
        if far == 2: arm(J["shB"], J["elB"], J["haB"], True, True)
        cv.line(neck, neck_top, 3.6, SKIN)
        torso()
        if far != 1: arm(J["shA"], J["elA"], J["haA"], False, False)
        if far != 2: arm(J["shB"], J["elB"], J["haB"], False, True)
        head_front()


def render(data, ex_id, frame, scale, bg):
    from PIL import Image, ImageDraw
    W, H = data["canvas"]
    im = Image.new("RGB", (int(W * scale), int(H * scale)), bg)
    d = ImageDraw.Draw(im)
    ex = data["exercises"][ex_id]
    fr = ex["frames"][frame]
    J = {j: (fr["p"][2 * i], fr["p"][2 * i + 1]) for i, j in enumerate(data["joints"])}
    g = data["ground"]
    d.line([(4 * scale, g * scale), ((W - 4) * scale, g * scale)], fill=(70, 90, 84), width=max(1, int(1.5 * scale)))
    draw_avatar(Canvas(d, scale, 0, 0), J, ex["view"] == "side", fr["far"])
    return im


def main():
    from PIL import Image
    import os
    data = json.load(open(sys.argv[1])); out = sys.argv[2]; os.makedirs(out, exist_ok=True)
    S = 4; bg = (16, 24, 22)
    rows = []
    for ex_id, ex in data["exercises"].items():
        n = len(ex["frames"])
        imgs = [render(data, ex_id, i, S, bg) for i in range(n)]
        imgs[0].save(f"{out}/{ex_id}.gif", save_all=True, append_images=imgs[1:], duration=ex["loopMs"] // n, loop=0)
        rows.append([imgs[i * n // 4] for i in range(4)])
    w, h = rows[0][0].size
    sheet = Image.new("RGB", (w * 4, h * len(rows)))
    for r, row in enumerate(rows):
        for c, im in enumerate(row): sheet.paste(im, (c * w, r * h))
    sheet.save(f"{out}/avatar_contact_sheet.png")


if __name__ == "__main__":
    main()
