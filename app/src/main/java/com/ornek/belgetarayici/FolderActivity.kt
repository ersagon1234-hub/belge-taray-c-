package com.ornek.belgetarayici

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FolderActivity : BaseActivity() {
    companion object {
        const val EXTRA_FOLDER = "klasor"
    }

    private lateinit var folder: String
    private lateinit var list: ListView
    private lateinit var empty: TextView
    private var docs: List<Doc> = emptyList()
    private val dateFormat = SimpleDateFormat("d MMM yyyy HH:mm", Locale.forLanguageTag("tr-TR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra(EXTRA_FOLDER)
        if (name == null) {
            finish()
            return
        }
        folder = name

        list = ListView(this)
        list.setOnItemClickListener { _, _, position, _ ->
            docs.getOrNull(position)?.let { showActions(it) }
        }
        empty = TextView(this).apply {
            text = "Bu klasörde belge yok.\n“Bu klasöre tara” ile ekleyebilirsiniz."
            textSize = 16f
            setPadding(0, dp(24), 0, 0)
        }
        val listArea = FrameLayout(this).apply {
            addView(list)
            addView(empty)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(makeTitle(folder))
            addView(makeButton("Bu klasöre tara") { startScan(folder) })
            addView(listArea, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        root.padForSystemBars(dp(16))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (::folder.isInitialized) refresh()
    }

    override fun refresh() {
        docs = archive.documents(folder)
        val labels = docs.map { d ->
            val ocr = if (archive.hasOcr(d)) " · metin var" else ""
            "📄  ${d.name}\n${dateFormat.format(Date(d.createdAt))} · ${d.pageCount} sayfa$ocr"
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        empty.visibility = if (docs.isEmpty()) View.VISIBLE else View.GONE
    }

    // ---------- Belge işlemleri ----------

    private fun showActions(doc: Doc) {
        val items = arrayOf(
            "Aç",
            "Başka uygulamada aç",
            "Paylaş",
            "Metne çevir (OCR)",
            "Başka klasöre taşı",
            "Yeniden adlandır",
            "Sil"
        )
        AlertDialog.Builder(this)
            .setTitle(doc.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openInApp(doc)
                    1 -> openExternal(doc)
                    2 -> share(doc)
                    3 -> showOcr(doc)
                    4 -> moveDialog(doc)
                    5 -> promptText("Yeniden adlandır", doc.name) { newName ->
                        if (!archive.renameDoc(doc, newName)) toast("Ad değiştirilemedi.")
                        refresh()
                    }
                    6 -> confirmDelete(doc)
                }
            }
            .show()
    }

    private fun openInApp(doc: Doc) {
        if (!doc.pdf.exists()) {
            toast("PDF dosyası bulunamadı.")
            return
        }
        startActivity(
            Intent(this, PdfViewerActivity::class.java)
                .putExtra(PdfViewerActivity.EXTRA_PATH, doc.pdf.absolutePath)
                .putExtra(PdfViewerActivity.EXTRA_TITLE, doc.name)
        )
    }

    private fun withShareCopy(doc: Doc, action: (Uri) -> Unit) {
        io.execute {
            try {
                val uri = archive.uriFor(archive.shareCopy(doc))
                runOnUiThread { action(uri) }
            } catch (e: Exception) {
                runOnUiThread { toast("PDF hazırlanamadı: ${e.localizedMessage}") }
            }
        }
    }

    private fun openExternal(doc: Doc) {
        withShareCopy(doc) { uri ->
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                clipData = ClipData.newRawUri(doc.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(view)
            } catch (e: ActivityNotFoundException) {
                toast("PDF görüntüleyici uygulama bulunamadı. “Aç” ile uygulama içinde görüntüleyin.")
            }
        }
    }

    private fun share(doc: Doc) {
        withShareCopy(doc) { uri ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, doc.name)
                clipData = ClipData.newRawUri(doc.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "PDF paylaş"))
        }
    }

    private fun moveDialog(doc: Doc) {
        val targets = archive.folders().map { it.name }.filter { it != doc.folder }
        if (targets.isEmpty()) {
            toast("Taşınacak başka klasör yok. Önce yeni klasör oluşturun.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Hangi klasöre taşınsın?")
            .setItems(targets.toTypedArray()) { _, which ->
                if (!archive.moveDoc(doc, targets[which])) toast("Belge taşınamadı.")
                refresh()
            }
            .show()
    }

    private fun confirmDelete(doc: Doc) {
        AlertDialog.Builder(this)
            .setTitle("Belge silinsin mi?")
            .setMessage("“${doc.name}” kalıcı olarak silinecek. Bu işlem geri alınamaz.")
            .setPositiveButton("Sil") { _, _ ->
                if (!archive.deleteDoc(doc)) toast("Belge silinemedi.")
                refresh()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    // ---------- OCR ----------

    private fun showOcr(doc: Doc) {
        if (archive.hasOcr(doc)) {
            openText(doc)
        } else {
            runOcr(doc)
        }
    }

    private fun openText(doc: Doc) {
        startActivity(TextActivity.intent(this, doc.name, doc.ocrFile, true))
    }

    private fun runOcr(doc: Doc) {
        val pages = doc.pageImages
        if (pages.isEmpty()) {
            toast("Bu belgede OCR için sayfa görüntüsü yok.")
            return
        }
        showBusy("Metin okunuyor… (1/${pages.size})")
        io.execute {
            try {
                val sb = StringBuilder()
                pages.forEachIndexed { i, page ->
                    runOnUiThread { updateBusy("Metin okunuyor… (${i + 1}/${pages.size})") }
                    val text = Ocr.recognizeFile(page).trim()
                    if (text.isNotEmpty()) {
                        if (sb.isNotEmpty()) sb.append("\n\n")
                        sb.append(text)
                    }
                }
                if (sb.isNotEmpty()) archive.saveOcr(doc, sb.toString())
                runOnUiThread {
                    hideBusy()
                    if (sb.isEmpty()) {
                        toast("Belgede metin bulunamadı.")
                    } else {
                        refresh()
                        openText(doc)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    hideBusy()
                    toast("Metin okunamadı: ${e.localizedMessage}")
                }
            }
        }
    }
}
