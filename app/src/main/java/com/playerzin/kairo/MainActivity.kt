package com.playerzin.kairo

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0B0B1A.toInt())
            setPadding(24, 90, 24, 24)
        }
        val log = TextView(this).apply {
            setTextColor(0xFFE0E0FF.toInt()); textSize = 16f
            text = "Kairo pronta. Fale comigo!\n"
        }
        val scroll = ScrollView(this).apply { addView(log) }
        val input = EditText(this).apply {
            hint = "Mensagem..."
            setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF8888AA.toInt())
        }
        val send = Button(this).apply { text = "Enviar" }
        send.setOnClickListener {
            val t = input.text.toString().trim()
            if (t.isNotEmpty()) {
                log.append("\nVocê: $t\n${Native.responder(t)}\n")
                input.setText("")
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }
        }
        val mp = LinearLayout.LayoutParams.MATCH_PARENT
        root.addView(scroll, LinearLayout.LayoutParams(mp, 0, 1f))
        root.addView(input, LinearLayout.LayoutParams(mp, -2))
        root.addView(send, LinearLayout.LayoutParams(mp, -2))
        setContentView(root)
    }
}
