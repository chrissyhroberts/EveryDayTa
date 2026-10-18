package com.everydayta.app

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.min

/** An explicit, user-initiated export. Files are only exposed to the chosen share target. */
object ShareExports {
    private const val TEAL = 0xff126a60.toInt()
    private const val SUBDUED = 0xff60756f.toInt()
    private const val GREY = 0xffe4e9e7.toInt()

    private fun shareFile(context: Context, filename: String): File {
        val dir = File(context.cacheDir, "shares")
        require(dir.isDirectory || dir.mkdirs()) { "Cannot create share cache" }
        // Old exports are unnecessary once the recipient has copied them. Don't evict a current share.
        dir.listFiles()?.filter { it.isFile && System.currentTimeMillis() - it.lastModified() > 48L * 3600_000L }
            ?.forEach { it.delete() }
        return File(dir, filename)
    }

    private fun uri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun quoteCsv(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""

    /** Only the displayed tracker's observations for the currently selected year. */
    fun dataset(context: Context, tracker: Tracker, entries: Map<LocalDate, Int>, year: Int): Uri {
        val sb = StringBuilder("tracker_uuid,tracker,date,score,label,level_index,scale_type\r\n")
        entries.toSortedMap().forEach { (day, score) ->
            if (day.year == year) {
                val label = tracker.labels.getOrNull(score - 1) ?: return@forEach
                sb.append(listOf(tracker.uuid, tracker.name, day.toString(),
                    ScaleSemantics.displayScore(tracker.scaleType, score).toString(), label,
                    score.toString(),tracker.scaleType)
                    .joinToString(",", transform = ::quoteCsv)).append("\r\n")
            }
        }
        val file = shareFile(context, "everydayta-${tracker.uuid}-$year-${System.currentTimeMillis()}.csv")
        file.writeText(sb.toString(), Charsets.UTF_8)
        return uri(context, file)
    }

    /** Render the selected year layout (or drilled-down month) to a legible standalone PNG. */
    fun image(context: Context, tracker: Tracker, entries: Map<LocalDate, Int>, year: Int,
              layout: Int, month: Int, showDates: Boolean = true): Uri {
        require(layout in 0..2 && year in 1900..2200 && month in 0..12)
        val width = 1250
        val height = if (month in 1..12 && layout == 2) 1170 else if (layout == 2) 1550 else 920
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(output)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEAL }
            paint.isFakeBoldText = true
            paint.textSize = 55f
            canvas.drawText(tracker.name.take(70), 68f, 106f, paint)
            paint.isFakeBoldText = false
            paint.color = SUBDUED
            paint.textSize = 30f
            val monthTitle = if (month in 1..12 && layout == 2)
                YearMonth.of(year, month).month.getDisplayName(TextStyle.FULL, Locale.UK) + " $year"
                else "$year · " + listOf("Weeks", "Block", "Calendar")[layout]
            canvas.drawText(monthTitle, 68f, 154f, paint)
            val panel = RectF(46f, 196f, width - 46f, height - 105f)
            paint.color = 0xfff8faf9.toInt()
            canvas.drawRoundRect(panel, 36f, 36f, paint)
            if (month in 1..12 && layout == 2) {
                month(canvas, tracker, entries, YearMonth.of(year, month), panel, paint)
            } else if (layout == 2) {
                calendarYear(canvas, tracker, entries, year, panel, paint, showDates)
            } else {
                // Same renderer used by the Android widget; give it a matching aspect ratio.
                val innerWidth = (panel.width() - 70f).toInt()
                val innerHeight = (panel.height() - 60f).toInt()
                val density = min(1.8f, context.resources.displayMetrics.density).coerceAtLeast(1f)
                val option = Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                        (innerWidth / density).toInt().coerceIn(120, 700) + 28)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                        (innerHeight / density).toInt().coerceIn(110, 900) + 67)
                }
                val rendered = WidgetArtwork.render(context, tracker, entries, year, layout, option)
                try {
                    val dst = RectF(panel.left + 35f, panel.top + 30f,
                        panel.right - 35f, panel.bottom - 30f)
                    // Preserve square cells / typography with a uniform scale, never FIT_XY.
                    val ratio = min(dst.width() / rendered.width, dst.height() / rendered.height)
                    val dw = rendered.width * ratio
                    val dh = rendered.height * ratio
                    canvas.drawBitmap(rendered, null, RectF(dst.centerX() - dw / 2f,
                        dst.centerY() - dh / 2f, dst.centerX() + dw / 2f,
                        dst.centerY() + dh / 2f), paint)
                } finally { rendered.recycle() }
            }
            paint.color = SUBDUED
            paint.textSize = 26f
            canvas.drawText("${entries.keys.count { it.year == year }} recorded days  ·  EveryDayTa",
                68f, height - 48f, paint)
            val file = shareFile(context, "everydayta-${tracker.uuid}-$year-${if (month in 1..12 && layout == 2) "month-$month" else "view-$layout"}-${System.currentTimeMillis()}.png")
            file.outputStream().use { out -> check(output.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG export failed" } }
            return uri(context, file)
        } finally { output.recycle() }
    }

    private fun calendarYear(canvas: Canvas, tracker: Tracker, entries: Map<LocalDate, Int>,
                             year: Int, rect: RectF, paint: Paint, showDates: Boolean) {
        val columns = 3
        val rows = 4
        val gap = 23f
        val usableWidth = rect.width() - 48f
        val usableHeight = rect.height() - 55f
        val mWidth = (usableWidth - gap * (columns - 1)) / columns
        val mHeight = (usableHeight - gap * (rows - 1)) / rows
        for (m in 1..12) {
            val ym = YearMonth.of(year, m)
            val c = (m - 1) % columns
            val r = (m - 1) / columns
            val x = rect.left + 24f + c * (mWidth + gap)
            val y = rect.top + 26f + r * (mHeight + gap)
            paint.color = TEAL
            paint.textSize = 28f
            paint.isFakeBoldText = true
            canvas.drawText(ym.month.getDisplayName(TextStyle.SHORT, Locale.UK), x, y + 25f, paint)
            paint.isFakeBoldText = false
            val cell = min((mWidth - 5f) / 7f, (mHeight - 53f) / 6f)
            val offset = ym.atDay(1).dayOfWeek.value - 1
            for (d in 1..ym.lengthOfMonth()) {
                val position = offset + d - 1
                val cx = x + (position % 7) * cell
                val cy = y + 47f + (position / 7) * cell
                val score = entries[ym.atDay(d)] ?: 0
                paint.color = if (score == 0) GREY else Palettes.color(tracker, score)
                canvas.drawRoundRect(RectF(cx + 2, cy + 2, cx + cell - 2, cy + cell - 2), 5f, 5f, paint)
                if (showDates) {
                    paint.color = if (score > 0 && (score - 1) * 5 / tracker.labels.size >= 3) Color.WHITE else TEAL
                    paint.textSize = cell * .38f
                    canvas.drawText(d.toString(), cx + cell * .16f, cy + cell * .61f, paint)
                }
            }
        }
    }

    private fun month(canvas: Canvas, tracker: Tracker, entries: Map<LocalDate, Int>,
                      month: YearMonth, rect: RectF, p: Paint) {
        val x0 = rect.left + 75f
        val usableW = rect.width() - 150f
        val boxW = usableW / 7f
        val boxH = (rect.height() - 155f) / 6f
        val y0 = rect.top + 135f
        p.color = SUBDUED
        p.textSize = 29f
        p.isFakeBoldText = true
        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { idx, label ->
            canvas.drawText(label, x0 + idx * boxW + boxW * .38f, rect.top + 90f, p)
        }
        p.isFakeBoldText = false
        val offset = month.atDay(1).dayOfWeek.value - 1
        for (day in 1..month.lengthOfMonth()) {
            val pos = offset + day - 1
            val x = x0 + (pos % 7) * boxW
            val y = y0 + (pos / 7) * boxH
            val score = entries[month.atDay(day)] ?: 0
            p.color = if (score == 0) GREY else Palettes.color(tracker, score)
            canvas.drawRoundRect(RectF(x + 7, y + 7, x + boxW - 7, y + boxH - 7), 12f, 12f, p)
            p.color = if (score > 0 && (score - 1) * 5 / tracker.labels.size >= 3) Color.WHITE else TEAL
            p.textSize = 35f
            canvas.drawText(day.toString(), x + 22f, y + boxH * .58f, p)
        }
    }
}
