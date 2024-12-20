package com.cheonjaeung.simplecarousel.android.pager

import android.view.ViewGroup.MarginLayoutParams
import androidx.annotation.IntDef
import androidx.annotation.RestrictTo
import androidx.recyclerview.widget.RecyclerView
import com.cheonjaeung.simplecarousel.android.CarouselLayoutManager

/**
 * Adapter that converts [RecyclerView]'s scroll events to [CarouselPager]'s events.
 */
internal class ScrollListenerAdapter(
    private val carouselPager: CarouselPager
) : RecyclerView.OnScrollListener() {

    private val recyclerView = carouselPager.recyclerView
    private val layoutManager: CarouselLayoutManager = recyclerView.layoutManager as CarouselLayoutManager

    @CarouselPager.ScrollState
    var scrollState: Int = CarouselPager.SCROLL_STATE_IDLE
        private set

    val relativePosition: Float
        get() {
            updateScrollEventValues()
            return scrollValues.position + scrollValues.offset
        }

    @AdapterState
    private var adapterState: Int = STATE_IDLE

    private var scrollValues = ScrollEventValues()
    private var isScrolling: Boolean = false
    private var dragStartPosition: Int = RecyclerView.NO_POSITION
    private var dragTargetPosition: Int = RecyclerView.NO_POSITION
    private var shouldDispatchSelected: Boolean = false
    private var isDataSetChanged: Boolean = false

    private fun reset() {
        adapterState = STATE_IDLE
        scrollState = CarouselPager.SCROLL_STATE_IDLE
        scrollValues.reset()
        isScrolling = false
        dragStartPosition = RecyclerView.NO_POSITION
        dragTargetPosition = RecyclerView.NO_POSITION
        shouldDispatchSelected = false
        isDataSetChanged = false
    }

    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
        // not dragging -> dragging
        if (
            (adapterState != STATE_IN_MANUAL_DRAG || scrollState != SCROLL_STATE_DRAGGING) &&
            newState == SCROLL_STATE_DRAGGING
        ) {
            adapterState = STATE_IN_MANUAL_DRAG
            if (dragTargetPosition != RecyclerView.NO_POSITION) {
                dragStartPosition = dragTargetPosition
                dragTargetPosition = RecyclerView.NO_POSITION
            } else if (dragStartPosition == RecyclerView.NO_POSITION) {
                val firstPartiallyVisiblePosition = layoutManager.findFirstVisibleItemPosition()
                val firstCompletelyVisiblePosition = layoutManager.findFirstCompletelyVisibleItemPosition()
                dragStartPosition = if (firstCompletelyVisiblePosition != RecyclerView.NO_POSITION) {
                    firstCompletelyVisiblePosition
                } else {
                    firstPartiallyVisiblePosition
                }
            }
            dispatchOnScrollStateChangedCallbacks(SCROLL_STATE_DRAGGING)
            return
        }

        // dragging -> settling
        if (adapterState == STATE_IN_MANUAL_DRAG && newState == SCROLL_STATE_SETTLING) {
            if (isScrolling) {
                dispatchOnScrollStateChangedCallbacks(SCROLL_STATE_SETTLING)
                shouldDispatchSelected = true
            }
            return
        }

        // dragging or settling -> idle
        if (adapterState == STATE_IN_MANUAL_DRAG && newState == SCROLL_STATE_IDLE) {
            updateScrollEventValues()
            var shouldDispatchIdle = false
            if (!isScrolling) {
                // Page didn't moved. It means that page is the start/end of the list or there is no items.
                // If the page is the start or end item, it dispatches scroll event.
                // If there is no items, it doesn't make any scroll event.
                if (scrollValues.position != RecyclerView.NO_POSITION) {
                    dispatchOnScrolledCallbacks(scrollValues.position, 0f, 0)
                }
                shouldDispatchIdle = true
            } else if (scrollValues.offsetPx == 0) {
                // Dispatches on selected event when the offset == 0 and page is changed.
                if (dragStartPosition != scrollValues.position) {
                    dispatchOnSelectedCallbacks(scrollValues.position)
                }
                shouldDispatchIdle = true
            }

            if (shouldDispatchIdle) {
                dispatchOnScrollStateChangedCallbacks(SCROLL_STATE_IDLE)
                reset()
            }
        }

        if (isDataSetChanged && adapterState == STATE_IN_SMOOTH_SCROLL && newState == SCROLL_STATE_IDLE) {
            updateScrollEventValues()
            if (scrollValues.offsetPx == 0) {
                if (dragTargetPosition != scrollValues.position) {
                    dispatchOnSelectedCallbacks(scrollValues.position)
                }
                dispatchOnScrollStateChangedCallbacks(SCROLL_STATE_IDLE)
                reset()
            }
        }
    }

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        isScrolling = true
        updateScrollEventValues()

        if (shouldDispatchSelected) {
            shouldDispatchSelected = false
            val scrollingForward = (dy == 0 && dx < 0 == carouselPager.isRtl()) || dy > 0
            dragTargetPosition = if (scrollingForward && scrollValues.offsetPx != 0) {
                if (scrollValues.position == layoutManager.itemCount - 1) {
                    0
                } else {
                    scrollValues.position + 1
                }
            } else {
                scrollValues.position
            }
            if (dragStartPosition != dragTargetPosition) {
                dispatchOnSelectedCallbacks(dragTargetPosition)
            }
        } else if (adapterState == STATE_IDLE) {
            dispatchOnSelectedCallbacks(scrollValues.position)
        }

        dispatchOnScrolledCallbacks(scrollValues.position, scrollValues.offset, scrollValues.offsetPx)

        // Dispatch idle when setCurrentItem() is called because it doesn't send idle state event.
        if (
            (scrollValues.position == dragTargetPosition || dragTargetPosition == RecyclerView.NO_POSITION) &&
            scrollValues.offsetPx == 0 &&
            scrollState != SCROLL_STATE_DRAGGING
        ) {
            dispatchOnScrollStateChangedCallbacks(SCROLL_STATE_IDLE)
            reset()
        }
    }

    /**
     * Updates [scrollValues] to calculate position and offsets.
     */
    private fun updateScrollEventValues() {
        val firstPartiallyVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        val firstCompletelyVisiblePosition = layoutManager.findFirstCompletelyVisibleItemPosition()
        scrollValues.position = if (firstCompletelyVisiblePosition != RecyclerView.NO_POSITION) {
            firstCompletelyVisiblePosition
        } else {
            firstPartiallyVisiblePosition
        }
        if (scrollValues.position == RecyclerView.NO_POSITION) {
            scrollValues.reset()
            return
        }

        val firstVisibleView = layoutManager.findViewByPosition(scrollValues.position)
        if (firstVisibleView == null) {
            scrollValues.reset()
            return
        }

        var leftDecoration = layoutManager.getLeftDecorationWidth(firstVisibleView)
        var topDecoration = layoutManager.getTopDecorationHeight(firstVisibleView)
        var rightDecoration = layoutManager.getRightDecorationWidth(firstVisibleView)
        var bottomDecoration = layoutManager.getBottomDecorationHeight(firstVisibleView)

        val params = firstVisibleView.layoutParams
        if (params is MarginLayoutParams) {
            leftDecoration += params.leftMargin
            topDecoration += params.topMargin
            rightDecoration += params.rightMargin
            bottomDecoration += params.bottomMargin
        }

        val totalWidth = firstVisibleView.width + leftDecoration + rightDecoration
        val totalHeight = firstVisibleView.height + topDecoration + bottomDecoration

        val startOffset: Int
        val sizePx: Int
        if (isHorizontal()) {
            sizePx = totalWidth
            var start = firstVisibleView.left - leftDecoration - recyclerView.paddingLeft
            if (carouselPager.isRtl()) {
                start = -start
            }
            startOffset = start
        } else {
            sizePx = totalHeight
            startOffset = firstVisibleView.top - topDecoration - recyclerView.paddingTop
        }

        scrollValues.offsetPx = -startOffset
        scrollValues.offset = if (sizePx != 0) {
            scrollValues.offsetPx.toFloat() / sizePx.toFloat()
        } else {
            0f
        }
    }

    private fun dispatchOnScrollStateChangedCallbacks(newState: Int) {
        for (callback in carouselPager.pageChangeCallbacks) {
            callback.onPageScrollStateChanged(newState)
        }
    }

    private fun dispatchOnScrolledCallbacks(position: Int, offset: Float, offsetPx: Int) {
        for (callback in carouselPager.pageChangeCallbacks) {
            callback.onPageScrolled(position, offset, offsetPx)
        }
    }

    private fun dispatchOnSelectedCallbacks(position: Int) {
        for (callback in carouselPager.pageChangeCallbacks) {
            callback.onPageSelected(position)
        }
    }

    private fun isHorizontal(): Boolean {
        return carouselPager.orientation == CarouselLayoutManager.HORIZONTAL
    }

    internal fun notifyProgrammaticScroll(targetPosition: Int, smoothScroll: Boolean) {
        adapterState = if (smoothScroll) {
            STATE_IN_SMOOTH_SCROLL
        } else {
            STATE_IN_IMMEDIATE_SCROLL
        }
        val isNewTarget = targetPosition != dragTargetPosition
        dragTargetPosition = targetPosition
        dispatchOnScrollStateChangedCallbacks(SCROLL_STATE_SETTLING)
        if (isNewTarget) {
            dispatchOnSelectedCallbacks(targetPosition)
        }
    }

    internal fun notifyDataSetChanged() {
        isDataSetChanged = true
    }

    companion object {
        private const val SCROLL_STATE_IDLE = CarouselPager.SCROLL_STATE_IDLE
        private const val SCROLL_STATE_DRAGGING = CarouselPager.SCROLL_STATE_DRAGGING
        private const val SCROLL_STATE_SETTLING = CarouselPager.SCROLL_STATE_SETTLING

        private const val STATE_IDLE = 0
        private const val STATE_IN_MANUAL_DRAG = 1
        private const val STATE_IN_SMOOTH_SCROLL = 2
        private const val STATE_IN_IMMEDIATE_SCROLL = 3
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(STATE_IDLE, STATE_IN_MANUAL_DRAG, STATE_IN_SMOOTH_SCROLL, STATE_IN_IMMEDIATE_SCROLL)
    private annotation class AdapterState

    private class ScrollEventValues {
        var position: Int = RecyclerView.NO_POSITION
        var offset: Float = 0f
        var offsetPx: Int = 0

        fun reset() {
            position = RecyclerView.NO_POSITION
            offset = 0f
            offsetPx = 0
        }
    }
}
