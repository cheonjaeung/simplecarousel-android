package com.cheonjaeung.simplecarousel.android

import android.graphics.PointF
import android.util.DisplayMetrics
import android.view.View
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Implementation of the [SnapHelper][androidx.recyclerview.widget.SnapHelper] that supports
 * carousel snapping. It is specially designed for [CarouselLayoutManager]. The [RecyclerView] to
 * set to [attachToRecyclerView] should use [CarouselLayoutManager].
 */
@Suppress("FoldInitializerAndIfToElvis")
class CarouselSnapHelper : LinearSnapHelper() {
    private var recyclerView: RecyclerView? = null

    private var horizontalHelper: OrientationHelper? = null
    private var verticalHelper: OrientationHelper? = null

    override fun attachToRecyclerView(recyclerView: RecyclerView?) {
        super.attachToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onFling(velocityX: Int, velocityY: Int): Boolean {
        val recyclerView = this.recyclerView
        if (recyclerView == null) {
            return false
        }

        val layoutManager = recyclerView.layoutManager
        if (layoutManager == null) {
            return false
        }

        if (recyclerView.adapter == null) {
            return false
        }

        val minFlingVelocity = recyclerView.minFlingVelocity
        val isVelocityExceeded = abs(velocityX) > minFlingVelocity || abs(velocityY) > minFlingVelocity
        return isVelocityExceeded && snapFromFling(layoutManager, velocityX, velocityY)
    }

    private fun snapFromFling(
        layoutManager: RecyclerView.LayoutManager,
        velocityX: Int,
        velocityY: Int
    ): Boolean {
        val smoothScroller = createScroller(layoutManager)
        if (smoothScroller == null || smoothScroller !is CarouselSmoothScroller) {
            return false
        }

        val targetPosition = findTargetSnapPosition(layoutManager, velocityX, velocityY)

        if (layoutManager is CarouselLayoutManager) {
            smoothScroller.targetPosition = targetPosition
            layoutManager.startSmoothScroll(smoothScroller)
            return true
        } else {
            if (targetPosition == RecyclerView.NO_POSITION) {
                return false
            }

            smoothScroller.targetPosition = targetPosition
            layoutManager.startSmoothScroll(smoothScroller)
            return true
        }
    }

    override fun createScroller(layoutManager: RecyclerView.LayoutManager): RecyclerView.SmoothScroller? {
        val context = recyclerView?.context
        if (context == null) {
            return null
        }

        return object : CarouselSmoothScroller(context, layoutManager) {
            override fun onTargetFound(targetView: View, state: RecyclerView.State, action: Action) {
                val recyclerView = recyclerView
                if (recyclerView == null) {
                    return
                }

                @Suppress("NAME_SHADOWING")
                val layoutManager = recyclerView.layoutManager
                if (layoutManager == null) {
                    return
                }

                val snapDistances = calculateDistanceToFinalSnap(layoutManager, targetView)
                if (snapDistances == null) {
                    return
                }

                val dx = snapDistances[0]
                val dy = snapDistances[1]
                val time = calculateTimeForDeceleration(max(abs(dx), abs(dy)))
                if (time > 0) {
                    action.update(dx, dy, time, mDecelerateInterpolator)
                }
            }

            override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
                return MILLISECONDS_PER_INCH /displayMetrics.densityDpi
            }
        }
    }

    override fun findTargetSnapPosition(
        layoutManager: RecyclerView.LayoutManager?,
        velocityX: Int,
        velocityY: Int
    ): Int {
        if (layoutManager !is RecyclerView.SmoothScroller.ScrollVectorProvider) {
            return RecyclerView.NO_POSITION
        }

        val circular = if (layoutManager is CarouselLayoutManager) {
            layoutManager.circular
        } else {
            false
        }

        val itemCount = layoutManager.itemCount
        if (itemCount == 0) {
            return RecyclerView.NO_POSITION
        }

        val currentView = findSnapView(layoutManager)
        if (currentView == null) {
            return RecyclerView.NO_POSITION
        }

        val currentPosition = layoutManager.getPosition(currentView)
        if (currentPosition == RecyclerView.NO_POSITION) {
            return RecyclerView.NO_POSITION
        }

        val vector = if (layoutManager is CarouselLayoutManager) {
            layoutManager.computeScrollVectorForPosition(itemCount - 1, false)
        } else {
            layoutManager.computeScrollVectorForPosition(itemCount - 1)
        }
        if (vector == null) {
            return RecyclerView.NO_POSITION
        }

        val nextPositionSteps = calculateNextPositionSteps(layoutManager, vector, velocityX, velocityY)
        if (nextPositionSteps == 0) {
            return RecyclerView.NO_POSITION
        }

        if (circular) {
            return currentPosition + nextPositionSteps
        } else {
            var targetPosition = currentPosition + nextPositionSteps
            if (targetPosition < 0) {
                targetPosition = 0
            }
            if (targetPosition >= itemCount) {
                targetPosition = itemCount - 1
            }
            return targetPosition
        }
    }

    /**
     * Calculates how many steps is required to reach the target.
     */
    private fun calculateNextPositionSteps(
        layoutManager: RecyclerView.LayoutManager,
        vector: PointF,
        velocityX: Int,
        velocityY: Int
    ): Int {
        var nextPositionSteps = 0
        if (layoutManager.canScrollHorizontally()) {
            val direction = if (vector.x < 0) {
                DIRECTION_LEFT_TOP
            } else {
                DIRECTION_RIGHT_BOTTOM
            }
            nextPositionSteps = guessNextPositionDiff(
                layoutManager,
                getHorizontalHelper(layoutManager),
                velocityX,
                0
            ) * direction
        } else if (layoutManager.canScrollVertically()) {
            val direction = if (vector.y < 0) {
                DIRECTION_LEFT_TOP
            } else {
                DIRECTION_RIGHT_BOTTOM
            }
            nextPositionSteps = guessNextPositionDiff(
                layoutManager,
                getVerticalHelper(layoutManager),
                0,
                velocityY
            ) * direction
        }
        return nextPositionSteps
    }

    /**
     * Guesses how many items will be passed to reach the target. It may be incorrect because it only
     * uses current attached views for calculating distance.
     *
     * @return The difference between the current position and the target position.
     */
    private fun guessNextPositionDiff(
        layoutManager: RecyclerView.LayoutManager,
        helper: OrientationHelper,
        velocityX: Int,
        velocityY: Int
    ): Int {
        val distances = calculateScrollDistance(velocityX, velocityY)
        val distancePerChild = computeAverageDistancePerChild(layoutManager, helper).toFloat()
        if (distancePerChild <= 0) {
            return 0
        }
        val distance = if (layoutManager.canScrollHorizontally()) {
            distances[0]
        } else {
            distances[1]
        }
        return (distance / distancePerChild).roundToInt()
    }

    /**
     * Computes an average pixel distance to pass a single child. It returns negative value if the
     * calculation is failed.
     */
    private fun computeAverageDistancePerChild(
        layoutManager: RecyclerView.LayoutManager,
        helper: OrientationHelper
    ): Int {
        val childCount = layoutManager.childCount
        if (childCount == 0) {
            return INVALID_DISTANCE
        }

        var minStart = Int.MAX_VALUE
        var maxEnd = Int.MIN_VALUE
        for (i in 0 until childCount) {
            val child = layoutManager.getChildAt(i)
            if (child == null) {
                continue
            }
            val childPosition = layoutManager.getPosition(child)
            if (childPosition == RecyclerView.NO_POSITION) {
                continue
            }

            val childStart = helper.getDecoratedStart(child)
            if (childStart < minStart) {
                minStart = childStart
            }
            val childEnd = helper.getDecoratedEnd(child)
            if (childEnd > maxEnd) {
                maxEnd = childEnd
            }
        }
        if (minStart == Int.MAX_VALUE || maxEnd == Int.MIN_VALUE) {
            return INVALID_DISTANCE
        }

        val distance = maxEnd - minStart
        if (distance == 0) {
            return INVALID_DISTANCE
        }

        return distance / childCount
    }

    private fun getHorizontalHelper(layoutManager: RecyclerView.LayoutManager): OrientationHelper {
        if (horizontalHelper == null || horizontalHelper?.layoutManager != layoutManager) {
            horizontalHelper = OrientationHelper.createHorizontalHelper(layoutManager)
        }
        return requireNotNull(horizontalHelper)
    }

    private fun getVerticalHelper(layoutManager: RecyclerView.LayoutManager): OrientationHelper {
        if (verticalHelper == null || verticalHelper?.layoutManager != layoutManager) {
            verticalHelper = OrientationHelper.createVerticalHelper(layoutManager)
        }
        return requireNotNull(verticalHelper)
    }

    companion object {
        @Suppress("unused")
        private const val TAG: String = "CarouselSnapHelper"

        private const val DIRECTION_LEFT_TOP: Int = -1
        private const val DIRECTION_RIGHT_BOTTOM: Int = 1

        private const val INVALID_DISTANCE: Int = -1

        private const val MILLISECONDS_PER_INCH: Float = 100f
    }
}
