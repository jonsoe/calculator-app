package com.example.calculator

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.calculator.databinding.ActivityMainBinding
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Calculator state
    private var currentInput = StringBuilder()
    private var firstOperand: BigDecimal? = null
    private var pendingOperator: String? = null
    private var justCalculated = false
    private var lastExpression = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtonListeners()
        updateDisplay("0", "")
    }

    // ── Button wiring ──────────────────────────────────────────

    private fun setupButtonListeners() {
        // Digit buttons
        val digitButtons = mapOf(
            binding.btn0 to "0", binding.btn1 to "1", binding.btn2 to "2",
            binding.btn3 to "3", binding.btn4 to "4", binding.btn5 to "5",
            binding.btn6 to "6", binding.btn7 to "7", binding.btn8 to "8",
            binding.btn9 to "9"
        )
        digitButtons.forEach { (btn, digit) ->
            btn.setOnClickListener { animateButton(it); onDigit(digit) }
        }

        // Decimal
        binding.btnDecimal.setOnClickListener { animateButton(it); onDecimal() }

        // Operators
        binding.btnPlus.setOnClickListener    { animateButton(it); onOperator("+") }
        binding.btnMinus.setOnClickListener   { animateButton(it); onOperator("-") }
        binding.btnMultiply.setOnClickListener{ animateButton(it); onOperator("×") }
        binding.btnDivide.setOnClickListener  { animateButton(it); onOperator("÷") }
        binding.btnPercent.setOnClickListener { animateButton(it); onPercent() }

        // Actions
        binding.btnEquals.setOnClickListener  { animateButton(it); onEquals() }
        binding.btnClear.setOnClickListener   { animateButton(it); onClear() }
        binding.btnDelete.setOnClickListener  { animateButton(it); onDelete() }
        binding.btnPlusMinus.setOnClickListener { animateButton(it); onPlusMinus() }
    }

    // ── Input handlers ────────────────────────────────────────

    private fun onDigit(digit: String) {
        if (justCalculated) {
            currentInput.clear()
            justCalculated = false
        }
        // Limit to 12 digits
        if (currentInput.length >= 12) return
        if (digit == "0" && currentInput.toString() == "0") return

        if (currentInput.toString() == "0") currentInput.clear()
        currentInput.append(digit)
        updateDisplay(formatNumber(currentInput.toString()), buildExpression())
    }

    private fun onDecimal() {
        if (justCalculated) { currentInput.clear(); currentInput.append("0"); justCalculated = false }
        if (!currentInput.contains(".")) {
            if (currentInput.isEmpty()) currentInput.append("0")
            currentInput.append(".")
        }
        updateDisplay(currentInput.toString(), buildExpression())
    }

    private fun onOperator(op: String) {
        if (currentInput.isEmpty() && firstOperand == null) return

        // Chain calculation: 5 + 3 × → calculate 5+3 first, then ×
        if (firstOperand != null && currentInput.isNotEmpty() && !justCalculated) {
            calculate()
        }

        if (currentInput.isNotEmpty()) {
            firstOperand = parseSafe(currentInput.toString())
        }

        pendingOperator = op
        lastExpression = "${formatResult(firstOperand!!)} $op"
        currentInput.clear()
        justCalculated = false

        updateDisplay(formatResult(firstOperand!!), lastExpression)
    }

    private fun onEquals() {
        if (firstOperand == null || currentInput.isEmpty()) return
        lastExpression = buildExpression() + " ="
        calculate()
        justCalculated = true
        updateDisplay(formatResult(parseSafe(currentInput.toString())), lastExpression)
    }

    private fun onPercent() {
        if (currentInput.isEmpty()) return
        val value = parseSafe(currentInput.toString())
        val result = if (firstOperand != null) {
            // 200 + 15% → 200 + (200 × 0.15)
            firstOperand!! * (value / BigDecimal("100"))
        } else {
            value / BigDecimal("100")
        }
        currentInput.clear()
        currentInput.append(formatResult(result))
        updateDisplay(formatResult(result), buildExpression())
    }

    private fun onPlusMinus() {
        if (currentInput.isEmpty()) return
        val value = parseSafe(currentInput.toString())
        if (value.compareTo(BigDecimal.ZERO) == 0) return
        val negated = value.negate()
        currentInput.clear()
        currentInput.append(formatResult(negated))
        updateDisplay(formatResult(negated), buildExpression())
    }

    private fun onClear() {
        currentInput.clear()
        firstOperand = null
        pendingOperator = null
        justCalculated = false
        lastExpression = ""
        updateDisplay("0", "")
        binding.btnClear.text = "AC"
    }

    private fun onDelete() {
        if (justCalculated) { onClear(); return }
        if (currentInput.isNotEmpty()) {
            currentInput.deleteCharAt(currentInput.length - 1)
            val display = if (currentInput.isEmpty()) "0" else currentInput.toString()
            updateDisplay(display, buildExpression())
        }
    }

    // ── Calculation engine ─────────────────────────────────────

    private fun calculate() {
        val second = parseSafe(currentInput.toString())
        val first  = firstOperand ?: return

        val result = try {
            when (pendingOperator) {
                "+"  -> first + second
                "-"  -> first - second
                "×"  -> first * second
                "÷"  -> if (second.compareTo(BigDecimal.ZERO) == 0) null
                         else first.divide(second, MathContext(10, RoundingMode.HALF_UP))
                else -> second
            }
        } catch (e: ArithmeticException) { null }

        if (result == null) {
            showError("Can't divide by zero")
            return
        }

        currentInput.clear()
        currentInput.append(formatResult(result))
        firstOperand = result
        pendingOperator = null
    }

    // ── Helpers ───────────────────────────────────────────────

    private fun parseSafe(s: String): BigDecimal =
        try { BigDecimal(s.replace(",", "")) } catch (e: Exception) { BigDecimal.ZERO }

    private fun formatResult(value: BigDecimal): String {
        val stripped = value.stripTrailingZeros()
        return if (stripped.scale() <= 0) {
            stripped.toBigInteger().toString()
        } else {
            // Max 8 decimal places
            value.setScale(8, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()
        }
    }

    private fun formatNumber(s: String): String = s  // keep raw while typing

    private fun buildExpression(): String {
        val op = pendingOperator ?: return ""
        val first = firstOperand ?: return ""
        return if (currentInput.isEmpty()) {
            "${formatResult(first)} $op"
        } else {
            "${formatResult(first)} $op ${currentInput}"
        }
    }

    private fun showError(msg: String) {
        updateDisplay(msg, "")
        currentInput.clear()
        firstOperand = null
        pendingOperator = null
        justCalculated = true
    }

    // ── Display ───────────────────────────────────────────────

    private fun updateDisplay(main: String, expression: String) {
        binding.tvExpression.text = expression
        binding.tvResult.text = main

        // Auto-shrink large numbers
        binding.tvResult.textSize = when {
            main.length > 12 -> 32f
            main.length > 9  -> 42f
            main.length > 6  -> 52f
            else             -> 64f
        }

        // Toggle AC / C
        binding.btnClear.text = if (currentInput.isNotEmpty() && !justCalculated) "C" else "AC"
    }

    // ── Button press animation ────────────────────────────────

    private fun animateButton(view: View) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.88f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.88f, 1f)
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 120
            interpolator = OvershootInterpolator()
            start()
        }
    }
}
