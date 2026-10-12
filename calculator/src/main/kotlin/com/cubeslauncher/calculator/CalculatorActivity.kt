package com.cubeslauncher.calculator

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cubeslauncher.calculator.databinding.ActivityCalculatorBinding
import com.cubeslauncher.common.theme.AccentColorManager
import com.cubeslauncher.common.theme.ThemeHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class CalculatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalculatorBinding
    private val engine = CalculatorEngine()
    private var currentThemeMode: String = "dark"
    private var currentAccentColor: String = "#0078D7"

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        currentThemeMode = runBlocking { CalculatorPreferencesManager(applicationContext).themeMode.first() }
        currentAccentColor = runBlocking { CalculatorPreferencesManager(applicationContext).accentColor.first() }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityCalculatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AccentColorManager.init(applicationContext)

        setupEngine()
        setupDigitButtons()
        setupOperatorButtons()
        setupFunctionButtons()
        setupMemoryButtons()
        setupControlButtons()
        applyAccentColor()
        applyWindowInsets()
    }

    override fun onResume() {
        super.onResume()
        val newMode = runBlocking { CalculatorPreferencesManager(applicationContext).themeMode.first() }
        val newAccent = runBlocking { CalculatorPreferencesManager(applicationContext).accentColor.first() }
        if (newMode != currentThemeMode || newAccent != currentAccentColor) {
            recreate()
        }
    }

    @Deprecated("Use onBackPressedDispatcher", ReplaceWith("onBackPressedDispatcher"))
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    private fun setupEngine() {
        engine.onDisplayChanged = { expression, result ->
            binding.expressionDisplay.text = expression
            binding.resultDisplay.text = result
        }
    }

    private fun setupDigitButtons() {
        binding.btn0.setOnClickListener { engine.onDigit('0') }
        binding.btn1.setOnClickListener { engine.onDigit('1') }
        binding.btn2.setOnClickListener { engine.onDigit('2') }
        binding.btn3.setOnClickListener { engine.onDigit('3') }
        binding.btn4.setOnClickListener { engine.onDigit('4') }
        binding.btn5.setOnClickListener { engine.onDigit('5') }
        binding.btn6.setOnClickListener { engine.onDigit('6') }
        binding.btn7.setOnClickListener { engine.onDigit('7') }
        binding.btn8.setOnClickListener { engine.onDigit('8') }
        binding.btn9.setOnClickListener { engine.onDigit('9') }
        binding.btnDecimal.setOnClickListener { engine.onDecimal() }
    }

    private fun setupOperatorButtons() {
        binding.btnAdd.setOnClickListener { engine.onOperator('+') }
        binding.btnSubtract.setOnClickListener { engine.onOperator('\u2212') }
        binding.btnMultiply.setOnClickListener { engine.onOperator('\u00D7') }
        binding.btnDivide.setOnClickListener { engine.onOperator('\u00F7') }
        binding.btnEquals.setOnClickListener { engine.onEquals() }
    }

    private fun setupFunctionButtons() {
        binding.btnPercent.setOnClickListener { engine.onPercent() }
        binding.btnReciprocal.setOnClickListener { engine.onReciprocal() }
        binding.btnSquare.setOnClickListener { engine.onSquare() }
        binding.btnSquareRoot.setOnClickListener { engine.onSquareRoot() }
        binding.btnNegate.setOnClickListener { engine.onNegate() }
    }

    private fun setupMemoryButtons() {
        binding.btnMemoryClear.setOnClickListener { engine.onMemoryClear() }
        binding.btnMemoryRecall.setOnClickListener { engine.onMemoryRecall() }
        binding.btnMemoryAdd.setOnClickListener { engine.onMemoryAdd() }
        binding.btnMemorySubtract.setOnClickListener { engine.onMemorySubtract() }
        binding.btnMemoryStore.setOnClickListener { engine.onMemoryStore() }
    }

    private fun setupControlButtons() {
        binding.btnClear.setOnClickListener { engine.onClear() }
        binding.btnClearEntry.setOnClickListener { engine.onClearEntry() }
        binding.btnBackspace.setOnClickListener { engine.onBackspace() }
    }

    private fun applyAccentColor() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AccentColorManager.accentColorFlow.collect { accentColor ->
                    updateEqualsButtonColor(accentColor)
                }
            }
        }
    }

    private fun updateEqualsButtonColor(accentColor: Int) {
        val background = binding.btnEquals.background
        if (background is RippleDrawable) {
            val shape = background.getDrawable(0)
            if (shape is GradientDrawable) {
                shape.setColor(accentColor)
            } else {
                // Rebuild the drawable
                val newShape = GradientDrawable().apply {
                    this.shape = GradientDrawable.RECTANGLE
                    setColor(accentColor)
                }
                background.setDrawable(0, newShape)
            }
            background.invalidateSelf()
        } else {
            // Fallback: create a new ripple drawable with accent color
            val shape = GradientDrawable().apply {
                this.shape = GradientDrawable.RECTANGLE
                setColor(accentColor)
            }
            val ripple = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#33FFFFFF")),
                shape,
                null
            )
            binding.btnEquals.background = ripple
        }
    }

    private fun applyWindowInsets() {
        // Top inset on the spacer to push content below status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.topSpacer) { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            view.setPadding(view.paddingLeft, topInset, view.paddingRight, view.paddingBottom)
            insets
        }

        // Bottom inset on the last row to account for navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomRow) { view, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottomInset)
            insets
        }
    }
}
