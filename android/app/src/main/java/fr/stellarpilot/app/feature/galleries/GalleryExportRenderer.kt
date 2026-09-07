package fr.stellarpilot.app.feature.galleries

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import fr.stellarpilot.app.BuildConfig
import fr.stellarpilot.app.data.remote.GallerySession
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt


private const val EXPORT_LONG_EDGE = 2000
private const val EXPORT_JPEG_QUALITY = 94

private val EXPORT_BACKGROUND = Color.rgb(6, 17, 31)
private val EXPORT_ORANGE = Color.rgb(255, 138, 61)


fun stellarPilotVersionLabel(): String {
    val baseVersion = BuildConfig.VERSION_NAME
        .substringBefore("-device")
        .substringBefore("-simulation")
    return "StellarPilot v$baseVersion"
}


fun formatGalleryDate(value: String): String {
    return runCatching {
        OffsetDateTime.parse(value)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    }.getOrElse {
        value.ifBlank { "Date inconnue" }
    }
}


fun formatGalleryLocation(session: GallerySession): String {
    session.placeName
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    val latitude = session.latitude
    val longitude = session.longitude
    if (latitude != null && longitude != null) {
        val latDirection = if (latitude >= 0.0) "N" else "S"
        val lonDirection = if (longitude >= 0.0) "E" else "W"
        return String.format(
            Locale.FRANCE,
            "%.4f° %s • %.4f° %s",
            abs(latitude),
            latDirection,
            abs(longitude),
            lonDirection
        )
    }

    return "Localisation inconnue"
}


object GalleryExportRenderer {

    fun saveToDeviceGallery(
        context: Context,
        previewBytes: ByteArray,
        session: GallerySession
    ): String {
        val source = BitmapFactory.decodeByteArray(
            previewBytes,
            0,
            previewBytes.size
        ) ?: error("Image de galerie illisible")

        val exported = render(
            source = source,
            session = session
        )
        val filename = exportFilename(session)
        val resolver = context.contentResolver

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/StellarPilot"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: error("Impossible de créer l'image dans la galerie Android")

        try {
            resolver.openOutputStream(uri)?.use { stream ->
                check(
                    exported.compress(
                        Bitmap.CompressFormat.JPEG,
                        EXPORT_JPEG_QUALITY,
                        stream
                    )
                ) {
                    "Échec de l'encodage JPEG"
                }
            } ?: error("Flux d'enregistrement indisponible")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val ready = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, ready, null, null)
            }
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        } finally {
            if (exported !== source) exported.recycle()
            source.recycle()
        }

        return filename
    }

    private fun render(
        source: Bitmap,
        session: GallerySession
    ): Bitmap {
        val sourceLongEdge = max(source.width, source.height).toFloat()
        val exportScale = EXPORT_LONG_EDGE / sourceLongEdge
        val outputWidth = max(1, (source.width * exportScale).roundToInt())
        val outputHeight = max(1, (source.height * exportScale).roundToInt())

        val output = Bitmap.createBitmap(
            outputWidth,
            outputHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(output)
        canvas.drawColor(EXPORT_BACKGROUND)

        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(
            source,
            null,
            RectF(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat()),
            imagePaint
        )

        val orange = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EXPORT_ORANGE
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EXPORT_ORANGE
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.SANS_SERIF,
                android.graphics.Typeface.NORMAL
            )
        }
        val boldPaint = Paint(textPaint).apply {
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.SANS_SERIF,
                android.graphics.Typeface.BOLD
            )
        }

        val shortEdge = minOf(outputWidth, outputHeight).toFloat()
        val borderInset = shortEdge * 0.030f
        val corner = shortEdge * 0.018f
        val strokeWidth = max(3f, shortEdge * 0.0032f)

        orange.style = Paint.Style.STROKE
        orange.strokeWidth = strokeWidth
        val borderRect = RectF(
            borderInset,
            borderInset,
            outputWidth - borderInset,
            outputHeight - borderInset
        )
        canvas.drawRoundRect(borderRect, corner, corner, orange)
        orange.style = Paint.Style.FILL

        val shadowRadius = shortEdge * 0.006f
        val shadowOffset = shortEdge * 0.002f
        textPaint.setShadowLayer(
            shadowRadius,
            shadowOffset,
            shadowOffset,
            Color.argb(225, 0, 0, 0)
        )
        boldPaint.setShadowLayer(
            shadowRadius,
            shadowOffset,
            shadowOffset,
            Color.argb(225, 0, 0, 0)
        )

        val contentInset = borderInset + shortEdge * 0.020f
        val versionText =
            "${stellarPilotVersionLabel()} © ${copyrightYear(session)}"
        textPaint.textSize = shortEdge * 0.050f
        textPaint.textAlign = Paint.Align.LEFT

        val logoSize = shortEdge * 0.078f
        val logoGap = shortEdge * 0.014f
        val versionWidth = textPaint.measureText(versionText)
        val headerWidth = logoSize + logoGap + versionWidth
        val headerLeft = (outputWidth - headerWidth) / 2f
        val logoTop = contentInset

        drawStellarPilotMark(
            canvas = canvas,
            left = headerLeft,
            top = logoTop,
            size = logoSize,
            orange = orange
        )

        val headerBaseline =
            logoTop + logoSize / 2f -
                (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(
            versionText,
            headerLeft + logoSize + logoGap,
            headerBaseline,
            textPaint
        )

        val bottomPad = contentInset
        val firstLineBaseline =
            outputHeight - bottomPad - shortEdge * 0.046f
        val secondLineBaseline =
            outputHeight - bottomPad - shortEdge * 0.010f

        boldPaint.textAlign = Paint.Align.LEFT
        boldPaint.textSize = shortEdge * 0.050f
        canvas.drawText(
            "Objet : ${session.targetName}",
            contentInset,
            firstLineBaseline,
            boldPaint
        )

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = shortEdge * 0.033f
        canvas.drawText(
            "${session.capturedFrames} captures • ${session.acceptedFrames} stackées",
            contentInset,
            secondLineBaseline,
            textPaint
        )

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = shortEdge * 0.041f
        canvas.drawText(
            formatGalleryDate(session.createdAt),
            outputWidth - contentInset,
            firstLineBaseline,
            textPaint
        )

        textPaint.textSize = shortEdge * 0.031f
        canvas.drawText(
            formatGalleryLocation(session),
            outputWidth - contentInset,
            secondLineBaseline,
            textPaint
        )

        return output
    }

    private fun drawStellarPilotMark(
        canvas: Canvas,
        left: Float,
        top: Float,
        size: Float,
        orange: Paint
    ) {
        val centerX = left + size * 0.46f
        val centerY = top + size * 0.56f

        val ring = Paint(orange).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.085f
        }
        val fill = Paint(orange).apply {
            style = Paint.Style.FILL
        }

        canvas.save()
        canvas.rotate(-18f, centerX, centerY)
        canvas.drawOval(
            RectF(
                left + size * 0.08f,
                top + size * 0.38f,
                left + size * 0.84f,
                top + size * 0.72f
            ),
            ring
        )
        canvas.restore()

        canvas.drawCircle(centerX, centerY, size * 0.18f, fill)
        canvas.drawCircle(
            left + size * 0.80f,
            top + size * 0.17f,
            size * 0.055f,
            fill
        )
    }

    private fun copyrightYear(session: GallerySession): Int {
        return runCatching {
            OffsetDateTime.parse(session.createdAt).year
        }.getOrDefault(java.time.Year.now().value)
    }

    private fun exportFilename(session: GallerySession): String {
        val date = runCatching {
            OffsetDateTime.parse(session.createdAt)
                .toLocalDate()
                .toString()
        }.getOrDefault(java.time.LocalDate.now().toString())

        val safeTarget = session.targetName
            .trim()
            .replace(Regex("[^A-Za-z0-9À-ÿ_-]+"), "_")
            .trim('_')
            .take(48)
            .ifBlank { "Objet" }

        return "${date}_StellarPilot_${safeTarget}.jpg"
    }
}
