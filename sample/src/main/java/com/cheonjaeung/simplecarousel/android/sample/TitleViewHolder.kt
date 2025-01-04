package com.cheonjaeung.simplecarousel.android.sample

import android.view.View
import android.widget.TextView

class TitleViewHolder(view: View, private val title: String) : SampleAdapter.BaseViewHolder(view) {
    override fun bind() {
        val textView = itemView.findViewById<TextView>(R.id.title)
        textView.text = title
    }
}
