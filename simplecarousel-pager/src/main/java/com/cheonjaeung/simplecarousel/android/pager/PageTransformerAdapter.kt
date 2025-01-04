package com.cheonjaeung.simplecarousel.android.pager

import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

internal class PageTransformerAdapter(
    private val carouselPager: CarouselPager
) : ViewPager2.OnPageChangeCallback() {
    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        if (carouselPager.pageTransformer == null) {
            return
        }

        val layoutManager = carouselPager.recyclerView.layoutManager ?: return

        val transformOffset = -positionOffset
        for (i in 0 until layoutManager.childCount) {
            val view = layoutManager.getChildAt(i) ?: throw IllegalStateException(
                "LayoutManager returns null child view at the $i while transforming pages"
            )
            val currentPosition = layoutManager.getPosition(view)
            val diff = calculatePositionDiff(position, currentPosition, layoutManager.itemCount)
            val viewOffset = transformOffset + diff
            carouselPager.pageTransformer?.transformPage(view, viewOffset)
        }
    }

    private fun calculatePositionDiff(
        reference: Int,
        position: Int,
        maxItemCount: Int
    ): Int {
        return if (carouselPager.circular) {
            val steps = (position - reference + maxItemCount) % maxItemCount
            val stepsOpposite = (reference - position + maxItemCount) % maxItemCount * -1
            return if (abs(steps) < abs(stepsOpposite)) {
                steps
            } else {
                stepsOpposite
            }
        } else {
            position - reference
        }
    }
}
