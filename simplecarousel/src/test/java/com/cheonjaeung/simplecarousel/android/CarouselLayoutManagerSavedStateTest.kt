package com.cheonjaeung.simplecarousel.android

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CarouselLayoutManagerSavedStateTest {

    @Test
    fun testSaveAndRestoreDefaultState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recyclerView = RecyclerView(context)
        val adapter = TestAdapter(context)
        recyclerView.adapter = adapter
        val layoutManager = CarouselLayoutManager()
        recyclerView.layoutManager = layoutManager

        val layoutSize = 1000
        recyclerView.triggerLayout(layoutSize)

        val firstVisibleItemPositionBeforeSave = layoutManager.findFirstVisibleItemPosition()
        assertEquals(0, firstVisibleItemPositionBeforeSave)

        val savedState = layoutManager.onSaveInstanceState()

        val newLayoutManager = CarouselLayoutManager()
        recyclerView.layoutManager = newLayoutManager
        newLayoutManager.onRestoreInstanceState(savedState)
        recyclerView.triggerLayout(layoutSize)

        val firstVisibleItemPositionAfterRestore = newLayoutManager.findFirstVisibleItemPosition()
        assertEquals(firstVisibleItemPositionBeforeSave, firstVisibleItemPositionAfterRestore)
    }

    @Test
    fun testSaveAndRestoreScrollPosition() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recyclerView = RecyclerView(context)
        val adapter = TestAdapter(context)
        recyclerView.adapter = adapter
        val layoutManager = CarouselLayoutManager()
        recyclerView.layoutManager = layoutManager

        val layoutSize = 1000
        recyclerView.triggerLayout(layoutSize)

        layoutManager.scrollToPosition(5)
        recyclerView.triggerLayout(layoutSize)

        val firstVisibleItemPositionBeforeSave = layoutManager.findFirstVisibleItemPosition()
        assertEquals(5, firstVisibleItemPositionBeforeSave)

        val savedState = layoutManager.onSaveInstanceState()

        val newLayoutManager = CarouselLayoutManager()
        recyclerView.layoutManager = newLayoutManager
        newLayoutManager.onRestoreInstanceState(savedState)
        recyclerView.triggerLayout(layoutSize)

        val firstVisibleItemPositionAfterRestore = newLayoutManager.findFirstVisibleItemPosition()
        assertEquals(firstVisibleItemPositionBeforeSave, firstVisibleItemPositionAfterRestore)
    }

    private fun RecyclerView.triggerLayout(size: Int) {
        measure(
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
        )
        layout(0, 0, size, size)
    }

    class TestAdapter(private val context: Context) : RecyclerView.Adapter<TestViewHolder>() {
        override fun getItemCount(): Int = 100

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TestViewHolder {
            val itemView = TextView(context)
            itemView.layoutParams = RecyclerView.LayoutParams(TestViewHolder.VIEW_SIZE, TestViewHolder.VIEW_SIZE)
            return TestViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: TestViewHolder, position: Int) {
            holder.bind("Item $position")
        }
    }

    class TestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(text: String) {
            (itemView as? TextView)?.let { textView ->
                textView.text = text
            }
        }

        companion object {
            const val VIEW_SIZE = 100
        }
    }
}
