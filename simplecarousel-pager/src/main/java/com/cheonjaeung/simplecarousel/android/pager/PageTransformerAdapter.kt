package com.cheonjaeung.simplecarousel.android.pager

internal class PageTransformerAdapter(
    private val carouselPager: CarouselPager
) : CarouselPager.OnPageChangeCallback() {
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
            val viewOffset = transformOffset + currentPosition - position
            carouselPager.pageTransformer?.transformPage(view, viewOffset)
        }
    }
}
