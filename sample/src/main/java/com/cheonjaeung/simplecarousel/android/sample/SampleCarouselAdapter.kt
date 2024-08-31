package com.cheonjaeung.simplecarousel.android.sample

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SampleCarouselAdapter : RecyclerView.Adapter<SampleCarouselAdapter.SampleCarouselViewHolder>() {

    private val items: List<Int> = (0..199).toList()

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SampleCarouselViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.view_holder_item, parent, false)
        return SampleCarouselViewHolder(view)
    }

    override fun onBindViewHolder(holder: SampleCarouselViewHolder, position: Int) {
        if (position != RecyclerView.NO_POSITION) {
            holder.bind(items[position])
        }
    }

    class SampleCarouselViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(text: Int) {
            val textView = itemView.findViewById<TextView>(R.id.text)
            textView.text = text.toString()
        }
    }

    class SampleCarouselItemDecoration(private val spacing: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            super.getItemOffsets(outRect, view, parent, state)
            outRect.set(spacing, spacing, spacing, spacing)
        }
    }
}
