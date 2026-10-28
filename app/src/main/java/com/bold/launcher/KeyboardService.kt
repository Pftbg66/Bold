package com.bold.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout

class KeyboardService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private var overlay: LinearLayout? = null
    private var shift = false
    private var numpad = false
    private val letterKeys = mutableListOf<Button>()

    private val keyColor = 0xFF34343A.toInt()
    private val callColor = 0xFF2E7D32.toInt()

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val screen = wm.maximumWindowMetrics.bounds
        val h = (screen.height() * Config.KEYBOARD_FRACTION).toInt()

        val lp = WindowManager.LayoutParams(
            MATCH_PARENT, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // NOT_FOCUSABLE: tapping keys must never steal focus from the app's text field
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        ).apply { gravity = Gravity.BOTTOM }

        overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1B1B1F.toInt())
            setPadding(8, 8, 8, 8)
        }
        render()
        wm.addView(overlay, lp)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Auto-switch to the number pad when a phone or number field gets focus
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED) return
        val node = event.source ?: return
        if (!node.isEditable) return
        val cls = node.inputType and InputType.TYPE_MASK_CLASS
        val wantNumpad = cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_NUMBER
        if (wantNumpad != numpad) { numpad = wantNumpad; render() }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        overlay?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        super.onDestroy()
    }

    // ---------- Keyboard UI ----------

    private fun render() {
        val root = overlay ?: return
        root.removeAllViews()
        letterKeys.clear()
        if (numpad) buildNumpad(root) else buildLetters(root)
    }

    private fun buildLetters(root: LinearLayout) {
        listOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm").forEach { chars ->
            val row = newRow(root)
            chars.forEach { c ->
                val b = key(c.toString(), 1f) { typeChar(c) }
                if (c.isLetter()) letterKeys += b
                row.addView(b)
            }
        }
        val bottom = newRow(root)
        bottom.addView(key("⇧", 1.2f) { shift = !shift; refreshLabels() })
        bottom.addView(key("123", 1.2f) { numpad = true; render() })
        bottom.addView(key("⌂", 1.1f) { goHome() })
        bottom.addView(key("◀", 1.1f) { performGlobalAction(GLOBAL_ACTION_BACK) })
        bottom.addView(key("space", 3f) { insert(" ") })
        bottom.addView(key(".", 1f) { insert(".") })
        bottom.addView(key("⌫", 1.3f) { backspace() })
        bottom.addView(key("⏎", 1.3f) { enter() })
    }

    private fun buildNumpad(root: LinearLayout) {
        listOf("123", "456", "789", "*0#").forEach { chars ->
            val row = newRow(root)
            chars.forEach { c -> row.addView(key(c.toString(), 1f, size = 30f) { insert(c.toString()) }) }
        }
        val bottom = newRow(root)
        bottom.addView(key("abc", 1.2f) { numpad = false; render() })
        bottom.addView(key("⌂", 1f) { goHome() })
        bottom.addView(key("◀", 1f) { performGlobalAction(GLOBAL_ACTION_BACK) })
        bottom.addView(key("+", 1f) { insert("+") })
        bottom.addView(key("⌫", 1.2f) { backspace() })
        bottom.addView(key("Call", 1.6f, color = callColor) { call() })
    }

    private fun newRow(parent: LinearLayout) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        parent.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
    }

    private fun key(
        label: String, weight: Float, color: Int = keyColor, size: Float = 20f, onTap: () -> Unit
    ) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = size
        setTextColor(Color.WHITE)
        setPadding(0, 0, 0, 0)
        background = GradientDrawable().apply { cornerRadius = 16f; setColor(color) }
        layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, weight).apply { setMargins(4, 4, 4, 4) }
        setOnClickListener { onTap() }
    }

    private fun refreshLabels() = letterKeys.forEach {
        it.text = if (shift) it.text.toString().uppercase() else it.text.toString().lowercase()
    }

    private fun typeChar(c: Char) {
        insert(if (shift && c.isLetter()) c.uppercase() else c.toString())
        if (shift) { shift = false; refreshLabels() }
    }

    private fun goHome() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        } catch (_: Exception) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    // ---------- Calling ----------

    /** Dials whatever number is in the focused field. */
    private fun call() {
        val number = focusedField()?.text?.toString().orEmpty().filter { it.isDigit() || it in "+*#" }
        if (number.isEmpty()) return
        val uri = Uri.fromParts("tel", number, null)
        try {
            startActivity(Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: SecurityException) {
            // No call permission: fall back to opening the dialer pre-filled
            startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // ---------- Text injection ----------

    private fun focusedField(): AccessibilityNodeInfo? =
        rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

    /** Rewrites the focused field's text. transform(text, selStart, selEnd) -> (newText, newCursor) */
    private fun edit(transform: (String, Int, Int) -> Pair<String, Int>) {
        val node = focusedField() ?: return
        val text = if (node.isShowingHintText) "" else node.text?.toString().orEmpty()
        var s = node.textSelectionStart; var e = node.textSelectionEnd
        if (s < 0 || e < 0) { s = text.length; e = text.length }
        val lo = minOf(s, e).coerceIn(0, text.length)
        val hi = maxOf(s, e).coerceIn(0, text.length)
        val (newText, cursor) = transform(text, lo, hi)

        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        })
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
        })
    }

    private fun insert(s: String) = edit { t, lo, hi -> t.substring(0, lo) + s + t.substring(hi) to lo + s.length }

    private fun backspace() = edit { t, lo, hi ->
        when {
            hi > lo -> t.substring(0, lo) + t.substring(hi) to lo
            lo > 0 -> t.substring(0, lo - 1) + t.substring(lo) to lo - 1
            else -> t to 0
        }
    }

    private fun enter() {
        val node = focusedField() ?: return
        val ok = node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        if (!ok) insert("\n") // multiline fields without an IME action
    }
}
