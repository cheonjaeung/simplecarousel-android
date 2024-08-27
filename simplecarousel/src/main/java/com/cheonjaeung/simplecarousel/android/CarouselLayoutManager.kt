package com.cheonjaeung.simplecarousel.android

import android.content.Context
import android.graphics.PointF
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Orientation
import kotlin.math.abs

/**
 * A [RecyclerView.LayoutManager] that makes [RecyclerView] behave like a carousel.
 *
 * The implementation of this layout manager is highly inspired by
 * [LinearLayoutManager][androidx.recyclerview.widget.LinearLayoutManager].
 * The core logic is very similar to the LinearLayoutManager and some code are added to
 * make carousel behavior.
 */
class CarouselLayoutManager : RecyclerView.LayoutManager, RecyclerView.SmoothScroller.ScrollVectorProvider {

    /**
     * Enable circular mode which means that the first/last item will be connected to the first/last.
     */
    var circular: Boolean = DEFAULT_CIRCULAR
        set(value) {
            if (field != value) {
                field = value
                layoutHelper.circular = value
                requestLayout()
            }
        }

    /**
     * Current orientation of the this layout manager.
     */
    @Orientation
    var orientation: Int = DEFAULT_ORIENTATION
        set(value) {
            if (value != HORIZONTAL && value != VERTICAL) {
                throw IllegalArgumentException("Invalid orientation: $value")
            }
            assertNotInLayoutOrScroll(null)
            if (value != field) {
                field = value
                orientationHelper = OrientationHelper.createOrientationHelper(this, value)
                layoutHelper.orientationHelper = orientationHelper
                anchorInfo.orientationHelper = orientationHelper
                requestLayout()
            }
        }

    private var orientationHelper: OrientationHelper = OrientationHelper.createOrientationHelper(
        this,
        orientation
    )

    private val layoutHelper: LayoutHelper = LayoutHelper()

    private val anchorInfo: AnchorInfo = AnchorInfo()

    /**
     * A temporal storage for [SavedState] to use when first restoring.
     */
    private var pendingSavedState: SavedState? = null

    /**
     * A temporal storage for position to layout.
     * This variable is used when this layout manager need to scroll to a position.
     */
    private var pendingPosition: Int = NO_POSITION

    /**
     * Constructs a [CarouselLayoutManager] with default options.
     */
    @Suppress("unused")
    constructor() : this(DEFAULT_ORIENTATION)

    /**
     * Constructs a [CarouselLayoutManager] with specific orientation.
     */
    @Suppress("unused")
    constructor(@Orientation orientation: Int) : this(orientation, DEFAULT_CIRCULAR)

    /**
     * Constructs a [CarouselLayoutManager] with specific circular mode.
     */
    @Suppress("unused")
    constructor(circular: Boolean) : this(DEFAULT_ORIENTATION, circular)

    @Suppress("unused")
    constructor(@Orientation orientation: Int, circular: Boolean) : super() {
        this.orientation = orientation
        this.circular = circular
        this.layoutHelper.orientationHelper = orientationHelper
        this.layoutHelper.circular = circular
        this.anchorInfo.orientationHelper = orientationHelper
    }

    /**
     * Constructs a [CarouselLayoutManager] from XML attribute.
     * To set in XML layout, set this layout manager to [androidx.recyclerview.R.attr.layoutManager]
     * in [RecyclerView].
     */
    @Suppress("unused")
    constructor(
        context: Context,
        attrs: AttributeSet,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super() {
        val properties = getProperties(context, attrs, defStyleAttr, defStyleRes)
        this.orientation = properties.orientation
        this.circular = DEFAULT_CIRCULAR
        this.layoutHelper.orientationHelper = orientationHelper
        this.layoutHelper.circular = circular
        this.anchorInfo.orientationHelper = orientationHelper
    }

    override fun isAutoMeasureEnabled(): Boolean {
        return true
    }

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.WRAP_CONTENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onSaveInstanceState(): Parcelable {
        val pendingSavedState = this.pendingSavedState
        if (pendingSavedState != null) {
            return SavedState(pendingSavedState)
        }
        val newSavedState = SavedState()
        if (childCount > 0) {
            val view = getChildAtClosestToStart()
            if (view != null) {
                newSavedState.anchorPosition = getPosition(view)
                val decoratedStart = orientationHelper.getDecoratedStart(view)
                val startAfterPadding = orientationHelper.startAfterPadding
                newSavedState.anchorOffset = decoratedStart - startAfterPadding
            }
        } else {
            newSavedState.invalidateAnchor()
        }
        return newSavedState
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            pendingSavedState = state
            if (pendingPosition != NO_POSITION) {
                pendingSavedState?.invalidateAnchor()
            }
            requestLayout()
        }
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        if (recycler == null || state == null) {
            return
        }

        // Don't layout anything when there are no items.
        if (state.itemCount == 0) {
            removeAndRecycleAllViews(recycler)
            return
        }

        // If there is pending state, set pending position to saved position.
        val pendingSavedState = this.pendingSavedState
        if (pendingSavedState != null && pendingSavedState.hasValidAnchor()) {
            pendingPosition = pendingSavedState.anchorPosition
        }

        // Updates anchor info if it is invalid or there are pending states.
        if (!anchorInfo.isValid || pendingSavedState != null || pendingPosition != NO_POSITION) {
            anchorInfo.invalidate()
            updateAnchorInfoForLayout(anchorInfo, state)
            anchorInfo.isValid = true
        }

        layoutHelper.shouldRecycle = false

        // Moves current attached views to scrap for filling items.
        detachAndScrapAttachedViews(recycler)

        // Update layout helper by layout direction.
        layoutHelper.layoutDirection = if (layoutHelper.latestScrollDelta < 0) {
            DIRECTION_START
        } else {
            DIRECTION_END
        }
        if (layoutHelper.layoutDirection == DIRECTION_START) {
            layoutHelper.updateForFillingToStart(anchorInfo.position, anchorInfo.coordinate)
        } else {
            layoutHelper.updateForFillingToEnd(anchorInfo.position, anchorInfo.coordinate)
        }

        // Fill items with layout helper.
        fill(recycler, state)
    }

    /**
     * Updates [anchorInfo] for [onLayoutChildren].
     */
    private fun updateAnchorInfoForLayout(anchorInfo: AnchorInfo, state: RecyclerView.State) {
        // Try update anchor info from pending states.
        if (anchorInfo.updateFromPending(pendingPosition, pendingSavedState, state)) {
            return
        } else {
            // Set useless pending position to no position cause it is invalid.
            pendingPosition = NO_POSITION
        }

        // Try update anchor info from existing children.
        val anchorView = findAnchorView(state)
        if (anchorView != null) {
            anchorInfo.updateFromView(anchorView, getPosition(anchorView))
            return
        }

        // Update anchor info from nothing when previous steps are failed.
        anchorInfo.updateFromNothing()
    }

    override fun onLayoutCompleted(state: RecyclerView.State?) {
        super.onLayoutCompleted(state)
        pendingSavedState = null
        pendingPosition = NO_POSITION
        anchorInfo.invalidate()
    }

    override fun canScrollHorizontally(): Boolean {
        return orientation == HORIZONTAL
    }

    override fun canScrollVertically(): Boolean {
        return orientation == VERTICAL
    }

    override fun scrollToPosition(position: Int) {
        pendingPosition = position
        if (pendingSavedState != null) {
            pendingSavedState?.invalidateAnchor()
        }
        requestLayout()
    }

    override fun smoothScrollToPosition(
        recyclerView: RecyclerView?,
        state: RecyclerView.State?,
        position: Int
    ) {
        val smoothScroller = LinearSmoothScroller(recyclerView?.context)
        smoothScroller.targetPosition = position
        startSmoothScroll(smoothScroller)
    }

    override fun computeScrollVectorForPosition(targetPosition: Int): PointF? {
        if (childCount == 0) {
            return null
        }
        val firstChild = getChildAt(0) ?: return null
        val firstChildPosition = getPosition(firstChild)
        val scrollDirection = if (targetPosition < firstChildPosition) {
            DIRECTION_START.toFloat()
        } else {
            DIRECTION_END.toFloat()
        }
        return if (orientation == HORIZONTAL) {
            PointF(scrollDirection, 0f)
        } else {
            PointF(0f, scrollDirection)
        }
    }

    override fun scrollHorizontallyBy(
        dx: Int,
        recycler: RecyclerView.Recycler?,
        state: RecyclerView.State?
    ): Int {
        if (!canScrollHorizontally() || recycler == null || state == null) {
            return 0
        }
        return scrollBy(dx, recycler, state)
    }

    override fun scrollVerticallyBy(
        dy: Int,
        recycler: RecyclerView.Recycler?,
        state: RecyclerView.State?
    ): Int {
        if (!canScrollVertically() || recycler == null || state == null) {
            return 0
        }
        return scrollBy(dy, recycler, state)
    }

    private fun scrollBy(
        delta: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        // Don't scroll when there are no items or not scrolled.
        if (delta == 0 || childCount == 0) {
            return 0
        }

        // Update layout helper by scrolling direction.
        // If there is no anchor view, don't scroll.
        if (delta < 0) {
            val anchorView = getChildAtClosestToStart() ?: return 0
            val anchorPosition = getPosition(anchorView)
            layoutHelper.updateForScrollingToStart(delta, anchorView, anchorPosition, state)
        } else {
            val anchorView = getChildAtClosestToEnd() ?: return 0
            val anchorPosition = getPosition(anchorView)
            layoutHelper.updateForScrollingToEnd(delta, anchorView, anchorPosition, state)
        }
        layoutHelper.shouldRecycle = true

        // Fill items by scrolling amount.
        val filledSpace = fill(recycler, state)
        val consumed = layoutHelper.scrollingOffset + filledSpace
        if (consumed < 0) {
            return 0
        }

        // Calculate final scrolled amount.
        val scrolled = if (abs(delta) > consumed) {
            consumed * layoutHelper.layoutDirection
        } else {
            delta
        }
        orientationHelper.offsetChildren(-scrolled)
        layoutHelper.latestScrollDelta = scrolled
        return scrolled
    }

    /**
     * Measure and layout items until there is no item or no empty space.
     *
     * @return Pixel size that it filled.
     */
    private fun fill(recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        val fillStart = layoutHelper.availableSpace

        if (layoutHelper.scrollingOffset != SCROLLING_OFFSET_NAN) {
            if (layoutHelper.availableSpace < 0) {
                layoutHelper.scrollingOffset += layoutHelper.availableSpace
            }
        }

        var remainingSpace = layoutHelper.availableSpace
        while (remainingSpace > 0 && layoutHelper.hasNext(state)) {
            // Measure the current view size.
            val view = layoutHelper.next(recycler, state)
            if (layoutHelper.layoutDirection == DIRECTION_END) {
                addView(view)
            } else {
                addView(view, 0)
            }
            measureChildWithMargins(view, 0, 0)
            val viewSize = orientationHelper.getDecoratedMeasurement(view)
            val viewSizeInOther = orientationHelper.getDecoratedMeasurementInOther(view)

            // Calculate the coordinates of the view.
            val left: Int
            val top: Int
            val right: Int
            val bottom: Int
            if (orientation == VERTICAL) {
                left = paddingLeft
                right = left + viewSizeInOther
                if (layoutHelper.layoutDirection == DIRECTION_START) {
                    bottom = layoutHelper.offset
                    top = layoutHelper.offset - viewSize
                } else {
                    top = layoutHelper.offset
                    bottom = layoutHelper.offset + viewSize
                }
            } else {
                top = paddingTop
                bottom = top + viewSizeInOther
                if (layoutHelper.layoutDirection == DIRECTION_START) {
                    right = layoutHelper.offset
                    left = layoutHelper.offset - viewSize
                } else {
                    left = layoutHelper.offset
                    right = layoutHelper.offset + viewSize
                }
            }

            // Layout the view into the coordinate.
            layoutDecoratedWithMargins(view, left, top, right, bottom)

            // Update layout helper for the next view.
            layoutHelper.offset += viewSize * layoutHelper.layoutDirection
            layoutHelper.availableSpace -= viewSize
            remainingSpace -= viewSize

            // Update scrolling offset if it triggered by scrolling.
            if (layoutHelper.scrollingOffset != SCROLLING_OFFSET_NAN) {
                if (layoutHelper.availableSpace < 0) {
                    layoutHelper.scrollingOffset += layoutHelper.availableSpace
                }
                layoutHelper.scrollingOffset += viewSize
            }

            // Recycle out of bounds items after filling.
            recycleChildren(recycler)
        }

        return fillStart - layoutHelper.availableSpace
    }

    override fun assertNotInLayoutOrScroll(message: String?) {
        if (pendingSavedState == null) {
            super.assertNotInLayoutOrScroll(message)
        }
    }

    /**
     * Returns a child view at the closest to start of the layout.
     */
    private fun getChildAtClosestToStart(): View? {
        return getChildAt(0)
    }

    /**
     * Returns a child view at the closest to end of the layout.
     */
    private fun getChildAtClosestToEnd(): View? {
        return getChildAt(childCount - 1)
    }

    /**
     * Returns a child view that can be used as an anchor view.
     */
    private fun findAnchorView(state: RecyclerView.State): View? {
        if (childCount == 0) {
            return null
        }

        val boundsStart = orientationHelper.startAfterPadding
        val boundsEnd = orientationHelper.endAfterPadding

        var bestCandidate: View? = null
        var secondCandidate: View? = null
        var thirdCandidate: View? = null
        var leastCandidate: View? = null

        for (i in 0 until childCount) {
            val view = getChildAt(i)
            if (view != null) {
                val position = getPosition(view)
                val viewStart = orientationHelper.getDecoratedStart(view)
                val viewEnd = orientationHelper.getDecoratedEnd(view)
                if (position in 0 until state.itemCount) {
                    val params = view.layoutParams as RecyclerView.LayoutParams
                    if (params.isItemRemoved) {
                        // If the view is removed item, it is the least anchor view.
                        if (leastCandidate == null) {
                            leastCandidate = view
                        }
                    } else {
                        val isViewOutOfBoundsBeforeStart = viewEnd <= boundsStart && viewStart < boundsStart
                        val isViewOutOfBoundsAfterEnd = viewStart >= boundsEnd && viewEnd > boundsEnd
                        if (isViewOutOfBoundsBeforeStart || isViewOutOfBoundsAfterEnd) {
                            // If the view is out of bounds, it will be the anchor view candidate.
                            if (isViewOutOfBoundsBeforeStart) {
                                thirdCandidate = view
                            } else if (secondCandidate == null) {
                                secondCandidate = view
                            }
                        } else {
                            // If the view is not out of bounds, it will be the best anchor view.
                            bestCandidate = view
                            break
                        }
                    }
                }
            }
        }

        @Suppress("IfThenToElvis")
        return if (bestCandidate != null) {
            bestCandidate
        } else if (secondCandidate != null) {
            secondCandidate
        } else {
            thirdCandidate
        }
    }

    /**
     * Recycles out of bounds children by current state.
     */
    private fun recycleChildren(recycler: RecyclerView.Recycler) {
        if (!layoutHelper.shouldRecycle) {
            return
        }
        val scrollingOffset = layoutHelper.scrollingOffset
        if (layoutHelper.layoutDirection == DIRECTION_END) {
            recycleChildrenFromStart(recycler, scrollingOffset)
        } else {
            recycleChildrenFromEnd(recycler, scrollingOffset)
        }
    }

    /**
     * Recycles out of bounds children from start of view. It may called after scrolling toward the end.
     */
    @Suppress("UnnecessaryVariable")
    private fun recycleChildrenFromStart(recycler: RecyclerView.Recycler, scrollingOffset: Int) {
        if (scrollingOffset < 0) {
            return
        }
        val limit = scrollingOffset
        val childCount = this.childCount
        for (i in 0 until childCount) {
            val view = getChildAt(i)
            if (
                orientationHelper.getDecoratedEnd(view) > limit
            ) {
                recycleChildrenInRange(recycler, 0, i)
                return
            }
        }
    }

    /**
     * Recycles out of bounds children from end of view. It may called after scrolling toward the start.
     */
    private fun recycleChildrenFromEnd(recycler: RecyclerView.Recycler, scrollingOffset: Int) {
        if (scrollingOffset < 0) {
            return
        }
        val limit = orientationHelper.end - scrollingOffset
        val childCount = this.childCount
        for (i in (childCount - 1) downTo 0) {
            val view = getChildAt(i)
            if (
                orientationHelper.getDecoratedStart(view) < limit
            ) {
                recycleChildrenInRange(recycler, childCount - 1, i)
                return
            }
        }
    }

    /**
     * Recycles children in the given range.
     *
     * @param start Inclusive index to start recycle.
     * @param end Exclusive index to end recycle.
     */
    private fun recycleChildrenInRange(recycler: RecyclerView.Recycler, start: Int, end: Int) {
        if (start == end) {
            return
        }
        if (end > start) {
            for (i in (end - 1) downTo start) {
                removeAndRecycleViewAt(i, recycler)
            }
        } else {
            for (i in start downTo (end + 1)) {
                removeAndRecycleViewAt(i, recycler)
            }
        }
    }

    companion object {
        const val HORIZONTAL: Int = RecyclerView.HORIZONTAL
        const val VERTICAL: Int = RecyclerView.VERTICAL

        private const val DEFAULT_ORIENTATION: Int = VERTICAL
        private const val DEFAULT_CIRCULAR: Boolean = true

        private const val DIRECTION_START: Int = -1
        private const val DIRECTION_END: Int = 1

        private const val NO_POSITION: Int = RecyclerView.NO_POSITION
        private const val INVALID_OFFSET: Int = Int.MIN_VALUE
        private const val SCROLLING_OFFSET_NAN: Int = Int.MIN_VALUE
    }

    /**
     * A parcelable class to save the state of the layout manager.
     */
    private data class SavedState(
        var anchorPosition: Int = NO_POSITION,
        var anchorOffset: Int = INVALID_OFFSET
    ) : Parcelable {

        constructor(parcel: Parcel) : this(
            anchorPosition = parcel.readInt(),
            anchorOffset = parcel.readInt()
        )

        constructor(other: SavedState) : this(
            anchorPosition = other.anchorPosition,
            anchorOffset = other.anchorOffset
        )

        fun hasValidAnchor(): Boolean {
            return anchorPosition != NO_POSITION && anchorOffset != INVALID_OFFSET
        }

        fun invalidateAnchor() {
            anchorPosition = NO_POSITION
            anchorOffset = INVALID_OFFSET
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeInt(anchorPosition)
            parcel.writeInt(anchorOffset)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(parcel: Parcel): SavedState {
                return SavedState(parcel)
            }

            override fun newArray(size: Int): Array<SavedState?> {
                return arrayOfNulls(size)
            }
        }
    }

    /**
     * Helper class to hold temporary values while [CarouselLayoutManager] is laying out items.
     * It holds various values like where layout should start, current adapter position and others.
     */
    private data class LayoutHelper(
        /**
         * If `true`, layout manager should recycle out of bounds views after layout finished.
         */
        var shouldRecycle: Boolean = false,

        /**
         * Pixel offset where layout should start.
         */
        var offset: Int = 0,

        /**
         * The amount of pixel offset this manager can scroll without creating new views.
         * It used only when scrolling.
         */
        var scrollingOffset: Int = SCROLLING_OFFSET_NAN,

        /**
         * Current position on the recycler view adapter.
         */
        var currentPosition: Int = RecyclerView.NO_POSITION,

        /**
         * Pixel size that the layout manager should fill.
         */
        var availableSpace: Int = 0,

        /**
         * Direction where the layout manager should fill.
         */
        var layoutDirection: Int = DIRECTION_END,

        /**
         * Direction where the layout manager traverse adapter items.
         */
        var itemDirection: Int = DIRECTION_END,

        /**
         * The latest scroll delta.
         */
        var latestScrollDelta: Int = 0,

        /**
         * Is the layout manager's circular mode enabled.
         */
        var circular: Boolean = true
    ) {
        /**
         * Reference of [OrientationHelper] in the layout manager.
         */
        lateinit var orientationHelper: OrientationHelper

        /**
         * Updates the layout helper to layout items to the start direction.
         *
         * @param position A position to start get item from adapter.
         * @param offset A pixel offset to start layout.
         */
        fun updateForFillingToStart(position: Int, offset: Int) {
            this.currentPosition = position
            this.offset = offset
            this.scrollingOffset = SCROLLING_OFFSET_NAN
            this.layoutDirection = DIRECTION_START
            this.itemDirection = DIRECTION_START
            this.availableSpace = offset - orientationHelper.startAfterPadding
        }

        /**
         * Updates the layout helper to layout items to the end direction.
         *
         * @param position A position to start get item from adapter.
         * @param offset A pixel offset to start layout.
         */
        fun updateForFillingToEnd(position: Int, offset: Int) {
            this.currentPosition = position
            this.offset = offset
            this.scrollingOffset = SCROLLING_OFFSET_NAN
            this.layoutDirection = DIRECTION_END
            this.itemDirection = DIRECTION_END
            this.availableSpace = orientationHelper.endAfterPadding - offset
        }

        /**
         * Updates the layout helper to layout items while scrolling to the start direction.
         *
         * @param delta Amount of pixels to scroll.
         * @param view A view at the closest to start when scrolling triggered.
         * @param position A position of the [view].
         */
        fun updateForScrollingToStart(delta: Int, view: View, position: Int, state: RecyclerView.State) {
            val absDelta = abs(delta)
            this.layoutDirection = DIRECTION_START
            this.itemDirection = DIRECTION_START
            this.currentPosition = position + itemDirection
            validateCurrentPositionForCircular(state)
            this.offset = orientationHelper.getDecoratedStart(view)
            this.scrollingOffset = orientationHelper.startAfterPadding - orientationHelper.getDecoratedStart(view)
            this.availableSpace = absDelta - this.scrollingOffset
        }

        /**
         * Updates the layout helper to layout items while scrolling to the end direction.
         *
         * @param delta Amount of pixels to scroll.
         * @param view A view at the closest to start when scrolling triggered.
         * @param position A position of the [view].
         */
        fun updateForScrollingToEnd(delta: Int, view: View, position: Int, state: RecyclerView.State) {
            val absDelta = abs(delta)
            this.layoutDirection = DIRECTION_END
            this.itemDirection = DIRECTION_END
            this.currentPosition = position + itemDirection
            validateCurrentPositionForCircular(state)
            this.offset = orientationHelper.getDecoratedEnd(view)
            this.scrollingOffset = orientationHelper.getDecoratedEnd(view) - orientationHelper.endAfterPadding
            this.availableSpace = absDelta - this.scrollingOffset
        }

        /**
         * Returns `true` if there are more items in the adapter.
         */
        fun hasNext(state: RecyclerView.State): Boolean {
            return if (circular) {
                state.itemCount > 0
            } else {
                currentPosition >= 0 && currentPosition < state.itemCount
            }
        }

        /**
         * Returns a view that will be laid out. And also it updates [currentPosition] to the
         * next position.
         */
        fun next(recycler: RecyclerView.Recycler, state: RecyclerView.State): View {
            val view = recycler.getViewForPosition(currentPosition)
            currentPosition += itemDirection
            validateCurrentPositionForCircular(state)
            return view
        }

        /**
         * Validates [currentPosition] if [circular]. If [currentPosition] is out of first/last,
         * moves it to last/first.
         */
        private fun validateCurrentPositionForCircular(state: RecyclerView.State) {
            if (circular) {
                if (currentPosition < 0) {
                    currentPosition = state.itemCount - 1
                } else if (currentPosition > state.itemCount - 1) {
                    currentPosition = 0
                }
            }
        }
    }

    /**
     * Helper class to hold temporary values while [CarouselLayoutManager] is laying out items.
     * It holds values about the anchor view
     */
    private data class AnchorInfo(
        /**
         * Position of the anchor view.
         */
        var position: Int = NO_POSITION,

        /**
         * Coordinate of the anchor view.
         */
        var coordinate: Int = INVALID_OFFSET,

        /**
         * If `false`, the anchor view should be updated.
         */
        var isValid: Boolean = false
    ) {
        /**
         * Reference of [OrientationHelper] in the layout manager.
         */
        lateinit var orientationHelper: OrientationHelper

        /**
         * Updates anchor info from nothing. It means that there is no view to use as the anchor.
         * For successful layout, it assumes the anchor view exists at the start of the view.
         * [coordinate] will be the start padding and the [position] will be the first item.
         */
        fun updateFromNothing() {
            this.position = 0
            this.coordinate = orientationHelper.startAfterPadding
        }

        /**
         * Updates anchor info from pending states. It only updates info when [pendingPosition] is valid.
         *
         * @return `true` if anchor info is updated successfully.
         */
        fun updateFromPending(
            pendingPosition: Int,
            pendingSavedState: SavedState?,
            state: RecyclerView.State,
        ): Boolean {
            if (pendingPosition == NO_POSITION) {
                return false
            }

            if (pendingPosition < 0 || pendingPosition >= state.itemCount) {
                return false
            }

            this.position = pendingPosition

            this.coordinate = if (pendingSavedState != null && pendingSavedState.hasValidAnchor()) {
                orientationHelper.startAfterPadding + pendingSavedState.anchorOffset
            } else {
                orientationHelper.startAfterPadding
            }

            return true
        }

        /**
         * Updates anchor info from a view.
         *
         * @param view The view to use as the anchor.
         * @param position The position of the view.
         */
        fun updateFromView(view: View, position: Int) {
            this.position = position
            this.coordinate = orientationHelper.getDecoratedStart(view)
        }

        fun invalidate() {
            position = NO_POSITION
            coordinate = INVALID_OFFSET
            isValid = false
        }
    }
}
