package com.ornek.belgetarayici

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import androidx.activity.ComponentActivity
import java.io.File
import java.util.concurrent.Executors

/** Çevrimdışı çalışan, uygulama içi basit PDF görüntüleyici (Android PdfRenderer). */
class PdfViewerActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PATH = "yol"
        const val EXTRA_TITLE = "baslik"
    }

    private var descriptor: ParcelFileDescriptor? = null
    @Volatile
    private var renderer: PdfRenderer? = null
    private val pageCache = LruCache<Int, Bitmap>(4)
    private val renderExecutor = Executors.newSingleThreadExecutor()
    private var pageWidth = 1080

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val file = intent.getStringExtra(EXTRA_PATH)?.let { File(it) }
        if (file == null || !file.exists()) {
            toast("PDF bulunamadı.")
            finish()
            return
        }
        val pdfRenderer = try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            descriptor = pfd
            PdfRenderer(pfd)
        } catch (e: Exception) {
            toast("PDF açılamadı: ${e.localizedMessage}")
            finish()
            return
        }
        renderer = pdfRenderer
        pageWidth = resources.displayMetrics.widthPixels

        val list = ListView(this).apply {
            divider = null
            dividerHeight = dp(8)
            adapter = PageAdapter(pdfRenderer.pageCount)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(224, 224, 224))
            addView(makeTitle(intent.getStringExtra(EXTRA_TITLE) ?: "PDF"))
            addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        root.padForSystemBars(dp(8))
        setContentView(root)
    }

    override fun onDestroy() {
        // Kapatma, sıradaki çizim işlerinden sonra aynı iş parçacığında yapılır.
        renderExecutor.execute {
            try {
                renderer?.close()
                descriptor?.close()
            } catch (e: Exception) {
                // yoksay
            }
            renderer = null
        }
        renderExecutor.shutdown()
        super.onDestroy()
    }

    private fun renderPage(index: Int): Bitmap? {
        val r = renderer ?: return null
        return try {
            val page = r.openPage(index)
            try {
                val w = pageWidth
                val h = (w.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            } finally {
                page.close()
            }
        } catch (e: Exception) {
            null
        }
    }

    private inner class PageAdapter(private val count: Int) : BaseAdapter() {
        override fun getCount(): Int = count
        override fun getItem(position: Int): Any = position
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val iv = (convertView as? ImageView) ?: ImageView(this@PdfViewerActivity).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.WHITE)
                layoutParams = AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            iv.tag = position

            val cached = pageCache.get(position)
            if (cached != null) {
                iv.minimumHeight = 0
                iv.setImageBitmap(cached)
                return iv
            }

            iv.setImageDrawable(null)
            iv.minimumHeight = (pageWidth * 1.4).toInt()
            renderExecutor.execute {
                val bmp = renderPage(position) ?: return@execute
                runOnUiThread {
                    pageCache.put(position, bmp)
                    if (iv.tag == position) {
                        iv.minimumHeight = 0
                        iv.setImageBitmap(bmp)
                    }
                }
            }
            return iv
        }
    }
}
