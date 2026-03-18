package com.proxyshare.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import java.util.LinkedList

class TrafficGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val maxPoints = 30
    private val rxPoints = LinkedList<Long>()
    private val txPoints = LinkedList<Long>()

    private val paintRx = Paint().apply {
        color = Color.parseColor("#58A6FF")
        strokeWidth = 3f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }
    private val paintTx = Paint().apply {
        color = Color.parseColor("#F0883E")
        strokeWidth = 3f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }
    private val paintFillRx = Paint().apply {
        color = Color.parseColor("#2058A6FF")
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val paintFillTx = Paint().apply {
        color = Color.parseColor("#20F0883E")
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val paintGrid = Paint().apply {
        color = Color.parseColor("#30363D")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    fun addPoint(rx: Long, tx: Long) {
        rxPoints.add(rx)
        txPoints.add(tx)
        if (rxPoints.size > maxPoints) rxPoints.poll()
        if (txPoints.size > maxPoints) txPoints.poll()
        invalidate()
    }

    fun clear() {
        rxPoints.clear()
        txPoints.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // Grid lines
        for (i in 1..3) {
            val y = h * i / 4
            canvas.drawLine(0f, y, w, y, paintGrid)
        }

        if (rxPoints.size < 2) return

        val maxVal = maxOf(rxPoints.max() ?: 1L, txPoints.max() ?: 1L, 1L).toFloat()
        val step = w / (maxPoints - 1)

        fun drawLine(points: LinkedList<Long>, paint: Paint, fillPaint: Paint) {
            val path = Path()
            val fillPath = Path()
            val offset = maxPoints - points.size
            points.forEachIndexed { i, v ->
                val x = (offset + i) * step
                val y = h - (v / maxVal * (h - 8f)) - 4f
                if (i == 0) { path.moveTo(x, y); fillPath.moveTo(x, h) ; fillPath.lineTo(x, y) }
                else { path.lineTo(x, y); fillPath.lineTo(x, y) }
            }
            val lastX = (offset + points.size - 1) * step
            fillPath.lineTo(lastX, h)
            fillPath.close()
            canvas.drawPath(fillPath, fillPaint)
            canvas.drawPath(path, paint)
        }

        drawLine(txPoints, paintTx, paintFillTx)
        drawLine(rxPoints, paintRx, paintFillRx)
    }
}
