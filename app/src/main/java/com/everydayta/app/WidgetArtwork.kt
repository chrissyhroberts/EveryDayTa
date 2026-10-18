package com.everydayta.app

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.content.res.Configuration
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Renders one year at widget-host dimensions, without altering cell or text proportions. */
object WidgetArtwork {
    private const val TEAL = 0xff11695d.toInt()
    private const val GREY = 0xffe1e7e4.toInt()
    private const val MUTED = 0xff71847d.toInt()
    private val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun render(context: Context, tracker: Tracker?, entries: Map<LocalDate, Int>,
               year: Int, layout: Int, options: Bundle): Bitmap {
        // Values are dp for the *entire* widget. Subtract the same paddings/header
        // that widget.xml uses so our image keeps the correct physical aspect ratio.
        // Android reports portrait (min width, MAX height) and landscape
        // (max width, MIN height) bounds, not the actual view dimensions.
        // Using both MIN values made tall calendar widgets render as tiny
        // letterboxed images inside a large ImageView (fitCenter).
        val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val (widgetW, widgetH) = WidgetLayoutRules.hostDimensions(
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250),
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 150),
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0),
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0),
            portrait
        )
        val imageW = (widgetW - 28).coerceAtLeast(90)
        val imageH = (widgetH - 48).coerceAtLeast(38)
        // Small enough for RemoteViews while retaining readable month captions.
        val scale = min(1.8f, context.resources.displayMetrics.density)
        val width = (imageW * scale).toInt().coerceIn(130, 1000)
        val height = (imageH * scale).toInt().coerceIn(72, 1250)
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawColor(Color.TRANSPARENT)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val colorFor: (LocalDate) -> Int = { day ->
            if (tracker == null) GREY else Palettes.color(tracker, entries[day] ?: 0)
        }
        when (layout) {
            1 -> renderBlock(canvas, width.toFloat(), height.toFloat(), year, colorFor, paint)
            2 -> renderMonths(canvas, width.toFloat(), height.toFloat(), year, colorFor, paint)
            else -> renderWeeks(canvas, width.toFloat(), height.toFloat(), year, colorFor, paint)
        }
        return image
    }

    private fun cell(canvas: Canvas, x: Float, y: Float, width: Float, height: Float,
                     color: Int, paint: Paint) {
        val gap = max(1.2f, min(width, height) * 0.11f)
        val r = min(3.5f, min(width, height) * 0.18f)
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(x + gap/2, y + gap/2,
            x + width - gap/2, y + height - gap/2), r, r, paint)
    }

    private fun renderBlock(canvas: Canvas, w: Float, h: Float, year: Int,
                            colors: (LocalDate) -> Int, paint: Paint) {
        val jan = LocalDate.of(year, 1, 1)
        val days = jan.lengthOfYear()
        val columns = WidgetLayoutRules.blockColumns(w.toInt(), h.toInt(), days)
        val rows = (days + columns - 1) / columns
        val size = min(w / columns, h / rows)
        val originX = (w - size * columns) / 2
        val originY = (h - size * rows) / 2
        for (i in 0 until days) {
            cell(canvas, originX + (i % columns) * size,
                originY + (i / columns) * size, size, size,
                colors(jan.plusDays(i.toLong())), paint)
        }
    }

    private fun renderWeeks(canvas: Canvas, w: Float, h: Float, year: Int,
                            colors: (LocalDate) -> Int, paint: Paint) {
        val jan = LocalDate.of(year, 1, 1)
        val weeks = WidgetLayoutRules.weekCount(year)
        // Reserve a narrow column for the weekday initials at the left of
        // *each* band. Keep the normal Monday-first heatmap alignment.
        val labelW = min(20f, max(12f, w * 0.048f))
        val gridAreaW = (w - labelW).coerceAtLeast(20f)
        val bands = WidgetLayoutRules.weekBands(gridAreaW.toInt(), h.toInt(), year)
        val colsPerBand = ceil(weeks.toDouble() / bands).toInt()
        val captionH = if (bands == 1) 0f else min(16f, h * 0.042f)
        val bandGap = if (bands == 1) 0f else max(2f, h * 0.012f)
        val usableH = h - bands*captionH - (bands-1)*bandGap
        val square = min(gridAreaW / colsPerBand, usableH / (bands * 7))
        val x0 = (w - (labelW + square * colsPerBand)) / 2f
        val gridX = x0 + labelW
        val totalGridH = bands * (7 * square + captionH) + (bands-1) * bandGap
        val y0 = (h - totalGridH) / 2f

        paint.style = Paint.Style.FILL
        paint.isFakeBoldText = false
        paint.color = MUTED
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = min(12f, (square * 0.82f).coerceAtLeast(3f))
        val weekdays = arrayOf("M", "T", "W", "T", "F", "S", "S")
        for (band in 0 until bands) {
            val top = y0 + band * (7 * square + captionH + bandGap) + captionH
            for (day in weekdays.indices) {
                // Text baseline centred vertically in each day row.
                val baseline = top + day * square + square / 2f -
                    (paint.ascent() + paint.descent()) / 2f
                canvas.drawText(weekdays[day], x0 + labelW / 2f, baseline, paint)
            }
        }
        paint.textAlign = Paint.Align.LEFT

        if (bands > 1) {
            paint.color = MUTED
            paint.isFakeBoldText = true
            paint.textSize = min(13f, max(8f, captionH * 0.76f))
            for (band in 0 until bands) {
                val startWeek = band * colsPerBand
                val startDate = jan.minusDays((jan.dayOfWeek.value - 1).toLong())
                    .plusDays(startWeek * 7L)
                val endDate = startDate.plusDays((colsPerBand * 7 - 1).toLong())
                    .coerceAtMost(LocalDate.of(year, 12, 31))
                val name = "${months[startDate.monthValue-1]}–${months[endDate.monthValue-1]}"
                val y = y0 + band * (7 * square + captionH + bandGap)
                canvas.drawText(name, gridX + 2f, y + captionH * 0.78f, paint)
            }
            paint.isFakeBoldText = false
        }
        for (i in 0 until jan.lengthOfYear()) {
            val (week, weekday) = WidgetLayoutRules.weekCell(year, i + 1)
            val band = week / colsPerBand
            val col = week % colsPerBand
            val x = gridX + col * square
            val y = y0 + band * (7 * square + captionH + bandGap) + captionH + weekday * square
            cell(canvas, x, y, square, square, colors(jan.plusDays(i.toLong())), paint)
        }
    }

    /** For one-row widgets, show the entire year as twelve tiny but legible mini-months.
     * This avoids shrinking a 3x4 wall calendar into invisible postage stamps.
     */
    private fun renderCompactMonths(canvas: Canvas, w: Float, h: Float, year: Int,
                                    colors: (LocalDate) -> Int, paint: Paint) {
        val cols = if (w >= 360f) 12 else 6
        val rows = 12 / cols
        val gutterX = max(2f, w * .003f)
        val gutterY = if (rows > 1) max(2f, h * .02f) else 0f
        val panelW = (w - (cols - 1) * gutterX) / cols
        val panelH = (h - (rows - 1) * gutterY) / rows
        val labelH = min(16f, max(7f, panelH * .21f))
        val square = min((panelW - 1f) / 7f, (panelH - labelH) / 6f).coerceAtLeast(.5f)
        paint.textAlign = Paint.Align.CENTER
        for (month in 1..12) {
            val index = month - 1
            val col = index % cols
            val row = index / cols
            val left = col * (panelW + gutterX)
            val top = row * (panelH + gutterY)
            val gridX = left + (panelW - 7 * square) / 2f
            val gridY = top + labelH + (panelH - labelH - 6 * square) / 2f
            paint.color = TEAL
            paint.isFakeBoldText = true
            paint.textSize = min(13f, max(6f, min(labelH * .76f, panelW * .18f)))
            canvas.drawText(months[index], left + panelW / 2f, top + labelH * .80f, paint)
            paint.isFakeBoldText = false
            val ym = YearMonth.of(year, month)
            val offset = ym.atDay(1).dayOfWeek.value - 1
            for (day in 1..ym.lengthOfMonth()) {
                val position = offset + day - 1
                cell(canvas, gridX + (position % 7) * square,
                    gridY + (position / 7) * square,
                    square, square, colors(ym.atDay(day)), paint)
            }
        }
        paint.textAlign = Paint.Align.LEFT
    }

    private fun renderMonths(canvas: Canvas, w: Float, h: Float, year: Int,
                             colors: (LocalDate) -> Int, paint: Paint) {
        if (WidgetLayoutRules.isCompactCalendar(w.toInt(), h.toInt())) {
            renderCompactMonths(canvas, w, h, year, colors, paint)
            return
        }
        val monthCols = WidgetLayoutRules.calendarColumns(w.toInt(), h.toInt())
        val monthRows = 12/monthCols
        // Compact gutters, so the month grids grow to fill the widget.
        val gutterX = max(3f, w*0.008f)
        val gutterY = max(4f, h*0.009f)
        val monthW = (w-(monthCols-1)*gutterX)/monthCols
        val monthH = (h-(monthRows-1)*gutterY)/monthRows
        val labelH = min(22f, max(11f, monthH*0.16f))
        val square = min(monthW/7f, (monthH-labelH)/6f)
        val gridW = square*7
        val gridH = square*6
        for (month in 1..12) {
            val mx = (month-1)%monthCols
            val my = (month-1)/monthCols
            val cellLeft = mx*(monthW+gutterX)
            val cellTop = my*(monthH+gutterY)
            val x0 = cellLeft+(monthW-gridW)/2
            val y0 = cellTop+labelH+(monthH-labelH-gridH)/2
            paint.color = TEAL
            paint.style = Paint.Style.FILL
            paint.isFakeBoldText = true
            paint.textSize = min(17f, max(9f, labelH*0.80f))
            canvas.drawText(months[month-1], x0+2, cellTop+labelH*0.82f, paint)
            paint.isFakeBoldText = false
            val ym = YearMonth.of(year,month)
            val offset = ym.atDay(1).dayOfWeek.value-1
            for (d in 1..ym.lengthOfMonth()) {
                val pos = offset+d-1
                cell(canvas, x0+(pos%7)*square, y0+(pos/7)*square,
                    square, square, colors(ym.atDay(d)), paint)
            }
        }
    }
}
