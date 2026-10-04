package com.jef.guiascomposicao

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Camada só de desenho. A janela que a contém é FLAG_NOT_TOUCHABLE,
 * então todos os toques passam direto para a câmera embaixo.
 */
class GuidesView(context: Context) : View(context) {

    var guide: GuideType = GuideType.THIRDS
        set(value) { field = value; invalidate() }

    /** 0 = normal, 1 = espelho horizontal, 2 = espelho vertical, 3 = ambos (giro 180°). */
    var orientation: Int = 0
        set(value) { field = ((value % 4) + 4) % 4; invalidate() }

    /** Rotação em passos de 90°: 0 = 0°, 1 = 90°, 2 = 180°, 3 = 270°. */
    var quarterTurns: Int = 0
        set(value) { field = ((value % 4) + 4) % 4; invalidate() }

    private val d = resources.displayMetrics.density
    private val phi = 1.6180339f

    // Linha dupla (contorno escuro + linha clara) para ficar visível em qualquer fundo.
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x88000000.toInt()
        strokeWidth = 3f * d
        strokeCap = Paint.Cap.ROUND
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xEEFFFFFF.toInt()
        strokeWidth = 1.2f * d
        strokeCap = Paint.Cap.ROUND
    }
    private val dashShadow = Paint(shadow).apply { pathEffect = DashPathEffect(floatArrayOf(8 * d, 6 * d), 0f) }
    private val dashStroke = Paint(stroke).apply { pathEffect = DashPathEffect(floatArrayOf(8 * d, 6 * d), 0f) }

    private val path = Path()
    private val oval = RectF()

    override fun onDraw(c: Canvas) {
        val screenW = width.toFloat()
        val screenH = height.toFloat()
        if (screenW <= 0f || screenH <= 0f) return

        // Em 90° e 270° a área de desenho troca largura/altura,
        // para o guia girado continuar cobrindo a tela inteira.
        val swap = quarterTurns % 2 == 1
        val w = if (swap) screenH else screenW
        val h = if (swap) screenW else screenH

        c.save()
        c.translate(screenW / 2f, screenH / 2f)
        c.rotate(90f * quarterTurns)
        c.translate(-w / 2f, -h / 2f)

        val sx = if (orientation == 1 || orientation == 3) -1f else 1f
        val sy = if (orientation >= 2) -1f else 1f
        c.scale(sx, sy, w / 2f, h / 2f)

        when (guide) {
            GuideType.THIRDS -> grid(c, w, h, 1f / 3f)
            GuideType.GOLDEN_SECTION -> grid(c, w, h, 1f / (phi * phi)) // ~0.382
            GuideType.GOLDEN_TRIANGLES -> {
                line(c, 0f, h, w, 0f)
                ray(c, 0f, 0f, h, w)
                ray(c, w, h, -h, -w)
            }
            GuideType.SPIRAL_SECTION -> spiral(c, w, h, withArcs = false)
            GuideType.GOLDEN_SPIRAL -> spiral(c, w, h, withArcs = true)
            GuideType.HARMONIOUS_TRIANGLES -> {
                line(c, 0f, 0f, w, h)
                ray(c, w, 0f, -h, w)
                ray(c, 0f, h, h, -w)
            }
            GuideType.CROSS -> {
                line(c, w / 2f, 0f, w / 2f, h)
                line(c, 0f, h / 2f, w, h / 2f)
            }
            GuideType.FOCAL_MASS -> focalMass(c, w, h)
            GuideType.V_SHAPE -> {
                line(c, w * 0.2f, 0f, w * 0.5f, h)
                line(c, w * 0.8f, 0f, w * 0.5f, h)
            }
            GuideType.DIAGONAL -> {
                line(c, 0f, 0f, w, h * 0.85f)
                line(c, 0f, 0f, w * 0.85f, h)
            }
            GuideType.RADIAL -> radial(c, w, h)
            GuideType.L_SHAPE -> {
                line(c, w * 0.3f, h * 0.15f, w * 0.3f, h * 0.75f)
                line(c, w * 0.3f, h * 0.75f, w * 0.85f, h * 0.75f)
            }
            GuideType.S_CURVE -> {
                path.reset()
                path.moveTo(w * 0.7f, h * 0.1f)
                path.cubicTo(w * 0.2f, h * 0.15f, w * 0.2f, h * 0.45f, w * 0.5f, h * 0.5f)
                path.cubicTo(w * 0.85f, h * 0.55f, w * 0.8f, h * 0.88f, w * 0.3f, h * 0.9f)
                drawPath(c, path)
            }
            GuideType.PYRAMID -> {
                path.reset()
                path.moveTo(w * 0.5f, h * 0.15f)
                path.lineTo(w * 0.15f, h * 0.85f)
                path.lineTo(w * 0.85f, h * 0.85f)
                path.close()
                drawPath(c, path)
            }
            GuideType.CIRCULAR -> {
                val r = min(w, h) * 0.3f
                c.drawCircle(w / 2f, h / 2f, r, shadow)
                c.drawCircle(w / 2f, h / 2f, r, stroke)
            }
            GuideType.PARALLELS -> {
                for (i in 1 until 6) {
                    val y = h * i / 6f
                    line(c, 0f, y, w, y)
                }
            }
        }
        c.restore()
    }

    // ---------- primitivas ----------

    private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        c.drawLine(x1, y1, x2, y2, shadow)
        c.drawLine(x1, y1, x2, y2, stroke)
    }

    /** Reta saindo de (x,y) na direção (dx,dy); o que passa da tela é cortado pelo Canvas. */
    private fun ray(c: Canvas, x: Float, y: Float, dx: Float, dy: Float) =
        line(c, x, y, x + dx * 4f, y + dy * 4f)

    private fun drawPath(c: Canvas, p: Path) {
        c.drawPath(p, shadow)
        c.drawPath(p, stroke)
    }

    /** Quarto de elipse com centro (cx,cy), raios (rx,ry), começando em startAngle. */
    private fun arc(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, startAngle: Float) {
        oval.set(cx - rx, cy - ry, cx + rx, cy + ry)
        c.drawArc(oval, startAngle, 90f, false, shadow)
        c.drawArc(oval, startAngle, 90f, false, stroke)
    }

    // ---------- guias compostos ----------

    private fun grid(c: Canvas, w: Float, h: Float, a: Float) {
        val b = 1f - a
        line(c, w * a, 0f, w * a, h)
        line(c, w * b, 0f, w * b, h)
        line(c, 0f, h * a, w, h * a)
        line(c, 0f, h * b, w, h * b)
    }

    /**
     * Subdivisão áurea: a cada passo corta 61,8% do retângulo restante,
     * girando o lado do corte (esquerda → baixo → direita → cima).
     * Com withArcs, desenha um quarto de elipse em cada pedaço, formando a espiral.
     * Funciona em qualquer proporção de tela (retrato ou paisagem).
     */
    private fun spiral(c: Canvas, w: Float, h: Float, withArcs: Boolean) {
        val k = 1f / phi
        var x0 = 0f; var y0 = 0f; var x1 = w; var y1 = h
        var step = if (w >= h) 0 else 3 // 0=esq, 1=baixo, 2=dir, 3=cima
        repeat(10) {
            val rw = x1 - x0
            val rh = y1 - y0
            if (rw < 2f || rh < 2f) return
            when (step) {
                0 -> { val a = rw * k
                    line(c, x0 + a, y0, x0 + a, y1)
                    if (withArcs) arc(c, x0 + a, y0, a, rh, 90f)
                    x0 += a }
                1 -> { val b = rh * k
                    line(c, x0, y1 - b, x1, y1 - b)
                    if (withArcs) arc(c, x0, y1 - b, rw, b, 0f)
                    y1 -= b }
                2 -> { val a = rw * k
                    line(c, x1 - a, y0, x1 - a, y1)
                    if (withArcs) arc(c, x1 - a, y1, a, rh, 270f)
                    x1 -= a }
                3 -> { val b = rh * k
                    line(c, x0, y0 + b, x1, y0 + b)
                    if (withArcs) arc(c, x1, y0 + b, rw, b, 180f)
                    y0 += b }
            }
            step = (step + 1) % 4
        }
    }

    /** Zona de massa focal (elipse tracejada) com pontos de "dispersão" à direita. */
    private fun focalMass(c: Canvas, w: Float, h: Float) {
        val cx = w * 0.42f
        val cy = h * 0.5f
        oval.set(cx - w * 0.2f, cy - h * 0.08f, cx + w * 0.2f, cy + h * 0.08f)
        c.drawOval(oval, dashShadow)
        c.drawOval(oval, dashStroke)
        val r = 2.5f * d
        for (fx in floatArrayOf(0.7f, 0.78f, 0.86f, 0.94f)) {
            c.drawCircle(w * fx, cy, r, shadow)
            c.drawCircle(w * fx, cy, r, stroke)
        }
    }

    private fun radial(c: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) * 0.4f
        for (deg in intArrayOf(0, 45, 90, 135)) {
            val rad = Math.toRadians(deg.toDouble())
            val dx = (cos(rad) * r).toFloat()
            val dy = (sin(rad) * r).toFloat()
            line(c, cx - dx, cy - dy, cx + dx, cy + dy)
        }
    }
}