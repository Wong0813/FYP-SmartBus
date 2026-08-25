package com.upsi.smartbus.core.util

import android.content.Context
import android.graphics.*
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.upsi.smartbus.core.model.Bus
import java.util.Locale

data class MarkerResult(
    val descriptor: BitmapDescriptor,
    val anchorX: Float,
    val anchorY: Float
)

object MapMarkerHelper {

    /**
     * Creates a custom composite map marker with a floating info card above
     * the GPS pulse circle dot showing speed, next stop, and distance.
     */
    fun createBusMarkerWithInfoCard(
        ctx: Context,
        bus: Bus,
        color: Int,
        isSelected: Boolean
    ): MarkerResult {
        val density = ctx.resources.displayMetrics.density
        val routeName = bus.routeName.ifEmpty { bus.name.ifEmpty { bus.id } }
        val status = bus.status.lowercase()
        val isWorking = status == "working"
        val isResting = status == "resting"
        val isSpeeding = bus.speed > 30.0 && isWorking

        // Prepare subtitle text (Speed, Next Stop, Distance)
        val subText = when {
            isWorking -> {
                val speedVal = bus.speed.toInt()
                val nextStopName = bus.nextStop.trim()
                val dist = bus.distanceToNext
                val distStr = if (dist > 0.0) "${String.format(Locale.ENGLISH, "%.1f", dist)} km" else ""

                when {
                    nextStopName.isNotEmpty() && distStr.isNotEmpty() ->
                        "$speedVal km/h • Next: $nextStopName ($distStr)"
                    nextStopName.isNotEmpty() ->
                        "$speedVal km/h • Next: $nextStopName"
                    else ->
                        "$speedVal km/h • Live"
                }
            }
            isResting -> "⏸ Resting at depot"
            else -> "⚪ Offline / Depot"
        }

        // Text Paints
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.parseColor("#0F172A")
            textSize = 10.5f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = when {
                isSpeeding -> Color.parseColor("#DC2626")
                isWorking -> Color.parseColor("#059669")
                isResting -> Color.parseColor("#D97706")
                else -> Color.parseColor("#64748B")
            }
            textSize = 9.0f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val titleW = titlePaint.measureText(routeName)
        val subW = subPaint.measureText(subText)
        val textWidth = maxOf(titleW + 20 * density, subW)
        val cardPaddingH = 10 * density
        val cardWidth = textWidth + cardPaddingH * 2
        val cardHeight = 36 * density
        val cardRadius = 8 * density
        val pointerH = 5 * density
        val pointerW = 8 * density
        val dotDiameter = (if (isSelected) 26 else 22) * density
        val dotRadius = dotDiameter / 2f
        val gap = 2 * density
        val shadowMargin = 4 * density

        val totalW = maxOf(cardWidth + shadowMargin * 2, dotDiameter + shadowMargin * 2)
        val totalH = cardHeight + pointerH + gap + dotDiameter + shadowMargin * 2

        val bitmap = Bitmap.createBitmap(totalW.toInt().coerceAtLeast(1), totalH.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = totalW / 2f

        val cardLeft = cx - cardWidth / 2f
        val cardTop = shadowMargin
        val cardRight = cx + cardWidth / 2f
        val cardBottom = cardTop + cardHeight
        val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)

        // Drop shadow for card
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.parseColor("#28000000")
            maskFilter = BlurMaskFilter(2.5f * density, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(
            RectF(cardLeft, cardTop + 1.5f * density, cardRight, cardBottom + 1.5f * density),
            cardRadius, cardRadius, shadowPaint
        )

        // Card White Background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, bgPaint)

        // Card Border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = if (isSelected) color else Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = if (isSelected) 1.8f * density else 1.0f * density
        }
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, borderPaint)

        // Left accent bar (colored strip)
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val accentRect = RectF(cardLeft + 1.5f * density, cardTop + 4 * density, cardLeft + 4.5f * density, cardBottom - 4 * density)
        canvas.drawRoundRect(accentRect, 1.5f * density, 1.5f * density, accentPaint)

        // Status dot inside card
        val statusDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = when {
                isResting -> Color.parseColor("#FF9800")
                isWorking -> Color.parseColor("#10B981")
                else -> Color.parseColor("#94A3B8")
            }
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cardLeft + 12 * density, cardTop + 12.5f * density, 3 * density, statusDotPaint)

        // Route Title Text
        canvas.drawText(routeName, cardLeft + 18.5f * density, cardTop + 15.5f * density, titlePaint)

        // Subtitle (Speed, Next Stop, Distance)
        canvas.drawText(subText, cardLeft + 10 * density, cardTop + 28.5f * density, subPaint)

        // Pointer Triangle
        val pointerPath = Path().apply {
            moveTo(cx - pointerW / 2f, cardBottom - 0.5f)
            lineTo(cx, cardBottom + pointerH)
            lineTo(cx + pointerW / 2f, cardBottom - 0.5f)
            close()
        }
        canvas.drawPath(pointerPath, bgPaint)
        canvas.drawLine(cx - pointerW / 2f, cardBottom - 0.5f, cx, cardBottom + pointerH, borderPaint)
        canvas.drawLine(cx, cardBottom + pointerH, cx + pointerW / 2f, cardBottom - 0.5f, borderPaint)

        // GPS Pulse Circle Dot at bottom
        val dotCy = cardBottom + pointerH + gap + dotRadius

        // 1. Outer aura
        val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            alpha = 45
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, dotCy, dotRadius + 3.5f * density, auraPaint)

        // 2. Middle ring
        val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            alpha = 130
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, dotCy, dotRadius - 1.5f * density, midPaint)

        // 3. Inner solid core
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, dotCy, dotRadius - 4.5f * density, corePaint)

        // 4. White stroke
        val whiteStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.0f * density
        }
        canvas.drawCircle(cx, dotCy, dotRadius - 4.5f * density, whiteStroke)

        val anchorX = 0.5f
        val anchorY = dotCy / totalH

        return MarkerResult(
            descriptor = BitmapDescriptorFactory.fromBitmap(bitmap),
            anchorX = anchorX,
            anchorY = anchorY
        )
    }
}
