package com.cheonjaeung.simplecarousel.android

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
class ViewBoundsHelperTest {

    private lateinit var viewBoundsHelper: ViewBoundsHelper
    private lateinit var testParentInfoCallback: TestParentInfoCallback

    @Before
    fun setup() {
        testParentInfoCallback = TestParentInfoCallback()
        viewBoundsHelper = ViewBoundsHelper(testParentInfoCallback)
    }

    @Test
    fun testFindCompletelyVisibleView_insideParentReturnsView() {
        val parentStart = 0
        val parentEnd = 100
        val child = View(RuntimeEnvironment.getApplication())
        val childStart = 10
        val childEnd = 90

        testParentInfoCallback.setParentBounds(parentStart, parentEnd)
        testParentInfoCallback.setChildBounds(child, childStart, childEnd)
        testParentInfoCallback.addView(child)

        val result = viewBoundsHelper.findCompletelyVisibleView(0, 1)

        assertNotNull("View should be completely visible", result)
        assertEquals("Returned view should be the child", child, result)
    }

    @Test
    fun testFindCompletelyVisibleView_outsideParentReturnsNull() {
        val parentStart = 0
        val parentEnd = 100
        val child = View(RuntimeEnvironment.getApplication())
        val childStart = -10
        val childEnd = -1

        testParentInfoCallback.setParentBounds(parentStart, parentEnd)
        testParentInfoCallback.setChildBounds(child, childStart, childEnd)
        testParentInfoCallback.addView(child)

        val result = viewBoundsHelper.findCompletelyVisibleView(0, 1)

        assertNull("View should not be completely visible", result)
    }

    @Test
    fun testFindCompletelyVisibleView_partiallyVisibleReturnsNull() {
        val parentStart = 0
        val parentEnd = 100
        val childLeftOverlap = View(RuntimeEnvironment.getApplication())
        val childRightOverlap = View(RuntimeEnvironment.getApplication())

        testParentInfoCallback.setParentBounds(parentStart, parentEnd)
        testParentInfoCallback.setChildBounds(childLeftOverlap, -10, 50)
        testParentInfoCallback.setChildBounds(childRightOverlap, 50, 110)

        testParentInfoCallback.addView(childLeftOverlap)
        testParentInfoCallback.addView(childRightOverlap)

        val resultLeft = viewBoundsHelper.findCompletelyVisibleView(0, 1)
        assertNull("Partially visible view (left overlap) should not be considered completely visible", resultLeft)

        val resultRight = viewBoundsHelper.findCompletelyVisibleView(1, 2)
        assertNull("Partially visible view (right overlap) should not be considered completely visible", resultRight)
    }

    @Test
    fun testFindPartiallyVisibleView_insideParentReturnsView() {
        val parentStart = 0
        val parentEnd = 100
        val child = View(RuntimeEnvironment.getApplication())
        val childStart = 10
        val childEnd = 90

        testParentInfoCallback.setParentBounds(parentStart, parentEnd)
        testParentInfoCallback.setChildBounds(child, childStart, childEnd)
        testParentInfoCallback.addView(child)

        val result = viewBoundsHelper.findPartiallyVisibleView(0, 1)

        assertNotNull("View should be partially visible", result)
        assertEquals("Returned view should be the child", child, result)
    }

    @Test
    fun testFindPartiallyVisibleView_outsideParentReturnsNull() {
        val parentStart = 0
        val parentEnd = 100
        val child = View(RuntimeEnvironment.getApplication())
        val childStart = -20
        val childEnd = -10

        testParentInfoCallback.setParentBounds(parentStart, parentEnd)
        testParentInfoCallback.setChildBounds(child, childStart, childEnd)
        testParentInfoCallback.addView(child)

        val result = viewBoundsHelper.findPartiallyVisibleView(0, 1)

        assertNull("View should not be partially visible", result)
    }

    @Test
    fun testFindPartiallyVisibleView_partiallyOverlappingParentReturnsView() {
        val parentStart = 0
        val parentEnd = 100
        val child1 = View(RuntimeEnvironment.getApplication())
        val child2 = View(RuntimeEnvironment.getApplication())
        val child3 = View(RuntimeEnvironment.getApplication())

        testParentInfoCallback.setParentBounds(parentStart, parentEnd)
        testParentInfoCallback.setChildBounds(child1, -10, 50)
        testParentInfoCallback.setChildBounds(child2, 50, 110)
        testParentInfoCallback.setChildBounds(child3, -10, 110)

        testParentInfoCallback.addView(child1)
        testParentInfoCallback.addView(child2)
        testParentInfoCallback.addView(child3)

        val result1 = viewBoundsHelper.findPartiallyVisibleView(0, 1)
        assertNotNull("View 1 should be partially visible", result1)
        assertEquals("Returned view 1 should be the child1", child1, result1)

        val result2 = viewBoundsHelper.findPartiallyVisibleView(1, 2)
        assertNotNull("View 2 should be partially visible", result2)
        assertEquals("Returned view 2 should be the child2", child2, result2)

        val result3 = viewBoundsHelper.findPartiallyVisibleView(2, 3)
        assertNotNull("View 3 should be partially visible", result3)
        assertEquals("Returned view 3 should be the child3", child3, result3)
    }

    class TestParentInfoCallback : ViewBoundsHelper.ParentInfoCallback {
        private var parentStart: Int = 0
        private var parentEnd: Int = 0
        private val childBounds = mutableMapOf<View, Pair<Int, Int>>()
        private val children = mutableListOf<View>()

        fun setParentBounds(start: Int, end: Int) {
            parentStart = start
            parentEnd = end
        }

        fun setChildBounds(child: View, start: Int, end: Int) {
            childBounds[child] = Pair(start, end)
        }

        fun addView(child: View) {
            children.add(child)
        }

        override fun getChildAt(index: Int): View? {
            return if (index >= 0 && index < children.size) children[index] else null
        }

        override fun getParentStartAfterPadding(): Int = parentStart

        override fun getParentEndBeforePadding(): Int = parentEnd

        override fun getChildStartWithinParent(child: View): Int = childBounds[child]?.first ?: 0

        override fun getChildEndWithinParent(child: View): Int = childBounds[child]?.second ?: 0
    }
}
