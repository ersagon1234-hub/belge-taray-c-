package com.ornek.belgetarayici

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale

data class FolderInfo(val name: String, val docCount: Int)

data class Doc(
    val id: String,
    val folder: String,
    val name: String,
    val createdAt: Long,
    val pageCount: Int,
    val dir: File
) {
    val pdf: File get() = File(dir, "belge.pdf")
    val ocrFile: File get() = File(dir, "ocr.txt")
    val pageImages: List<File>
        get() = (dir.listFiles { f -> f.name.startsWith("sayfa_") && f.name.endsWith(".jpg") }
            ?: emptyArray<File>()).sortedBy { it.name }
}

/**
 * Belge arşivi: tamamen telefonun uygulama-özel (internal) depolamasında tutulur.
 *
 *   files/arsiv/<Klasör adı>/<belge kimliği>/
 *       belge.pdf        taranan PDF
 *       sayfa_001.jpg    sayfa görüntüleri (OCR için)
 *       ocr.txt          OCR sonucu (varsa)
 *       meta.json        ad, tarih, sayfa sayısı
 *
 * Veritabanı yok; klasör = dizin. Bu sayede çevrimdışı ve basit çalışır.
 */
class Archive(context: Context) {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "arsiv").apply { mkdirs() }
    private val prefs = app.getSharedPreferences("arsiv", Context.MODE_PRIVATE)

    init {
        // Varsayılan klasörler yalnızca ilk açılışta oluşturulur; silinirlerse geri gelmez.
        if (!prefs.getBoolean("varsayilan_olusturuldu", false)) {
            listOf("Faturalar", "Kimlikler", "Evraklar").forEach { File(root, it).mkdirs() }
            prefs.edit().putBoolean("varsayilan_olusturuldu", true).apply()
        }
    }

    // ---------- Klasörler ----------

    fun cleanName(raw: String): String =
        raw.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
            .take(60)

    fun folders(): List<FolderInfo> =
        (root.listFiles { f -> f.isDirectory } ?: emptyArray<File>())
            .sortedBy { it.name.lowercase(Locale.forLanguageTag("tr-TR")) }
            .map { FolderInfo(it.name, countDocs(it)) }

    private fun countDocs(folderDir: File): Int =
        folderDir.listFiles { f -> File(f, "meta.json").exists() }?.size ?: 0

    /** Hata varsa mesajı, başarılıysa null döndürür. */
    fun createFolder(raw: String): String? {
        val name = cleanName(raw)
        if (name.isEmpty()) return "Klasör adı boş olamaz."
        val dir = File(root, name)
        if (dir.exists()) return "Bu adda bir klasör zaten var."
        return if (dir.mkdirs()) null else "Klasör oluşturulamadı."
    }

    fun renameFolder(oldName: String, raw: String): String? {
        val name = cleanName(raw)
        if (name.isEmpty()) return "Klasör adı boş olamaz."
        if (name == oldName) return null
        val target = File(root, name)
        if (target.exists()) return "Bu adda bir klasör zaten var."
        return if (File(root, oldName).renameTo(target)) null else "Klasör yeniden adlandırılamadı."
    }

    fun deleteFolder(name: String): Boolean = File(root, name).deleteRecursively()

    // ---------- Belgeler ----------

    fun documents(folder: String): List<Doc> =
        (File(root, folder).listFiles { f -> f.isDirectory } ?: emptyArray<File>())
            .mapNotNull { readDoc(folder, it) }
            .sortedByDescending { it.createdAt }

    private fun readDoc(folder: String, dir: File): Doc? {
        val meta = File(dir, "meta.json")
        if (!meta.exists()) return null
        return try {
            val j = JSONObject(meta.readText())
            Doc(
                id = dir.name,
                folder = folder,
                name = j.optString("name", dir.name),
                createdAt = j.optLong("createdAt", dir.lastModified()),
                pageCount = j.optInt("pageCount", 0),
                dir = dir
            )
        } catch (e: Exception) {
            null
        }
    }

    @Throws(IOException::class)
    fun saveScan(folder: String, name: String, pdfUri: Uri, pageCount: Int, pageUris: List<Uri>): Doc {
        val folderDir = File(root, folder)
        if (!folderDir.isDirectory) throw IOException("Klasör bulunamadı.")
        val now = System.currentTimeMillis()
        val dir = File(folderDir, now.toString())
        if (!dir.mkdirs()) throw IOException("Belge klasörü oluşturulamadı.")
        try {
            copy(pdfUri, File(dir, "belge.pdf"))
            pageUris.forEachIndexed { i, uri ->
                copy(uri, File(dir, String.format(Locale.ROOT, "sayfa_%03d.jpg", i + 1)))
            }
            val meta = JSONObject()
                .put("name", name)
                .put("createdAt", now)
                .put("pageCount", maxOf(pageCount, pageUris.size))
            File(dir, "meta.json").writeText(meta.toString())
            return readDoc(folder, dir) ?: throw IOException("Belge okunamadı.")
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw if (e is IOException) e else IOException(e.message, e)
        }
    }

    private fun copy(src: Uri, dst: File) {
        val input = app.contentResolver.openInputStream(src)
            ?: throw IOException("Dosya okunamadı.")
        input.use { i -> dst.outputStream().use { o -> i.copyTo(o) } }
    }

    fun moveDoc(doc: Doc, targetFolder: String): Boolean {
        if (doc.folder == targetFolder) return true
        val targetDir = File(root, targetFolder)
        if (!targetDir.isDirectory) return false
        var dest = File(targetDir, doc.id)
        if (dest.exists()) dest = File(targetDir, doc.id + "_1")
        return doc.dir.renameTo(dest)
    }

    fun renameDoc(doc: Doc, raw: String): Boolean {
        val name = raw.trim().replace(Regex("\\s+"), " ").take(80)
        if (name.isEmpty()) return false
        val meta = File(doc.dir, "meta.json")
        return try {
            val j = JSONObject(meta.readText())
            j.put("name", name)
            meta.writeText(j.toString())
            true
        } catch (e: Exception) {
            false
        }
    }

    fun deleteDoc(doc: Doc): Boolean = doc.dir.deleteRecursively()

    // ---------- OCR metni ----------

    fun hasOcr(doc: Doc): Boolean = doc.ocrFile.exists()

    fun saveOcr(doc: Doc, text: String) {
        doc.ocrFile.writeText(text)
    }

    // ---------- Paylaşma / açma ----------

    /** PDF'i belge adıyla geçici bir dosyaya kopyalar (alıcı uygulama düzgün dosya adı görsün diye). */
    @Throws(IOException::class)
    fun shareCopy(doc: Doc): File {
        val dir = File(app.cacheDir, "paylasim")
        dir.deleteRecursively()
        dir.mkdirs()
        val base = cleanName(doc.name).ifEmpty { "belge" }
        val out = File(dir, "$base.pdf")
        doc.pdf.copyTo(out, overwrite = true)
        return out
    }

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
}
