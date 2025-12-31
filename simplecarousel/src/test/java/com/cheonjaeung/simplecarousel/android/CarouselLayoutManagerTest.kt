package com.cheonjaeung.simplecarousel.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class CarouselLayoutManagerTest {

    @Test
    fun testConstructor_noArgs() {
        val layoutManager = CarouselLayoutManager()

        assertEquals(CarouselLayoutManager.VERTICAL, layoutManager.orientation)
        assertTrue(layoutManager.circular)
        assertFalse(layoutManager.reverseLayout)
    }

    @Test
    fun testConstructor_withOrientation() {
        val orientation = CarouselLayoutManager.HORIZONTAL
        val layoutManager = CarouselLayoutManager(orientation)

        assertEquals(orientation, layoutManager.orientation)
        assertTrue(layoutManager.circular)
        assertFalse(layoutManager.reverseLayout)
    }

    @Test
    fun testConstructor_withOrientationAndCircular() {
        val orientation = CarouselLayoutManager.HORIZONTAL
        val circular = false
        val layoutManager = CarouselLayoutManager(orientation, circular)

        assertEquals(orientation, layoutManager.orientation)
        assertEquals(circular, layoutManager.circular)
        assertFalse(layoutManager.reverseLayout)
    }

    @Test
    fun testConstructor_withOrientationAndCircularAndReverseLayout() {
        val orientation = CarouselLayoutManager.HORIZONTAL
        val circular = false
        val reverseLayout = true
        val layoutManager = CarouselLayoutManager(orientation, circular, reverseLayout)

        assertEquals(orientation, layoutManager.orientation)
        assertEquals(circular, layoutManager.circular)
        assertEquals(reverseLayout, layoutManager.reverseLayout)
    }

    @Test
    fun testConstructor_fromXml() {
        val orientation = CarouselLayoutManager.HORIZONTAL
        val reverseLayout = true

        val context = ApplicationProvider.getApplicationContext<Context>()

        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(android.R.attr.orientation, "horizontal")
            .addAttribute(androidx.recyclerview.R.attr.reverseLayout, "true")
            .build()

        val layoutManager = CarouselLayoutManager(context, attrs, 0, 0)

        assertEquals(orientation, layoutManager.orientation)
        assertTrue(layoutManager.circular)
        assertEquals(reverseLayout, layoutManager.reverseLayout)
    }
}
