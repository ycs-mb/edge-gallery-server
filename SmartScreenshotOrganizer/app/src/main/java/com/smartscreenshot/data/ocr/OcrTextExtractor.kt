package com.smartscreenshot.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

data class OcrOutput(val text: String, val bitmap: Bitmap?)

@Singleton
class OcrTextExtractor @Inject constructor(private val context: Context) {

  private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

  /** Decodes [uri], runs offline OCR, and returns the text plus the decoded bitmap. */
  suspend fun extract(uri: Uri): OcrOutput {
    val bitmap =
      try {
        decodeBitmap(uri)
      } catch (_: Throwable) {
        null
      } ?: return OcrOutput("", null)

    val text =
      try {
        recognize(bitmap)
      } catch (_: Throwable) {
        ""
      }
    return OcrOutput(text, bitmap)
  }

  private fun decodeBitmap(uri: Uri): Bitmap {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val source = ImageDecoder.createSource(context.contentResolver, uri)
      ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
        decoder.isMutableRequired = false
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
      }
    } else {
      @Suppress("DEPRECATION")
      MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
  }

  private suspend fun recognize(bitmap: Bitmap): String =
    suspendCancellableCoroutine { cont ->
      val image = InputImage.fromBitmap(bitmap, 0)
      recognizer
        .process(image)
        .addOnSuccessListener { result -> cont.resume(result.text) }
        .addOnFailureListener { cont.resume("") }
    }
}
