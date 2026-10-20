package com.misgus.pengu.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class ReorderableCardsTest {
    private val centers = listOf(50f, 150f, 250f, 350f)

    @Test
    fun draggingDownStopsAtTheLastCrossedCard() {
        assertEquals(1, targetIndex(fromIndex = 0, draggedCenter = 170f, centers = centers))
        assertEquals(2, targetIndex(fromIndex = 0, draggedCenter = 270f, centers = centers))
        assertEquals(3, targetIndex(fromIndex = 0, draggedCenter = 900f, centers = centers))
    }

    @Test
    fun draggingUpStopsAtTheFirstCrossedCard() {
        assertEquals(2, targetIndex(fromIndex = 3, draggedCenter = 240f, centers = centers))
        assertEquals(1, targetIndex(fromIndex = 3, draggedCenter = 140f, centers = centers))
        assertEquals(0, targetIndex(fromIndex = 3, draggedCenter = 10f, centers = centers))
    }

    @Test
    fun stayingInsideTheOwnCardKeepsTheIndex() {
        assertEquals(1, targetIndex(fromIndex = 1, draggedCenter = 150f, centers = centers))
        assertEquals(1, targetIndex(fromIndex = 1, draggedCenter = 120f, centers = centers))
        assertEquals(1, targetIndex(fromIndex = 1, draggedCenter = 180f, centers = centers))
    }
}
