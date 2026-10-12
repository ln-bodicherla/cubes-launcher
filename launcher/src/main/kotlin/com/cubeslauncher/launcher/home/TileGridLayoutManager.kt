package com.cubeslauncher.launcher.home

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.launcher.model.TileSize

class TileGridLayoutManager(
    private var spanCount: Int = 4,
    private val spacing: Int = 0
) : RecyclerView.LayoutManager() {

    internal class TileRect(
        val col: Int,
        val row: Int,
        val colSpan: Int,
        val rowSpan: Int
    )

    private val placements = arrayListOf<TileRect>()
    private var totalGridRows = 0
    private var scrollY = 0

    var tileSizeProvider: ((Int) -> TileSize)? = null

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams =
        RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )

    override fun canScrollVertically() = true

    fun setSpanCount(count: Int) {
        if (spanCount != count) {
            spanCount = count
            requestLayout()
        }
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        detachAndScrapAttachedViews(recycler)
        if (width == 0 || state.itemCount == 0) {
            totalGridRows = 0
            return
        }
        computePlacements(state.itemCount)
        val maxScroll = maxOf(0, totalHeight() - height)
        if (scrollY > maxScroll) scrollY = maxScroll
        fill(recycler, state)
    }

    private fun computePlacements(count: Int) {
        placements.clear()
        val grid = mutableListOf<BooleanArray>()

        fun ensure(row: Int) {
            while (grid.size <= row) grid.add(BooleanArray(spanCount))
        }

        fun fits(c: Int, r: Int, cs: Int, rs: Int): Boolean {
            if (c + cs > spanCount) return false
            ensure(r + rs - 1)
            for (rr in r until r + rs)
                for (cc in c until c + cs)
                    if (grid[rr][cc]) return false
            return true
        }

        fun mark(c: Int, r: Int, cs: Int, rs: Int) {
            ensure(r + rs - 1)
            for (rr in r until r + rs)
                for (cc in c until c + cs)
                    grid[rr][cc] = true
        }

        for (i in 0 until count) {
            val ts = tileSizeProvider?.invoke(i) ?: TileSize.SMALL
            val cs = when (ts) {
                TileSize.SMALL -> 1
                TileSize.MEDIUM -> 2
                TileSize.WIDE -> 3
            }
            val rs = when (ts) {
                TileSize.SMALL -> 1
                TileSize.MEDIUM -> 2
                TileSize.WIDE -> 2
            }

            var r = 0
            search@ while (true) {
                for (c in 0..spanCount - cs) {
                    if (fits(c, r, cs, rs)) {
                        mark(c, r, cs, rs)
                        placements.add(TileRect(c, r, cs, rs))
                        break@search
                    }
                }
                r++
            }
        }
        totalGridRows = grid.size
    }

    private fun cellSize(): Int = (width - paddingLeft - paddingRight) / spanCount

    private fun totalHeight(): Int = totalGridRows * cellSize() + paddingTop + paddingBottom

    private fun fill(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        val cs = cellSize()
        if (cs <= 0) return
        val half = spacing / 2

        for (i in 0 until state.itemCount) {
            if (i >= placements.size) break
            val p = placements[i]
            val l = paddingLeft + p.col * cs + half
            val t = paddingTop + p.row * cs + half - scrollY
            val r = paddingLeft + (p.col + p.colSpan) * cs - half
            val b = paddingTop + (p.row + p.rowSpan) * cs - half - scrollY

            if (b <= 0 || t >= height) continue

            val v = recycler.getViewForPosition(i)
            addView(v)
            v.measure(
                View.MeasureSpec.makeMeasureSpec(r - l, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(b - t, View.MeasureSpec.EXACTLY)
            )
            layoutDecorated(v, l, t, r, b)
        }
    }

    override fun scrollVerticallyBy(
        dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State
    ): Int {
        if (childCount == 0 && state.itemCount == 0) return 0
        val max = maxOf(0, totalHeight() - height)
        val newY = (scrollY + dy).coerceIn(0, max)
        val consumed = newY - scrollY
        if (consumed == 0) return 0
        scrollY = newY
        detachAndScrapAttachedViews(recycler)
        fill(recycler, state)
        return consumed
    }

    override fun computeVerticalScrollOffset(state: RecyclerView.State) = scrollY
    override fun computeVerticalScrollRange(state: RecyclerView.State) = totalHeight()
    override fun computeVerticalScrollExtent(state: RecyclerView.State) = height

    fun getPositionForPoint(x: Float, y: Float): Int {
        val cs = cellSize()
        if (cs <= 0) return -1
        val half = spacing / 2
        for (i in placements.indices) {
            val p = placements[i]
            val l = paddingLeft + p.col * cs + half
            val t = paddingTop + p.row * cs + half - scrollY
            val r = paddingLeft + (p.col + p.colSpan) * cs - half
            val b = paddingTop + (p.row + p.rowSpan) * cs - half - scrollY
            if (x >= l && x < r && y >= t && y < b) return i
        }
        return -1
    }

    fun getCellSize(): Int = cellSize()

    fun getScrollYOffset(): Int = scrollY
}
