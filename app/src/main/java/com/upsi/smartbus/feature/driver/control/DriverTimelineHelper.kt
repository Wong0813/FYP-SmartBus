package com.upsi.smartbus.feature.driver.control

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.upsi.smartbus.R

/**
 * Driver Stop Timeline UI Builder.
 * Builds dynamic horizontal stop dots, labels, and directional arrows.
 */
object DriverTimelineHelper {

    fun buildTimeline(
        context: Context,
        container: LinearLayout,
        routeStops: List<String>,
        nextStopIndex: Int,
        routeColor: Int
    ) {
        container.removeAllViews()

        val dp = { n: Int ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, n.toFloat(), context.resources.displayMetrics).toInt()
        }
        val colorDone = ColorUtils.setAlphaComponent(routeColor, 120)
        val colorInactive = Color.parseColor("#CBD5E1")

        for ((idx, abbr) in routeStops.withIndex()) {
            val isPassed = idx < nextStopIndex
            val isNext = idx == nextStopIndex
            val isActive = isNext

            val stopLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }

            val dotSize = if (isNext) dp(14) else dp(9)
            val dot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).also {
                    it.gravity = Gravity.CENTER_HORIZONTAL
                }
                background = when {
                    isNext -> ContextCompat.getDrawable(context, R.drawable.ic_map_stop_next)
                    isPassed -> createCircle(colorDone)
                    isActive -> createCircle(routeColor)
                    else -> createCircle(colorInactive)
                }
            }

            val label = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also {
                    it.topMargin = dp(3)
                    it.gravity = Gravity.CENTER_HORIZONTAL
                }
                text = abbr
                textSize = if (isNext) 10f else 9f
                gravity = Gravity.CENTER
                typeface = if (isNext) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(
                    when {
                        isNext -> routeColor
                        isPassed -> colorDone
                        else -> colorInactive
                    }
                )
            }

            stopLayout.addView(dot)
            stopLayout.addView(label)

            val wrapper = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(3), 0, dp(3), 0)
            }
            wrapper.addView(stopLayout)
            container.addView(wrapper)

            if (idx < routeStops.size - 1) {
                val isArrowActive = (idx == nextStopIndex - 1)
                val isArrowDone = (idx < nextStopIndex - 1)
                val arrow = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, dp(20)
                    ).also { it.gravity = Gravity.CENTER_VERTICAL }
                    text = " ➔ "
                    textSize = if (isArrowActive) 14f else 11f
                    setTextColor(
                        when {
                            isArrowActive -> routeColor
                            isArrowDone -> colorDone
                            else -> colorInactive
                        }
                    )
                    typeface = if (isArrowActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                }
                container.addView(arrow)
            }
        }
    }

    private fun createCircle(color: Int): ShapeDrawable {
        return ShapeDrawable(OvalShape()).apply {
            paint.color = color
        }
    }
}
