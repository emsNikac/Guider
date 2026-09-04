package com.nikac.guider.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GuiderNavigationTest {
    @Test
    fun adjacentPageChangeSlides() {
        assertEquals(PageTransition.SLIDE, pageTransition(currentPage = 1, targetPage = 2))
        assertEquals(PageTransition.SLIDE, pageTransition(currentPage = 2, targetPage = 1))
    }

    @Test
    fun distantPageChangeFadesThrough() {
        assertEquals(PageTransition.FADE_THROUGH, pageTransition(currentPage = 0, targetPage = 2))
        assertEquals(PageTransition.FADE_THROUGH, pageTransition(currentPage = 4, targetPage = 0))
    }

    @Test
    fun samePageDoesNotTransition() {
        assertEquals(PageTransition.NONE, pageTransition(currentPage = 3, targetPage = 3))
    }
}
