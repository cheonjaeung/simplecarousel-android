package com.cheonjaeung.simplecarousel.android.pager

import android.view.View
import androidx.annotation.Px
import androidx.viewpager2.widget.ViewPager2

/**
 * A reimplementation of the [androidx.viewpager2.widget.MarginPageTransformer] but for [CarouselPager].
 *
 * Note: [androidx.viewpager2.widget.MarginPageTransformer] requires [ViewPager2] internally. This is why
 * [CarouselPager] can't use [MarginPageTransformer][androidx.viewpager2.widget.MarginPageTransformer].
 * It's recommended to use this class instead of [androidx.viewpager2.widget.MarginPageTransformer].
 */
class CarouselMarginPageTransformer(@Px private val marginPx: Int) : ViewPager2.PageTransformer {
    init {
        if (marginPx < 0) {
            throw IllegalArgumentException("marginPx must be non negative")
        }
    }

    override fun transformPage(page: View, position: Float) {
        val carouselPager = requireCarouselPager(page)

        val offset = marginPx * position
        if (carouselPager.orientation == CarouselPager.HORIZONTAL) {
            page.translationX = if (carouselPager.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                -offset
            } else {
                offset
            }
        } else {
            page.translationY = offset
        }
    }

    private fun requireCarouselPager(page: View): CarouselPager {
        val parent = page.parent
        val grandParent = parent.parent
        if (grandParent is CarouselPager) {
            return grandParent
        }
        throw IllegalStateException(
            "Except CarouselPager but was ${grandParent::class.java.canonicalName}"
        )
    }
}
