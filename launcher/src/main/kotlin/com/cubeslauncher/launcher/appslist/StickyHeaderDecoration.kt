package com.cubeslauncher.launcher.appslist

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.launcher.R
import com.cubeslauncher.common.theme.AccentColorManager

/**
 * Draws a WP10-style sticky header tile at the top of the RecyclerView.
 * The tile is a small colored square with the section letter centered inside.
 */
class StickyHeaderDecoration : RecyclerView.ItemDecoration() {

    private var textPaint: Paint? = null
    private var tilePaint: Paint? = null
    private var bgPaint: Paint? = null
    private var tileSize = 0f
    private var marginTop = 0f
    private var marginBottom = 0f
    private var textSize = 0f

    private fun ensureInit(view: View) {
        if (textPaint != null) return

        val ctx = view.context
        val res = ctx.resources

        tileSize = res.getDimension(R.dimen.letter_header_tile_size)
        marginTop = 12f * res.displayMetrics.density  // 12dp
        marginBottom = 4f * res.displayMetrics.density  // 4dp
        textSize = res.getDimension(R.dimen.letter_header_text_size)

        val typeface = ResourcesCompat.getFont(ctx, R.font.selawik_semibold)

        textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ResourcesCompat.getColor(res, R.color.wp_white, null)
            this.textSize = this@StickyHeaderDecoration.textSize
            this.typeface = typeface ?: Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        tilePaint = Paint().apply {
            color = AccentColorManager.getAccentColor()
        }

        val bgColor = TypedValue().let { tv ->
            ctx.theme.resolveAttribute(R.attr.wpBackground, tv, true)
            tv.data
        }
        bgPaint = Paint().apply {
            color = bgColor
        }
    }

    fun invalidateColors() {
        textPaint = null
        tilePaint = null
        bgPaint = null
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val adapter = parent.adapter as? AppsListAdapter ?: return
        if (adapter.itemCount == 0) return

        ensureInit(parent)
        val textPaint = this.textPaint ?: return
        val tilePaint = this.tilePaint ?: return
        val bgPaint = this.bgPaint ?: return

        val totalHeight = marginTop + tileSize + marginBottom

        // Find current header letter
        val firstChild = parent.getChildAt(0) ?: return
        val firstPos = parent.getChildAdapterPosition(firstChild)
        if (firstPos == RecyclerView.NO_POSITION) return

        var currentLetter: String? = null
        for (i in firstPos downTo 0) {
            val item = adapter.getItemAt(i)
            if (item is AppItem.Header) {
                currentLetter = item.letter
                break
            }
        }
        if (currentLetter == null) return

        // Check if next header pushes this one up
        var stickyTop = 0f
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val pos = parent.getChildAdapterPosition(child)
            if (pos == RecyclerView.NO_POSITION) continue
            val item = adapter.getItemAt(pos)
            if (item is AppItem.Header && item.letter != currentLetter) {
                val nextTop = child.top.toFloat()
                if (nextTop < totalHeight) {
                    stickyTop = nextTop - totalHeight
                }
                break
            }
        }

        // Hide the actual header item view if it's behind the sticky header
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val pos = parent.getChildAdapterPosition(child)
            if (pos == RecyclerView.NO_POSITION) continue
            val item = adapter.getItemAt(pos)
            if (item is AppItem.Header && item.letter == currentLetter) {
                // Hide if its top is within the sticky header area
                child.visibility = if (child.top.toFloat() < totalHeight) View.INVISIBLE else View.VISIBLE
                break
            }
        }

        val paddingStart = parent.paddingLeft.toFloat()

        // Draw background strip
        c.drawRect(0f, stickyTop, parent.width.toFloat(), stickyTop + totalHeight, bgPaint)

        // Draw tile
        val tileLeft = paddingStart
        val tileTop = stickyTop + marginTop
        c.drawRect(tileLeft, tileTop, tileLeft + tileSize, tileTop + tileSize, tilePaint)

        // Draw letter centered in tile
        val fm = textPaint.fontMetrics
        val textY = tileTop + (tileSize - fm.ascent - fm.descent) / 2f
        c.drawText(
            currentLetter,
            tileLeft + tileSize / 2f,
            textY,
            textPaint
        )
    }
}
