package com.ornek.belgetarayici

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

/**
 * Çevrimdışı OCR (ML Kit, Latin alfabesi, model uygulamaya gömülü).
 * Bu fonksiyonlar bekleme yapar; ana iş parçacığında ÇAĞRILMAMALIDIR.
 */
object Ocr {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /** Galeriden seçilen veya kamerayla çekilen fotoğraf (EXIF yönü dikkate alınır). */
    fun recognizeUri(context: Context, uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        return Tasks.await(recognizer.process(image)).text
    }

    /** Arşivdeki sayfa görüntüsü. Çok büyükse bellek için küçültülür. */
    fun recognizeFile(file: File): String {
        val bmp = decodeScaled(file, 3000) ?: return ""
        try {
            return Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0))).text
        } finally {
            bmp.recycle()
        }
    }

    private fun decodeScaled(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }
}
