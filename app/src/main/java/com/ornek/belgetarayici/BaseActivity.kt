package com.ornek.belgetarayici

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Tarama akışını ve ortak yardımcıları (iletişim kutuları, arka plan iş parçacığı) barındırır. */
abstract class BaseActivity : ComponentActivity() {
    protected lateinit var archive: Archive
    protected val io: ExecutorService = Executors.newSingleThreadExecutor()

    private var scanTargetFolder: String? = null
    private var busyDialog: AlertDialog? = null

    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            onScanResult(result.resultCode, result.data)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        archive = Archive(this)
    }

    override fun onDestroy() {
        hideBusy()
        io.shutdownNow()
        super.onDestroy()
    }

    /** Arşivde değişiklik olunca liste ekranlarını yenilemek için. */
    protected open fun refresh() {}

    // ---------- Tarama ----------

    protected fun startScan(folder: String?) {
        scanTargetFolder = folder
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(50)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                scanLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { error ->
                toast("Tarayıcı açılamadı: ${error.localizedMessage}")
            }
    }

    private fun onScanResult(resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK) {
            toast("Tarama iptal edildi.")
            return
        }
        val result = GmsDocumentScanningResult.fromActivityResultIntent(data)
        val pdf = result?.pdf
        if (result == null || pdf == null) {
            toast("Tarama sonucu okunamadı.")
            return
        }
        val pages = result.pages?.map { it.imageUri } ?: emptyList()
        askSaveDetails(pdf.uri, pdf.pageCount, pages)
    }

    private fun askSaveDetails(pdfUri: Uri, pageCount: Int, pageUris: List<Uri>) {
        val folders = archive.folders().map { it.name }.ifEmpty {
            archive.createFolder("Evraklar")
            archive.folders().map { it.name }
        }
        val defaultName = "Belge " + SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.ROOT).format(Date())

        val nameInput = EditText(this).apply {
            setText(defaultName)
            setSingleLine()
            selectAll()
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, folders)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        val spinner = Spinner(this)
        spinner.adapter = adapter
        val preselect = scanTargetFolder?.let { folders.indexOf(it) } ?: -1
        if (preselect >= 0) spinner.setSelection(preselect)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(nameInput)
            addView(spinner)
        }

        AlertDialog.Builder(this)
            .setTitle("Belgeyi kaydet")
            .setView(box)
            .setCancelable(false)
            .setPositiveButton("Kaydet") { _, _ ->
                val folder = spinner.selectedItem as? String ?: return@setPositiveButton
                val name = nameInput.text.toString().trim().ifEmpty { defaultName }
                saveScan(folder, name, pdfUri, pageCount, pageUris)
            }
            .setNegativeButton("Vazgeç") { _, _ -> toast("Belge kaydedilmedi.") }
            .show()
    }

    private fun saveScan(folder: String, name: String, pdfUri: Uri, pageCount: Int, pageUris: List<Uri>) {
        showBusy("Kaydediliyor…")
        io.execute {
            try {
                archive.saveScan(folder, name, pdfUri, pageCount, pageUris)
                runOnUiThread {
                    hideBusy()
                    toast("Belge kaydedildi: $folder")
                    refresh()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    hideBusy()
                    toast("Kaydedilemedi: ${e.localizedMessage}")
                }
            }
        }
    }

    // ---------- Ortak yardımcılar ----------

    protected fun showBusy(message: String) {
        hideBusy()
        busyDialog = AlertDialog.Builder(this).setMessage(message).setCancelable(false).show()
    }

    protected fun updateBusy(message: String) {
        busyDialog?.setMessage(message)
    }

    protected fun hideBusy() {
        busyDialog?.dismiss()
        busyDialog = null
    }

    protected fun promptText(title: String, initial: String, onOk: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(initial)
            setSingleLine()
            setSelection(text.length)
        }
        val box = FrameLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Tamam") { _, _ -> onOk(input.text.toString()) }
            .setNegativeButton("Vazgeç", null)
            .show()
    }
}
