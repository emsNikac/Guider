package com.example.guider.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuiderNavigationTest {
    @Test
    fun differentPageChangeAnimates() {
        assertTrue(shouldAnimatePageChange(currentPage = 1, targetPage = 2))
        assertTrue(shouldAnimatePageChange(currentPage = 2, targetPage = 1))
        assertTrue(shouldAnimatePageChange(currentPage = 0, targetPage = 2))
        assertTrue(shouldAnimatePageChange(currentPage = 4, targetPage = 0))
    }

    @Test
    fun samePageDoesNotAnimate() {
        assertFalse(shouldAnimatePageChange(currentPage = 3, targetPage = 3))
    }
}
