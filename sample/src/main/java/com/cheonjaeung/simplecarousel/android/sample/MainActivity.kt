package com.cheonjaeung.simplecarousel.android.sample

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.cheonjaeung.simplecarousel.android.CarouselLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var mainLayout: ConstraintLayout
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
        mainLayout = findViewById(R.id.main)

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

                R.id.reverseLayout -> {
                    layoutManager.reverseLayout = !layoutManager.reverseLayout
                    true
                }

                R.id.layoutDirectionLtr -> {
                    mainLayout.layoutDirection = View.LAYOUT_DIRECTION_LTR
                    true
                }

                R.id.layoutDirectionRtl -> {
                    mainLayout.layoutDirection = View.LAYOUT_DIRECTION_RTL
                    true
                }

                else -> false
            }
        }
    }
}
