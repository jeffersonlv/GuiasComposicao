package com.jef.guiascomposicao

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/**
 * Camada só de desenho. A janela que a contém é FLAG_NOT_TOUCHABLE,
 * então todos os toques passam direto para a câmera embaixo.
 */
class GuidesView(context: Context) : View(context) {

    var guide: GuideType = GuideType.THIRDS
        set(value) { field = value; invalidate() }

    private val d = resources.displayMetrics.density

    // Linha dupla (contorno escuro + linha clara) para ficar visível em qualquer fundo.
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x88000000.toInt()
        strokeWidth = 3f * d
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xEEFFFFFF.toInt()
        strokeWidth = 1.2f * d
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        when (guide) {
            GuideType.THIRDS -> grid(c, w, h, 1f / 3f, 2f / 3f)
            GuideType.GOLDEN -> {
                val phi = 1.6180339f
                val small = 1f / (phi * phi)   // ~0.382
                grid(c, w, h, small, 1f - small)
            }
            GuideType.DIAGONALS -> diagonals(c, w, h)
            GuideType.PARALLELS -> parallels(c, w, h)
        }
    }

    private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        c.drawLine(x1, y1, x2, y2, shadow)
        c.drawLine(x1, y1, x2, y2, stroke)
    }

    private fun grid(c: Canvas, w: Float, h: Float, a: Float, b: Float) {
        line(c, w * a, 0f, w * a, h)
        line(c, w * b, 0f, w * b, h)
        line(c, 0f, h * a, w, h * a)
        line(c, 0f, h * b, w, h * b)
    }

    /** Diagonais principais + recíprocas (perpendiculares saindo dos outros cantos). */
    private fun diagonals(c: Canvas, w: Float, h: Float) {
        line(c, 0f, 0f, w, h)
        line(c, w, 0f, 0f, h)
        // A parte fora da tela é cortada automaticamente pelo Canvas.
        ray(c, w, 0f, -h, w)
        ray(c, 0f, h, h, -w)
        ray(c, 0f, 0f, h, w)
        ray(c, w, h, -h, -w)
    }

    private fun ray(c: Canvas, x: Float, y: Float, dx: Float, dy: Float) =
        line(c, x, y, x + dx * 4f, y + dy * 4f)

    /** Linhas horizontais paralelas, úteis para alinhar horizonte e prédios. */
    private fun parallels(c: Canvas, w: Float, h: Float) {
        val n = 6
        for (i in 1 until n) {
            val y = h * i / n
            line(c, 0f, y, w, y)
        }
    }
}
