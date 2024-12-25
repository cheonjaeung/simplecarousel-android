package com.cheonjaeung.simplecarousel.android.pager

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.ClassLoaderCreator
import android.util.AttributeSet
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IntDef
import androidx.annotation.IntRange
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.StatefulAdapter
import androidx.viewpager2.widget.ViewPager2
import com.cheonjaeung.simplecarousel.android.CarouselLayoutManager
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * [CarouselPager] is a similar component to [androidx.viewpager2.widget.ViewPager2] but it supports carousel
 * behavior.
 */
class CarouselPager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : ViewGroup(context, attrs, defStyleAttr, defStyleRes) {
    /**
     * The underlying [RecyclerView].
     */
    internal val recyclerView: RecyclerView

    /**
     * The layout manager for the underlying [recyclerView].
     */
    private val layoutManager: CarouselPagerLayoutManager

    /**
     * Reusable bounds to layout internal [recyclerView].
     */
    private val recyclerViewBounds: Rect = Rect()

    /**
     * A [RecyclerView.Adapter] to provider page items on demand.
     */
    var adapter: RecyclerView.Adapter<*>?
        get() = recyclerView.adapter
        set(value) {
            recyclerView.adapter?.unregisterAdapterDataObserver(dataSetChangeNotifier)
            recyclerView.adapter = value
            currentItem = 0
            restorePendingState()
            recyclerView.adapter?.registerAdapterDataObserver(dataSetChangeNotifier)
        }

    private var pendingAdapterState: Parcelable? = null

    /**
     * Enable circular mode which means that the first/ last item will be connected to the last/ first.
     */
    var circular: Boolean
        get() = layoutManager.circular
        set(value) {
            layoutManager.circular = value
        }

    /**
     * Current orientation of this pager. Either [HORIZONTAL] or [VERTICAL].
     */
    @Orientation
    var orientation: Int
        @Orientation
        get() = layoutManager.orientation
        set(value) {
            if (value != HORIZONTAL && value != VERTICAL) {
                throw IllegalArgumentException("Invalid orientation: $value")
            }
            layoutManager.orientation = value
        }

    private var _currentItem: Int = RecyclerView.NO_POSITION

    /**
     * The currently selected page adapter position.
     */
    var currentItem: Int
        get() = _currentItem
        set(value) {
            setCurrentItem(value, true)
        }

    private var pendingCurrentItem: Int = RecyclerView.NO_POSITION

    /**
     * Current scroll state of the [CarouselPager]. Returned value can be on of [SCROLL_STATE_IDLE],
     * [SCROLL_STATE_DRAGGING] or [SCROLL_STATE_SETTLING].
     */
    @ScrollState
    val scrollState: Int
        get() = scrollListenerAdapter.scrollState

    /**
     * The number of pages that should be retained to either side of the current page. The value must
     * either [DEFAULT_OFFSCREEN_PAGE_LIMIT] or positive.
     *
     * Pages over the limit will be recreated from adapter when needed. When limit is [DEFAULT_OFFSCREEN_PAGE_LIMIT],
     * it just derives to [RecyclerView].
     */
    @OffscreenPageLimit
    var offscreenPageLimit: Int = DEFAULT_OFFSCREEN_PAGE_LIMIT
        set(value) {
            if (value != DEFAULT_OFFSCREEN_PAGE_LIMIT && value < 1) {
                throw IllegalArgumentException(
                    "offscreenPageLimit must be positive or DEFAULT_OFFSCREEN_PAGE_LIMIT: value=$value"
                )
            }
            field = value
            recyclerView.requestLayout()
        }

    /**
     * Returns the number of [RecyclerView.ItemDecoration] count currently added to this [CarouselPager].
     */
    @Suppress("unused")
    val itemDecorationCount: Int
        get() = recyclerView.itemDecorationCount

    private val scrollListenerAdapter: ScrollListenerAdapter
    private val pageTransformerAdapter: PageTransformerAdapter

    private val dataSetChangeNotifier = DataSetChangeNotifier()

    internal val pageChangeCallbacks: MutableList<ViewPager2.OnPageChangeCallback> = mutableListOf()
    internal var pageTransformer: ViewPager2.PageTransformer? = null

    private var savedRecyclerViewItemAnimatorExists: Boolean = false
    private var savedRecyclerViewItmeAnimator: RecyclerView.ItemAnimator? = null

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.CarouselPager)
        ViewCompat.saveAttributeDataForStyleable(
            this,
            context,
            R.styleable.CarouselPager,
            attrs,
            a,
            defStyleAttr,
            defStyleRes
        )
        val circular = a.getBoolean(R.styleable.CarouselPager_circular, DEFAULT_CIRCULAR)
        val orientation = a.getInt(R.styleable.CarouselPager_android_orientation, DEFAULT_ORIENTATION)
        a.recycle()

        recyclerView = RecyclerView(context)
        recyclerView.id = View.generateViewId()
        recyclerView.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
        recyclerView.setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_PAGING)

        layoutManager = CarouselPagerLayoutManager(orientation, circular)
        recyclerView.layoutManager = layoutManager

        recyclerView.addOnChildAttachStateChangeListener(ChildLayoutParamsEnforcer())

        val pagerSnapHelper = PagerSnapHelper()
        pagerSnapHelper.attachToRecyclerView(recyclerView)

        scrollListenerAdapter = ScrollListenerAdapter(this)
        recyclerView.addOnScrollListener(scrollListenerAdapter)

        pageTransformerAdapter = PageTransformerAdapter(this)
        registerOnPageChangeCallback(pageTransformerAdapter)

        attachViewToParent(recyclerView, 0, recyclerView.layoutParams)
    }

    override fun onSaveInstanceState(): Parcelable? {
        val viewState = super.onSaveInstanceState() ?: return null
        val savedState = SavedState(viewState)
        savedState.recyclerViewId = recyclerView.id
        savedState.currentItem = if (pendingCurrentItem != RecyclerView.NO_POSITION) {
            pendingCurrentItem
        } else {
            currentItem
        }
        if (pendingAdapterState != null) {
            savedState.adapterState = pendingAdapterState
        } else {
            val adapter = this.adapter
            if (adapter is StatefulAdapter) {
                savedState.adapterState = adapter.saveState()
            }
        }
        return savedState
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        pendingCurrentItem = state.currentItem
        pendingAdapterState = state.adapterState
    }

    private fun restorePendingState() {
        if (pendingCurrentItem == RecyclerView.NO_POSITION) {
            return
        }
        val adapter = this.adapter ?: return
        val pendingAdapterState = this.pendingAdapterState
        if (pendingAdapterState != null) {
            if (adapter is StatefulAdapter) {
                adapter.restoreState(pendingAdapterState)
            }
            this.pendingAdapterState = null
        }
        currentItem = pendingCurrentItem
        pendingCurrentItem = RecyclerView.NO_POSITION
        recyclerView.scrollToPosition(currentItem)
    }

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>?) {
        val savedState = container?.get(id)
        if (savedState is SavedState) {
            val prevRecyclerViewId = savedState.recyclerViewId
            val currentRecyclerViewId = recyclerView.id
            container.put(currentRecyclerViewId, container.get(prevRecyclerViewId))
            container.remove(prevRecyclerViewId)
        }
        super.dispatchRestoreInstanceState(container)
        restorePendingState()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        measureChild(recyclerView, widthMeasureSpec, heightMeasureSpec)

        val width = recyclerView.measuredWidth
        val height = recyclerView.measuredHeight
        val measuredState = recyclerView.measuredState

        setMeasuredDimension(
            resolveSizeAndState(width, widthMeasureSpec, measuredState),
            resolveSizeAndState(
                height,
                heightMeasureSpec,
                measuredState shl MEASURED_HEIGHT_STATE_SHIFT
            )
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        recyclerViewBounds.set(
            paddingLeft,
            paddingTop,
            right - left - paddingRight,
            bottom - top - paddingBottom
        )

        recyclerView.layout(
            recyclerViewBounds.left,
            recyclerViewBounds.top,
            recyclerViewBounds.right,
            recyclerViewBounds.bottom
        )
    }

    /**
     * Sets the currently selected page item position.
     * When smooth scroll, it can jump to nearby position to target before smooth scroll.
     *
     * @param position Position to select.
     * @param smoothScroll It scrolls to the [position] with animation if `true` or move immediately otherwise.
     */
    fun setCurrentItem(position: Int, smoothScroll: Boolean) {
        val adapter = this.adapter
        if (adapter == null) {
            if (pendingCurrentItem != RecyclerView.NO_POSITION) {
                pendingCurrentItem = position
            }
            return
        }

        if (adapter.itemCount <= 0) {
            return
        }

        if (position == currentItem && scrollListenerAdapter.scrollState == SCROLL_STATE_IDLE) {
            return
        }

        if (position == currentItem && smoothScroll) {
            return
        }

        var prevPosition: Float = currentItem.toFloat()
        _currentItem = position

        if (scrollListenerAdapter.scrollState != SCROLL_STATE_IDLE) {
            prevPosition = scrollListenerAdapter.relativePosition
        }

        scrollListenerAdapter.notifyProgrammaticScroll(position, smoothScroll)

        if (!smoothScroll) {
            recyclerView.scrollToPosition(position)
            return
        }

        if (circular) {
            val vector = layoutManager.computeScrollVectorForPosition(position) ?: return
            val steps: Int
            val stepsOpposite: Int
            if (position < prevPosition) {
                steps = prevPosition.toInt() - position
                stepsOpposite = (layoutManager.itemCount - prevPosition.toInt()) + position
            } else {
                steps = position - prevPosition.toInt()
                stepsOpposite = (layoutManager.itemCount - position) + prevPosition.toInt()
            }
            if (abs(steps) > 3 || abs(stepsOpposite) > 3) {
                val isReversed = if (orientation == HORIZONTAL) isRtl() else false
                recyclerView.scrollToPosition(
                    if ((vector.x < 0 || vector.y < 0) xor isReversed) {
                        position + 3
                    } else {
                        position - 3
                    }
                )
                recyclerView.post {
                    recyclerView.smoothScrollToPosition(position)
                }
                return
            }
        } else {
            if (abs(position - prevPosition) > 3) {
                recyclerView.scrollToPosition(
                    if (position > prevPosition) {
                        position - 3
                    } else {
                        position + 3
                    }
                )
                recyclerView.post {
                    recyclerView.smoothScrollToPosition(position)
                }
                return
            }
        }
        recyclerView.smoothScrollToPosition(position)
    }

    override fun canScrollHorizontally(direction: Int): Boolean {
        return recyclerView.canScrollHorizontally(direction)
    }

    override fun canScrollVertically(direction: Int): Boolean {
        return recyclerView.canScrollVertically(direction)
    }

    /**
     * Adds a [ViewPager2.OnPageChangeCallback] to receive change changing and scrolling events.
     */
    fun registerOnPageChangeCallback(onPageChangeCallback: ViewPager2.OnPageChangeCallback) {
        pageChangeCallbacks.add(onPageChangeCallback)
    }

    /**
     * Removes a [ViewPager2.OnPageChangeCallback] that was previously added to this pager.
     */
    fun unregisterOnPageChangeCallback(onPageChangeCallback: ViewPager2.OnPageChangeCallback) {
        pageChangeCallbacks.remove(onPageChangeCallback)
    }

    /**
     * Sets a [ViewPager2.PageTransformer] to apply transformation to the visible and attached pages.
     *
     * Note: Setting a [ViewPager2.PageTransformer] disables dataset change animations of [RecyclerView] to
     * avoiding animation conflicts. Sets `null` to restore dataset change animations.
     */
    fun setPageTransformer(transformer: ViewPager2.PageTransformer?) {
        if (transformer == pageTransformer) {
            return
        }

        if (transformer != null) {
            if (!savedRecyclerViewItemAnimatorExists) {
                savedRecyclerViewItmeAnimator = recyclerView.itemAnimator
                savedRecyclerViewItemAnimatorExists = true
            }
            recyclerView.itemAnimator = null
        } else {
            if (savedRecyclerViewItemAnimatorExists) {
                recyclerView.itemAnimator = savedRecyclerViewItmeAnimator
                savedRecyclerViewItmeAnimator = null
                savedRecyclerViewItemAnimatorExists = false
            }
        }

        pageTransformer = transformer
        requestTransform()
    }

    /**
     * Requests a call to transform current visible and attached pages via added [ViewPager2.PageTransformer].
     * To set [ViewPager2.PageTransformer], use [setPageTransformer] method.
     */
    fun requestTransform() {
        if (pageTransformer == null) {
            return
        }

        val relativePosition = scrollListenerAdapter.relativePosition
        val position = relativePosition.toInt()
        val positionOffset = relativePosition - position.toFloat()
        val positionOffsetPx = (calculatePageSize() * positionOffset).roundToInt()
        pageTransformerAdapter.onPageScrolled(position, positionOffset, positionOffsetPx)
    }

    /**
     * Adds an [RecyclerView.ItemDecoration] to this [CarouselPager].
     */
    @Suppress("unused")
    fun addItemDecoration(decoration: RecyclerView.ItemDecoration) {
        recyclerView.addItemDecoration(decoration)
    }

    /**
     * Adds an [RecyclerView.ItemDecoration] to this [CarouselPager] at the specific index.
     */
    @Suppress("unused")
    fun addItemDecoration(decoration: RecyclerView.ItemDecoration, index: Int) {
        recyclerView.addItemDecoration(decoration, index)
    }

    /**
     * Returns an [RecyclerView.ItemDecoration] previously added to this [CarouselPager].
     */
    @Suppress("unused")
    fun getItemDecorationAt(index: Int): RecyclerView.ItemDecoration {
        return recyclerView.getItemDecorationAt(index)
    }

    /**
     * Removes the [RecyclerView.ItemDecoration] previously added to this [CarouselPager].
     */
    @Suppress("unused")
    fun removeItemDecoration(decoration: RecyclerView.ItemDecoration) {
        recyclerView.removeItemDecoration(decoration)
    }

    /**
     * Removes a [RecyclerView.ItemDecoration] at the specific index in this [CarouselPager].
     */
    @Suppress("unused")
    fun removeItemDecorationAt(index: Int) {
        recyclerView.removeItemDecorationAt(index)
    }

    /**
     * Invalidates all [RecyclerView.ItemDecoration]s in this [CarouselPager].
     */
    @Suppress("unused")
    fun invalidateItemDecorations() {
        recyclerView.invalidateItemDecorations()
    }

    internal fun isRtl(): Boolean {
        return layoutDirection == View.LAYOUT_DIRECTION_RTL
    }

    private fun calculatePageSize(): Int {
        return if (orientation == CarouselLayoutManager.HORIZONTAL) {
            recyclerView.width - recyclerView.paddingLeft - recyclerView.paddingRight
        } else {
            recyclerView.height - recyclerView.paddingTop - recyclerView.paddingBottom
        }
    }

    companion object {
        const val HORIZONTAL: Int = RecyclerView.HORIZONTAL
        const val VERTICAL: Int = RecyclerView.VERTICAL

        /**
         * Scroll state means that the [CarouselPager] is not currently scrolling.
         */
        const val SCROLL_STATE_IDLE: Int = RecyclerView.SCROLL_STATE_IDLE

        /**
         * Scroll state means that the [CarouselPager] is currently dragging by outside control
         * such as user interaction.
         */
        const val SCROLL_STATE_DRAGGING: Int = RecyclerView.SCROLL_STATE_DRAGGING

        /**
         * Scroll state means that the [CarouselPager] is currently animating to a final position
         * after outside control such as user interaction.
         */
        const val SCROLL_STATE_SETTLING: Int = RecyclerView.SCROLL_STATE_SETTLING

        const val DEFAULT_CIRCULAR = true
        const val DEFAULT_ORIENTATION = HORIZONTAL
        const val DEFAULT_OFFSCREEN_PAGE_LIMIT = -1
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(HORIZONTAL, VERTICAL)
    annotation class Orientation

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(SCROLL_STATE_IDLE, SCROLL_STATE_DRAGGING, SCROLL_STATE_SETTLING)
    annotation class ScrollState

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    @Retention(AnnotationRetention.SOURCE)
    @IntRange(from = 1)
    @IntDef(DEFAULT_OFFSCREEN_PAGE_LIMIT)
    annotation class OffscreenPageLimit

    private class SavedState : BaseSavedState {
        var recyclerViewId: Int
        var currentItem: Int
        var adapterState: Parcelable?

        constructor(parcel: Parcel) : super(parcel) {
            recyclerViewId = parcel.readInt()
            currentItem = parcel.readInt()
            adapterState = null
        }

        constructor(viewState: Parcelable) : super(viewState) {
            recyclerViewId = 0
            currentItem = RecyclerView.NO_POSITION
            adapterState = null
        }

        @RequiresApi(24)
        constructor(parcel: Parcel, classLoader: ClassLoader) : super(parcel, classLoader) {
            recyclerViewId = parcel.readInt()
            currentItem = parcel.readInt()
            adapterState = parcel.readParcelable(classLoader)
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(recyclerViewId)
            out.writeInt(currentItem)
            if (adapterState != null) {
                out.writeParcelable(adapterState, flags)
            }
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : ClassLoaderCreator<SavedState> {
            override fun createFromParcel(source: Parcel, loader: ClassLoader): SavedState {
                return if (Build.VERSION.SDK_INT >= 24) {
                    SavedState(source, loader)
                } else {
                    SavedState(source)
                }
            }

            override fun createFromParcel(source: Parcel): SavedState {
                return SavedState(source)
            }

            override fun newArray(size: Int): Array<SavedState?> {
                return arrayOfNulls(size)
            }
        }
    }

    private inner class CarouselPagerLayoutManager(
        orientation: Int,
        circular: Boolean
    ) : CarouselLayoutManager(orientation, circular) {
        override fun calculateExtraLayoutSpace(state: RecyclerView.State): Pair<Int, Int> {
            val limit = offscreenPageLimit
            if (limit == DEFAULT_OFFSCREEN_PAGE_LIMIT) {
                return super.calculateExtraLayoutSpace(state)
            }
            val offscreenSize = limit * calculatePageSize()
            return Pair(offscreenSize, offscreenSize)
        }
    }

    private inner class DataSetChangeNotifier : RecyclerView.AdapterDataObserver() {
        override fun onChanged() {
            scrollListenerAdapter.notifyDataSetChanged()
        }
    }
}
