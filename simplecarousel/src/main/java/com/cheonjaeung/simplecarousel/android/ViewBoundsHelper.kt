package com.cheonjaeung.simplecarousel.android

import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * A helper class to check view bounds within its container view.
 */
class ViewBoundsHelper(private val parentInfoCallback: ParentInfoCallback) {
    /**
     * Finds the first partially visible view starting from given [from] index to given [to] index.
     *
     * It always assumes that [RecyclerView.mClipToPadding] is `true`. The paddings are always considered
     * when finding visible view.
     *
     * @param from The first index to search.
     * @param to The last index to search. (Exclusive)
     *
     * @return The first partially visible view or `null` if there is no visible view.
     */
    fun findPartiallyVisibleView(from: Int, to: Int): View? {
        var target: View? = null
        if (from < to) {
            for (i in from until to) {
                val child = parentInfoCallback.getChildAt(i) ?: continue
                if (isChildPartiallyVisibleWithinParent(child)) {
                    target = child
                    break
                }
            }
        } else {
            for (i in from downTo (to + 1)) {
                val child = parentInfoCallback.getChildAt(i) ?: continue
                if (isChildPartiallyVisibleWithinParent(child)) {
                    target = child
                    break
                }
            }
        }
        return target
    }

    /**
     * Finds the first completely visible view starting from given [from] index to given [to] index.
     *
     * It always assumes that [RecyclerView.mClipToPadding] is `true`. The paddings are always considered
     * when finding visible view.
     *
     * @param from The first index to search.
     * @param to The last index to search. (Exclusive)
     *
     * @return The first completely visible view or `null` if there is no visible view.
     */
    fun findCompletelyVisibleView(from: Int, to: Int): View? {
        var target: View? = null
        if (from < to) {
            for (i in from until to) {
                val child = parentInfoCallback.getChildAt(i) ?: continue
                if (isChildCompletelyVisibleWithinParent(child)) {
                    target = child
                    break
                }
            }
        } else {
            for (i in from downTo (to + 1)) {
                val child = parentInfoCallback.getChildAt(i) ?: continue
                if (isChildCompletelyVisibleWithinParent(child)) {
                    target = child
                    break
                }
            }
        }
        return target
    }

    private fun isChildPartiallyVisibleWithinParent(child: View): Boolean {
        val parentStart = parentInfoCallback.getParentStartAfterPadding()
        val parentEnd = parentInfoCallback.getParentEndBeforePadding()
        val childStart = parentInfoCallback.getChildStartWithinParent(child)
        val childEnd = parentInfoCallback.getChildEndWithinParent(child)
        return isPartiallyVisible(childStart, childEnd, parentStart, parentEnd)
    }

    private fun isChildCompletelyVisibleWithinParent(child: View): Boolean {
        val parentStart = parentInfoCallback.getParentStartAfterPadding()
        val parentEnd = parentInfoCallback.getParentEndBeforePadding()
        val childStart = parentInfoCallback.getChildStartWithinParent(child)
        val childEnd = parentInfoCallback.getChildEndWithinParent(child)
        return isCompletelyVisible(childStart, childEnd, parentStart, parentEnd)
    }

    private fun isPartiallyVisible(
        childStart: Int,
        childEnd: Int,
        parentStart: Int,
        parentEnd: Int
    ): Boolean {
        if (childEnd < parentStart || childStart > parentEnd) return false
        return childStart > parentStart || childEnd < parentEnd
    }

    private fun isCompletelyVisible(
        childStart: Int,
        childEnd: Int,
        parentStart: Int,
        parentEnd: Int
    ): Boolean {
        return childStart >= parentStart && childEnd <= parentEnd
    }

    /**
     * Callbacks to get information about parent
     */
    interface ParentInfoCallback {
        /**
         * Returns a view at the given index.
         */
        fun getChildAt(index: Int): View?

        /**
         * Returns the start (left or top) position of the parent view after the padding.
         */
        fun getParentStartAfterPadding(): Int

        /**
         * Returns the end (right or bottom) position of the parent view before the padding.
         */
        fun getParentEndBeforePadding(): Int

        /**
         * Returns the start (left or top) position of the given [child] view within the parent.
         */
        fun getChildStartWithinParent(child: View): Int

        /**
         * Returns the end (right or bottom) position of the given [child] view within the parent.
         */
        fun getChildEndWithinParent(child: View): Int
    }
}
