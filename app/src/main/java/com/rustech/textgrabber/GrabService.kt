package com.rustech.textgrabber

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import kotlin.math.abs

class GrabService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private var bubble: View? = null
    private var panel: View? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "opacity") bubble?.alpha = opacity()
    }

    private fun opacity() = prefs.getInt("opacity", 70) / 100f

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        showBubble()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        closePanel()
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    // ---------- Floating bilog ----------
    private fun showBubble() {
        val size = (56 * resources.displayMetrics.density).toInt()
        val v = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF3A3A3C.toInt())
                setStroke(6, 0xFFFFFFFF.toInt())
            }
            alpha = opacity()
        }
        val lp = LP(
            size, size, LP.TYPE_ACCESSIBILITY_OVERLAY,
            LP.FLAG_NOT_FOCUSABLE or LP.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 300 }

        var startX = 0; var startY = 0
        var touchX = 0f; var touchY = 0f
        var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y
                    touchX = e.rawX; touchY = e.rawY
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - touchX).toInt()
                    val dy = (e.rawY - touchY).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    if (moved) {
                        lp.x = startX + dx; lp.y = startY + dy
                        wm.updateViewLayout(v, lp)
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved) grab()
            }
            true
        }
        wm.addView(v, lp)
        bubble = v
    }

    // ---------- Basahin ang screen ----------
    private fun grab() {
        if (panel != null) { closePanel(); return }
        val root = rootInActiveWindow
        if (root == null) { toast("Walang mabasang screen"); return }

        val items = ArrayList<Pair<Rect, String>>()
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null || !n.isVisibleToUser) return
            val t = n.text?.toString() ?: n.contentDescription?.toString()
            if (!t.isNullOrBlank()) {
                val r = Rect()
                n.getBoundsInScreen(r)
                if (r.width() > 0 && r.height() > 0) items.add(r to t.trim())
            }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)

        if (items.isEmpty()) { toast("Walang nakitang text (baka image o protected ang screen)"); return }
        showPanel(items)
    }

    // ---------- Overlay ng mga text box ----------
    private fun showPanel(items: List<Pair<Rect, String>>) {
        val p = FrameLayout(this).apply {
            setBackgroundColor(0x44000000)
            setOnClickListener { closePanel() }
        }
        for ((r, t) in items) {
            val box = View(this).apply {
                background = GradientDrawable().apply {
                    setColor(0x3300A8FF)
                    setStroke(3, 0xFF00A8FF.toInt())
                }
                setOnClickListener { copy(t) }
            }
            p.addView(box, FrameLayout.LayoutParams(r.width(), r.height()).apply {
                leftMargin = r.left; topMargin = r.top
            })
        }
        val all = Button(this).apply {
            text = "COPY LAHAT"
            setOnClickListener { copy(items.joinToString("\n") { it.second }) }
        }
        p.addView(all, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = 160 })

        val lp = LP(
            LP.MATCH_PARENT, LP.MATCH_PARENT, LP.TYPE_ACCESSIBILITY_OVERLAY,
            LP.FLAG_LAYOUT_IN_SCREEN or LP.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.layoutInDisplayCutoutMode = LP.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        wm.addView(p, lp)
        panel = p
    }

    private fun closePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
    }

    private fun copy(text: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("grab", text))
        toast("Na-copy: " + text.take(40))
        closePanel()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
