package com.cubeslauncher.calculator

import kotlin.math.sqrt

/**
 * Pure calculation engine for a standard calculator with left-to-right evaluation.
 * Supports basic arithmetic (+, -, x, /), percentage, reciprocal, square, square root,
 * and memory operations (MC, MR, M+, M-, MS).
 */
class CalculatorEngine {

    /** Display callback: (expression, result) */
    var onDisplayChanged: ((expression: String, result: String) -> Unit)? = null

    // Internal state
    private var currentEntry: String = "0"
    private var expression: String = ""
    private var pendingOperator: Char? = null
    private var accumulator: Double = 0.0
    private var memory: Double = 0.0
    private var hasAccumulator: Boolean = false
    private var lastEqualsOperator: Char? = null
    private var lastEqualsOperand: Double = 0.0
    private var justPressedEquals: Boolean = false
    private var justPressedOperator: Boolean = false
    private var errorState: Boolean = false

    init {
        notifyDisplay()
    }

    // --- Digit input ---
    fun onDigit(d: Char) {
        if (errorState) clearAll()

        if (justPressedEquals) {
            // Start fresh after equals
            clearAll()
        }

        if (justPressedOperator) {
            currentEntry = "0"
            justPressedOperator = false
        }

        if (currentEntry == "0" || currentEntry == "-0") {
            // Replace leading zero (keep minus sign if present)
            currentEntry = if (currentEntry == "-0") "-$d" else d.toString()
        } else {
            if (currentEntry.replace("-", "").replace(".", "").length >= 16) return
            currentEntry += d
        }

        notifyDisplay()
    }

    // --- Decimal point ---
    fun onDecimal() {
        if (errorState) clearAll()

        if (justPressedEquals) {
            clearAll()
        }

        if (justPressedOperator) {
            currentEntry = "0"
            justPressedOperator = false
        }

        if (!currentEntry.contains('.')) {
            currentEntry += '.'
        }

        notifyDisplay()
    }

    // --- Negate ---
    fun onNegate() {
        if (errorState) return

        if (currentEntry == "0") return

        currentEntry = if (currentEntry.startsWith('-')) {
            currentEntry.substring(1)
        } else {
            "-$currentEntry"
        }

        notifyDisplay()
    }

    // --- Backspace ---
    fun onBackspace() {
        if (errorState) {
            clearAll()
            return
        }

        if (justPressedEquals || justPressedOperator) return

        if (currentEntry.length <= 1 || (currentEntry.length == 2 && currentEntry.startsWith('-'))) {
            currentEntry = "0"
        } else {
            currentEntry = currentEntry.dropLast(1)
        }

        notifyDisplay()
    }

    // --- Clear entry (CE) ---
    fun onClearEntry() {
        if (errorState) {
            clearAll()
            return
        }

        currentEntry = "0"
        justPressedEquals = false
        notifyDisplay()
    }

    // --- Clear all (C) ---
    fun onClear() {
        clearAll()
        notifyDisplay()
    }

    // --- Operator (+, -, x, /) ---
    fun onOperator(op: Char) {
        if (errorState) {
            clearAll()
            return
        }

        if (justPressedEquals) {
            // Continue from result
            justPressedEquals = false
            expression = formatNumber(accumulator) + " " + operatorSymbol(op)
            pendingOperator = op
            currentEntry = formatNumber(accumulator)
            justPressedOperator = true
            notifyDisplay()
            return
        }

        if (justPressedOperator) {
            // Replace operator
            pendingOperator = op
            expression = if (expression.isNotEmpty()) {
                expression.dropLast(1) + operatorSymbol(op)
            } else {
                formatNumber(accumulator) + " " + operatorSymbol(op)
            }
            notifyDisplay()
            return
        }

        val currentValue = parseCurrentEntry()

        if (hasAccumulator && pendingOperator != null) {
            // Evaluate pending operation
            val result = evaluate(accumulator, pendingOperator!!, currentValue)
            if (result == null) {
                setError("Cannot divide by zero")
                return
            }
            accumulator = result
            expression = formatNumber(accumulator) + " " + operatorSymbol(op)
            currentEntry = formatNumber(accumulator)
        } else {
            accumulator = currentValue
            hasAccumulator = true
            expression = formatNumber(currentValue) + " " + operatorSymbol(op)
        }

        pendingOperator = op
        justPressedOperator = true
        notifyDisplay()
    }

    // --- Equals ---
    fun onEquals() {
        if (errorState) {
            clearAll()
            return
        }

        if (justPressedEquals && lastEqualsOperator != null) {
            // Repeat last operation
            val result = evaluate(accumulator, lastEqualsOperator!!, lastEqualsOperand)
            if (result == null) {
                setError("Cannot divide by zero")
                return
            }
            expression = formatNumber(accumulator) + " " + operatorSymbol(lastEqualsOperator!!) + " " + formatNumber(lastEqualsOperand) + " ="
            accumulator = result
            currentEntry = formatNumber(accumulator)
            justPressedEquals = true
            notifyDisplay()
            return
        }

        val currentValue = parseCurrentEntry()

        if (pendingOperator != null && hasAccumulator) {
            lastEqualsOperator = pendingOperator
            lastEqualsOperand = currentValue
            val result = evaluate(accumulator, pendingOperator!!, currentValue)
            if (result == null) {
                setError("Cannot divide by zero")
                return
            }
            expression = formatNumber(accumulator) + " " + operatorSymbol(pendingOperator!!) + " " + formatNumber(currentValue) + " ="
            accumulator = result
            currentEntry = formatNumber(accumulator)
        } else {
            accumulator = currentValue
            hasAccumulator = true
            expression = formatNumber(currentValue) + " ="
        }

        pendingOperator = null
        justPressedEquals = true
        justPressedOperator = false
        notifyDisplay()
    }

    // --- Percentage ---
    fun onPercent() {
        if (errorState) return

        val currentValue = parseCurrentEntry()
        val result = if (hasAccumulator && pendingOperator != null) {
            // Percentage of accumulator (e.g., 200 + 10% => 200 + 20)
            accumulator * (currentValue / 100.0)
        } else {
            currentValue / 100.0
        }

        currentEntry = formatNumber(result)
        if (justPressedEquals) {
            justPressedEquals = false
        }
        justPressedOperator = false
        notifyDisplay()
    }

    // --- Reciprocal (1/x) ---
    fun onReciprocal() {
        if (errorState) return

        val currentValue = parseCurrentEntry()
        if (currentValue == 0.0) {
            setError("Cannot divide by zero")
            return
        }

        val result = 1.0 / currentValue
        val displayEntry = formatNumber(currentValue)
        currentEntry = formatNumber(result)

        // Update expression to show 1/(x)
        if (justPressedEquals) {
            expression = "1/($displayEntry)"
            accumulator = result
            justPressedEquals = false
        } else if (justPressedOperator) {
            currentEntry = formatNumber(result)
        } else {
            // Insert into expression inline
            expression = if (expression.isNotEmpty() && pendingOperator != null) {
                expression + " 1/($displayEntry)"
            } else {
                "1/($displayEntry)"
            }
        }

        justPressedOperator = false
        notifyDisplay()
    }

    // --- Square (x^2) ---
    fun onSquare() {
        if (errorState) return

        val currentValue = parseCurrentEntry()
        val result = currentValue * currentValue
        val displayEntry = formatNumber(currentValue)

        if (result.isInfinite() || result.isNaN()) {
            setError("Overflow")
            return
        }

        currentEntry = formatNumber(result)

        if (justPressedEquals) {
            expression = "sqr($displayEntry)"
            accumulator = result
            justPressedEquals = false
        } else if (justPressedOperator) {
            currentEntry = formatNumber(result)
        } else {
            expression = if (expression.isNotEmpty() && pendingOperator != null) {
                expression + " sqr($displayEntry)"
            } else {
                "sqr($displayEntry)"
            }
        }

        justPressedOperator = false
        notifyDisplay()
    }

    // --- Square root ---
    fun onSquareRoot() {
        if (errorState) return

        val currentValue = parseCurrentEntry()
        if (currentValue < 0.0) {
            setError("Invalid input")
            return
        }

        val result = sqrt(currentValue)
        val displayEntry = formatNumber(currentValue)
        currentEntry = formatNumber(result)

        if (justPressedEquals) {
            expression = "\u221A($displayEntry)"
            accumulator = result
            justPressedEquals = false
        } else if (justPressedOperator) {
            currentEntry = formatNumber(result)
        } else {
            expression = if (expression.isNotEmpty() && pendingOperator != null) {
                expression + " \u221A($displayEntry)"
            } else {
                "\u221A($displayEntry)"
            }
        }

        justPressedOperator = false
        notifyDisplay()
    }

    // --- Memory operations ---
    fun onMemoryClear() {
        memory = 0.0
    }

    fun onMemoryRecall() {
        if (errorState) clearAll()

        if (justPressedEquals) {
            justPressedEquals = false
        }

        currentEntry = formatNumber(memory)
        justPressedOperator = false
        notifyDisplay()
    }

    fun onMemoryAdd() {
        if (errorState) return
        memory += parseCurrentEntry()
    }

    fun onMemorySubtract() {
        if (errorState) return
        memory -= parseCurrentEntry()
    }

    fun onMemoryStore() {
        if (errorState) return
        memory = parseCurrentEntry()
    }

    // --- Internal helpers ---

    private fun clearAll() {
        currentEntry = "0"
        expression = ""
        pendingOperator = null
        accumulator = 0.0
        hasAccumulator = false
        lastEqualsOperator = null
        lastEqualsOperand = 0.0
        justPressedEquals = false
        justPressedOperator = false
        errorState = false
    }

    private fun setError(message: String) {
        currentEntry = message
        expression = ""
        pendingOperator = null
        hasAccumulator = false
        justPressedEquals = false
        justPressedOperator = false
        errorState = true
        notifyDisplay()
    }

    private fun evaluate(left: Double, op: Char, right: Double): Double? {
        val result = when (op) {
            '+' -> left + right
            '-', '\u2212' -> left - right
            '\u00D7', '*' -> left * right
            '\u00F7', '/' -> {
                if (right == 0.0) return null
                left / right
            }
            else -> left
        }
        if (result.isInfinite() || result.isNaN()) return null
        return result
    }

    private fun parseCurrentEntry(): Double {
        return currentEntry.toDoubleOrNull() ?: 0.0
    }

    private fun operatorSymbol(op: Char): Char {
        return when (op) {
            '+' -> '+'
            '-', '\u2212' -> '\u2212'
            '*', '\u00D7' -> '\u00D7'
            '/', '\u00F7' -> '\u00F7'
            else -> op
        }
    }

    private fun formatNumber(value: Double): String {
        if (value.isNaN()) return "NaN"
        if (value.isInfinite()) return "Overflow"

        // If the value is a whole number, show without decimal
        return if (value == value.toLong().toDouble() && !value.isInfinite() && value in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()) {
            value.toLong().toString()
        } else {
            // Show up to 10 decimal places, trimming trailing zeros
            val formatted = "%.10f".format(value).trimEnd('0').trimEnd('.')
            formatted
        }
    }

    private fun notifyDisplay() {
        onDisplayChanged?.invoke(expression, currentEntry)
    }
}
