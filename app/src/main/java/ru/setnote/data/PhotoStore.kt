package ru.setnote.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Keeps a private, bounded-size copy; gallery access is not needed after import. */
class PhotoStore(private val context: Context) : ru.setnote.domain.PhotoRepository {
    override suspend fun import(source: String): String =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(source)
            val bitmap =
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(context.contentResolver, uri)
                    ) { decoder, info, _ ->
                        val scale = minOf(1.0, 1600.0 / maxOf(info.size.width, info.size.height))
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1),
                        )
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    val opts =
                        android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri).use {
                        android.graphics.BitmapFactory.decodeStream(it, null, opts)
                    }
                    require(opts.outWidth > 0 && opts.outHeight > 0) { "Не удалось прочитать фото" }
                    opts.inSampleSize = 1
                    while (maxOf(opts.outWidth, opts.outHeight) / opts.inSampleSize > 1600) opts
                        .inSampleSize *= 2
                    opts.inJustDecodeBounds = false
                    context.contentResolver.openInputStream(uri).use {
                        android.graphics.BitmapFactory.decodeStream(it, null, opts)
                    } ?: error("Не удалось прочитать фото")
                }
            val dir = File(context.filesDir, "photos").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            try {
                file.outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it))
                }
                file.absolutePath
            } catch (e: Exception) {
                file.delete()
                throw e
            } finally {
                bitmap.recycle()
            }
        }
}
