package com.mik.speedtest.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.dosse.speedtest.R

class GaugeView : View {
    private var strokeWidth = 0f
    private var backgroundColor = 0
    private var fillColor = 0
    private var startAngle = 0
    private var angles = 0
    private var maxValue = 0
    private var value = 0

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.GaugeView, 0, 0)
        setStrokeWidth(a.getDimension(R.styleable.GaugeView_gauge_strokeWidth, 10f))
        setBackgroundColor(a.getColor(R.styleable.GaugeView_gauge_backgroundColor, -0x333334))
        setFillColor(a.getColor(R.styleable.GaugeView_gauge_fillColor, -0x1))
        setStartAngle(a.getInt(R.styleable.GaugeView_gauge_startAngle, 135))
        setAngles(a.getInt(R.styleable.GaugeView_gauge_angles, 270))
        setMaxValue(a.getInt(R.styleable.GaugeView_gauge_maxValue, 1000))
    }

    constructor(context: Context?) : super(context) {}

    private var paint: Paint? = null
    private var rect: RectF? = null
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = if (width < height) width.toFloat() else height.toFloat()
        val w = size - 2 * strokeWidth
        val h = size - 2 * strokeWidth
        val radius = if (w < h) w / 2 else h / 2
        if (rect == null) rect = RectF()
        rect!![(width - 2 * strokeWidth) / 2 - radius + strokeWidth, (height - 2 * strokeWidth) / 2 - radius + strokeWidth, (width - 2 * strokeWidth) / 2 - radius + strokeWidth + w] = (height - 2 * strokeWidth) / 2 - radius + strokeWidth + h
        if (paint == null) paint = Paint()
        paint!!.strokeWidth = strokeWidth
        paint!!.isAntiAlias = true
        paint!!.style = Paint.Style.STROKE
        paint!!.strokeCap = Paint.Cap.ROUND
        paint!!.color = backgroundColor
        canvas.drawArc(rect, startAngle.toFloat(), angles.toFloat(), false, paint)
        paint!!.color = fillColor
        canvas.drawArc(rect, startAngle.toFloat(), (startAngle + value * (Math.abs(angles).toDouble() / maxValue) - startAngle).toFloat(), false, paint)
    }

    fun setValue(value: Int) {
        this.value = value
        invalidate()
    }

    fun getValue(): Int {
        return value
    }

    fun getStrokeWidth(): Float {
        return strokeWidth
    }

    fun setStrokeWidth(strokeWidth: Float) {
        this.strokeWidth = strokeWidth
        invalidate()
    }

    fun getBackgroundColor(): Int {
        return backgroundColor
    }

    override fun setBackgroundColor(backgroundColor: Int) {
        this.backgroundColor = backgroundColor
        invalidate()
    }

    fun setFillColor(fillColor: Int) {
        this.fillColor = fillColor
        invalidate()
    }

    fun getFillColor(): Int {
        return fillColor
    }

    fun getStartAngle(): Int {
        return startAngle
    }

    fun setStartAngle(startAngle: Int) {
        this.startAngle = startAngle
        invalidate()
    }

    fun getAngles(): Int {
        return angles
    }

    fun setAngles(angles: Int) {
        this.angles = angles
        invalidate()
    }

    fun getMaxValue(): Int {
        return maxValue
    }

    fun setMaxValue(maxValue: Int) {
        this.maxValue = maxValue
        invalidate()
    }
}