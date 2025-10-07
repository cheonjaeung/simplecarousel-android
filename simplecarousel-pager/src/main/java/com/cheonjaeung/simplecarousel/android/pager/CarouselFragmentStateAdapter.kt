package com.cheonjaeung.simplecarousel.android.pager

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.CallSuper
import androidx.annotation.OptIn
import androidx.collection.LongSparseArray
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.adapter.FragmentStateAdapter.FragmentTransactionCallback
import androidx.viewpager2.adapter.StatefulAdapter
import androidx.viewpager2.widget.ViewPager2
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A special [RecyclerView.Adapter] to support [Fragment] as item of [CarouselPager]. This adapter must be
 * set to [CarouselPager] instead of [FragmentStateAdapter]. Because [FragmentStateAdapter] only supports
 * [ViewPager2], not [CarouselPager].
 *
 * This class is basically a reimplementation of the [FragmentStateAdapter]. Most of features are same to
 * [FragmentStateAdapter] but it has some fixes for supporting carousel behavior.
 */
abstract class CarouselFragmentStateAdapter : RecyclerView.Adapter<FragmentViewHolder>, StatefulAdapter {
    private val fragmentManager: FragmentManager

    private val lifecycle: Lifecycle

    private val fragments = LongSparseArray<Fragment>()
    private val savedStates = LongSparseArray<Fragment.SavedState>()
    private val viewHolderIdsByItemId = LongSparseArray<Int>()

    private var fragmentMaxLifecycleEnforcer: FragmentMaxLifecycleEnforcer? = null

    private val fragmentEventDispatcher = FragmentEventDispatcher()

    private var isInGracePeriod = false
    private var hasStaleFragments = false

    /**
     * Creates a [CarouselFragmentStateAdapter] and ties its fragments' lifecycle to the [FragmentActivity].
     */
    constructor(fragmentActivity: FragmentActivity) : this(
        fragmentActivity.supportFragmentManager,
        fragmentActivity.lifecycle
    )

    /**
     * Creates a [CarouselFragmentStateAdapter] and ties its fragments' lifecycle to the [Fragment].
     */
    constructor(fragment: Fragment) : this(
        fragment.childFragmentManager,
        fragment.lifecycle
    )

    /**
     * Creates a [CarouselFragmentStateAdapter] and ties its fragments' lifecycle to the specific host.
     */
    constructor(fragmentManager: FragmentManager, lifecycle: Lifecycle) : super() {
        this.fragmentManager = fragmentManager
        this.lifecycle = lifecycle
        super.setHasStableIds(true)
    }

    override fun saveState(): Parcelable {
        val savedState = Bundle(fragments.size() + savedStates.size())

        for (i in 0 until fragments.size()) {
            val itemId = fragments.keyAt(i)
            val fragment = fragments.get(itemId)
            if (fragment != null && fragment.isAdded) {
                val key = createKey(KEY_PREFIX_FRAGMENT, itemId)
                fragmentManager.putFragment(savedState, key, fragment)
            }
        }

        for (i in 0 until savedStates.size()) {
            val itemId = savedStates.keyAt(i)
            if (containsItem(itemId)) {
                val key = createKey(KEY_PREFIX_STATE, itemId)
                savedState.putParcelable(key, savedStates.get(itemId))
            }
        }

        return savedState
    }

    override fun restoreState(savedState: Parcelable) {
        if (savedStates.isEmpty() || fragments.isEmpty()) {
            throw IllegalStateException("The adapter is not fresh while restoring state")
        }

        if (savedState !is Bundle) {
            throw IllegalArgumentException("CarouselFragmentStateAdapter can only restore Bundle type")
        }

        if (savedState.classLoader == null) {
            savedState.classLoader = this::class.java.classLoader
        }

        for (key in savedState.keySet()) {
            if (isValidKey(key, KEY_PREFIX_FRAGMENT)) {
                val itemId = parseIdFromKey(key, KEY_PREFIX_FRAGMENT)
                val fragment = fragmentManager.getFragment(savedState, key)
                if (fragment != null) {
                    fragments.put(itemId, fragment)
                    continue
                }
            }

            if (isValidKey(key, KEY_PREFIX_STATE)) {
                val itemId = parseIdFromKey(key, KEY_PREFIX_STATE)
                val state = if (Build.VERSION.SDK_INT >= 33) {
                    savedState.getParcelable(key, Fragment.SavedState::class.java)
                } else {
                    savedState.getParcelable(key)
                }
                if (state != null) {
                    if (containsItem(itemId)) {
                        savedStates.put(itemId, state)
                    }
                    continue
                }
            }

            throw IllegalArgumentException("Unexpected key found while restoring state: $key")
        }

        if (!fragments.isEmpty()) {
            isInGracePeriod = true
            hasStaleFragments = true
            gcFragments()
            scheduleGracePeriodEnd()
        }
    }

    private fun scheduleGracePeriodEnd() {
        val handler = Handler(Looper.getMainLooper())
        val runnable = Runnable {
            isInGracePeriod = false
            gcFragments()
        }

        lifecycle.addObserver(
            object : LifecycleEventObserver {
                override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                    if (event == Lifecycle.Event.ON_DESTROY) {
                        handler.removeCallbacks(runnable)
                        source.lifecycle.removeObserver(this)
                    }
                }
            }
        )

        handler.postDelayed(runnable, 10000)
    }

    /**
     * Creates a key to save state.
     */
    private fun createKey(prefix: String, id: Long): String {
        return prefix + id.toString()
    }

    /**
     * Checks the given key is valid key to restore state.
     */
    private fun isValidKey(key: String, prefix: String): Boolean {
        return key.startsWith(prefix) && key.length > prefix.length
    }

    /**
     * Parses a long type ID from the given key to restore state.
     */
    private fun parseIdFromKey(key: String, prefix: String): Long {
        return key.substring(prefix.length).toLong()
    }

    @CallSuper
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        fragmentMaxLifecycleEnforcer = FragmentMaxLifecycleEnforcer()
        fragmentMaxLifecycleEnforcer?.register(recyclerView)
    }

    @CallSuper
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        fragmentMaxLifecycleEnforcer?.unregister(recyclerView)
        fragmentMaxLifecycleEnforcer = null
    }

    /**
     * Provides a new [Fragment] at the specified position.
     */
    abstract fun createFragment(position: Int): Fragment

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FragmentViewHolder {
        return FragmentViewHolder.create(parent)
    }

    override fun onBindViewHolder(holder: FragmentViewHolder, position: Int) {
        val itemId = holder.itemId
        val viewHolderId = holder.container.id
        val boundItemId = findBoundItemIdByViewHolderId(viewHolderId)
        if (boundItemId != null && boundItemId != itemId) {
            removeFragment(boundItemId)
            viewHolderIdsByItemId.remove(boundItemId)
        }

        viewHolderIdsByItemId.put(itemId, viewHolderId)
        ensureFragment(position)

        val container = holder.container
        if (container.isAttachedToWindow) {
            placeFragmentInViewHolder(holder)
        }

        gcFragments()
    }

    override fun onViewAttachedToWindow(holder: FragmentViewHolder) {
        placeFragmentInViewHolder(holder)
        gcFragments()
    }

    /**
     * Places a [Fragment] into a [RecyclerView.ViewHolder].
     *
     * In this method, it uses 3 components: 'fragment', 'container view' and 'fragment's view'.
     * Components can have these states:
     * - The fragment can be added or not.
     * - The fragment view can be created or not.
     * - The container view can be attached or not.
     *
     * This method handle fragment by the combination of states.
     */
    private fun placeFragmentInViewHolder(holder: FragmentViewHolder) {
        val fragment = fragments.get(holder.itemId) ?: throw IllegalStateException(
            "Fragment is not found"
        )
        val container = holder.container
        val fragmentView = fragment.view

        // Fragment is not added, fragment view is created.
        if (!fragment.isAdded && fragmentView != null) {
            throw IllegalStateException("Fragment is not added but it's view is created")
        }

        // Fragment is added, fragment view is not created.
        if (fragment.isAdded && fragmentView == null) {
            lazyAddToContainer(fragment, container)
            return
        }

        // Fragment is added, fragment view is created, container view is attached.
        if (fragment.isAdded && fragmentView?.parent != null) {
            if (fragmentView.parent != container) {
                addToContainer(fragmentView, container)
            }
            return
        }

        // Fragment is added, fragment view is created, container view is not attached.
        if (fragment.isAdded && fragmentView != null) {
            addToContainer(fragmentView, container)
            return
        }

        // Fragment is not added, fragment view is not created, container view is not attached.
        if (!shouldDelayFragmentTransactions()) {
            lazyAddToContainer(fragment, container)
            val listeners = fragmentEventDispatcher.dispatchPreAdded(fragment)

            try {
                fragment.setMenuVisibility(false)
                fragmentManager.beginTransaction()
                    .add(fragment, "f${holder.itemId}")
                    .setMaxLifecycle(fragment, Lifecycle.State.STARTED)
                    .commitNow()
                fragmentMaxLifecycleEnforcer?.updateFragmentMaxLifecycle(false)
            } finally {
                fragmentEventDispatcher.dispatchPostEvents(listeners)
            }
        } else {
            // There is nothing to do when fragment manager is destroyed.
            if (fragmentManager.isDestroyed) {
                return
            }

            lifecycle.addObserver(
                object : LifecycleEventObserver {
                    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                        source.lifecycle.removeObserver(this)
                        if (holder.container.isAttachedToWindow) {
                            placeFragmentInViewHolder(holder)
                        }
                    }
                }
            )
        }
    }

    /**
     * Adds a fragment's view to the container view.
     *
     * If the [container] has multiple children, it throws exception.
     * If the [container] has a child, it replaces to the [fragmentView].
     * If the [fragmentView] has parent, it will removed from original parent and added to the [container].
     */
    private fun addToContainer(fragmentView: View, container: FrameLayout) {
        if (container.childCount > 1) {
            throw IllegalStateException("Container view already has child view")
        }

        if (fragmentView.parent == container) {
            return
        }

        if (container.childCount > 0) {
            container.removeAllViews()
        }

        if (fragmentView.parent != null) {
            val fragmentParent = fragmentView.parent as ViewGroup
            fragmentParent.removeView(fragmentView)
        }

        container.addView(fragmentView)
    }

    /**
     * Adds fragment view to [container] after the [Fragment.onViewCreated] called.
     */
    private fun lazyAddToContainer(fragment: Fragment, container: FrameLayout) {
        fragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    f: Fragment,
                    v: View,
                    savedInstanceState: Bundle?
                ) {
                    if (f == fragment) {
                        fm.unregisterFragmentLifecycleCallbacks(this)
                        addToContainer(v, container)
                    }
                }
            },
            false
        )
    }

    override fun onViewRecycled(holder: FragmentViewHolder) {
        val viewHolderId = holder.container.id
        val boundItemId = findBoundItemIdByViewHolderId(viewHolderId)
        if (boundItemId != null) {
            removeFragment(boundItemId)
            viewHolderIdsByItemId.remove(boundItemId)
        }
    }

    final override fun onFailedToRecycleView(holder: FragmentViewHolder): Boolean {
        return true
    }

    private fun gcFragments() {
        if (!hasStaleFragments || shouldDelayFragmentTransactions()) {
            return
        }

        val targets = hashSetOf<Long>()
        for (i in 0 until fragments.size()) {
            val itemId = fragments.keyAt(i)
            if (!containsItem(itemId)) {
                targets.add(itemId)
                viewHolderIdsByItemId.remove(itemId)
            }
        }

        if (!isInGracePeriod) {
            hasStaleFragments = false

            for (i in 0 until fragments.size()) {
                val itemId = fragments.keyAt(i)
                if (!isFragmentViewBound(itemId)) {
                    targets.add(itemId)
                }
            }
        }

        for (itemId in targets) {
            removeFragment(itemId)
        }
    }

    private fun removeFragment(targetId: Long) {
        val fragment = fragments.get(targetId) ?: return

        val fragmentView = fragment.view
        if (fragmentView != null) {
            val parent = fragmentView.parent
            if (parent != null && parent is ViewGroup) {
                parent.removeAllViews()
            }
        }

        if (!containsItem(targetId)) {
            savedStates.remove(targetId)
        }

        if (!fragment.isAdded) {
            fragments.remove(targetId)
            return
        }

        if (shouldDelayFragmentTransactions()) {
            hasStaleFragments = false
            return
        }

        if (fragment.isAdded && containsItem(targetId)) {
            val listeners = fragmentEventDispatcher.dispatchPreSavedInstanceState(fragment)
            val savedState = fragmentManager.saveFragmentInstanceState(fragment)
            fragmentEventDispatcher.dispatchPostEvents(listeners)
            if (savedState != null) {
                savedStates.put(targetId, savedState)
            }
        }

        val listeners = fragmentEventDispatcher.dispatchPreRemove(fragment)
        try {
            fragmentManager.beginTransaction()
                .remove(fragment)
                .commitNow()
            fragments.remove(targetId)
        } finally {
            fragmentEventDispatcher.dispatchPostEvents(listeners)
        }
    }

    /**
     * Finds a item id by the given view holder id. If a view holder is bound, it's item id must saved in
     * the [viewHolderIdsByItemId]. If it is not bound, it returns `null`.
     */
    private fun findBoundItemIdByViewHolderId(viewHolderId: Int): Long? {
        var key: Long? = null
        for (i in 0 until viewHolderIdsByItemId.size()) {
            if (viewHolderIdsByItemId.valueAt(i) == viewHolderId) {
                if (key != null) {
                    throw IllegalStateException("A ViewHolder can be bound to one item")
                }
                key = viewHolderIdsByItemId.keyAt(i)
            }
        }
        return key
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    private fun containsItem(itemId: Long): Boolean {
        return itemId in 0..<itemCount
    }

    private fun ensureFragment(position: Int) {
        val key = getItemId(position)
        if (!fragments.containsKey(key)) {
            val newFragment = createFragment(position)
            newFragment.setInitialSavedState(savedStates[key])
            fragments.put(key, newFragment)
        }
    }

    private fun isFragmentViewBound(itemId: Long): Boolean {
        if (viewHolderIdsByItemId.containsKey(itemId)) {
            return true
        }
        val fragment = fragments.get(itemId) ?: return false
        val fragmentView = fragment.view ?: return false
        return fragmentView.parent != null
    }

    override fun setHasStableIds(hasStableIds: Boolean) {
        throw UnsupportedOperationException(
            "CarouselFragmentStateAdapter doesn't support to change hasStateIds option"
        )
    }

    private fun shouldDelayFragmentTransactions(): Boolean {
        return fragmentManager.isStateSaved
    }

    companion object {
        private const val KEY_PREFIX_FRAGMENT = "f#"
        private const val KEY_PREFIX_STATE = "s#"
    }

    private inner class FragmentMaxLifecycleEnforcer {
        private var carouselPager: CarouselPager? = null
        private var lifecycleObserver: LifecycleEventObserver? = null
        private var onPageChangeCallback: ViewPager2.OnPageChangeCallback? = null
        private var adapterDataObserver: RecyclerView.AdapterDataObserver? = null
        private var primaryItemId = RecyclerView.NO_ID

        fun register(recyclerView: RecyclerView) {
            carouselPager = getCarouselPager(recyclerView)

            onPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
                override fun onPageScrollStateChanged(state: Int) {
                    updateFragmentMaxLifecycle(false)
                }

                override fun onPageSelected(position: Int) {
                    updateFragmentMaxLifecycle(false)
                }
            }.also {
                carouselPager?.registerOnPageChangeCallback(it)
            }

            adapterDataObserver = object : DataSetObserver() {
                override fun onChanged() {
                    updateFragmentMaxLifecycle(true)
                }
            }.also {
                registerAdapterDataObserver(it)
            }

            lifecycleObserver = LifecycleEventObserver { _, _ ->
                updateFragmentMaxLifecycle(false)
            }.also {
                lifecycle.addObserver(it)
            }
        }

        fun unregister(recyclerView: RecyclerView) {
            val carouselPager = getCarouselPager(recyclerView)
            onPageChangeCallback?.let { carouselPager.unregisterOnPageChangeCallback(it) }
            adapterDataObserver?.let { unregisterAdapterDataObserver(it) }
            lifecycleObserver?.let { lifecycle.removeObserver(it) }
            this.carouselPager = null
        }

        private fun getCarouselPager(recyclerView: RecyclerView): CarouselPager {
            val pager = recyclerView.parent
            if (pager !is CarouselPager) {
                throw IllegalStateException("CarouselFragmentStateAdapter must used with " +
                    CarouselPager::class.java.canonicalName)
            }
            return pager
        }

        fun updateFragmentMaxLifecycle(isDataSetChanged: Boolean) {
            if (shouldDelayFragmentTransactions()) {
                return
            }

            val carouselPager = this.carouselPager ?: return

            if (carouselPager.scrollState != CarouselPager.SCROLL_STATE_IDLE) {
                return
            }

            if (fragments.isEmpty() || itemCount == 0) {
                return
            }

            val currentItem = carouselPager.currentItem
            if (currentItem >= itemCount) {
                return
            }

            val currentItemId = getItemId(currentItem)
            if (currentItemId == primaryItemId && !isDataSetChanged) {
                return
            }

            val currentItemFragment = fragments.get(currentItemId)
            if (currentItemFragment == null || !currentItemFragment.isAdded) {
                return
            }

            primaryItemId = currentItemId
            val transaction = fragmentManager.beginTransaction()

            var resumeTarget: Fragment? = null
            val listenersList = mutableListOf<List<FragmentTransactionCallback.OnPostEventListener>>()
            for (i in 0 until fragments.size()) {
                val itemId = fragments.keyAt(i)
                val fragment = fragments.valueAt(i)

                if (fragment.isAdded) {
                    continue
                }

                if (itemId != primaryItemId) {
                    transaction.setMaxLifecycle(fragment, Lifecycle.State.STARTED)
                    listenersList.add(
                        fragmentEventDispatcher.dispatchMaxLifecyclePreUpdated(
                            fragment,
                            Lifecycle.State.STARTED
                        )
                    )
                } else {
                    resumeTarget = fragment
                }

                fragment.setMenuVisibility(itemId == primaryItemId)
            }

            if (resumeTarget != null) {
                transaction.setMaxLifecycle(resumeTarget, Lifecycle.State.RESUMED)
                listenersList.add(
                    fragmentEventDispatcher.dispatchMaxLifecyclePreUpdated(
                        resumeTarget,
                        Lifecycle.State.RESUMED
                    )
                )
            }

            if (transaction.isEmpty) {
                transaction.commitNow()
                listenersList.reverse()
                for (listeners in listenersList) {
                    fragmentEventDispatcher.dispatchPostEvents(listeners)
                }
            }
        }

        private open inner class DataSetObserver : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {}

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                onChanged()
            }

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                onChanged()
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                onChanged()
            }

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                onChanged()
            }

            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                onChanged()
            }
        }
    }

    private class FragmentEventDispatcher {
        private val callbacks: CopyOnWriteArrayList<FragmentTransactionCallback> = CopyOnWriteArrayList()

        fun dispatchPostEvents(listeners: List<FragmentTransactionCallback.OnPostEventListener>) {
            for (listener in listeners) {
                listener.onPost()
            }
        }

        fun dispatchMaxLifecyclePreUpdated(
            fragment: Fragment,
            maxState: Lifecycle.State
        ): List<FragmentTransactionCallback.OnPostEventListener> {
            val result = mutableListOf<FragmentTransactionCallback.OnPostEventListener>()
            for (callback in callbacks) {
                result.add(callback.onFragmentMaxLifecyclePreUpdated(fragment, maxState))
            }
            return result
        }

        fun dispatchPreAdded(fragment: Fragment): List<FragmentTransactionCallback.OnPostEventListener> {
            val result = mutableListOf<FragmentTransactionCallback.OnPostEventListener>()
            for (callback in callbacks) {
                result.add(callback.onFragmentPreAdded(fragment))
            }
            return result
        }

        @OptIn(FragmentStateAdapter.ExperimentalFragmentStateAdapterApi::class)
        fun dispatchPreSavedInstanceState(
            fragment: Fragment
        ): List<FragmentTransactionCallback.OnPostEventListener> {
            val result = mutableListOf<FragmentTransactionCallback.OnPostEventListener>()
            for (callback in callbacks) {
                result.add(callback.onFragmentPreSavedInstanceState(fragment))
            }
            return result
        }

        fun dispatchPreRemove(fragment: Fragment): List<FragmentTransactionCallback.OnPostEventListener> {
            val result = mutableListOf<FragmentTransactionCallback.OnPostEventListener>()
            for (callback in callbacks) {
                result.add(callback.onFragmentPreRemoved(fragment))
            }
            return result
        }
    }
}
