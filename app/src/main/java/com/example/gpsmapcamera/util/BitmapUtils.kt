package com.example.gpsmapcamera.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
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

fun loadThumbnailBitmap(context: Context, uri: Uri, maxWidth: Int, maxHeight: Int): Bitmap? {
    if (maxWidth <= 0 || maxHeight <= 0) return loadBitmapFromUri(context, uri)
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.setTargetSize(maxWidth, maxHeight)
                decoder.isMutableRequired = false
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val resolver = context.contentResolver
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
            options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight)
            options.inJustDecodeBounds = false
            resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
        }
    } catch (_: Exception) {
        null
    }
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height: Int, width: Int) = options.outHeight to options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
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

    val overlayHeight = (result.height * 0.22f).roundToInt()
    val overlayTop = result.height - overlayHeight
    val overlayRect = RectF(0f, overlayTop.toFloat(), result.width.toFloat(), result.height.toFloat())

    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AA111111")
    }
    val overlayCorner = overlayHeight * 0.08f
    canvas.drawRoundRect(overlayRect, overlayCorner, overlayCorner, backgroundPaint)

    val padding = overlayHeight * 0.08f
    val leftColumnWidth = result.width * 0.30f
    val pillHeight = overlayHeight * 0.22f
    val pillTop = overlayTop + padding
    val pillBottom = pillTop + pillHeight
    val thumbRect = RectF(
        padding,
        pillBottom + padding * 0.6f,
        padding + leftColumnWidth,
        result.height - padding
    )
    val pillRect = RectF(
        thumbRect.left,
        pillTop,
        thumbRect.right,
        pillBottom
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
    val thumbCorner = overlayHeight * 0.06f
    if (mapBitmap != null) {
        drawCenterCropRoundedBitmap(canvas, mapBitmap, thumbRect, thumbCorner)
    } else {
        val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#303030")
        }
        canvas.drawRoundRect(thumbRect, thumbCorner, thumbCorner, placeholderPaint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = overlayHeight * 0.11f
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

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = overlayHeight * 0.16f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = overlayHeight * 0.13f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    val lines = config.details.takeIf { it.isNotBlank() }?.lines()
        ?: listOf("Location title", "Address line", "Lat/Long", "Date/Time")

    var currentY = overlayTop + padding * 0.6f
    val title = TextUtils.ellipsize(
        lines.first(),
        titlePaint,
        textAvailableWidth,
        TextUtils.TruncateAt.END
    ).toString()
    canvas.drawText(title, textStartX, currentY + titlePaint.textSize, titlePaint)
    currentY += titlePaint.textSize * 0.8f

    val bodyText = lines.drop(1).joinToString("\n").trim()
    if (bodyText.isNotEmpty()) {
        val textWidth = textAvailableWidth.roundToInt().coerceAtLeast(1)
        val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(bodyText, 0, bodyText.length, textPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .setIncludePad(false)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setMaxLines(3)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(bodyText, textPaint, textWidth, Layout.Alignment.ALIGN_NORMAL, 1.15f, 0f, false)
        }
        canvas.save()
        canvas.translate(textStartX, currentY)
        layout.draw(canvas)
        canvas.restore()
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

private fun drawCenterCropRoundedBitmap(canvas: Canvas, bitmap: Bitmap, dest: RectF, corner: Float) {
    val saveCount = canvas.save()
    val path = Path().apply {
        addRoundRect(dest, corner, corner, Path.Direction.CW)
    }
    canvas.clipPath(path)
    drawCenterCropBitmap(canvas, bitmap, dest)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return null
    return try {
        resolver.openOutputStream(uri)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pendingValues = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, pendingValues, null, null)
        }
        uri
    } catch (_: Exception) {
        resolver.delete(uri, null, null)
        null
    }
}
