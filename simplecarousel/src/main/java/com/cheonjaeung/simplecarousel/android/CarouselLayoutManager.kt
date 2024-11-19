package com.cheonjaeung.simplecarousel.android

import android.content.Context
import android.graphics.PointF
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Orientation
import kotlin.math.abs

/**
 * A [RecyclerView.LayoutManager] that makes [RecyclerView] behave like a carousel.
 */
open class CarouselLayoutManager : RecyclerView.LayoutManager, RecyclerView.SmoothScroller.ScrollVectorProvider {

    /**
     * Enable circular mode which means that the first/last item will be connected to the last/first.
     */
    var circular: Boolean = DEFAULT_CIRCULAR
        set(value) {
            if (field != value) {
                field = value
                layoutHelper.setCircular(value)
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
                viewBoundsHelper = ViewBoundsHelper(createParentInfoCallback(value))
                layoutHelper.setOrientationHelper(orientationHelper)
                anchorInfo.setOrientationHelper(orientationHelper)
                requestLayout()
            }
        }

    /**
     * Reverses layout order.
     *
     * If it is `true`, the first item is placed at the end of [RecyclerView] and the last item is
     * placed at the start. When the [orientation] is horizontal, it depends on layout direction.
     * For example, if the [RecyclerView] is RTL and [reverseLayout] is `true`, the [RecyclerView]
     * is working like LTR.
     */
    var reverseLayout = false
        set(value) {
            assertNotInLayoutOrScroll(null)
            if (value != field) {
                field = value
                requestLayout()
            }
        }

    /**
     * If `true`, the layout manager places items to the left/top direction.
     *
     * This is different to [reverseLayout]. [reverseLayout] is depends on layout direction. It means
     * that even if [reverseLayout] is `true`, actual layout direction may not reversed.
     * To clear layout reversing, it is used in internal layout functions.
     */
    private val layoutToLeftTop: Boolean
        get() {
            return if (orientation == HORIZONTAL) {
                reverseLayout xor isRtl()
            } else {
                reverseLayout
            }
        }

    @Suppress("LeakingThis")
    private var orientationHelper: OrientationHelper = OrientationHelper.createOrientationHelper(
        this,
        orientation
    )

    private var viewBoundsHelper: ViewBoundsHelper = ViewBoundsHelper(createParentInfoCallback(orientation))

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
     * Constructs a [CarouselLayoutManager] with specific orientation and circular mode.
     */
    @Suppress("unused")
    constructor(@Orientation orientation: Int, circular: Boolean) : this(orientation, circular, false)

    /**
     * Constructs a [CarouselLayoutManager] with specific orientation, circular mode and reverse layout option.
     */
    @Suppress("unused")
    constructor(@Orientation orientation: Int, circular: Boolean, reverseLayout: Boolean) : super() {
        this.orientation = orientation
        this.circular = circular
        this.reverseLayout = reverseLayout
        this.layoutHelper.setOrientationHelper(orientationHelper)
        this.layoutHelper.setCircular(circular)
        this.anchorInfo.setOrientationHelper(orientationHelper)
    }

    /**
     * Constructs a [CarouselLayoutManager] from XML attribute.
     * To set in XML layout, set this layout manager to `android:layoutManager` in the [RecyclerView].
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
        this.reverseLayout = properties.reverseLayout
        this.layoutHelper.setOrientationHelper(orientationHelper)
        this.layoutHelper.setCircular(circular)
        this.anchorInfo.setOrientationHelper(orientationHelper)
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
        newSavedState.layoutToLeftTop = layoutToLeftTop
        if (childCount > 0) {
            if (layoutToLeftTop) {
                val view = getChildAtClosestToRightOrBottom()
                if (view != null) {
                    newSavedState.anchorPosition = getPosition(view)
                    val decoratedEnd = orientationHelper.getDecoratedEnd(view)
                    val endAfterPadding = orientationHelper.endAfterPadding
                    newSavedState.anchorOffset = endAfterPadding - decoratedEnd
                }
            } else {
                val view = getChildAtClosestToLeftOrTop()
                if (view != null) {
                    newSavedState.anchorPosition = getPosition(view)
                    val decoratedStart = orientationHelper.getDecoratedStart(view)
                    val startAfterPadding = orientationHelper.startAfterPadding
                    newSavedState.anchorOffset = decoratedStart - startAfterPadding
                }
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
        if (DEBUG) {
            Log.d(TAG, "onLayoutChildren: isPreLayout=${state?.isPreLayout}")
        }

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
            anchorInfo.validate()
        }

        // Moves current attached views to scrap for filling items.
        detachAndScrapAttachedViews(recycler)

        val extraSpaces = calculateExtraLayoutSpace(state)
        val extraStart = extraSpaces.first.coerceAtLeast(0)
        val extraEnd = extraSpaces.second.coerceAtLeast(0)
        if (anchorInfo.layoutToLeftTop) {
            // Fill to main direction
            layoutHelper.updateForFillingToLeftOrTop(
                position = anchorInfo.position,
                offset = anchorInfo.coordinate,
                extraSpace = extraStart,
                noRecycleSpace = extraEnd,
                layoutToLeftTop = layoutToLeftTop
            )
            fill(recycler, state)

            // Fill to opposite direction if not filled
            layoutHelper.updateForFillingToRightOrBottom(
                position = anchorInfo.position,
                offset = anchorInfo.coordinate,
                extraSpace = extraEnd,
                noRecycleSpace = extraStart,
                layoutToLeftTop = layoutToLeftTop
            )
            layoutHelper.moveCurrentPosition(state)
            fill(recycler, state)
        } else {
            // Fill to main direction
            layoutHelper.updateForFillingToRightOrBottom(
                position = anchorInfo.position,
                offset = anchorInfo.coordinate,
                extraSpace = extraEnd,
                noRecycleSpace = extraStart,
                layoutToLeftTop = layoutToLeftTop
            )
            fill(recycler, state)

            // Fill to opposite direction if not filled
            layoutHelper.updateForFillingToLeftOrTop(
                position = anchorInfo.position,
                offset = anchorInfo.coordinate,
                extraSpace = extraStart,
                noRecycleSpace = extraEnd,
                layoutToLeftTop = layoutToLeftTop
            )
            layoutHelper.moveCurrentPosition(state)
            fill(recycler, state)
        }
    }

    /**
     * Updates [anchorInfo] for [onLayoutChildren].
     */
    private fun updateAnchorInfoForLayout(anchorInfo: AnchorInfo, state: RecyclerView.State) {
        // Try update anchor info from pending states.
        if (anchorInfo.updateFromPending(pendingPosition, pendingSavedState, layoutToLeftTop, state)) {
            return
        } else {
            // Set useless pending position to no position cause it is invalid.
            pendingPosition = NO_POSITION
        }

        // Try update anchor info from existing children.
        val anchorView = findAnchorView(state, layoutToLeftTop)
        if (anchorView != null) {
            anchorInfo.updateFromView(anchorView, getPosition(anchorView), layoutToLeftTop)
            return
        }

        // Update anchor info from nothing when previous steps are failed.
        anchorInfo.updateFromNothing(layoutToLeftTop)
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
        val smoothScroller = CarouselSmoothScroller(recyclerView?.context, this)
        smoothScroller.targetPosition = position
        startSmoothScroll(smoothScroller)
    }

    /**
     * Calculates the vector pointing to the direction where the [targetPosition] can be found
     * in the shortest way.
     *
     * @param targetPosition The target adapter position.
     */
    override fun computeScrollVectorForPosition(targetPosition: Int): PointF? {
        return computeScrollVectorForPosition(targetPosition, circular)
    }

    /**
     * Calculates the vector pointing to the direction where the [targetPosition] can be found
     * in the shortest way.
     *
     * @param targetPosition The target adapter position.
     * @param assumedCircular The assumed circular mode. It calculates vector with given [assumedCircular]
     * that is independent of [CarouselLayoutManager.circular] condition.
     */
    fun computeScrollVectorForPosition(targetPosition: Int, assumedCircular: Boolean): PointF? {
        if (childCount == 0) {
            return null
        }

        val firstChild = getChildAt(0) ?: return null
        val firstChildPosition = getPosition(firstChild)

        if (assumedCircular) {
            val stepsToLeftTop: Int
            val stepsToRightBottom: Int
            if (targetPosition < firstChildPosition) {
                stepsToLeftTop = firstChildPosition - targetPosition
                stepsToRightBottom = (itemCount - firstChildPosition) + targetPosition
            } else {
                stepsToLeftTop = (itemCount - targetPosition) + firstChildPosition
                stepsToRightBottom = targetPosition - firstChildPosition
            }
            val scrollDirection = if ((stepsToLeftTop < stepsToRightBottom) != layoutToLeftTop) {
                DIRECTION_LEFT_TOP.toFloat()
            } else {
                DIRECTION_RIGHT_BOTTOM.toFloat()
            }
            return if (orientation == HORIZONTAL) {
                PointF(scrollDirection, 0f)
            } else {
                PointF(0f, scrollDirection)
            }
        } else {
            val scrollDirection = if ((targetPosition < firstChildPosition) != layoutToLeftTop) {
                DIRECTION_LEFT_TOP.toFloat()
            } else {
                DIRECTION_RIGHT_BOTTOM.toFloat()
            }
            return if (orientation == HORIZONTAL) {
                PointF(scrollDirection, 0f)
            } else {
                PointF(0f, scrollDirection)
            }
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
        val extraSpaces = calculateExtraLayoutSpace(state)
        val extraStart = extraSpaces.first.coerceAtLeast(0)
        val extraEnd = extraSpaces.second.coerceAtLeast(0)
        if (delta < 0) {
            val anchorView = getChildAtClosestToStart() ?: return 0
            val anchorPosition = getPosition(anchorView)
            layoutHelper.updateForScrollingToLeftOrTop(
                delta = delta,
                view = anchorView,
                position = anchorPosition,
                extraSpace = extraStart,
                noRecycleSpace = extraEnd,
                layoutToLeftTop = layoutToLeftTop,
                state = state
            )
        } else {
            val anchorView = getChildAtClosestToEnd() ?: return 0
            val anchorPosition = getPosition(anchorView)
            layoutHelper.updateForScrollingToRightOrBottom(
                delta = delta,
                view = anchorView,
                position = anchorPosition,
                extraSpace = extraEnd,
                noRecycleSpace = extraStart,
                layoutToLeftTop = layoutToLeftTop,
                state = state
            )
        }

        // Fill items by scrolling amount.
        val scrollingOffsetBeforeFilling = layoutHelper.scrollingOffset
        val filledSpace = fill(recycler, state)
        val consumed = scrollingOffsetBeforeFilling + filledSpace
        if (consumed < 0) {
            if (DEBUG) {
                Log.d(TAG, "scrollBy: scrolled=0 (no more element)")
            }
            return 0
        }

        // Calculate final scrolled amount.
        val scrolled = if (abs(delta) > consumed) {
            consumed * layoutHelper.layoutDirection
        } else {
            delta
        }
        orientationHelper.offsetChildren(-scrolled)
        if (DEBUG) {
            Log.d(TAG, "scrollBy: scrolled=$scrolled, requested=$delta")
        }
        return scrolled
    }

    /**
     * Measure and layout items until there is no item or no empty space.
     *
     * @return Pixel size that it filled.
     */
    private fun fill(recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        val fillStart = layoutHelper.availableSpace

        if (layoutHelper.scrollingOffset != INVALID_OFFSET) {
            if (layoutHelper.availableSpace < 0) {
                layoutHelper.adjustScrollOffset(layoutHelper.availableSpace)
            }
        }

        var remainingSpace = layoutHelper.availableSpace + layoutHelper.extraSpace
        while (remainingSpace > 0 && layoutHelper.hasNext(state)) {
            // Measure the current view size.
            val view = layoutHelper.next(recycler, state)
            if (layoutHelper.layoutDirection == DIRECTION_RIGHT_BOTTOM) {
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
                if (isLtr()) {
                    left = paddingLeft
                    right = left + viewSizeInOther
                } else {
                    right = width - paddingRight
                    left = right - viewSizeInOther
                }
                if (layoutHelper.layoutDirection == DIRECTION_LEFT_TOP) {
                    bottom = layoutHelper.offset
                    top = layoutHelper.offset - viewSize
                } else {
                    top = layoutHelper.offset
                    bottom = layoutHelper.offset + viewSize
                }
            } else {
                top = paddingTop
                bottom = top + viewSizeInOther
                if (layoutHelper.layoutDirection == DIRECTION_LEFT_TOP) {
                    right = layoutHelper.offset
                    left = layoutHelper.offset - viewSize
                } else {
                    left = layoutHelper.offset
                    right = layoutHelper.offset + viewSize
                }
            }

            // Layout the view into the coordinate.
            layoutDecoratedWithMargins(view, left, top, right, bottom)
            if (DEBUG) {
                val p = view.layoutParams as RecyclerView.LayoutParams
                Log.d(
                    TAG,
                    "child at ${getPosition(view)} laid out with " +
                        "left=${left + p.leftMargin}, top=${top + p.topMargin}, " +
                        "right=${right - p.rightMargin}, bottom=${bottom - p.bottomMargin}"
                )
            }

            // Update layout helper for the next view.
            layoutHelper.updateForFillingNext(viewSize)
            remainingSpace -= viewSize

            // Update scrolling offset if it triggered by scrolling.
            if (layoutHelper.scrollingOffset != INVALID_OFFSET) {
                if (layoutHelper.availableSpace < 0) {
                    layoutHelper.adjustScrollOffset(layoutHelper.availableSpace)
                }
                layoutHelper.adjustScrollOffset(viewSize)
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
     * Calculates the pixel amount of extra space that this layout manager should lay out.
     *
     * By default, this layout manager lays out only the visible size of [RecyclerView] without padding.
     * But in some cases, layout manager should lays out items over the default size. For example, when
     * a [RecyclerView] disables `clipToPadding`, [RecyclerView] will shows items covered by padding.
     * In this case, the layout manager should lay out items at the padding area.
     *
     * @return A pair of extra pixel sizes. Left/right for horizontal or top/bottom for vertical.
     */
    protected open fun calculateExtraLayoutSpace(state: RecyclerView.State): Pair<Int, Int> {
        val extraSpace = if (state.hasTargetScrollPosition()) {
            orientationHelper.totalSpace
        } else {
            0
        }
        return Pair(
            extraSpace + orientationHelper.startAfterPadding,
            extraSpace + orientationHelper.endPadding
        )
    }

    private fun isLtr(): Boolean {
        return layoutDirection == View.LAYOUT_DIRECTION_LTR
    }

    private fun isRtl(): Boolean {
        return layoutDirection == View.LAYOUT_DIRECTION_RTL
    }

    /**
     * Returns the adapter position of the first partially visible item.
     *
     * This function doesn't consider layout direction. Even if [reverseLayout] is `true`, it always find
     * the visible view from left or top.
     *
     * If items have item decorations or margins, it will be considered in visibility calculation.
     *
     * It always calculate item visibility like [RecyclerView.mClipToPadding] is `true`. Views behind the
     * padding are ignored.
     *
     * @return The adapter position of the first partially visible item or [RecyclerView.NO_POSITION] if there
     * are not any partially visible items.
     */
    fun findFirstVisibleItemPosition(): Int {
        val view = viewBoundsHelper.findPartiallyVisibleView(0, childCount)
        return if (view != null) getPosition(view) else RecyclerView.NO_POSITION
    }

    /**
     * Returns the adapter position of the first completely visible item.
     *
     * This function doesn't consider layout direction. Even if [reverseLayout] is `true`, it always find
     * the visible view from left or top.
     *
     * If items have item decorations or margins, it will be considered in visibility calculation.
     *
     * It always calculate item visibility like [RecyclerView.mClipToPadding] is `true`. Views behind the
     * padding are ignored.
     *
     * @return The adapter position of the first completely visible item or [RecyclerView.NO_POSITION] if there
     * are not any completely visible items.
     */
    fun findFirstCompletelyVisibleItemPosition(): Int {
        val view = viewBoundsHelper.findCompletelyVisibleView(0, childCount)
        return if (view != null) getPosition(view) else RecyclerView.NO_POSITION
    }

    /**
     * Returns the adapter position of the last partially visible item.
     *
     * This function doesn't consider layout direction. Even if [reverseLayout] is `true`, it always find
     * the visible view from right or bottom.
     *
     * If items have item decorations or margins, it will be considered in visibility calculation.
     *
     * It always calculate item visibility like [RecyclerView.mClipToPadding] is `true`. Views behind the
     * padding are ignored.
     *
     * @return The adapter position of the last partially visible item or [RecyclerView.NO_POSITION] if there
     * are not any partially visible items.
     */
    fun findLastVisibleItemPosition(): Int {
        val view = viewBoundsHelper.findPartiallyVisibleView(childCount - 1, -1)
        return if (view != null) getPosition(view) else RecyclerView.NO_POSITION
    }

    /**
     * Returns the adapter position of the last completely visible item.
     *
     * This function doesn't consider layout direction. Even if [reverseLayout] is `true`, it always find
     * the visible view from right or bottom.
     *
     * If items have item decorations or margins, it will be considered in visibility calculation.
     *
     * It always calculate item visibility like [RecyclerView.mClipToPadding] is `true`. Views behind the
     * padding are ignored.
     *
     * @return The adapter position of the last completely visible item or [RecyclerView.NO_POSITION] if there
     * are not any completely visible items.
     */
    fun findLastCompletelyVisibleItemPosition(): Int {
        val view = viewBoundsHelper.findCompletelyVisibleView(childCount - 1, -1)
        return if (view != null) getPosition(view) else RecyclerView.NO_POSITION
    }

    /**
     * Returns a child view at the closest to left or top of the layout.
     */
    private fun getChildAtClosestToLeftOrTop(): View? {
        return getChildAt(if (layoutToLeftTop) childCount - 1 else 0)
    }

    /**
     * Returns a child view at the closest to right or bottom of the layout.
     */
    private fun getChildAtClosestToRightOrBottom(): View? {
        return getChildAt(if (layoutToLeftTop) 0 else childCount - 1)
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
    private fun findAnchorView(state: RecyclerView.State, layoutToLeftTop: Boolean): View? {
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
                            if (layoutToLeftTop) {
                                if (isViewOutOfBoundsAfterEnd) {
                                    thirdCandidate = view
                                } else if (secondCandidate == null) {
                                    secondCandidate = view
                                }
                            } else {
                                if (isViewOutOfBoundsBeforeStart) {
                                    thirdCandidate = view
                                } else if (secondCandidate == null) {
                                    secondCandidate = view
                                }
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
        val scrollingOffset = layoutHelper.scrollingOffset
        val extraSpace = layoutHelper.extraSpace
        val noRecycleSpace = layoutHelper.noRecycleSpace
        if (layoutHelper.layoutDirection == DIRECTION_RIGHT_BOTTOM) {
            recycleChildrenFromLeftOrTop(recycler, scrollingOffset, extraSpace, noRecycleSpace)
        } else {
            recycleChildrenFromRightOrBottom(recycler, scrollingOffset, extraSpace, noRecycleSpace)
        }
    }

    /**
     * Recycles out of bounds children from left/top of view. It may called after scrolling toward
     * the right/bottom.
     */
    private fun recycleChildrenFromLeftOrTop(
        recycler: RecyclerView.Recycler,
        scrollingOffset: Int,
        extraSpace: Int,
        noRecycleSpace: Int
    ) {
        if (scrollingOffset + extraSpace < 0) {
            return
        }
        val limit = scrollingOffset - noRecycleSpace
        val childCount = this.childCount
        for (i in 0 until childCount) {
            val view = getChildAt(i)
            if (
                orientationHelper.getDecoratedEnd(view) > limit ||
                orientationHelper.getTransformedEndWithDecoration(view) > limit
            ) {
                recycleChildrenInRange(recycler, 0, i)
                return
            }
        }
    }

    /**
     * Recycles out of bounds children from right/bottom of view. It may called after scrolling
     * toward the left/top.
     */
    private fun recycleChildrenFromRightOrBottom(
        recycler: RecyclerView.Recycler,
        scrollingOffset: Int,
        extraSpace: Int,
        noRecycleSpace: Int
    ) {
        if (scrollingOffset + extraSpace < 0) {
            return
        }
        val limit = orientationHelper.end - scrollingOffset + noRecycleSpace
        val childCount = this.childCount
        for (i in (childCount - 1) downTo 0) {
            val view = getChildAt(i)
            if (
                orientationHelper.getDecoratedStart(view) < limit ||
                orientationHelper.getTransformedStartWithDecoration(view) < limit
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
        if (DEBUG) {
            Log.d(TAG, "${abs(end - start)} children recycled")
        }
    }

    private fun createParentInfoCallback(orientation: Int): ViewBoundsHelper.ParentInfoCallback {
        return when (orientation) {
            HORIZONTAL -> object : ViewBoundsHelper.ParentInfoCallback {
                override fun getChildAt(index: Int): View? {
                    return this@CarouselLayoutManager.getChildAt(index)
                }

                override fun getParentStartAfterPadding(): Int {
                    return this@CarouselLayoutManager.paddingLeft
                }

                override fun getParentEndBeforePadding(): Int {
                    return this@CarouselLayoutManager.width - this@CarouselLayoutManager.paddingRight
                }

                override fun getChildStartWithinParent(child: View): Int {
                    val params = child.layoutParams as RecyclerView.LayoutParams
                    return this@CarouselLayoutManager.getDecoratedLeft(child) - params.leftMargin
                }

                override fun getChildEndWithinParent(child: View): Int {
                    val params = child.layoutParams as RecyclerView.LayoutParams
                    return this@CarouselLayoutManager.getDecoratedRight(child) + params.rightMargin
                }
            }

            VERTICAL -> object : ViewBoundsHelper.ParentInfoCallback {
                override fun getChildAt(index: Int): View? {
                    return this@CarouselLayoutManager.getChildAt(index)
                }

                override fun getParentStartAfterPadding(): Int {
                    return this@CarouselLayoutManager.paddingTop
                }

                override fun getParentEndBeforePadding(): Int {
                    return this@CarouselLayoutManager.height - this@CarouselLayoutManager.paddingBottom
                }

                override fun getChildStartWithinParent(child: View): Int {
                    val params = child.layoutParams as RecyclerView.LayoutParams
                    return this@CarouselLayoutManager.getDecoratedTop(child) - params.topMargin
                }

                override fun getChildEndWithinParent(child: View): Int {
                    val params = child.layoutParams as RecyclerView.LayoutParams
                    return this@CarouselLayoutManager.getDecoratedBottom(child) + params.bottomMargin
                }
            }

            else -> throw IllegalArgumentException("Invalid orientation: $orientation")
        }
    }

    companion object {
        private const val TAG: String = "CarouselLayoutManager"
        private const val DEBUG: Boolean = false

        const val HORIZONTAL: Int = RecyclerView.HORIZONTAL
        const val VERTICAL: Int = RecyclerView.VERTICAL

        private const val DEFAULT_ORIENTATION: Int = VERTICAL
        private const val DEFAULT_CIRCULAR: Boolean = true

        private const val DIRECTION_LEFT_TOP: Int = -1
        private const val DIRECTION_RIGHT_BOTTOM: Int = 1

        private const val DIRECTION_HEAD: Int = -1
        private const val DIRECTION_TAIL: Int = 1

        private const val NO_POSITION: Int = RecyclerView.NO_POSITION
        private const val INVALID_OFFSET: Int = Int.MIN_VALUE

        private const val INT_TRUE: Int = 1
        private const val INT_FALSE: Int = 0
    }

    /**
     * A parcelable class to save the state of the layout manager.
     */
    private data class SavedState(
        var anchorPosition: Int = NO_POSITION,
        var anchorOffset: Int = INVALID_OFFSET,
        var layoutToLeftTop: Boolean = false
    ) : Parcelable {

        constructor(parcel: Parcel) : this(
            anchorPosition = parcel.readInt(),
            anchorOffset = parcel.readInt(),
            layoutToLeftTop = parcel.readInt() == INT_TRUE
        )

        constructor(other: SavedState) : this(
            anchorPosition = other.anchorPosition,
            anchorOffset = other.anchorOffset,
            layoutToLeftTop = other.layoutToLeftTop
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
            val intBoolean = if (layoutToLeftTop) INT_TRUE else INT_FALSE
            parcel.writeInt(intBoolean)
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
    private class LayoutHelper {
        /**
         * Pixel offset where layout should start.
         */
        var offset: Int = 0
            private set

        /**
         * The amount of pixel offset this manager can scroll without creating new views.
         * It used only when scrolling.
         */
        var scrollingOffset: Int = INVALID_OFFSET
            private set

        /**
         * Current position on the recycler view adapter.
         */
        var currentPosition: Int = RecyclerView.NO_POSITION
            private set

        /**
         * Pixel size that the layout manager should fill.
         */
        var availableSpace: Int = 0
            private set

        /**
         * Pixel size that the layout manager should fill after filling [availableSpace].
         */
        var extraSpace: Int = 0
            private set

        /**
         * Pixel size that should be excluded for recycling.
         */
        var noRecycleSpace: Int = 0
            private set

        /**
         * Direction where the layout manager should fill.
         */
        var layoutDirection: Int = DIRECTION_RIGHT_BOTTOM
            private set

        /**
         * Direction where the layout manager traverse adapter items.
         */
        var itemDirection: Int = DIRECTION_TAIL
            private set

        /**
         * Is the layout manager's circular mode enabled.
         */
        var circular: Boolean = true
            private set

        /**
         * Reference of [OrientationHelper] in the layout manager.
         */
        lateinit var orientationHelper: OrientationHelper
            private set

        fun setCircular(circular: Boolean) {
            this.circular = circular
        }

        fun setOrientationHelper(orientationHelper: OrientationHelper) {
            this.orientationHelper = orientationHelper
        }

        /**
         * Adjusts [scrollingOffset] by the [value].
         */
        fun adjustScrollOffset(value: Int) {
            this.scrollingOffset += value
        }

        /**
         * Updates the layout helper to layout items to the left/top direction.
         *
         * @param position A position to start get item from adapter.
         * @param offset A pixel offset to start layout.
         * @param extraSpace A pixel size to fill additionally to left or top direction.
         * @param noRecycleSpace A pixel size that should be excluded for recycling.
         * @param layoutToLeftTop Is the layout direction to left or top.
         */
        fun updateForFillingToLeftOrTop(
            position: Int,
            offset: Int,
            extraSpace: Int,
            noRecycleSpace: Int,
            layoutToLeftTop: Boolean
        ) {
            this.currentPosition = position
            this.offset = offset
            this.scrollingOffset = INVALID_OFFSET
            this.layoutDirection = DIRECTION_LEFT_TOP
            this.itemDirection = if (layoutToLeftTop) DIRECTION_TAIL else DIRECTION_HEAD
            this.availableSpace = offset - orientationHelper.startAfterPadding
            this.extraSpace = extraSpace
            this.noRecycleSpace = noRecycleSpace
        }

        /**
         * Updates the layout helper to layout items to the right/bottom direction.
         *
         * @param position A position to start get item from adapter.
         * @param offset A pixel offset to start layout.
         * @param extraSpace A pixel size to fill additionally to right or bottom direction.
         * @param noRecycleSpace A pixel size that should be excluded for recycling.
         * @param layoutToLeftTop Is the layout direction to left or top.
         */
        fun updateForFillingToRightOrBottom(
            position: Int,
            offset: Int,
            extraSpace: Int,
            noRecycleSpace: Int,
            layoutToLeftTop: Boolean
        ) {
            this.currentPosition = position
            this.offset = offset
            this.scrollingOffset = INVALID_OFFSET
            this.layoutDirection = DIRECTION_RIGHT_BOTTOM
            this.itemDirection = if (layoutToLeftTop) DIRECTION_HEAD else DIRECTION_TAIL
            this.availableSpace = orientationHelper.endAfterPadding - offset
            this.extraSpace = extraSpace
            this.noRecycleSpace = noRecycleSpace
        }

        /**
         * Updates the layout helper to layout items while scrolling to the left/top direction.
         *
         * @param delta Amount of pixels to scroll.
         * @param view A view at the closest to start when scrolling triggered.
         * @param position A position of the [view].
         * @param extraSpace A pixel size to fill additionally to left or top direction.
         * @param noRecycleSpace A pixel size that should be excluded for recycling.
         * @param layoutToLeftTop Is the layout direction to left or top.
         */
        fun updateForScrollingToLeftOrTop(
            delta: Int,
            view: View,
            position: Int,
            extraSpace: Int,
            noRecycleSpace: Int,
            layoutToLeftTop: Boolean,
            state: RecyclerView.State
        ) {
            val absDelta = abs(delta)
            this.layoutDirection = DIRECTION_LEFT_TOP
            this.itemDirection = if (layoutToLeftTop) DIRECTION_TAIL else DIRECTION_HEAD
            this.currentPosition = position + itemDirection
            validateCurrentPositionForCircular(state)
            this.offset = orientationHelper.getDecoratedStart(view)
            this.scrollingOffset = orientationHelper.startAfterPadding - orientationHelper.getDecoratedStart(view)
            this.availableSpace = absDelta - this.scrollingOffset
            this.extraSpace = extraSpace
            this.noRecycleSpace = noRecycleSpace
        }

        /**
         * Updates the layout helper to layout items while scrolling to the right/bottom direction.
         *
         * @param delta Amount of pixels to scroll.
         * @param view A view at the closest to end when scrolling triggered.
         * @param position A position of the [view].
         * @param extraSpace A pixel size to fill additionally to right or bottom direction.
         * @param noRecycleSpace A pixel size that should be excluded for recycling.
         * @param layoutToLeftTop Is the layout direction to left or top.
         */
        fun updateForScrollingToRightOrBottom(
            delta: Int,
            view: View,
            position: Int,
            extraSpace: Int,
            noRecycleSpace: Int,
            layoutToLeftTop: Boolean,
            state: RecyclerView.State
        ) {
            val absDelta = abs(delta)
            this.layoutDirection = DIRECTION_RIGHT_BOTTOM
            this.itemDirection = if (layoutToLeftTop) DIRECTION_HEAD else DIRECTION_TAIL
            this.currentPosition = position + itemDirection
            validateCurrentPositionForCircular(state)
            this.offset = orientationHelper.getDecoratedEnd(view)
            this.scrollingOffset = orientationHelper.getDecoratedEnd(view) - orientationHelper.endAfterPadding
            this.availableSpace = absDelta - this.scrollingOffset
            this.extraSpace = extraSpace
            this.noRecycleSpace = noRecycleSpace
        }

        /**
         * Updates [offset] and [availableSpace] to fill next item.
         * This method must called whenever one item is placed in layout.
         *
         * @param viewSize Placed view size in pixels.
         */
        fun updateForFillingNext(viewSize: Int) {
            this.offset += viewSize * this.layoutDirection
            this.availableSpace -= viewSize
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
            moveCurrentPosition(state)
            return view
        }

        /**
         * Moves [currentPosition] to next position without getting item.
         */
        fun moveCurrentPosition(state: RecyclerView.State) {
            this.currentPosition += itemDirection
            validateCurrentPositionForCircular(state)
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

        override fun toString(): String {
            return "LayoutHelper(" +
                "offset=$offset, " +
                "scrollingOffset=$scrollingOffset, " +
                "currentPosition=$currentPosition, " +
                "availableSpace=$availableSpace, " +
                "extraSpace=$extraSpace, " +
                "noRecycleSpace=$noRecycleSpace, " +
                "layoutDirection=$layoutDirection, " +
                "itemDirection=$itemDirection, " +
                "circular=$circular" +
                ")"
        }
    }

    /**
     * Helper class to hold temporary values while [CarouselLayoutManager] is laying out items.
     * It holds values about the anchor view.
     */
    private class AnchorInfo {
        /**
         * Position of the anchor view.
         */
        var position: Int = NO_POSITION
            private set

        /**
         * Coordinate of the anchor view.
         */
        var coordinate: Int = INVALID_OFFSET
            private set

        /**
         * Is the layout direction to left or top.
         */
        var layoutToLeftTop: Boolean = false
            private set

        /**
         * If `false`, the anchor view should be updated.
         */
        var isValid: Boolean = false
            private set

        /**
         * Reference of [OrientationHelper] in the layout manager.
         */
        lateinit var orientationHelper: OrientationHelper
            private set

        fun setOrientationHelper(orientationHelper: OrientationHelper) {
            this.orientationHelper = orientationHelper
        }

        /**
         * Updates anchor info from nothing. It means that there is no view to use as the anchor.
         * For successful layout, it assumes the anchor view exists at the start of the view.
         * [coordinate] will be the start padding and the [position] will be the first item.
         */
        fun updateFromNothing(layoutToLeftTop: Boolean) {
            this.position = 0
            this.layoutToLeftTop = layoutToLeftTop
            this.coordinate = if (layoutToLeftTop) {
                orientationHelper.endAfterPadding
            } else {
                orientationHelper.startAfterPadding
            }
        }

        /**
         * Updates anchor info from pending states. It only updates info when [pendingPosition] is valid.
         *
         * @return `true` if anchor info is updated successfully.
         */
        fun updateFromPending(
            pendingPosition: Int,
            pendingSavedState: SavedState?,
            layoutToLeftTop: Boolean,
            state: RecyclerView.State
        ): Boolean {
            if (pendingPosition == NO_POSITION) {
                return false
            }

            if (pendingPosition < 0 || pendingPosition >= state.itemCount) {
                return false
            }

            this.position = pendingPosition
            if (pendingSavedState != null && pendingSavedState.hasValidAnchor()) {
                this.layoutToLeftTop = pendingSavedState.layoutToLeftTop
                this.coordinate = if (this.layoutToLeftTop) {
                    orientationHelper.endAfterPadding - pendingSavedState.anchorOffset
                } else {
                    orientationHelper.startAfterPadding + pendingSavedState.anchorOffset
                }
                return true
            }

            this.layoutToLeftTop = layoutToLeftTop
            this.coordinate = if (layoutToLeftTop) {
                orientationHelper.endAfterPadding
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
         * @param layoutToLeftTop Is the layout direction to left or top.
         */
        fun updateFromView(view: View, position: Int, layoutToLeftTop: Boolean) {
            this.position = position
            this.layoutToLeftTop = layoutToLeftTop
            this.coordinate = if (this.layoutToLeftTop) {
                orientationHelper.getDecoratedEnd(view) + orientationHelper.totalSpaceChange
            } else {
                orientationHelper.getDecoratedStart(view)
            }
        }

        /**
         * Makes it valid. After validate, [AnchorInfo] is not updated until [invalidate].
         */
        fun validate() {
            this.isValid = true
        }

        /**
         * Makes it invalid and updatable.
         */
        fun invalidate() {
            position = NO_POSITION
            coordinate = INVALID_OFFSET
            isValid = false
        }

        override fun toString(): String {
            return "AnchorInfo(" +
                "position=$position, " +
                "coordinate=$coordinate, " +
                "layoutToLeftTop=$layoutToLeftTop, " +
                "isValid=$isValid" +
                ")"
        }
    }
}
