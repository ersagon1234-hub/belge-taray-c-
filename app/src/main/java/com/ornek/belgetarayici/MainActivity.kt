package com.ornek.belgetarayici

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.content.Intent
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File

class MainActivity : BaseActivity() {
    private lateinit var list: ListView
    private var folders: List<FolderInfo> = emptyList()
    private var cameraUri: Uri? = null

    private val pickPhoto =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) ocrPhoto(uri)
        }

    private val takePhoto =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
            val uri = cameraUri
            if (saved && uri != null) ocrPhoto(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        list = ListView(this)
        list.setOnItemClickListener { _, _, position, _ ->
            val folder = folders.getOrNull(position) ?: return@setOnItemClickListener
            startActivity(
                Intent(this, FolderActivity::class.java)
                    .putExtra(FolderActivity.EXTRA_FOLDER, folder.name)
            )
        }
        list.setOnItemLongClickListener { _, _, position, _ ->
            folders.getOrNull(position)?.let { showFolderOptions(it) }
            true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(makeTitle("Belge Tarayıcı"))
            addView(makeButton("Belge Tara") { startScan(null) })
            addView(makeButton("Fotoğraftan Metin (OCR)") { showPhotoSourceDialog() })
            addView(TextView(context).apply {
                text = "Klasörler (uzun basarak yeniden adlandır / sil)"
                textSize = 14f
                setPadding(0, dp(16), 0, dp(4))
            })
            addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(makeButton("+ Yeni Klasör") { newFolderDialog() })
        }
        root.padForSystemBars(dp(16))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun refresh() {
        folders = archive.folders()
        val labels = folders
            .map { "📁  ${it.name}   (${it.docCount})" }
            .ifEmpty { listOf("Henüz klasör yok. Aşağıdan yeni klasör ekleyin.") }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
    }

    // ---------- Klasörler ----------

    private fun newFolderDialog() {
        promptText("Yeni klasör", "") { name ->
            archive.createFolder(name)?.let { toast(it) }
            refresh()
        }
    }

    private fun showFolderOptions(folder: FolderInfo) {
        AlertDialog.Builder(this)
            .setTitle(folder.name)
            .setItems(arrayOf("Yeniden adlandır", "Sil")) { _, which ->
                when (which) {
                    0 -> promptText("Klasörü yeniden adlandır", folder.name) { newName ->
                        archive.renameFolder(folder.name, newName)?.let { toast(it) }
                        refresh()
                    }
                    1 -> confirmDeleteFolder(folder)
                }
            }
            .show()
    }

    private fun confirmDeleteFolder(folder: FolderInfo) {
        AlertDialog.Builder(this)
            .setTitle("Klasör silinsin mi?")
            .setMessage("“${folder.name}” klasörü ve içindeki ${folder.docCount} belge kalıcı olarak silinecek. Bu işlem geri alınamaz.")
            .setPositiveButton("Sil") { _, _ ->
                archive.deleteFolder(folder.name)
                refresh()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    // ---------- Fotoğraftan metin ----------

    private fun showPhotoSourceDialog() {
        AlertDialog.Builder(this)
            .setTitle("Fotoğraftan metin")
            .setItems(arrayOf("Galeriden seç", "Fotoğraf çek")) { _, which ->
                if (which == 0) pickPhoto.launch("image/*") else launchCamera()
            }
            .show()
    }

    private fun launchCamera() {
        val dir = File(cacheDir, "kamera").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val uri = archive.uriFor(File(dir, "foto.jpg"))
        cameraUri = uri
        try {
            takePhoto.launch(uri)
        } catch (e: ActivityNotFoundException) {
            toast("Kamera uygulaması bulunamadı.")
        }
    }

    private fun ocrPhoto(uri: Uri) {
        showBusy("Metin okunuyor…")
        io.execute {
            try {
                val text = Ocr.recognizeUri(this, uri).trim()
                val out = File(cacheDir, "ocr_gecici.txt")
                if (text.isNotEmpty()) out.writeText(text)
                runOnUiThread {
                    hideBusy()
                    if (text.isEmpty()) {
                        toast("Fotoğrafta metin bulunamadı.")
                    } else {
                        startActivity(TextActivity.intent(this, "Fotoğraftan metin", out, false))
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
