package com.ornek.belgetarayici

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

fun Context.makeButton(label: String, onClick: () -> Unit): Button =
    Button(this).apply {
        text = label
        setAllCaps(false)
        textSize = 16f
        setOnClickListener { onClick() }
    }

fun Context.makeTitle(label: String): TextView =
    TextView(this).apply {
        text = label
        textSize = 22f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(12))
    }

/** Android 15'te uygulama sistem çubuklarının altına çizilir; içeriği çubukların dışında tutar. */
fun View.padForSystemBars(extra: Int) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
        )
        v.setPadding(bars.left + extra, bars.top + extra, bars.right + extra, bars.bottom + extra)
        insets
    }
}
