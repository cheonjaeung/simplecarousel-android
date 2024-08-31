package com.cheonjaeung.simplecarousel.android.sample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.cheonjaeung.simplecarousel.android.CarouselLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var toolBar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SampleCarouselAdapter
    private lateinit var layoutManager: CarouselLayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        initMenu()
    }

    private fun initViews() {
        toolBar = findViewById(R.id.toolBar)

        recyclerView = findViewById(R.id.recyclerView)

        adapter = SampleCarouselAdapter()
        recyclerView.adapter = adapter

        layoutManager = CarouselLayoutManager(RecyclerView.VERTICAL)
        recyclerView.layoutManager = layoutManager

        val itemSpacing = (8 * resources.displayMetrics.density).roundToInt()
        recyclerView.addItemDecoration(SampleCarouselAdapter.SampleCarouselItemDecoration(itemSpacing))
    }

    private fun initMenu() {
        toolBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.orientationHorizontal -> {
                    layoutManager.orientation = CarouselLayoutManager.HORIZONTAL
                    true
                }

                R.id.orientataionVertical -> {
                    layoutManager.orientation = CarouselLayoutManager.VERTICAL
                    true
                }

                else -> false
            }
        }
    }
}
