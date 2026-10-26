package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object ImageStorageHelper {

    suspend fun processAndSaveImage(
        context: Context,
        imageUri: Uri,
        maxDimension: Int = 1024,
        quality: Int = 78
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeSampledBitmapFromUri(context, imageUri, maxDimension) ?: return@withContext null
            val rotatedBitmap = rotateBitmapIfRequired(context, imageUri, bitmap)

            // Save to app private internal storage
            val sightingsDir = File(context.filesDir, "sightings").apply { if (!exists()) mkdirs() }
            val fileName = "sighting_${UUID.randomUUID()}.jpg"
            val file = File(sightingsDir, fileName)

            val outputStream = FileOutputStream(file)
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            outputStream.flush()
            outputStream.close()

            // Generate compact base64 representation for instant 2-person real-time Firestore sync
            val thumbBitmap = if (rotatedBitmap.width > 480 || rotatedBitmap.height > 480) {
                val scale = 480f / maxOf(rotatedBitmap.width, rotatedBitmap.height)
                Bitmap.createScaledBitmap(
                    rotatedBitmap,
                    (rotatedBitmap.width * scale).toInt().coerceAtLeast(1),
                    (rotatedBitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                rotatedBitmap
            }
            val byteStream = ByteArrayOutputStream()
            thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 65, byteStream)
            val base64Bytes = byteStream.toByteArray()
            val base64String = "data:image/jpeg;base64," + Base64.encodeToString(base64Bytes, Base64.NO_WRAP)

            Pair(file.absolutePath, base64String)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (bitmap != null) {
                rotateBitmapIfRequired(context, uri, bitmap)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeSampledBitmapFromUri(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        var inSampleSize = 1
        val (width, height) = options.outWidth to options.outHeight
        if (width > maxDimension || height > maxDimension) {
            val halfWidth = width / 2
            val halfHeight = height / 2
            while ((halfWidth / inSampleSize) >= maxDimension && (halfHeight / inSampleSize) >= maxDimension) {
                inSampleSize *= 2
            }
        }

        options.inJustDecodeBounds = false
        options.inSampleSize = inSampleSize
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun rotateBitmapIfRequired(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exifInterface = ExifInterface(inputStream)
            val orientation = exifInterface.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            inputStream.close()

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270f)
                else -> bitmap
            }
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
}
