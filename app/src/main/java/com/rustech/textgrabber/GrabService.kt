package com.rustech.textgrabber

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.SharedPreferences
import android.graphics.Color
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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class GrabService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private var bubble: View? = null
    private var panel: View? = null
    private var picker: View? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "opacity") bubble?.alpha = opacity()
    }

    private fun opacity() = prefs.getInt("opacity", 70) / 100f
    private fun px(v: Int) = (v * resources.displayMetrics.density).toInt()

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
        closePicker()
        closePanel()
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    // ---------- Floating bilog ----------
    private fun showBubble() {
        val size = px(56)
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
        if (panel != null) { closePicker(); closePanel(); return }
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
            setOnClickListener { closePicker(); closePanel() }
        }
        for ((r, t) in items) {
            val box = View(this).apply {
                background = GradientDrawable().apply {
                    setColor(0x3300A8FF)
                    setStroke(3, 0xFF00A8FF.toInt())
                }
                // Dati: kinokopya agad. Ngayon: bubukas ang word picker.
                setOnClickListener { showPicker(t) }
            }
            p.addView(box, FrameLayout.LayoutParams(r.width(), r.height()).apply {
                leftMargin = r.left; topMargin = r.top
            })
        }
        val hint = TextView(this).apply {
            text = "Pindutin ang box para pumili ng salita"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(px(14), px(8), px(14), px(8))
            background = GradientDrawable().apply {
                setColor(0xCC000000.toInt())
                cornerRadius = px(16).toFloat()
            }
        }
        p.addView(hint, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = px(80) })

        val lp = LP(
            LP.MATCH_PARENT, LP.MATCH_PARENT, LP.TYPE_ACCESSIBILITY_OVERLAY,
            LP.FLAG_LAYOUT_IN_SCREEN or LP.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.layoutInDisplayCutoutMode = LP.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        wm.addView(p, lp)
        panel = p
    }

    // ---------- Word picker: ikaw ang pipili ng salita ----------
    private fun chipBg(selected: Boolean) = GradientDrawable().apply {
        cornerRadius = px(8).toFloat()
        setColor(if (selected) 0xFF0A84FF.toInt() else 0xFFE5E5EA.toInt())
    }

    private fun showPicker(text: String) {
        closePicker()
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return
        val selected = BooleanArray(words.size)
        val chips = ArrayList<TextView>()

        fun paint(i: Int) {
            chips[i].background = chipBg(selected[i])
            chips[i].setTextColor(if (selected[i]) Color.WHITE else Color.BLACK)
        }

        val flow = FlowLayout(this, px(6)).apply { setPadding(px(4), px(4), px(4), px(4)) }
        words.forEachIndexed { i, w ->
            val chip = TextView(this).apply {
                this.text = w
                textSize = 17f
                setPadding(px(12), px(8), px(12), px(8))
                setOnClickListener { selected[i] = !selected[i]; paint(i) }
            }
            chips.add(chip)
            flow.addView(chip)
            paint(i)
        }

        val scroll = ScrollView(this).apply { addView(flow) }

        val title = TextView(this).apply {
            this.text = "Tap ang mga salitang gusto mong i-copy"
            textSize = 15f
            setTextColor(0xFF3A3A3C.toInt())
            setPadding(px(4), 0, px(4), px(8))
        }

        val btnAll = Button(this).apply {
            this.text = "Lahat"
            setOnClickListener {
                for (i in selected.indices) { selected[i] = true; paint(i) }
            }
        }
        val btnClear = Button(this).apply {
            this.text = "Clear"
            setOnClickListener {
                for (i in selected.indices) { selected[i] = false; paint(i) }
            }
        }
        val btnCopy = Button(this).apply {
            this.text = "COPY"
            setOnClickListener {
                val out = words.filterIndexed { i, _ -> selected[i] }.joinToString(" ")
                if (out.isEmpty()) toast("Pumili muna ng salita") else copy(out)
            }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val w = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(btnAll, w); addView(btnClear, w); addView(btnCopy, w)
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(16), px(16), px(8))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = px(20).toFloat()
            }
            isClickable = true // para hindi magsara kapag pinindot ang loob ng card
            addView(title)
            addView(scroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(row)
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(0x88000000.toInt())
            setOnClickListener { closePicker() }
        }
        val maxH = (resources.displayMetrics.heightPixels * 0.65f).toInt()
        root.addView(card, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, maxH, Gravity.CENTER
        ).apply { leftMargin = px(16); rightMargin = px(16) })

        val lp = LP(
            LP.MATCH_PARENT, LP.MATCH_PARENT, LP.TYPE_ACCESSIBILITY_OVERLAY,
            LP.FLAG_LAYOUT_IN_SCREEN or LP.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.layoutInDisplayCutoutMode = LP.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        wm.addView(root, lp)
        picker = root
    }

    private fun closePicker() {
        picker?.let { runCatching { wm.removeView(it) } }
        picker = null
    }

    private fun closePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
    }

    private fun copy(text: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("grab", text))
        toast("Na-copy: " + text.take(40))
        closePicker()
        closePanel()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
