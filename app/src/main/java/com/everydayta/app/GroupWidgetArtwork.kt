package com.everydayta.app

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max
import kotlin.math.min

/** A group month as a horizontal diary: fixed-width tracker names, one score row each. */
object GroupWidgetArtwork {
    private const val MUTED = 0xff667d76.toInt()
    private const val INK = 0xff1b4039.toInt()
    private const val GREY = 0xffe2e8e4.toInt()
    private const val TEAL = 0xff176b60.toInt()

    fun render(context: Context, members: List<Tracker>, observations: Map<Long, Map<LocalDate, Int>>,
               month: YearMonth, options: Bundle): Bitmap {
        val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val (outerW, outerH) = WidgetLayoutRules.hostDimensions(
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250),
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 150),
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0),
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0), portrait)
        val density = min(1.8f, context.resources.displayMetrics.density).coerceAtLeast(1f)
        val w = ((outerW - 28) * density).toInt().coerceIn(130, 1000)
        val h = ((outerH - 48) * density).toInt().coerceIn(72, 1100)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (members.isEmpty()) {
            paint.color = MUTED
            paint.textSize = 16f
            canvas.drawText("No trackers in this group", 8f, h / 2f, paint)
            return bitmap
        }
        val labelW = GroupMonthRules.labelWidth(w)
        val gridX = labelW + 4f
        val gridW = (w - gridX - 2f).coerceAtLeast(40f)
        val cellW = gridW / month.lengthOfMonth()
        val headerH = min(22f, max(14f, h * .19f))
        // If not all members fit, reserve one row for an explicit overflow notice.
        val possibleRows = GroupMonthRules.visibleRows(members.size, h)
        val shown = if (possibleRows < members.size) (possibleRows - 1).coerceAtLeast(1) else members.size
        val hasOverflow = shown < members.size
        val totalRows = shown + if (hasOverflow) 1 else 0
        val rowH = ((h - headerH) / totalRows).coerceAtLeast(8f)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = min(13f, max(8f, cellW * .72f))
        paint.color = MUTED
        // Mark dates without cramming a 31-number header into tiny widgets.
        for (day in 1..month.lengthOfMonth()) {
            if (day == 1 || day == month.lengthOfMonth() || day % 5 == 0) {
                canvas.drawText(day.toString(), gridX + (day - .5f) * cellW,
                    headerH - 3f, paint)
            }
        }
        paint.textAlign = Paint.Align.LEFT
        members.take(shown).forEachIndexed { row, tracker ->
            val y = headerH + row * rowH
            paint.isFakeBoldText = true
            paint.textSize = min(15f, max(9f, rowH * .54f))
            paint.color = INK
            var name = tracker.name
            val usable = labelW - 8f
            while (name.length > 1 && paint.measureText(name) > usable) {
                name = name.dropLast(2) + "…"
            }
            val baseline = y + rowH / 2f - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(name, 2f, baseline, paint)
            paint.isFakeBoldText = false
            for (day in 1..month.lengthOfMonth()) {
                val date = month.atDay(day)
                val value = observations[tracker.id]?.get(date) ?: 0
                paint.color = if (value == 0) GREY else Palettes.color(tracker, value)
                paint.style = Paint.Style.FILL
                val left = gridX + (day - 1) * cellW
                val top = y + max(1f, rowH * .19f)
                val bottom = y + rowH - max(1f, rowH * .19f)
                val gap = min(1.8f, cellW * .15f)
                canvas.drawRoundRect(RectF(left + gap / 2, top, left + cellW - gap / 2, bottom),
                    min(2.5f, cellW * .22f), min(2.5f, cellW * .22f), paint)
                if (date == LocalDate.now()) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = max(1f, cellW * .12f)
                    paint.color = TEAL
                    canvas.drawRoundRect(RectF(left + gap / 2, top, left + cellW - gap / 2, bottom),
                        min(2.5f, cellW * .22f), min(2.5f, cellW * .22f), paint)
                    paint.style = Paint.Style.FILL
                }
            }
        }
        if (hasOverflow) {
            paint.textSize = min(13f, rowH * .63f)
            paint.color = MUTED
            canvas.drawText("+${members.size - shown} more", 2f,
                headerH + shown * rowH + rowH * .70f, paint)
        }
        return bitmap
    }
}
