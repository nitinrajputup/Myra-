package com.myra.ai.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Only acts when MYRA explicitly calls it; it never reacts to events on its own. */
class MyraAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: MyraAccessibilityService? = null
    }

    override fun onServiceConnected() {
        instance = this
        Automation.log("Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    private fun findScrollable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.isScrollable) return n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            findScrollable(c)?.let { return it }
        }
        return null
    }

    fun scroll(forward: Boolean): Boolean {
        val r = rootInActiveWindow
        val node = r?.let { findScrollable(it) }
        if (node != null) {
            val ok = node.performAction(
                if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            )
            if (ok) return true
        }
        val m = resources.displayMetrics
        val x = m.widthPixels / 2f
        val (y1, y2) = if (forward) m.heightPixels * 0.7f to m.heightPixels * 0.3f
        else m.heightPixels * 0.3f to m.heightPixels * 0.7f
        return swipe(x, y1, x, y2, 350)
    }

    fun clickText(text: String): Boolean {
        val r = rootInActiveWindow ?: return false
        for (n in r.findAccessibilityNodeInfosByText(text)) {
            var cur: AccessibilityNodeInfo? = n
            while (cur != null) {
                if (cur.isClickable) return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                cur = cur.parent
            }
        }
        return false
    }

    fun typeText(text: String): Boolean {
        val f = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        if (f.isPassword) return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun tap(x: Float, y: Float): Boolean = gesture(Path().apply { moveTo(x, y) }, 60)

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, dur: Long): Boolean =
        gesture(Path().apply { moveTo(x1, y1); lineTo(x2, y2) }, dur)

    private fun gesture(p: Path, dur: Long): Boolean {
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, dur)).build()
        return dispatchGesture(g, null, null)
    }

    fun back() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home() = performGlobalAction(GLOBAL_ACTION_HOME)

    /** Visible text on screen. Password fields are skipped. */
    fun screenText(): String {
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (depth > 40 || sb.length > 4000) return
            if (!n.isPassword) {
                val t = n.text ?: n.contentDescription
                if (!t.isNullOrBlank()) sb.append(t).append('\n')
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        rootInActiveWindow?.let { walk(it, 0) }
        return sb.toString().trim()
    }

    fun clickables(): List<String> {
        val out = mutableListOf<String>()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (depth > 40 || out.size > 60) return
            if (n.isClickable && !n.isPassword) {
                val t = (n.text ?: n.contentDescription)?.toString()
                if (!t.isNullOrBlank()) out.add(t)
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        rootInActiveWindow?.let { walk(it, 0) }
        return out.distinct()
    }
}
