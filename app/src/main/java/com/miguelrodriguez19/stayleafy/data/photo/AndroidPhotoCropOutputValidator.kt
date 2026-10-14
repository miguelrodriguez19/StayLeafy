package com.miguelrodriguez19.stayleafy.data.photo

import android.graphics.BitmapFactory
import java.io.File
import java.io.FileInputStream

object AndroidPhotoCropOutputValidator : PhotoCropOutputValidator {
    override fun isValidJpegSquare(file: File): Boolean = runCatching {
        if (!file.isFile || file.length() < 4L) return false
        FileInputStream(file).use { input ->
            if (input.read() != JPEG_START_MARKER_1 || input.read() != JPEG_START_MARKER_2) {
                return false
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outWidth != bounds.outHeight ||
            bounds.outWidth > PLANT_PHOTO_CROP_MAX_SIZE
        ) {
            return false
        }

        val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return false
        decoded.recycle()
        true
    }.getOrDefault(false)

    private const val JPEG_START_MARKER_1 = 0xFF
    private const val JPEG_START_MARKER_2 = 0xD8
}
