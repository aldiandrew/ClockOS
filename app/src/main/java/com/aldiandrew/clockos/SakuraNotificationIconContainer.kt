package com.aldiandrew.clockos

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.View
import kotlin.math.min

class SakuraNotificationIconContainer(
    context: android.content.Context,
    private val density: Float,
    private val slotSizePx: Int,
    private val iconSpacingPx: Int,
    private val desiredIconHeightPx: Int,
    private val appIconScale: Float = 0.75f
) : View(context) {

    private data class IconEntry(
        val key: String,
        val drawable: Drawable
    )

    private val icons = ArrayList<IconEntry>()
    private var iconTint = Color.WHITE

    private val dotPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

    fun setIcons(
        entries: List<Pair<String, Drawable>>,
        tint: Int
    ) {
        iconTint = tint
        icons.clear()

        entries.forEach { (key, drawable) ->
            val copy = try {
                drawable.mutate()
            } catch (_: Throwable) {
                drawable
            }

            try {
                copy.setTint(tint)
            } catch (_: Throwable) {
            }

            icons += IconEntry(key, copy)
        }

        requestLayout()
        invalidate()
    }

    fun clearIcons() {
        if (icons.isEmpty()) return
        icons.clear()
        requestLayout()
        invalidate()
    }

    fun desiredWidthPx(): Int {
        if (icons.isEmpty()) return 0
        return (
            icons.size * slotSizePx +
                (icons.size - 1).coerceAtLeast(0) * iconSpacingPx
        )
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        val widthLimit =
            MeasureSpec.getSize(widthMeasureSpec)

        val desired =
            desiredWidthPx()

        val width =
            when (MeasureSpec.getMode(widthMeasureSpec)) {
                MeasureSpec.EXACTLY -> widthLimit
                MeasureSpec.AT_MOST ->
                    min(desired, widthLimit)
                else -> desired
            }

        val height =
            when (MeasureSpec.getMode(heightMeasureSpec)) {
                MeasureSpec.EXACTLY ->
                    MeasureSpec.getSize(heightMeasureSpec)
                MeasureSpec.AT_MOST ->
                    min(
                        slotSizePx,
                        MeasureSpec.getSize(heightMeasureSpec)
                    )
                else -> slotSizePx
            }

        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (icons.isEmpty() || width <= 0 || height <= 0) {
            return
        }

        val slotWidth = slotSizePx.coerceAtLeast(1)
        val availableSlots =
            (
                width + iconSpacingPx
            ) / (
                slotWidth + iconSpacingPx
            )

        if (availableSlots <= 0) return

        val overflow =
            icons.size > availableSlots

        val iconCount =
            if (overflow) {
                (availableSlots - 1).coerceAtLeast(0)
            } else {
                icons.size
            }

        var x = 0

        for (index in 0 until iconCount) {
            drawIcon(
                canvas = canvas,
                drawable = icons[index].drawable,
                slotLeft = x
            )
            x += slotWidth + iconSpacingPx
        }

        if (overflow) {
            drawOverflowDot(
                canvas,
                x
            )
        }
    }

    private fun drawIcon(
        canvas: Canvas,
        drawable: Drawable,
        slotLeft: Int
    ) {
        val intrinsicWidth =
            drawable.intrinsicWidth
                .takeIf { it > 0 }
                ?: slotSizePx

        val intrinsicHeight =
            drawable.intrinsicHeight
                .takeIf { it > 0 }
                ?: slotSizePx

        val desiredHeight =
            min(
                desiredIconHeightPx
                    .coerceAtLeast(1),
                slotSizePx
            )

        var scale =
            desiredHeight.toFloat() /
                intrinsicHeight.toFloat()

        if (scale > 1f) {
            scale = 1f
        }

        scale *= appIconScale

        val drawWidth =
            (
                intrinsicWidth * scale
            )
                .toInt()
                .coerceAtLeast(1)

        val drawHeight =
            (
                intrinsicHeight * scale
            )
                .toInt()
                .coerceAtLeast(1)

        val left =
            slotLeft +
                (
                    slotSizePx - drawWidth
                ) / 2

        val top =
            (
                height - drawHeight
            ) / 2

        try {
            drawable.setBounds(
                left,
                top,
                left + drawWidth,
                top + drawHeight
            )
            drawable.draw(canvas)
        } catch (_: Throwable) {
        }
    }

    private fun drawOverflowDot(
        canvas: Canvas,
        slotLeft: Int
    ) {
        dotPaint.color = currentTint()

        val radius =
            (
                slotSizePx * 0.16f
            )
                .coerceAtLeast(
                    density * 1.5f
                )

        val cx =
            slotLeft +
                slotSizePx / 2f

        val cy =
            height / 2f

        canvas.drawCircle(
            cx,
            cy,
            radius,
            dotPaint
        )
    }

    private fun currentTint(): Int = iconTint
}
