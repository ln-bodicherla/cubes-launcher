package com.cubeslauncher.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.cubeslauncher.common.theme.AccentColorManager
import com.cubeslauncher.keyboard.KeyboardPreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

interface KeyboardActionListener {
    fun onKeyPress(key: Key)
    fun onKeyRelease(key: Key)
    fun onKeyRepeat(key: Key)
    fun onSuggestionSelected(suggestion: String)
    fun onSwipeComplete(keys: List<Key>)
    fun onVoiceInput()
}

class CubesKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- Theme colors ---
    private var colorBackground = 0xFF1A1A1A.toInt()
    private var colorKeySurface = 0xFF2B2B2B.toInt()
    private var colorKeyPressed = 0xFF3D3D3D.toInt()
    private var colorSpecialKey = 0xFF222222.toInt()
    private var colorTextPrimary = 0xFFFFFFFF.toInt()
    private var colorTextSecondary = 0xFFAAAAAA.toInt()
    private var colorAccent = Color.parseColor("#0078D7")
    private var colorSuggestionDivider = 0xFF444444.toInt()
    private var isDarkTheme = true

    // --- Dimensions (computed in onSizeChanged) ---
    private var keyHeightPx = 0f
    private var keyGapPx = 0f
    private var textSizeLetterPx = 0f
    private var textSizeSpecialPx = 0f
    private var textSizeSpacePx = 0f
    private var textSizeEmojiPx = 0f
    private var suggestionBarHeightPx = 0f

    // --- Fonts ---
    private var fontLight: Typeface
    private var fontRegular: Typeface
    private var fontSemibold: Typeface

    // --- Paints ---
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val previewTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val iconFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val suggestionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // --- State ---
    var currentPage: KeyboardPage = KeyboardLayouts.QWERTY
        set(value) {
            field = value
            computeKeyRects()
            requestLayout()
            invalidate()
        }

    var shiftState: ShiftState = ShiftState.OFF
        set(value) {
            field = value
            invalidate()
        }

    var actionLabel: String? = null

    var listener: KeyboardActionListener? = null

    // --- Suggestions ---
    private var suggestions: List<String> = emptyList()
    private var pressedSuggestionIndex = -1

    // --- Voice input state ---
    var isListening = false
        set(value) {
            field = value
            invalidate()
        }

    fun setSuggestions(list: List<String>) {
        suggestions = list
        invalidate()
    }

    // --- Key layout ---
    private var keyRects: List<KeyRect> = emptyList()
    private var pressedKey: KeyRect? = null
    private var lastShiftTapTime = 0L

    // --- Key preview ---
    private var previewKey: KeyRect? = null

    // --- Long-press alternates ---
    private var alternatesKeys: List<AlternateRect>? = null
    private var selectedAlternateIndex = -1

    // --- Backspace repeat ---
    private val handler = Handler(Looper.getMainLooper())
    private var isBackspaceRepeating = false
    private val backspaceRepeatRunnable = object : Runnable {
        override fun run() {
            pressedKey?.let { kr ->
                if (kr.key.type == KeyType.BACKSPACE) {
                    listener?.onKeyRepeat(kr.key)
                    handler.postDelayed(this, 50)
                }
            }
        }
    }

    // --- Long-press detection ---
    private var longPressTriggered = false
    private val longPressRunnable = Runnable {
        if (swipeTracker.isSwiping) return@Runnable
        pressedKey?.let { kr ->
            if (kr.key.type == KeyType.BACKSPACE) {
                isBackspaceRepeating = true
                handler.post(backspaceRepeatRunnable)
            } else if (kr.key.alternates.isNotEmpty()) {
                longPressTriggered = true
                swipeTracker.reset()
                showAlternates(kr)
            }
        }
    }

    // --- Swipe tracking ---
    private lateinit var swipeTracker: SwipeTracker
    private val swipeHighlightedKeys = mutableSetOf<KeyRect>()
    private val swipeTrailPath = Path()
    private val swipeTrailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val swipeHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // --- Keyboard height preference ---
    var keyboardHeightMode: Int = 1 // 0=short, 1=normal, 2=tall

    // --- Preference flags from service ---
    var glideTrailEnabled: Boolean = true
    var glideTypingEnabled: Boolean = true
    var showVoiceInput: Boolean = true

    private data class KeyRect(val key: Key, val rect: RectF)
    private data class AlternateRect(val char: Char, val rect: RectF)

    private val density get() = resources.displayMetrics.density

    init {
        fontLight = try {
            context.resources.getFont(
                context.resources.getIdentifier("selawik_semilight", "font", context.packageName)
            )
        } catch (_: Exception) {
            Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        fontRegular = try {
            context.resources.getFont(
                context.resources.getIdentifier("selawik_regular", "font", context.packageName)
            )
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
        fontSemibold = try {
            context.resources.getFont(
                context.resources.getIdentifier("selawik_semibold", "font", context.packageName)
            )
        } catch (_: Exception) {
            Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        swipeTracker = SwipeTracker(density)
        loadTheme()
    }

    fun loadTheme() {
        val prefs = KeyboardPreferencesManager(context)
        val theme = runBlocking { prefs.themeMode.first() }
        isDarkTheme = theme == "dark"
        colorAccent = AccentColorManager.getAccentColor()

        if (isDarkTheme) {
            colorBackground = 0xFF1A1A1A.toInt()
            colorKeySurface = 0xFF2B2B2B.toInt()
            colorKeyPressed = 0xFF3D3D3D.toInt()
            colorSpecialKey = 0xFF222222.toInt()
            colorTextPrimary = 0xFFFFFFFF.toInt()
            colorTextSecondary = 0xFFAAAAAA.toInt()
            colorSuggestionDivider = 0xFF444444.toInt()
        } else {
            colorBackground = 0xFFF2F2F2.toInt()
            colorKeySurface = 0xFFFFFFFF.toInt()
            colorKeyPressed = 0xFFD8D8D8.toInt()
            colorSpecialKey = 0xFFE0E0E0.toInt()
            colorTextPrimary = 0xFF000000.toInt()
            colorTextSecondary = 0xFF666666.toInt()
            colorSuggestionDivider = 0xFFCCCCCC.toInt()
        }
        invalidate()
    }

    private fun getKeyHeightDp(): Float = when (keyboardHeightMode) {
        0 -> 52f  // short
        2 -> 62f  // tall
        else -> 58f // normal
    }

    private val showSuggestionBar: Boolean
        get() = !currentPage.isEmoji

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val d = density
        val rows = currentPage.rows.size
        val gap = 3f * d
        val kh = getKeyHeightDp() * d
        val suggestionH = if (showSuggestionBar) 46f * d else 0f
        val totalHeight = (suggestionH + rows * kh + (rows + 1) * gap).toInt()
        setMeasuredDimension(width, totalHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val d = density
        val scaledDensity = resources.displayMetrics.scaledDensity
        keyGapPx = 3f * d
        keyHeightPx = getKeyHeightDp() * d
        suggestionBarHeightPx = if (showSuggestionBar) 46f * d else 0f
        textSizeLetterPx = 24f * scaledDensity
        textSizeSpecialPx = 13f * scaledDensity
        textSizeSpacePx = 12f * scaledDensity
        textSizeEmojiPx = 26f * scaledDensity

        iconPaint.strokeWidth = 2f * d
        computeKeyRects()
    }

    private fun computeKeyRects() {
        val rects = mutableListOf<KeyRect>()
        val availableWidth = width.toFloat()
        if (availableWidth <= 0) return

        // Find the row with the highest total weight as reference for uniform key sizing
        var refTotalWeight = 0f
        var refKeyCount = 0
        for (row in currentPage.rows) {
            val tw = row.keys.sumOf { it.widthWeight.toDouble() }.toFloat()
            if (tw > refTotalWeight) {
                refTotalWeight = tw
                refKeyCount = row.keys.size
            }
        }
        // Pixels per 1.0 weight unit, based on the widest row
        val refGapTotal = (refKeyCount + 1) * keyGapPx
        val unitWidth = (availableWidth - refGapTotal) / refTotalWeight

        var y = suggestionBarHeightPx + keyGapPx

        for (row in currentPage.rows) {
            val totalWeight = row.keys.sumOf { it.widthWeight.toDouble() }.toFloat()
            val numKeys = row.keys.size
            val gapTotal = (numKeys + 1) * keyGapPx

            // Center rows that have uniform key weights but less than max total weight
            // (e.g. the ASDF row with 9 equal keys vs 10 in QWERTY)
            val allSameWeight = row.keys.all { it.widthWeight == row.keys.first().widthWeight }
            val shouldCenter = totalWeight < refTotalWeight && allSameWeight

            if (shouldCenter) {
                val rowWidth = totalWeight * unitWidth + gapTotal
                val leftPad = (availableWidth - rowWidth) / 2f
                var x = leftPad + keyGapPx
                for (key in row.keys) {
                    val keyWidth = key.widthWeight * unitWidth
                    rects.add(KeyRect(key, RectF(x, y, x + keyWidth, y + keyHeightPx)))
                    x += keyWidth + keyGapPx
                }
            } else {
                // Fill full width
                val usableWidth = availableWidth - gapTotal
                var x = keyGapPx
                for (key in row.keys) {
                    val keyWidth = (key.widthWeight / totalWeight) * usableWidth
                    rects.add(KeyRect(key, RectF(x, y, x + keyWidth, y + keyHeightPx)))
                    x += keyWidth + keyGapPx
                }
            }
            y += keyHeightPx + keyGapPx
        }
        keyRects = rects
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(colorBackground)

        // Draw suggestion bar
        if (showSuggestionBar) {
            drawSuggestionBar(canvas)
        }

        // Draw keys
        for (kr in keyRects) {
            drawKey(canvas, kr, kr == pressedKey)
        }

        // Draw swipe highlights and trail
        if (swipeTracker.isSwiping && glideTrailEnabled) {
            swipeHighlightPaint.color = Color.argb(61, Color.red(colorAccent), Color.green(colorAccent), Color.blue(colorAccent)) // 24% alpha
            for (kr in swipeHighlightedKeys) {
                canvas.drawRect(kr.rect, swipeHighlightPaint)
            }

            swipeTrailPaint.color = Color.argb(179, Color.red(colorAccent), Color.green(colorAccent), Color.blue(colorAccent)) // 70% alpha
            swipeTrailPaint.strokeWidth = 3f * density
            canvas.drawPath(swipeTrailPath, swipeTrailPaint)
        }

        // Draw key preview popup
        previewKey?.let { kr ->
            if (kr.key.type == KeyType.LETTER && alternatesKeys == null && !currentPage.isEmoji) {
                drawPreviewPopup(canvas, kr)
            }
        }

        // Draw alternates popup
        alternatesKeys?.let { alts ->
            drawAlternatesPopup(canvas, alts)
        }
    }

    private fun drawSuggestionBar(canvas: Canvas) {
        val barHeight = suggestionBarHeightPx

        if (isListening) {
            // Voice input active: show red mic + "Listening..."
            drawMicIcon(canvas, 24f * density, barHeight / 2f, true)
            suggestionPaint.typeface = fontLight
            suggestionPaint.textSize = 18f * resources.displayMetrics.scaledDensity
            suggestionPaint.color = Color.parseColor("#F44336")
            suggestionPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(
                "Listening\u2026",
                48f * density,
                barHeight / 2f - (suggestionPaint.ascent() + suggestionPaint.descent()) / 2,
                suggestionPaint
            )
            suggestionPaint.textAlign = Paint.Align.CENTER
            dividerPaint.color = colorSuggestionDivider
            dividerPaint.strokeWidth = 0.5f * density
            canvas.drawLine(0f, barHeight, width.toFloat(), barHeight, dividerPaint)
            return
        }

        if (suggestions.isEmpty()) {
            // Toolbar mode: show mic icon on the right (like Gboard idle state)
            if (showVoiceInput) {
                drawMicIcon(canvas, width - barHeight / 2f, barHeight / 2f, false)
            }
            // Bottom separator
            dividerPaint.color = colorSuggestionDivider
            dividerPaint.strokeWidth = 0.5f * density
            canvas.drawLine(0f, barHeight, width.toFloat(), barHeight, dividerPaint)
            return
        }

        val slotWidth = width.toFloat() / 3f
        val suggestionTextSize = 18f * resources.displayMetrics.scaledDensity

        for (i in 0 until minOf(suggestions.size, 3)) {
            val left = i * slotWidth
            val rect = RectF(left, 0f, left + slotWidth, barHeight)

            if (i == pressedSuggestionIndex) {
                keyPaint.color = colorKeyPressed
                val pressRect = RectF(rect.left + 4 * density, 4 * density, rect.right - 4 * density, barHeight - 4 * density)
                canvas.drawRoundRect(pressRect, 6 * density, 6 * density, keyPaint)
            }

            suggestionPaint.typeface = if (i == 0 && suggestions.size > 1) fontRegular else fontLight
            suggestionPaint.textSize = suggestionTextSize
            suggestionPaint.color = if (i == 0 && suggestions.size > 1) colorAccent else colorTextPrimary

            val text = suggestions[i]
            val displayText = if (text.length > 12) text.take(11) + "\u2026" else text
            canvas.drawText(
                displayText,
                rect.centerX(),
                rect.centerY() - (suggestionPaint.ascent() + suggestionPaint.descent()) / 2,
                suggestionPaint
            )

            if (i < 2 && i < suggestions.size - 1) {
                dividerPaint.color = colorSuggestionDivider
                dividerPaint.strokeWidth = 0.5f * density
                val dividerX = rect.right
                val dividerPadding = 12f * density
                canvas.drawLine(dividerX, dividerPadding, dividerX, barHeight - dividerPadding, dividerPaint)
            }
        }

        // Bottom separator line
        dividerPaint.color = colorSuggestionDivider
        dividerPaint.strokeWidth = 0.5f * density
        canvas.drawLine(0f, barHeight, width.toFloat(), barHeight, dividerPaint)
    }

    private fun drawMicIcon(canvas: Canvas, cx: Float, cy: Float, active: Boolean = false) {
        val d = density
        val micColor = if (active) Color.parseColor("#F44336") else colorTextSecondary

        // Mic body (compact capsule, not stretched)
        val micW = 2.8f * d
        val micH = 4f * d
        val bodyTop = cy - 7f * d
        val bodyBottom = bodyTop + micH * 2f
        val bodyRect = RectF(cx - micW, bodyTop, cx + micW, bodyBottom)
        iconFillPaint.color = micColor
        canvas.drawRoundRect(bodyRect, micW, micW, iconFillPaint)

        // Curved holder arc
        iconPaint.color = micColor
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = 1.5f * d
        val arcW = 4.5f * d
        val arcCy = bodyBottom - micH * 0.3f
        val arcH = 4.5f * d
        val arcRect = RectF(cx - arcW, arcCy - arcH, cx + arcW, arcCy + arcH)
        canvas.drawArc(arcRect, 0f, 180f, false, iconPaint)

        // Stem
        val stemTop = arcCy + arcH
        val stemBottom = stemTop + 2f * d
        canvas.drawLine(cx, stemTop, cx, stemBottom, iconPaint)

        // Base
        val baseW = 3f * d
        canvas.drawLine(cx - baseW, stemBottom, cx + baseW, stemBottom, iconPaint)

        iconPaint.strokeWidth = 2f * d
    }

    private fun drawKey(canvas: Canvas, kr: KeyRect, pressed: Boolean) {
        val key = kr.key
        val rect = kr.rect

        // Determine key background color
        val bgColor = when {
            pressed && key.type != KeyType.ENTER -> colorKeyPressed
            key.type == KeyType.ENTER -> if (pressed) {
                adjustBrightness(colorAccent, 0.7f)
            } else colorAccent
            key.type == KeyType.SHIFT || key.type == KeyType.BACKSPACE ||
            key.type == KeyType.SYMBOL_SWITCH || key.type == KeyType.ABC_SWITCH ||
            key.type == KeyType.MORE_SYMBOLS || key.type == KeyType.EMOJI -> {
                if (pressed) colorKeyPressed else colorSpecialKey
            }
            else -> colorKeySurface
        }

        keyPaint.color = bgColor
        canvas.drawRect(rect, keyPaint)

        // Draw shift indicator for caps lock
        if (key.type == KeyType.SHIFT && shiftState == ShiftState.CAPS_LOCK) {
            val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            indicatorPaint.color = colorAccent
            val indicatorHeight = 3f * density
            canvas.drawRect(rect.left, rect.bottom - indicatorHeight, rect.right, rect.bottom, indicatorPaint)
        }

        val centerX = rect.centerX()
        val centerY = rect.centerY()

        when {
            key.iconName == "shift" -> drawShiftIcon(canvas, centerX, centerY)
            key.iconName == "backspace" -> drawBackspaceIcon(canvas, centerX, centerY)
            key.iconName == "enter" -> drawEnterIcon(canvas, centerX, centerY)
            key.iconName == "emoji" -> drawEmojiIcon(canvas, centerX, centerY)
            key.type == KeyType.SPACE -> drawSpaceBar(canvas, rect)
            key.type == KeyType.SYMBOL_SWITCH || key.type == KeyType.ABC_SWITCH ||
            key.type == KeyType.MORE_SYMBOLS -> {
                textPaint.typeface = fontRegular
                textPaint.textSize = textSizeSpecialPx
                textPaint.color = colorTextSecondary
                canvas.drawText(key.label, centerX, centerY - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
            }
            currentPage.isEmoji && key.type == KeyType.LETTER -> {
                // Emoji key - use system font
                textPaint.typeface = Typeface.DEFAULT
                textPaint.textSize = textSizeEmojiPx
                canvas.drawText(key.label, centerX, centerY - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
            }
            else -> {
                textPaint.typeface = fontLight
                textPaint.textSize = textSizeLetterPx
                textPaint.color = if (key.type == KeyType.ENTER) Color.WHITE else colorTextPrimary
                val label = if (shiftState != ShiftState.OFF && key.type == KeyType.LETTER &&
                    key.outputChar != null && key.outputChar.isLetter()) {
                    key.label.uppercase()
                } else {
                    key.label
                }
                canvas.drawText(label, centerX, centerY - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
            }
        }
    }

    private fun drawSpaceBar(canvas: Canvas, rect: RectF) {
        // Draw a subtle line in the space bar to indicate it
        val lineY = rect.centerY()
        val lineStartX = rect.centerX() - 30f * density
        val lineEndX = rect.centerX() + 30f * density
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTextSecondary
            strokeWidth = 1.5f * density
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(lineStartX, lineY, lineEndX, lineY, linePaint)
    }

    private fun drawShiftIcon(canvas: Canvas, cx: Float, cy: Float) {
        val d = density
        val size = 9f * d
        val path = Path()

        // Arrow pointing up
        path.moveTo(cx, cy - size)
        path.lineTo(cx + size, cy + size * 0.1f)
        path.lineTo(cx + size * 0.4f, cy + size * 0.1f)
        path.lineTo(cx + size * 0.4f, cy + size * 0.7f)
        path.lineTo(cx - size * 0.4f, cy + size * 0.7f)
        path.lineTo(cx - size * 0.4f, cy + size * 0.1f)
        path.lineTo(cx - size, cy + size * 0.1f)
        path.close()

        when (shiftState) {
            ShiftState.OFF -> {
                iconPaint.color = colorTextPrimary
                iconPaint.style = Paint.Style.STROKE
                canvas.drawPath(path, iconPaint)
            }
            ShiftState.SINGLE -> {
                iconFillPaint.color = colorTextPrimary
                canvas.drawPath(path, iconFillPaint)
            }
            ShiftState.CAPS_LOCK -> {
                iconFillPaint.color = colorTextPrimary
                canvas.drawPath(path, iconFillPaint)
                // Underline for caps lock
                val underY = cy + size * 0.9f
                iconPaint.color = colorTextPrimary
                iconPaint.style = Paint.Style.STROKE
                canvas.drawLine(cx - size * 0.4f, underY, cx + size * 0.4f, underY, iconPaint)
            }
        }
    }

    private fun drawBackspaceIcon(canvas: Canvas, cx: Float, cy: Float) {
        val d = density
        val w = 11f * d
        val h = 7f * d
        val notch = 5f * d

        iconPaint.color = colorTextPrimary
        iconPaint.style = Paint.Style.STROKE

        val path = Path()
        path.moveTo(cx - w + notch, cy - h)
        path.lineTo(cx + w, cy - h)
        path.lineTo(cx + w, cy + h)
        path.lineTo(cx - w + notch, cy + h)
        path.lineTo(cx - w, cy)
        path.close()

        canvas.drawPath(path, iconPaint)

        // X inside
        val xSize = 3f * d
        val xCx = cx + 2f * d
        canvas.drawLine(xCx - xSize, cy - xSize, xCx + xSize, cy + xSize, iconPaint)
        canvas.drawLine(xCx + xSize, cy - xSize, xCx - xSize, cy + xSize, iconPaint)
    }

    private fun drawEnterIcon(canvas: Canvas, cx: Float, cy: Float) {
        val d = density

        // If we have an action label, draw text instead
        actionLabel?.let { label ->
            textPaint.typeface = fontSemibold
            textPaint.textSize = textSizeSpecialPx
            textPaint.color = Color.WHITE
            canvas.drawText(label, cx, cy - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
            return
        }

        val w = 9f * d
        val h = 5f * d
        val arrowSize = 3.5f * d

        iconPaint.color = Color.WHITE
        iconPaint.style = Paint.Style.STROKE

        val path = Path()
        path.moveTo(cx + w, cy - h)
        path.lineTo(cx + w, cy)
        path.lineTo(cx - w, cy)
        path.moveTo(cx - w + arrowSize, cy - arrowSize)
        path.lineTo(cx - w, cy)
        path.lineTo(cx - w + arrowSize, cy + arrowSize)

        canvas.drawPath(path, iconPaint)
    }

    private fun drawEmojiIcon(canvas: Canvas, cx: Float, cy: Float) {
        val d = density
        val r = 9f * d

        iconPaint.color = colorTextPrimary
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = 1.5f * d

        // Circle face
        canvas.drawCircle(cx, cy, r, iconPaint)

        // Eyes
        iconFillPaint.color = colorTextPrimary
        canvas.drawCircle(cx - 3.5f * d, cy - 2f * d, 1.5f * d, iconFillPaint)
        canvas.drawCircle(cx + 3.5f * d, cy - 2f * d, 1.5f * d, iconFillPaint)

        // Smile arc
        val smileRect = RectF(cx - 5f * d, cy - 1f * d, cx + 5f * d, cy + 6f * d)
        canvas.drawArc(smileRect, 10f, 160f, false, iconPaint)

        iconPaint.strokeWidth = 2f * d // Reset
    }

    private fun drawPreviewPopup(canvas: Canvas, kr: KeyRect) {
        val d = density
        val popupWidth = kr.rect.width() * 1.4f
        val popupHeight = keyHeightPx * 1.3f
        val popupLeft = kr.rect.centerX() - popupWidth / 2
        val popupTop = kr.rect.top - popupHeight - 4 * d
        val popupRect = RectF(popupLeft, popupTop, popupLeft + popupWidth, popupTop + popupHeight)

        // Clamp to screen bounds
        if (popupRect.left < 0) popupRect.offset(-popupRect.left, 0f)
        if (popupRect.right > width) popupRect.offset(width - popupRect.right, 0f)
        if (popupRect.top < 0) popupRect.offset(0f, -popupRect.top)

        previewPaint.color = colorAccent
        canvas.drawRect(popupRect, previewPaint)

        previewTextPaint.typeface = fontRegular
        previewTextPaint.textSize = textSizeLetterPx * 1.3f
        previewTextPaint.color = Color.WHITE
        val label = if (shiftState != ShiftState.OFF && kr.key.outputChar?.isLetter() == true) {
            kr.key.label.uppercase()
        } else kr.key.label
        canvas.drawText(
            label,
            popupRect.centerX(),
            popupRect.centerY() - (previewTextPaint.ascent() + previewTextPaint.descent()) / 2,
            previewTextPaint
        )
    }

    private fun showAlternates(kr: KeyRect) {
        val d = density
        val alts = kr.key.alternates
        if (alts.isEmpty()) return

        val cellWidth = 38f * d
        val cellHeight = keyHeightPx * 1.15f
        val totalWidth = alts.size * cellWidth
        val startX = (kr.rect.centerX() - totalWidth / 2).coerceAtLeast(0f)
            .coerceAtMost((width - totalWidth).coerceAtLeast(0f))
        val top = (kr.rect.top - cellHeight - 4 * d).coerceAtLeast(0f)

        alternatesKeys = alts.mapIndexed { i, ch ->
            AlternateRect(ch, RectF(
                startX + i * cellWidth,
                top,
                startX + (i + 1) * cellWidth,
                top + cellHeight
            ))
        }
        selectedAlternateIndex = -1
        invalidate()
    }

    private fun drawAlternatesPopup(canvas: Canvas, alts: List<AlternateRect>) {
        for ((i, alt) in alts.withIndex()) {
            previewPaint.color = if (i == selectedAlternateIndex) colorAccent else colorSpecialKey
            canvas.drawRect(alt.rect, previewPaint)

            previewTextPaint.typeface = fontRegular
            previewTextPaint.textSize = textSizeLetterPx
            previewTextPaint.color = if (i == selectedAlternateIndex) Color.WHITE else colorTextPrimary
            val label = if (shiftState != ShiftState.OFF) alt.char.uppercase() else alt.char.toString()
            canvas.drawText(
                label,
                alt.rect.centerX(),
                alt.rect.centerY() - (previewTextPaint.ascent() + previewTextPaint.descent()) / 2,
                previewTextPaint
            )
        }
    }

    private fun isSwipeableKey(key: Key): Boolean {
        return key.type == KeyType.LETTER && key.outputChar?.isLetter() == true && !currentPage.isEmoji
    }

    private fun rebuildSwipeTrailPath() {
        swipeTrailPath.reset()
        val points = swipeTracker.pathPoints
        if (points.size < 2) return

        swipeTrailPath.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val cur = points[i]
            val midX = (prev.x + cur.x) / 2f
            val midY = (prev.y + cur.y) / 2f
            swipeTrailPath.quadTo(prev.x, prev.y, midX, midY)
        }
        val last = points.last()
        swipeTrailPath.lineTo(last.x, last.y)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Check suggestion bar touch
                if (showSuggestionBar && event.y < suggestionBarHeightPx) {
                    if (suggestions.isNotEmpty()) {
                        val slotWidth = width.toFloat() / 3f
                        pressedSuggestionIndex = (event.x / slotWidth).toInt().coerceIn(0, 2)
                        if (pressedSuggestionIndex >= suggestions.size) pressedSuggestionIndex = -1
                        invalidate()
                        return true
                    } else if (showVoiceInput) {
                        // Mic icon tap area (right side of suggestion bar)
                        val micCx = width - suggestionBarHeightPx / 2f
                        val tapRadius = suggestionBarHeightPx / 2f
                        if (event.x >= micCx - tapRadius && event.x <= micCx + tapRadius) {
                            listener?.onVoiceInput()
                            return true
                        }
                    }
                }

                val kr = findKeyAt(event.x, event.y) ?: return true
                pressedKey = kr
                longPressTriggered = false

                // Start swipe tracking for letter keys on QWERTY page (only if glide typing enabled)
                if (glideTypingEnabled && isSwipeableKey(kr.key)) {
                    swipeTracker.onDown(event.x, event.y, kr.key)
                }

                if (kr.key.type == KeyType.LETTER && !currentPage.isEmoji) {
                    previewKey = kr
                }

                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                handler.postDelayed(longPressRunnable, 400)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                // Handle suggestion bar drag
                if (pressedSuggestionIndex >= 0) {
                    if (event.y < suggestionBarHeightPx) {
                        val slotWidth = width.toFloat() / 3f
                        val newIndex = (event.x / slotWidth).toInt().coerceIn(0, 2)
                        if (newIndex != pressedSuggestionIndex) {
                            pressedSuggestionIndex = if (newIndex < suggestions.size) newIndex else -1
                            invalidate()
                        }
                    } else {
                        pressedSuggestionIndex = -1
                        invalidate()
                    }
                    return true
                }

                alternatesKeys?.let { alts ->
                    val idx = alts.indexOfFirst { it.rect.contains(event.x, event.y) }
                    if (idx != selectedAlternateIndex) {
                        selectedAlternateIndex = idx
                        invalidate()
                    }
                    return true
                }

                // Swipe tracking during move
                if (swipeTracker.isActive) {
                    // Interpolate between last path point and current touch to catch
                    // keys the finger crossed between touch events (like Gboard does)
                    var anyKeyAdded = false
                    val points = swipeTracker.pathPoints
                    if (points.isNotEmpty()) {
                        val prev = points.last()
                        val dx = event.x - prev.x
                        val dy = event.y - prev.y
                        val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                        val stepSize = keyGapPx * 3f
                        val steps = (dist / stepSize).toInt().coerceAtLeast(1)
                        for (i in 1 until steps) {
                            val t = i.toFloat() / steps
                            val ix = prev.x + dx * t
                            val iy = prev.y + dy * t
                            val ikr = findSwipeKeyAt(ix, iy)
                            if (ikr != null && swipeTracker.addIntermediateKey(ikr.key)) {
                                anyKeyAdded = true
                            }
                        }
                    }

                    val kr = findSwipeKeyAt(event.x, event.y)
                    val swipeKey = if (kr != null) kr.key else null
                    val newKeyAdded = swipeTracker.onMove(event.x, event.y, swipeKey)
                    anyKeyAdded = anyKeyAdded || newKeyAdded

                    if (swipeTracker.isSwiping) {
                        // Cancel long-press and backspace repeat when swiping
                        handler.removeCallbacks(longPressRunnable)
                        handler.removeCallbacks(backspaceRepeatRunnable)
                        isBackspaceRepeating = false
                        previewKey = null

                        // Update highlighted keys
                        swipeHighlightedKeys.clear()
                        for (trackedKey in swipeTracker.traversedKeys) {
                            keyRects.firstOrNull { it.key === trackedKey }?.let {
                                swipeHighlightedKeys.add(it)
                            }
                        }

                        rebuildSwipeTrailPath()

                        if (anyKeyAdded) {
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }

                        invalidate()
                        return true
                    }
                }

                val kr = findKeyAt(event.x, event.y)
                if (kr != pressedKey) {
                    handler.removeCallbacks(longPressRunnable)
                    handler.removeCallbacks(backspaceRepeatRunnable)
                    isBackspaceRepeating = false
                    pressedKey = kr
                    previewKey = if (kr?.key?.type == KeyType.LETTER && !currentPage.isEmoji) kr else null
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Handle suggestion selection
                if (pressedSuggestionIndex >= 0 && event.action == MotionEvent.ACTION_UP) {
                    if (pressedSuggestionIndex < suggestions.size && event.y < suggestionBarHeightPx) {
                        listener?.onSuggestionSelected(suggestions[pressedSuggestionIndex])
                    }
                    pressedSuggestionIndex = -1
                    swipeTracker.reset()
                    swipeHighlightedKeys.clear()
                    swipeTrailPath.reset()
                    invalidate()
                    return true
                }
                pressedSuggestionIndex = -1

                handler.removeCallbacks(longPressRunnable)
                handler.removeCallbacks(backspaceRepeatRunnable)
                isBackspaceRepeating = false

                // Handle alternates selection
                alternatesKeys?.let { alts ->
                    if (selectedAlternateIndex >= 0 && selectedAlternateIndex < alts.size) {
                        val ch = alts[selectedAlternateIndex].char
                        val finalChar = if (shiftState != ShiftState.OFF) ch.uppercaseChar() else ch
                        listener?.onKeyPress(Key(finalChar.toString(), finalChar, KeyType.LETTER))
                    }
                    alternatesKeys = null
                    selectedAlternateIndex = -1
                    pressedKey = null
                    previewKey = null
                    swipeTracker.reset()
                    swipeHighlightedKeys.clear()
                    swipeTrailPath.reset()
                    invalidate()
                    return true
                }

                // Handle swipe completion
                if (swipeTracker.isActive) {
                    val result = swipeTracker.onUp()
                    val traversedKeys = swipeTracker.traversedKeys.toList()

                    swipeHighlightedKeys.clear()
                    swipeTrailPath.reset()

                    when {
                        event.action == MotionEvent.ACTION_CANCEL -> {
                            // Discard swipe on cancel
                            swipeTracker.reset()
                        }
                        result == SwipeResult.SWIPE_COMPLETE -> {
                            swipeTracker.reset()
                            listener?.onSwipeComplete(traversedKeys)
                            pressedKey = null
                            previewKey = null
                            longPressTriggered = false
                            invalidate()
                            return true
                        }
                        else -> {
                            // TAP or SWIPE_SINGLE_KEY: fall through to normal tap handling
                            swipeTracker.reset()
                        }
                    }
                }

                if (event.action == MotionEvent.ACTION_UP && !longPressTriggered) {
                    pressedKey?.let { kr ->
                        if (kr.key.type == KeyType.SHIFT) {
                            handleShiftTap()
                        } else if (!isBackspaceRepeating) {
                            listener?.onKeyPress(kr.key)
                        }
                    }
                }

                pressedKey = null
                previewKey = null
                longPressTriggered = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleShiftTap() {
        val now = System.currentTimeMillis()
        shiftState = when (shiftState) {
            ShiftState.OFF -> {
                lastShiftTapTime = now
                ShiftState.SINGLE
            }
            ShiftState.SINGLE -> {
                if (now - lastShiftTapTime < 300) {
                    ShiftState.CAPS_LOCK
                } else {
                    lastShiftTapTime = now
                    ShiftState.OFF
                }
            }
            ShiftState.CAPS_LOCK -> ShiftState.OFF
        }
        listener?.onKeyPress(Key("", type = KeyType.SHIFT))
    }

    private fun findKeyAt(x: Float, y: Float): KeyRect? {
        return keyRects.firstOrNull { it.rect.contains(x, y) }
    }

    /** Find the nearest swipeable letter key, with an expanded hit area for fast swipes. */
    private fun findSwipeKeyAt(x: Float, y: Float): KeyRect? {
        val exact = keyRects.firstOrNull { it.rect.contains(x, y) && isSwipeableKey(it.key) }
        if (exact != null) return exact
        // Expand search by a few dp so fast swipes between keys still register
        val expand = 4f * density
        var best: KeyRect? = null
        var bestDist = Float.MAX_VALUE
        for (kr in keyRects) {
            if (!isSwipeableKey(kr.key)) continue
            if (x >= kr.rect.left - expand && x <= kr.rect.right + expand &&
                y >= kr.rect.top - expand && y <= kr.rect.bottom + expand) {
                val dx = x - kr.rect.centerX()
                val dy = y - kr.rect.centerY()
                val dist = dx * dx + dy * dy
                if (dist < bestDist) {
                    bestDist = dist
                    best = kr
                }
            }
        }
        return best
    }

    private fun adjustBrightness(color: Int, factor: Float): Int {
        val r = ((color shr 16 and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = ((color shr 8 and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
