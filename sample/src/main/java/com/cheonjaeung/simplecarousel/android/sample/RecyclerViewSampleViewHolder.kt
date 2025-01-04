package com.cheonjaeung.simplecarousel.android.sample

import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.RecyclerView
import com.cheonjaeung.simplecarousel.android.CarouselLayoutManager
import kotlin.math.roundToInt
import kotlin.random.Random

class RecyclerViewSampleViewHolder(
    view: View,
    private val enableCircular: Boolean,
    private val enableRtl: Boolean
) : SampleAdapter.BaseViewHolder(view) {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: Adapter

    override fun bind() {
        val containerView = itemView.findViewById<FrameLayout>(R.id.container)
        containerView.layoutDirection = if (enableRtl) {
            View.LAYOUT_DIRECTION_RTL
        } else {
            View.LAYOUT_DIRECTION_LTR
        }
        recyclerView = itemView.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = CarouselLayoutManager(RecyclerView.HORIZONTAL, enableCircular)
        adapter = Adapter()
        recyclerView.adapter = adapter

        if (recyclerView.itemDecorationCount == 0) {
            recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: Rect,
                    view: View,
                    parent: RecyclerView,
                    state: RecyclerView.State
                ) {
                    val position = parent.getChildAdapterPosition(view)
                    if (position != RecyclerView.NO_POSITION) {
                        val spacing = (16 * itemView.context.resources.displayMetrics.density).roundToInt()
                        if (enableCircular || position != adapter.itemCount - 1) {
                            if (enableRtl) {
                                outRect.left = spacing
                            } else {
                                outRect.right = spacing
                            }
                        }
                    }
                }
            })
        }
    }

    private class Adapter : RecyclerView.Adapter<ItemViewHolder>() {
        override fun getItemCount(): Int = 10

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val view = inflater.inflate(R.layout.view_holder_recycler_view_sample_item, parent, false)
            return ItemViewHolder(view)
        }

        override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
            holder.bind(position.toString())
        }
    }

    private class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(text: String) {
            val textView = itemView.findViewById<TextView>(R.id.text)
            textView.text = text

            val background = GradientDrawable()
            background.setColor(randomColor())
            background.shape = GradientDrawable.RECTANGLE
            background.cornerRadius = 8 * itemView.context.resources.displayMetrics.density

            val container = itemView.findViewById<FrameLayout>(R.id.container)
            container.background = background
        }

        @ColorInt
        private fun randomColor(): Int {
            val r = Random.nextInt(128, 256)
            val g = Random.nextInt(128, 256)
            val b = Random.nextInt(128, 256)
            return Color.rgb(r, g, b)
        }
    }
}
