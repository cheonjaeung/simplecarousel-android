package com.cheonjaeung.simplecarousel.android

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView

/**
 * A [RecyclerView.SmoothScroller] implementation for [CarouselLayoutManager].
 *
 * The [RecyclerView.LayoutManager] should be [CarouselLayoutManager] to get circular option and compute
 * scroll vector.
 */
open class CarouselSmoothScroller(
    context: Context?,
    layoutManager: RecyclerView.LayoutManager?
) : LinearSmoothScroller(context) {
    private val itemCount: Int = layoutManager?.itemCount ?: 0

    /**
     * The initial target position. This value will not changed after [setTargetPosition] called.
     */
    private var initialTargetPosition = RecyclerView.NO_POSITION

    /**
     * The current target position for rounding multiple times. If [CarouselLayoutManager.circular] is `true`,
     * the [initialTargetPosition] can be out of bounds. In this case, it holds temporal position until it
     * scrolls sufficiently.
     */
    private var virtualTargetPosition = RecyclerView.NO_POSITION

    /**
     * The valid target adapter position. If [CarouselLayoutManager.circular] is `true`, the [initialTargetPosition]
     * can be out of bounds position. This value holds a valid adapter position to find target view.
     */
    private var validTargetPosition = RecyclerView.NO_POSITION

    /**
     * Returns the target position to scroll to.
     *
     * It returns currently set target position. Note that it may returns [NOT_FOUND_YET_POSITION] if the
     * scroller should rotate multiple rounds.
     *
     * To get valid target adapter position, use [getTargetAdapterPosition] instead.
     */
    @Suppress("RedundantOverride")
    override fun getTargetPosition(): Int {
        return super.getTargetPosition()
    }

    /**
     * Returns the valid target adapter position.
     */
    fun getTargetAdapterPosition(): Int {
        return validTargetPosition
    }

    /**
     * Sets the target position to scroll to.
     *
     * @param targetPosition The target adapter position. This value can be out of bounds. When out of bounds position
     * is given, the [CarouselSmoothScroller] can scroll multiple rounds to find valid target position.
     */
    override fun setTargetPosition(targetPosition: Int) {
        initialTargetPosition = targetPosition
        virtualTargetPosition = targetPosition
        when {
            targetPosition < 0 -> {
                validTargetPosition = (targetPosition % itemCount + itemCount) % itemCount
                super.setTargetPosition(NOT_FOUND_YET_POSITION)
            }

            targetPosition > itemCount -> {
                validTargetPosition = targetPosition % itemCount
                super.setTargetPosition(NOT_FOUND_YET_POSITION)
            }

            else -> {
                validTargetPosition = RecyclerView.NO_POSITION
                super.setTargetPosition(targetPosition)
            }
        }
    }

    override fun updateActionForInterimTarget(action: Action?) {
        val layoutManager = layoutManager
        val vector = if (layoutManager is CarouselLayoutManager) {
            layoutManager.computeScrollVectorForPosition(initialTargetPosition, false)
        } else {
            computeScrollVectorForPosition(targetPosition)
        }
        if (vector == null || (vector.x == 0f && vector.y == 0f)) {
            action?.jumpTo(targetPosition)
            stop()
            return
        }
        normalize(vector)
        mTargetVector = vector

        mInterimTargetDx = (TARGET_SEEK_SCROLL_DISTANCE_PX * vector.x).toInt()
        mInterimTargetDy = (TARGET_SEEK_SCROLL_DISTANCE_PX * vector.y).toInt()
        val time = calculateTimeForScrolling(TARGET_SEEK_SCROLL_DISTANCE_PX)
        action?.update(
            (mInterimTargetDx * TARGET_SEEK_EXTRA_SCROLL_RATIO).toInt(),
            (mInterimTargetDy * TARGET_SEEK_EXTRA_SCROLL_RATIO).toInt(),
            (time * TARGET_SEEK_EXTRA_SCROLL_RATIO).toInt(),
            mLinearInterpolator
        )
    }

    override fun onChildAttachedToWindow(child: View?) {
        if (getChildPosition(child) == validTargetPosition) {
            if (virtualTargetPosition < 0) {
                virtualTargetPosition += itemCount
            } else if (virtualTargetPosition > itemCount - 1) {
                virtualTargetPosition -= itemCount
            } else {
                super.setTargetPosition(virtualTargetPosition)
                virtualTargetPosition = RecyclerView.NO_POSITION
            }
        }
        super.onChildAttachedToWindow(child)
    }

    companion object {
        /**
         * A special target position to make this scroller keep scrolling.
         *
         * [RecyclerView.SmoothScroller] uses -1 as invalid target position. When target position is -1,
         * it throws exception. [CarouselSmoothScroller] uses this value to avoid throwing and keep scrolling.
         */
        const val NOT_FOUND_YET_POSITION = -10

        private const val TARGET_SEEK_SCROLL_DISTANCE_PX = 10000
        private const val TARGET_SEEK_EXTRA_SCROLL_RATIO = 1.2f
    }
}
