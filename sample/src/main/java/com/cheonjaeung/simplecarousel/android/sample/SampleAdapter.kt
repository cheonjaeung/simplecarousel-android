package com.cheonjaeung.simplecarousel.android.sample

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class SampleAdapter : RecyclerView.Adapter<SampleAdapter.BaseViewHolder>() {
    override fun getItemCount(): Int = 16

    override fun getItemViewType(position: Int): Int {
        return position
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view: View
        return when (viewType) {
            TYPE_RECYCLER_VIEW_CIRCULAR_TITLE,
            TYPE_RECYCLER_VIEW_NON_CIRCULAR_TITLE,
            TYPE_RECYCLER_VIEW_RTL_CIRCULAR_TITLE,
            TYPE_RECYCLER_VIEW_RTL_NON_CIRCULAR_TITLE,
            TYPE_PAGER_CIRCULAR_TITLE,
            TYPE_PAGER_NON_CIRCULAR_TITLE,
            TYPE_PAGER_RTL_CIRCULAR_TITLE,
            TYPE_PAGER_RTL_NON_CIRCULAR_TITLE -> {
                view = inflater.inflate(R.layout.view_holder_title, parent, false)
                val title = when (viewType) {
                    TYPE_RECYCLER_VIEW_CIRCULAR_TITLE -> "RecyclerView: circular=true/rtl=false"
                    TYPE_RECYCLER_VIEW_NON_CIRCULAR_TITLE -> "RecyclerView: circular=false/rtl=false"
                    TYPE_RECYCLER_VIEW_RTL_CIRCULAR_TITLE -> "RecyclerView: circular=true/rtl=true"
                    TYPE_RECYCLER_VIEW_RTL_NON_CIRCULAR_TITLE -> "RecyclerView: circular=false/rtl=true"
                    TYPE_PAGER_CIRCULAR_TITLE -> "CarouselPager: circular=true/rtl=false"
                    TYPE_PAGER_NON_CIRCULAR_TITLE -> "CarouselPager: circular=false/rtl=false"
                    TYPE_PAGER_RTL_CIRCULAR_TITLE -> "CarouselPager: circular=true/rtl=true"
                    TYPE_PAGER_RTL_NON_CIRCULAR_TITLE -> "CarouselPager: circular=false/rtl=true"
                    else -> throw IllegalStateException("Impossible")
                }
                TitleViewHolder(view, title)
            }

            TYPE_RECYCLER_VIEW_CIRCULAR,
            TYPE_RECYCLER_VIEW_NON_CIRCULAR,
            TYPE_RECYCLER_VIEW_RTL_CIRCULAR,
            TYPE_RECYCLER_VIEW_RTL_NON_CIRCULAR -> {
                view = inflater.inflate(R.layout.view_holder_recycler_view_sample, parent, false)
                val circular = viewType == TYPE_RECYCLER_VIEW_CIRCULAR || viewType == TYPE_RECYCLER_VIEW_RTL_CIRCULAR
                val rtl = viewType == TYPE_RECYCLER_VIEW_RTL_CIRCULAR || viewType == TYPE_RECYCLER_VIEW_RTL_NON_CIRCULAR
                RecyclerViewSampleViewHolder(view, circular, rtl)
            }

            TYPE_PAGER_CIRCULAR,
            TYPE_PAGER_NON_CIRCULAR,
            TYPE_PAGER_RTL_CIRCULAR,
            TYPE_PAGER_RTL_NON_CIRCULAR -> {
                view = inflater.inflate(R.layout.view_holder_pager_sample, parent, false)
                val circular = viewType == TYPE_PAGER_CIRCULAR || viewType == TYPE_PAGER_RTL_CIRCULAR
                val rtl = viewType == TYPE_PAGER_RTL_CIRCULAR || viewType == TYPE_PAGER_RTL_NON_CIRCULAR
                PagerSampleViewHolder(view, circular, rtl)
            }

            else -> throw IllegalArgumentException("viewType is not defined: $viewType")
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        holder.bind()
    }

    companion object {
        private const val TYPE_RECYCLER_VIEW_CIRCULAR_TITLE = 0
        private const val TYPE_RECYCLER_VIEW_CIRCULAR = 1

        private const val TYPE_RECYCLER_VIEW_NON_CIRCULAR_TITLE = 2
        private const val TYPE_RECYCLER_VIEW_NON_CIRCULAR = 3

        private const val TYPE_RECYCLER_VIEW_RTL_CIRCULAR_TITLE = 4
        private const val TYPE_RECYCLER_VIEW_RTL_CIRCULAR = 5

        private const val TYPE_RECYCLER_VIEW_RTL_NON_CIRCULAR_TITLE = 6
        private const val TYPE_RECYCLER_VIEW_RTL_NON_CIRCULAR = 7

        private const val TYPE_PAGER_CIRCULAR_TITLE = 8
        private const val TYPE_PAGER_CIRCULAR = 9

        private const val TYPE_PAGER_NON_CIRCULAR_TITLE = 10
        private const val TYPE_PAGER_NON_CIRCULAR = 11

        private const val TYPE_PAGER_RTL_CIRCULAR_TITLE = 12
        private const val TYPE_PAGER_RTL_CIRCULAR = 13

        private const val TYPE_PAGER_RTL_NON_CIRCULAR_TITLE = 14
        private const val TYPE_PAGER_RTL_NON_CIRCULAR = 15
    }

    abstract class BaseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        abstract fun bind()
    }
}
