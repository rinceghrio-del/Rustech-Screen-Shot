package com.rustech.textgrabber

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }
        val info = TextView(this).apply {
            textSize = 16f
            text = "1. Pindutin ang button sa baba, hanapin ang TextGrabber, i-ON.\n" +
                    "2. Lalabas ang floating bilog (drag para ilipat).\n" +
                    "3. Pindutin ang bilog, tapos tap ang kahit anong box para i-copy.\n"
        }
        val btn = Button(this).apply {
            text = "Buksan ang Accessibility Settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val label = TextView(this).apply {
            textSize = 16f
            setPadding(0, 48, 0, 0)
        }
        val seek = SeekBar(this).apply {
            max = 80 // 20%..100%
            progress = prefs.getInt("opacity", 70) - 20
        }
        label.text = "Opacity ng bilog: ${seek.progress + 20}%"
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                label.text = "Opacity ng bilog: ${p + 20}%"
                prefs.edit().putInt("opacity", p + 20).apply()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        root.addView(info); root.addView(btn); root.addView(label); root.addView(seek)
        setContentView(root)
    }
}
