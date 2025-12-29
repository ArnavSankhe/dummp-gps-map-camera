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
    val bottomMargin = (result.height * 0.03f).roundToInt()
    val overlayTop = (result.height - overlayHeight - bottomMargin).coerceAtLeast(0)
    val overlayRect = RectF(0f, overlayTop.toFloat(), result.width.toFloat(), result.height.toFloat())

    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AA111111")
    }
    val overlayCorner = overlayHeight * 0.08f
    canvas.drawRoundRect(overlayRect, overlayCorner, overlayCorner, backgroundPaint)

    val padding = overlayHeight * 0.06f
    val leftColumnWidth = result.width * 0.33f
    val pillHeight = overlayHeight * 0.22f
    val pillTop = overlayTop + padding
    val pillBottom = pillTop + pillHeight * 0.7f
    val columnGap = overlayHeight * 0.06f
    val thumbTop = pillBottom + columnGap
    val thumbSize = minOf(leftColumnWidth, result.height - padding - thumbTop)
    val thumbRect = RectF(
        padding,
        thumbTop,
        padding + thumbSize,
        thumbTop + thumbSize
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
    val textAvailableHeight = overlayHeight - padding * 2f

    val textBlock = config.details.takeIf { it.isNotBlank() }
        ?: "Location title\nAddress line\nLat/Long\nDate/Time"
    val maxLines = 5
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    val textWidth = textAvailableWidth.roundToInt().coerceAtLeast(1)
    val textHeight = textAvailableHeight.roundToInt().coerceAtLeast(1)
    val baseSize = overlayHeight * 0.15f
    val minSize = overlayHeight * 0.08f
    val bestSize = findBestTextSize(textBlock, textPaint, textWidth, textHeight, maxLines, baseSize, minSize)
    textPaint.textSize = bestSize

    val layout = buildStaticLayout(textBlock, textPaint, textWidth, maxLines)
    canvas.save()
    canvas.translate(textStartX, overlayTop + padding)
    layout.draw(canvas)
    canvas.restore()

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

private fun buildStaticLayout(
    text: String,
    paint: TextPaint,
    width: Int,
    maxLines: Int
): StaticLayout {
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(0f, 1.12f)
        .setIncludePad(false)
        .setEllipsize(TextUtils.TruncateAt.END)
        .setMaxLines(maxLines)
        .build()
}

private fun findBestTextSize(
    text: String,
    paint: TextPaint,
    width: Int,
    maxHeight: Int,
    maxLines: Int,
    startSize: Float,
    minSize: Float
): Float {
    var size = startSize
    while (size >= minSize) {
        paint.textSize = size
        val layout = buildStaticLayout(text, paint, width, maxLines)
        if (layout.height <= maxHeight) {
            return size
        }
        size -= 1f
    }
    return minSize
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

fun compositeOverlayOnBitmap(base: Bitmap, overlay: Bitmap, yOffset: Int): Bitmap {
    val result = base.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(result)
    canvas.drawBitmap(overlay, 0f, yOffset.toFloat(), null)
    return result
}

fun cropCenterToAspect(bitmap: Bitmap, targetAspect: Float): Bitmap {
    if (targetAspect <= 0f) return bitmap
    val width = bitmap.width
    val height = bitmap.height
    if (width == 0 || height == 0) return bitmap

    val currentAspect = width.toFloat() / height.toFloat()
    return if (currentAspect > targetAspect) {
        val newWidth = (height * targetAspect).toInt().coerceAtMost(width)
        val xOffset = ((width - newWidth) / 2f).toInt().coerceAtLeast(0)
        Bitmap.createBitmap(bitmap, xOffset, 0, newWidth, height)
    } else if (currentAspect < targetAspect) {
        val newHeight = (width / targetAspect).toInt().coerceAtMost(height)
        val yOffset = ((height - newHeight) / 2f).toInt().coerceAtLeast(0)
        Bitmap.createBitmap(bitmap, 0, yOffset, width, newHeight)
    } else {
        bitmap
    }
}
