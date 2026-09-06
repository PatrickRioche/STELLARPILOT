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
import kotlin.math.min


private const val EXPORT_WIDTH = 1600
private const val EXPORT_HEIGHT = 2000
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

        val exported = render(source, session)
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
        val output = Bitmap.createBitmap(
            EXPORT_WIDTH,
            EXPORT_HEIGHT,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(output)
        canvas.drawColor(EXPORT_BACKGROUND)

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

        boldPaint.textAlign = Paint.Align.CENTER
        boldPaint.textSize = 78f
        canvas.drawText("StellarPilot", EXPORT_WIDTH / 2f, 130f, boldPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 27f
        textPaint.letterSpacing = 0.12f
        canvas.drawText(
            "OBSERVATION • CAPTURE • STACK",
            EXPORT_WIDTH / 2f,
            184f,
            textPaint
        )
        textPaint.letterSpacing = 0f

        val maxImageWidth = EXPORT_WIDTH - 110f
        val maxImageHeight = 1160f
        val scale = min(
            maxImageWidth / source.width.toFloat(),
            maxImageHeight / source.height.toFloat()
        )
        val imageWidth = source.width * scale
        val imageHeight = source.height * scale
        val imageLeft = (EXPORT_WIDTH - imageWidth) / 2f
        val imageTop = 250f
        val imageRect = RectF(
            imageLeft,
            imageTop,
            imageLeft + imageWidth,
            imageTop + imageHeight
        )

        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, null, imageRect, imagePaint)

        orange.style = Paint.Style.STROKE
        orange.strokeWidth = 5f
        canvas.drawRoundRect(imageRect, 18f, 18f, orange)
        orange.style = Paint.Style.FILL

        val dividerY = imageRect.bottom + 74f
        canvas.drawRoundRect(
            RectF(430f, dividerY, 1170f, dividerY + 5f),
            3f,
            3f,
            orange
        )

        val footerTop = dividerY + 115f
        val separatorTop = footerTop - 38f
        val separatorBottom = footerTop + 125f
        orange.style = Paint.Style.STROKE
        orange.strokeWidth = 2f
        canvas.drawLine(530f, separatorTop, 530f, separatorBottom, orange)
        canvas.drawLine(1070f, separatorTop, 1070f, separatorBottom, orange)
        orange.style = Paint.Style.FILL

        textPaint.textSize = 35f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(
            "${stellarPilotVersionLabel()} © ${copyrightYear(session)}",
            70f,
            footerTop,
            textPaint
        )

        boldPaint.textSize = 44f
        boldPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            "Objet : ${session.targetName}",
            EXPORT_WIDTH / 2f,
            footerTop,
            boldPaint
        )
        textPaint.textSize = 31f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            "${session.capturedFrames} capturées • ${session.acceptedFrames} stackées",
            EXPORT_WIDTH / 2f,
            footerTop + 58f,
            textPaint
        )

        textPaint.textSize = 34f
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            formatGalleryDate(session.createdAt),
            EXPORT_WIDTH - 70f,
            footerTop,
            textPaint
        )
        textPaint.textSize = 28f
        canvas.drawText(
            formatGalleryLocation(session),
            EXPORT_WIDTH - 70f,
            footerTop + 58f,
            textPaint
        )

        return output
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
