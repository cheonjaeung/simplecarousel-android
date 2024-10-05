package com.cheonjaeung.simplecarousel.android

import android.util.Log
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * Implementation of the [SnapHelper][androidx.recyclerview.widget.SnapHelper] that supports snapping
 * in either horizontal or vertical orientation.
 *
 * This class is specially customized for [RecyclerView][androidx.recyclerview.widget.RecyclerView]
 * with [CarouselLayoutManager]. The [RecyclerView] to set [attachToRecyclerView] should use
 * [CarouselLayoutManager].
 */
class CarouselSnapHelper : LinearSnapHelper() {
    override fun findTargetSnapPosition(
        layoutManager: RecyclerView.LayoutManager?,
        velocityX: Int,
        velocityY: Int
    ): Int {
        if (layoutManager !is CarouselLayoutManager) {
            Log.w(
                TAG,
                "LayoutManager should be ${CarouselLayoutManager::class.java.canonicalName} to use $TAG"
            )
            return RecyclerView.NO_POSITION
        }

        return if (layoutManager.circular) {
            // TODO It works but not good solution.
            RecyclerView.NO_POSITION
        } else {
            super.findTargetSnapPosition(layoutManager, velocityX, velocityY)
        }
    }

    companion object {
        private const val TAG: String = "CarouselSnapHelper"
    }
}
