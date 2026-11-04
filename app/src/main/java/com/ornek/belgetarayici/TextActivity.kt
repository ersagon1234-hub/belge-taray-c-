package com.ornek.belgetarayici

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import java.io.File

/** OCR sonucunu gösterir; düzenlenebilir, kopyalanabilir, paylaşılabilir. */
class TextActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_PATH = "yol"
        private const val EXTRA_TITLE = "baslik"
        private const val EXTRA_PERSIST = "kaydet"

        fun intent(context: Context, title: String, file: File, persist: Boolean): Intent =
            Intent(context, TextActivity::class.java)
                .putExtra(EXTRA_PATH, file.absolutePath)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_PERSIST, persist)
    }

    private lateinit var input: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val file = intent.getStringExtra(EXTRA_PATH)?.let { File(it) }
        if (file == null || !file.exists()) {
            toast("Metin bulunamadı.")
            finish()
            return
        }
        val persist = intent.getBooleanExtra(EXTRA_PERSIST, false)

        input = EditText(this).apply {
            setText(file.readText())
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setHorizontallyScrolling(false)
            textSize = 16f
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = { LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
            addView(makeButton("Kopyala") { copyText() }, lp())
            addView(makeButton("Paylaş") { shareText() }, lp())
            if (persist) {
                addView(makeButton("Kaydet") { saveText(file) }, lp())
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(makeTitle(intent.getStringExtra(EXTRA_TITLE) ?: "Metin"))
            addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(buttons)
        }
        root.padForSystemBars(dp(16))
        setContentView(root)
    }

    private fun copyText() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("OCR metni", input.text.toString()))
        toast("Metin kopyalandı.")
    }

    private fun shareText() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, input.text.toString())
        }
        startActivity(Intent.createChooser(send, "Metni paylaş"))
    }

    private fun saveText(file: File) {
        try {
            file.writeText(input.text.toString())
            toast("Metin kaydedildi.")
        } catch (e: Exception) {
            toast("Kaydedilemedi: ${e.localizedMessage}")
        }
    }
}
