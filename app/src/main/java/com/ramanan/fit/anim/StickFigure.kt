package com.ramanan.fit.anim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import com.ramanan.fit.anim.PoseLibrary as P

/**
 * Draws one frame of an exercise animation (cartoon avatar or stick figure). The caller supplies [timeMs] (time into the
 * current phase, from the workout's monotonic clock), so the figure freezes exactly when
 * the timer is paused and resumes from the same pose.
 */
@Composable
fun StickFigure(
    exerciseId: String,
    timeMs: Long,
    color: Color,
    modifier: Modifier = Modifier,
    groundColor: Color = color.copy(alpha = 0.35f),
    /** true = cartoon avatar, false = simple stick figure */
    avatar: Boolean = true,
) {
    val context = LocalContext.current
    val anim = remember(exerciseId) { P.get(context, exerciseId) }
    if (anim == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Animation guide missing for $exerciseId", color = Color(0xFFFFB4A9))
        }
        return
    }
    val buf = remember(anim) { FloatArray(anim.jointCount * 2) }
    Canvas(modifier) {
        val far = anim.sample(timeMs, buf)
        if (avatar) {
            val s = minOf(size.width / P.canvasW, size.height / P.canvasH)
            val ox = (size.width - P.canvasW * s) / 2f
            val oy = (size.height - P.canvasH * s) / 2f
            drawLine(groundColor, Offset(ox + 4 * s, oy + P.groundY * s), Offset(ox + (P.canvasW - 4) * s, oy + P.groundY * s), strokeWidth = 1.5f * s)
            drawAvatar(buf, anim.view == "side", far, s, ox, oy)
        } else {
            drawFigure(buf, anim.view == "side", far, color, groundColor)
        }
    }
}

private fun DrawScope.drawFigure(p: FloatArray, side: Boolean, far: Int, near: Color, ground: Color) {
    val s = minOf(size.width / P.canvasW, size.height / P.canvasH)
    val ox = (size.width - P.canvasW * s) / 2f
    val oy = (size.height - P.canvasH * s) / 2f
    fun pt(j: Int) = Offset(ox + p[2 * j] * s, oy + p[2 * j + 1] * s)
    val w = 4.2f * s
    val dim = near.copy(alpha = 0.45f)

    fun chain(c: Color, vararg js: Int) {
        for (i in 0 until js.size - 1) drawLine(c, pt(js[i]), pt(js[i + 1]), strokeWidth = w, cap = StrokeCap.Round)
    }

    drawLine(ground, Offset(ox + 4 * s, oy + P.groundY * s), Offset(ox + (P.canvasW - 4) * s, oy + P.groundY * s), strokeWidth = 1.5f * s)

    val armAFar = side || far == 1
    val armBFar = !side && far == 2
    // far limbs first so near limbs draw on top
    chain(if (armAFar) dim else near, P.SH_A, P.EL_A, P.HA_A)
    chain(if (side) dim else near, P.HIP_A, P.KN_A, P.AN_A, P.FT_A)
    if (armBFar) chain(dim, P.SH_B, P.EL_B, P.HA_B)
    chain(near, P.NECK, P.PELVIS)
    if (!side) { chain(near, P.SH_A, P.SH_B); chain(near, P.HIP_A, P.HIP_B) }
    chain(near, P.HIP_B, P.KN_B, P.AN_B, P.FT_B)
    if (!armBFar) chain(near, P.SH_B, P.EL_B, P.HA_B)

    drawCircle(near, radius = P.headRadius * s, center = pt(P.HEAD), style = Stroke(width = w))
    drawCircle(Color(0xFFFFD27A), radius = 1.4f * s, center = pt(P.NOSE))
}
