package com.rustech.textgrabber

import android.content.Context
import android.view.ViewGroup

/** Simpleng wrap layout: ilalagay ang mga chip sa susunod na linya kapag puno na. */
class FlowLayout(context: Context, private val gap: Int) : ViewGroup(context) {

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val maxW = MeasureSpec.getSize(widthSpec) - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            c.measure(
                MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST),
                MeasureSpec.UNSPECIFIED
            )
            val w = c.measuredWidth
            val h = c.measuredHeight
            if (x > 0 && x + w > maxW) {
                x = 0; y += rowH + gap; rowH = 0
            }
            x += w + gap
            rowH = maxOf(rowH, h)
        }
        setMeasuredDimension(
            MeasureSpec.getSize(widthSpec),
            y + rowH + paddingTop + paddingBottom
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = r - l - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val w = c.measuredWidth
            val h = c.measuredHeight
            if (x > 0 && x + w > maxW) {
                x = 0; y += rowH + gap; rowH = 0
            }
            c.layout(paddingLeft + x, paddingTop + y, paddingLeft + x + w, paddingTop + y + h)
            x += w + gap
            rowH = maxOf(rowH, h)
        }
    }
}
