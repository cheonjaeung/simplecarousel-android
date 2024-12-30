package com.cheonjaeung.simplecarousel.android.pager

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView

/**
 * A reimplementation of [androidx.viewpager2.adapter.FragmentViewHolder] for [CarouselFragmentStateAdapter].
 */
class FragmentViewHolder(container: FrameLayout) : RecyclerView.ViewHolder(container) {
    val container: FrameLayout
        get() = itemView as FrameLayout

    companion object {
        fun create(parent: ViewGroup): FragmentViewHolder {
            val container = FrameLayout(parent.context)
            container.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            container.id = View.generateViewId()
            container.isSaveEnabled = false
            return FragmentViewHolder(container)
        }
    }
}
