package com.ramanan.fit.anim

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import com.ramanan.fit.anim.PoseLibrary as P

/**
 * Original 2D fitness coach styled after the owner's preferred look: light-brown hair in a
 * high ponytail, teal sports bra, teal leggings with a yellow side stripe, white sneakers,
 * dark wristband, athletic build. Drawn only from shapes.
 * Port of tools/avatar.py — keep both in sync (avatar.py renders the previews and videos).
 */
private object AC {
    val SKIN = Color(0xFFEEC4A8); val SKIN_FAR = Color(0xFFCEA48A); val SKIN_SHADE = Color(0xFFDEB094)
    val HAIR = Color(0xFF966842); val HAIR_HI = Color(0xFFC49662); val HAIR_FAR = Color(0xFF785234)
    val TEAL = Color(0xFF1896A0); val TEAL_FAR = Color(0xFF106C74); val TEAL_DARK = Color(0xFF0E5C66)
    val STRIPE = Color(0xFFF2C828); val STRIPE_FAR = Color(0xFFBE9E1E)
    val SHOE = Color(0xFFF5F5F8); val SOLE = Color(0xFFC8CDD4)
    val EYE = Color(0xFF283C46); val EYE_HI = Color(0xFFFFFFFF); val LASH = Color(0xFF281A14)
    val LIP = Color(0xFFD65C64); val BLUSH = Color(0xFFF0968C); val BROW = Color(0xFF785234)
    val WRIST = Color(0xFF232328); val TEETH = Color(0xFFFFFFFF)

    const val HEAD_R = 6.2f
    const val W_THIGH = 6.2f; const val W_SHIN = 5.0f; const val W_SHOE = 4.0f; const val SHOE_R = 2.8f
    const val W_UARM = 3.9f; const val W_FARM = 3.3f; const val HAND_R = 1.9f

    // (x, y, r) in head-local units
    val PONY_FRONT = floatArrayOf(3.0f, -7.0f, 2.0f, 5.4f, -6.6f, 1.8f, 6.9f, -4.6f, 1.6f, 7.5f, -1.8f, 1.4f, 7.4f, 1.0f, 1.2f, 7.0f, 3.6f, 0.9f)
    val PONY_SIDE = floatArrayOf(-2.6f, -7.2f, 1.9f, -5.0f, -7.2f, 1.8f, -7.0f, -5.8f, 1.6f, -8.2f, -3.4f, 1.4f, -8.6f, -0.6f, 1.2f, -8.4f, 2.2f, 1.0f, -7.8f, 4.4f, 0.8f)
    // (x, y) fringe points
    val FRINGE_FRONT = floatArrayOf(6.2f, -0.6f, 4.6f, -2.4f, 2.0f, -3.2f, -0.6f, -3.5f, -3.2f, -2.9f, -5.4f, -1.6f, -6.2f, -0.4f)
    val FRINGE_SIDE = floatArrayOf(5.3f, -2.6f, 3.2f, -3.4f, 1.0f, -2.6f, -1.0f, -0.6f, -3.2f, 1.6f, -5.6f, 2.6f, -6.6f, 1.4f)
}

private fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
private fun norm(v: Offset): Offset {
    val l = hypot(v.x, v.y); return if (l > 1e-6f) Offset(v.x / l, v.y / l) else Offset.Zero
}
private fun perp(a: Offset, b: Offset): Offset { val d = norm(b - a); return Offset(-d.y, d.x) }

private class Painter(val ds: DrawScope, val s: Float, val ox: Float, val oy: Float) {
    fun px(p: Offset) = Offset(ox + p.x * s, oy + p.y * s)
    fun line(a: Offset, b: Offset, w: Float, c: Color) =
        ds.drawLine(c, px(a), px(b), strokeWidth = w * s, cap = StrokeCap.Round)
    fun circle(p: Offset, r: Float, c: Color) = ds.drawCircle(c, r * s, px(p))
    fun poly(pts: List<Offset>, c: Color) {
        val path = Path()
        pts.forEachIndexed { i, p -> val q = px(p); if (i == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y) }
        path.close(); ds.drawPath(path, c)
    }
}

internal fun DrawScope.drawAvatar(p: FloatArray, side: Boolean, far: Int, s: Float, ox: Float, oy: Float) {
    val c = Painter(this, s, ox, oy)
    fun j(i: Int) = Offset(p[2 * i], p[2 * i + 1])
    val head = j(P.HEAD); val neck = j(P.NECK); val pel = j(P.PELVIS)
    val shA = j(P.SH_A); val shB = j(P.SH_B)
    val up = norm(neck - pel); val down = up * -1f
    val theta = atan2(head.x - neck.x, -(head.y - neck.y))
    val ct = cos(theta); val st = sin(theta)
    fun H(x: Float, y: Float) = Offset(head.x + x * ct - y * st, head.y + x * st + y * ct)
    val nose = j(P.NOSE) - head
    val turn = nose.x * ct + nose.y * st
    val facing = if (turn >= 0f) 1f else -1f

    fun leg(h: Offset, k: Offset, a: Offset, f: Offset, isFar: Boolean, outward: Int) {
        val col = if (isFar) AC.TEAL_FAR else AC.TEAL
        c.line(h, k, AC.W_THIGH, col); c.line(k, a, AC.W_SHIN, col)
        val segs = listOf(Triple(h, k, AC.W_THIGH), Triple(k, a, AC.W_SHIN))
        for ((p0, p1, w) in segs) {
            val off = if (side) Offset.Zero else {
                val pp = perp(p0, p1)
                val sgn = if (pp.x * outward > 0f) 1f else -1f
                pp * (sgn * (w / 2f - 0.7f))
            }
            c.line(p0 + off, p1 + off, 0.7f, if (isFar) AC.STRIPE_FAR else AC.STRIPE)
        }
        c.circle(a, AC.SHOE_R, AC.SHOE); c.line(a, f, AC.W_SHOE, AC.SHOE)
        if (side) c.line(a + Offset(0f, AC.SHOE_R * 0.75f), f + Offset(0.8f, AC.W_SHOE * 0.45f), 1.0f, AC.SOLE)
        else c.line(a + Offset(-1.4f, AC.SHOE_R * 0.8f), a + Offset(1.4f, AC.SHOE_R * 0.8f), 1.0f, AC.SOLE)
    }

    fun arm(sh: Offset, el: Offset, ha: Offset, isFar: Boolean, band: Boolean) {
        val sk = if (isFar) AC.SKIN_FAR else AC.SKIN
        c.line(sh, el, AC.W_UARM, sk); c.line(el, ha, AC.W_FARM, sk)
        if (band) c.line(lerp(el, ha, 0.80f), lerp(el, ha, 0.88f), AC.W_FARM + 0.5f, AC.WRIST)
        c.circle(ha, AC.HAND_R, sk)
    }

    fun torso() {
        if (side) {
            val top = neck + down * 1.6f; val hip = pel + down * 0.2f
            c.line(top, hip, 9.4f, AC.SKIN)
            c.line(top, lerp(top, hip, 0.42f), 9.8f, AC.TEAL)
            c.line(lerp(top, hip, 0.88f), hip, 9.8f, AC.TEAL)
            return
        }
        var u = norm(shB - shA)
        if (u == Offset.Zero) u = Offset(-up.y, up.x)
        val hu = norm(j(P.HIP_B) - j(P.HIP_A))
        val shw = hypot(shB.x - shA.x, shB.y - shA.y) / 2f
        val waist = lerp(neck, pel, 0.66f)
        val ww = if (shw > 2f) max(shw * 0.58f, 4.3f) else 4.3f
        val hw = if (shw > 2f) max(shw * 0.80f, 5.9f) else 5.7f
        val hem = pel + down * 0.6f
        val chest = lerp(neck, pel, 0.42f)
        val cw = if (shw > 2f) max(shw * 0.82f, 5.0f) else 5.0f
        c.poly(listOf(shA + u * -0.4f, shB + u * 0.4f, waist + hu * ww, hem + hu * hw, hem + hu * -hw, waist + hu * -ww), AC.SKIN)
        c.line(lerp(chest, waist, 0.35f), lerp(waist, hem, 0.4f), 0.35f, AC.SKIN_SHADE)
        c.poly(listOf(lerp(neck, shA, 0.45f) + down * 0.6f, lerp(neck, shB, 0.45f) + down * 0.6f, chest + hu * cw, chest + hu * -cw), AC.TEAL)
        c.line(chest + hu * -cw, chest + hu * cw, 1.1f, AC.TEAL_DARK)
        c.poly(listOf(neck + u * -1.6f, neck + u * 1.6f, neck + down * 2.4f), AC.SKIN)
        val wb = lerp(waist, hem, 0.45f); val mw = (ww + hw) / 2f
        c.poly(listOf(wb + hu * mw, hem + hu * hw, hem + hu * -hw, wb + hu * -mw), AC.TEAL)
    }

    fun tail(spec: FloatArray, mirror: Float, shift: Float, col: Color) {
        var i = 0
        while (i + 5 < spec.size) {
            c.line(H(spec[i] * mirror + shift, spec[i + 1]), H(spec[i + 3] * mirror + shift, spec[i + 4]), 2f * spec[i + 2], col)
            i += 3
        }
    }

    fun cap(mirror: Float, shift: Float, fringe: FloatArray, a0: Float, a1: Float) {
        val pts = ArrayList<Offset>()
        val steps = 14
        for (i in 0..steps) {
            val ang = Math.toRadians((a0 + (a1 - a0) * i / steps).toDouble())
            pts += H((cos(ang) * 6.75).toFloat() * mirror + shift, (sin(ang) * 6.75).toFloat() - 0.3f)
        }
        var k = 0
        while (k < fringe.size) { pts += H(fringe[k] * mirror + shift, fringe[k + 1]); k += 2 }
        c.poly(pts, AC.HAIR)
    }

    fun faceFront(f: Float) {
        for (sx in floatArrayOf(-2.3f, 2.3f)) {
            val cx = sx + f
            c.circle(H(cx, -0.2f), 0.95f, AC.EYE)
            c.circle(H(cx + 0.3f, -0.55f), 0.32f, AC.EYE_HI)
            c.line(H(cx - 1.0f, -1.0f), H(cx + 1.1f, -1.25f), 0.4f, AC.LASH)
            c.line(H(cx - 1.1f, -2.2f), H(cx + 1.0f, -2.45f), 0.45f, AC.BROW)
        }
        c.line(H(f, 0.6f), H(f + 0.2f, 1.4f), 0.35f, AC.SKIN_SHADE)
        c.circle(H(-3.1f + f, 1.7f), 1.0f, AC.BLUSH); c.circle(H(3.1f + f, 1.7f), 1.0f, AC.BLUSH)
        c.poly(listOf(H(-1.5f + f, 2.7f), H(1.5f + f, 2.7f), H(f, 3.8f)), AC.LIP)
        c.line(H(-1.1f + f, 2.85f), H(1.1f + f, 2.85f), 0.45f, AC.TEETH)
    }

    fun headFront() {
        val f = turn.coerceIn(-4.0f, 4.0f) * 0.85f
        tail(AC.PONY_FRONT, 1f, -f * 0.3f, AC.HAIR_FAR)
        c.circle(head, AC.HEAD_R, AC.SKIN)
        cap(1f, f * 0.1f, AC.FRINGE_FRONT, 175f, 365f)
        c.circle(H(-5.9f + f * 0.1f, 0.9f), 1.2f, AC.HAIR); c.circle(H(5.9f + f * 0.1f, 0.9f), 1.2f, AC.HAIR)
        c.line(H(-3.8f + f * 0.1f, -5.0f), H(0.4f + f * 0.1f, -6.3f), 0.9f, AC.HAIR_HI)
        c.circle(H(2.6f + f * 0.1f, -7.0f), 1.5f, AC.HAIR)
        c.circle(H(2.6f + f * 0.1f, -7.0f), 0.7f, AC.TEAL)
        faceFront(f)
    }

    fun headSide() {
        val m = facing
        tail(AC.PONY_SIDE, m, 0f, AC.HAIR)
        c.circle(head, AC.HEAD_R, AC.SKIN)
        c.circle(H(6.0f * m, 0.7f), 1.0f, AC.SKIN)
        cap(m, 0f, AC.FRINGE_SIDE, 160f, 345f)
        c.circle(H(-2.4f * m, -7.0f), 1.4f, AC.HAIR); c.circle(H(-2.4f * m, -7.0f), 0.65f, AC.TEAL)
        c.line(H(-2.6f * m, -5.6f), H(1.6f * m, -6.4f), 0.9f, AC.HAIR_HI)
        c.circle(H(3.8f * m, -0.2f), 0.9f, AC.EYE); c.circle(H(4.1f * m, -0.55f), 0.3f, AC.EYE_HI)
        c.line(H(3.0f * m, -1.0f), H(4.9f * m, -1.2f), 0.4f, AC.LASH)
        c.line(H(2.9f * m, -2.3f), H(4.9f * m, -2.5f), 0.45f, AC.BROW)
        c.circle(H(3.2f * m, 1.9f), 0.9f, AC.BLUSH)
        c.poly(listOf(H(4.1f * m, 2.9f), H(5.7f * m, 2.7f), H(5.0f * m, 3.7f)), AC.LIP)
    }

    val neckTop = lerp(neck, head, 0.55f)
    if (side) {
        arm(shA, j(P.EL_A), j(P.HA_A), isFar = true, band = false)
        leg(j(P.HIP_A), j(P.KN_A), j(P.AN_A), j(P.FT_A), true, 0)
        leg(j(P.HIP_B), j(P.KN_B), j(P.AN_B), j(P.FT_B), false, 0)
        torso()
        c.line(neck, neckTop, 3.6f, AC.SKIN)
        headSide()
        arm(shB, j(P.EL_B), j(P.HA_B), isFar = false, band = true)
    } else {
        leg(j(P.HIP_A), j(P.KN_A), j(P.AN_A), j(P.FT_A), false, -1)
        leg(j(P.HIP_B), j(P.KN_B), j(P.AN_B), j(P.FT_B), false, 1)
        c.line(j(P.HIP_A), j(P.HIP_B), AC.W_THIGH, AC.TEAL)
        if (far == 1) arm(shA, j(P.EL_A), j(P.HA_A), isFar = true, band = false)
        if (far == 2) arm(shB, j(P.EL_B), j(P.HA_B), isFar = true, band = true)
        c.line(neck, neckTop, 3.6f, AC.SKIN)
        torso()
        if (far != 1) arm(shA, j(P.EL_A), j(P.HA_A), isFar = false, band = false)
        if (far != 2) arm(shB, j(P.EL_B), j(P.HA_B), isFar = false, band = true)
        headFront()
    }
}
