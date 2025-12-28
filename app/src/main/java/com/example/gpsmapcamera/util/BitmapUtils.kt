package com.example.gpsmapcamera.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.TextPaint
import androidx.exifinterface.media.ExifInterface
import com.example.gpsmapcamera.data.OverlayConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.roundToInt

fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = false
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (e: Exception) {
        null
    }
}

fun decodeCapturedBitmap(file: File): Bitmap? {
    if (!file.exists()) return null
    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
    val rotated = rotateBitmapIfRequired(file, bitmap)
    return rotated
}

private fun rotateBitmapIfRequired(file: File, bitmap: Bitmap): Bitmap {
    return try {
        val exif = ExifInterface(file.absolutePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        }
        if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } catch (_: IOException) {
        bitmap
    }
}

fun renderOverlayOnBitmap(
    base: Bitmap,
    config: OverlayConfig,
    mapBitmap: Bitmap?
): Bitmap {
    val result = base.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(result)

    val overlayHeight = (result.height * 0.24f).roundToInt()
    val overlayTop = result.height - overlayHeight
    val overlayRect = RectF(0f, overlayTop.toFloat(), result.width.toFloat(), result.height.toFloat())

    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AA111111")
    }
    canvas.drawRect(overlayRect, backgroundPaint)

    val padding = overlayHeight * 0.08f
    val thumbWidth = result.width * 0.28f
    val thumbRect = RectF(
        padding,
        overlayTop + padding,
        padding + thumbWidth,
        result.height - padding
    )

    if (mapBitmap != null) {
        drawCenterCropBitmap(canvas, mapBitmap, thumbRect)
    } else {
        val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#303030")
        }
        canvas.drawRect(thumbRect, placeholderPaint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = overlayHeight * 0.12f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "No map",
            thumbRect.centerX(),
            thumbRect.centerY() + textPaint.textSize / 2f,
            textPaint
        )
    }

    val textStartX = thumbRect.right + padding
    val textAvailableWidth = result.width - textStartX - padding

    val pillHeight = overlayHeight * 0.22f
    val pillWidth = pillHeight * 2.8f
    val pillRect = RectF(
        textStartX,
        overlayTop + padding,
        textStartX + pillWidth,
        overlayTop + padding + pillHeight
    )
    val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2F6FED")
    }
    canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, pillPaint)
    val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = pillHeight * 0.5f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText(
        "Check In",
        pillRect.centerX(),
        pillRect.centerY() + pillTextPaint.textSize * 0.35f,
        pillTextPaint
    )

    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = overlayHeight * 0.13f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    val lines = listOf(
        config.title.ifBlank { "Location title" },
        config.address.ifBlank { "Address line" },
        config.latLong.ifBlank { "Lat/Long" },
        config.dateTime.ifBlank { "Date/Time" }
    )

    var currentY = pillRect.bottom + padding * 0.7f
    val lineSpacing = textPaint.textSize * 1.25f
    for (line in lines) {
        val ellipsized = android.text.TextUtils.ellipsize(
            line,
            textPaint,
            textAvailableWidth,
            android.text.TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(ellipsized, textStartX, currentY + textPaint.textSize, textPaint)
        currentY += lineSpacing
    }

    return result
}

private fun drawCenterCropBitmap(canvas: Canvas, bitmap: Bitmap, dest: RectF) {
    val srcWidth = bitmap.width.toFloat()
    val srcHeight = bitmap.height.toFloat()
    val destWidth = dest.width()
    val destHeight = dest.height()

    val scale = maxOf(destWidth / srcWidth, destHeight / srcHeight)
    val scaledWidth = scale * srcWidth
    val scaledHeight = scale * srcHeight

    val left = dest.centerX() - scaledWidth / 2f
    val top = dest.centerY() - scaledHeight / 2f

    val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
    val destRect = RectF(left, top, left + scaledWidth, top + scaledHeight)

    val saveCount = canvas.save()
    canvas.clipRect(dest)
    canvas.drawBitmap(bitmap, srcRect, destRect, null)
    canvas.restoreToCount(saveCount)
}

fun saveBitmapToGallery(
    context: Context,
    bitmap: Bitmap,
    filename: String
): Uri? {
    val resolver: ContentResolver = context.contentResolver
    val contentValues = android.content.ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/GpsMapCamera")
    }

    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return null
    return try {
        resolver.openOutputStream(uri)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
        }
        uri
    } catch (_: Exception) {
        null
    }
}
