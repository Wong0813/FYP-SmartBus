package com.upsi.smartbus.feature.driver.control

import android.annotation.SuppressLint
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import com.upsi.smartbus.core.util.BusSpeedCalculator
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Driver D-Pad Simulation Physics & Velocity Controller.
 * Handles auto-repeat touch gestures, step-wise GPS delta movements,
 * and realistic physical speed calculation with smoothing.
 */
class DriverSimulationController(
    private val handler: Handler
) {
    val stepSizes = doubleArrayOf(0.0002, 0.0005, 0.001)
    val stepLabels = arrayOf("~20m", "~50m", "~100m")
    var stepSizeIndex = 0
        private set

    private var isRepeating = false
    private val repeatDelay = 150L
    private var currentMoveAction: (() -> Unit)? = null

    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (isRepeating) {
                currentMoveAction?.invoke()
                handler.postDelayed(this, repeatDelay)
            }
        }
    }

    fun toggleStepSize(): String {
        stepSizeIndex = (stepSizeIndex + 1) % stepSizes.size
        return stepLabels[stepSizeIndex]
    }

    val currentStepSize: Double
        get() = stepSizes[stepSizeIndex]

    @SuppressLint("ClickableViewAccessibility")
    fun attachRepeatButton(view: View, action: () -> Unit) {
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isRepeating = true
                    currentMoveAction = action
                    action() // Immediate first move
                    handler.postDelayed(repeatRunnable, 300) // Start repeating after 300ms
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isRepeating = false
                    currentMoveAction = null
                    handler.removeCallbacks(repeatRunnable)
                    true
                }
                else -> false
            }
        }
    }

    fun stopRepeating() {
        isRepeating = false
        currentMoveAction = null
        handler.removeCallbacks(repeatRunnable)
    }

    /**
     * Calculates displacement towards target stop.
     * Returns Pair(newLat, newLon).
     */
    fun calculateMoveTowards(
        curLat: Double, curLon: Double,
        targetLat: Double?, targetLon: Double?,
        forward: Boolean
    ): Pair<Double, Double> {
        val step = currentStepSize
        if (targetLat != null && targetLon != null) {
            val dLat = targetLat - curLat
            val dLon = targetLon - curLon
            val dist = hypot(dLat, dLon)

            if (dist > 0.00001) {
                val dirLat = dLat / dist
                val dirLon = dLon / dist
                val mult = if (forward) 1.0 else -1.0
                return Pair(curLat + dirLat * step * mult, curLon + dirLon * step * mult)
            }
        }
        val mult = if (forward) 1.0 else -1.0
        return Pair(curLat + step * mult, curLon)
    }

    /**
     * Computes speed based on physical displacement and time delta.
     */
    fun computeVelocity(
        lastLat: Double, lastLon: Double, lastTimeMs: Long,
        curLat: Double, curLon: Double, nowMs: Long,
        dutyStatus: String
    ): Int {
        if (dutyStatus != "WORKING") return 0

        val baseSpeed = BusSpeedCalculator.determineSimulatedSpeed(stepSizeIndex, dutyStatus)
        if (lastTimeMs <= 0 || (lastLat == 0.0 && lastLon == 0.0)) {
            return baseSpeed
        }

        val rawMeasuredSpeed = BusSpeedCalculator.calculateGpsSpeedKmh(
            lastLat, lastLon, lastTimeMs,
            curLat, curLon, nowMs
        )

        return if (rawMeasuredSpeed in 10.0..75.0) {
            BusSpeedCalculator.smoothSpeed(baseSpeed.toDouble(), rawMeasuredSpeed, 0.6).roundToInt()
        } else {
            baseSpeed
        }
    }
}
