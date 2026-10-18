package com.everydayta.app

import org.junit.Assert.*
import org.junit.Test

class WidgetLayoutRulesTest {
    @Test fun hostDimensionsUsesPortraitMaxHeight() {
        assertEquals(250 to 550, WidgetLayoutRules.hostDimensions(250, 120, 470, 550, true))
        assertEquals(470 to 120, WidgetLayoutRules.hostDimensions(250, 120, 470, 550, false))
        assertEquals(250 to 120, WidgetLayoutRules.hostDimensions(250, 120, 0, 0, true))
    }

    @Test fun squaresAcrossShapes() {
        listOf(320 to 75, 480 to 160, 400 to 400, 320 to 600).forEach { (w,h) ->
            val columns = WidgetLayoutRules.blockColumns(w,h)
            val rows = (365+columns-1)/columns
            val ratio = (w.toDouble()/columns)/(h.toDouble()/rows)
            assertTrue("cells too stretched at $w x $h: $ratio", ratio in 0.65..1.5)
        }
    }
    @Test fun weekLayoutsStayInBounds() {
        for (year in listOf(2024,2025,2026,2027)) {
            assertTrue(WidgetLayoutRules.weekCount(year) in 52..54)
            assertEquals(0, WidgetLayoutRules.weekCell(year,1).second - (java.time.LocalDate.of(year,1,1).dayOfWeek.value-1))
            for ((w,h) in listOf(480 to 110, 480 to 300, 300 to 500)) {
                val bands=WidgetLayoutRules.weekBands(w,h,year)
                assertTrue(bands in 1..4)
                val columns=kotlin.math.ceil(WidgetLayoutRules.weekCount(year).toDouble()/bands).toInt()
                val maxWeek=WidgetLayoutRules.weekCell(year,java.time.LocalDate.of(year,1,1).lengthOfYear()).first
                assertTrue(maxWeek < columns*bands)
            }
        }
    }
    @Test fun calendarChoosesPortraitAndLandscape() {
        assertEquals(3,WidgetLayoutRules.calendarColumns(480,550))
        assertEquals(4,WidgetLayoutRules.calendarColumns(480,200))
    }
}
