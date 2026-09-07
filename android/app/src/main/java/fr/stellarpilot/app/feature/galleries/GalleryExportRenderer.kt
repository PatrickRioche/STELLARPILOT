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
import fr.stellarpilot.app.R
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
private val EXPORT_OVERLAY = Color.argb(168, 6, 17, 31)


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

        val logo = BitmapFactory.decodeResource(
            context.resources,
            R.mipmap.ic_launcher
        )

        val exported = render(
            source = source,
            logo = logo,
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
            logo?.recycle()
            source.recycle()
        }

        return filename
    }

    private fun render(
        source: Bitmap,
        logo: Bitmap?,
        session: GallerySession
    ): Bitmap {
        // L'export suit le ratio de l'image d'origine. Aucune zone de titre ou
        // de pied de page n'est réservée : l'astrophotographie occupe donc la
        // totalité du fichier, en portrait comme en paysage.
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

        val imagePaint = Paint(
            Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
        )
        canvas.drawBitmap(
            source,
            null,
            RectF(
                0f,
                0f,
                outputWidth.toFloat(),
                outputHeight.toFloat()
            ),
            imagePaint
        )

        val orange = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EXPORT_ORANGE
        }
        val overlay = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EXPORT_OVERLAY
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
        val borderInset = shortEdge * 0.014f
        val corner = shortEdge * 0.018f

        // Le liseré est décalé vers l'intérieur : l'image reste visible entre
        // le bord du fichier et la signature orange.
        orange.style = Paint.Style.STROKE
        orange.strokeWidth = max(3f, shortEdge * 0.0035f)
        val borderRect = RectF(
            borderInset,
            borderInset,
            outputWidth - borderInset,
            outputHeight - borderInset
        )
        canvas.drawRoundRect(borderRect, corner, corner, orange)
        orange.style = Paint.Style.FILL

        val topHeight = shortEdge * 0.095f
        val topWidth = outputWidth * 0.46f
        val topRect = RectF(
            (outputWidth - topWidth) / 2f,
            0f,
            (outputWidth + topWidth) / 2f,
            topHeight
        )
        canvas.drawRoundRect(
            topRect,
            0f,
            shortEdge * 0.014f,
            overlay
        )

        val logoSize = topHeight * 0.78f
        val topGap = topHeight * 0.08f
        var textStartX = topRect.left + topGap

        if (logo != null) {
            val logoTop = (topHeight - logoSize) / 2f
            canvas.drawBitmap(
                logo,
                null,
                RectF(
                    textStartX,
                    logoTop,
                    textStartX + logoSize,
                    logoTop + logoSize
                ),
                imagePaint
            )
            textStartX += logoSize + topGap * 0.65f
        }

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = topHeight * 0.31f
        val versionText =
            "${stellarPilotVersionLabel()} © ${copyrightYear(session)}"
        val versionBaseline =
            topHeight / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(
            versionText,
            textStartX,
            versionBaseline,
            textPaint
        )

        val bottomHeight = shortEdge * 0.105f
        val bottomY = outputHeight - bottomHeight
        val leftWidth = outputWidth * 0.34f
        val rightWidth = outputWidth * 0.39f

        val leftRect = RectF(
            0f,
            bottomY,
            leftWidth,
            outputHeight.toFloat()
        )
        val rightRect = RectF(
            outputWidth - rightWidth,
            bottomY,
            outputWidth.toFloat(),
            outputHeight.toFloat()
        )

        canvas.drawRoundRect(
            leftRect,
            0f,
            shortEdge * 0.014f,
            overlay
        )
        canvas.drawRoundRect(
            rightRect,
            shortEdge * 0.014f,
            0f,
            overlay
        )

        val bottomPad = shortEdge * 0.016f
        val firstLineBaseline = bottomY + bottomHeight * 0.44f
        val secondLineBaseline = bottomY + bottomHeight * 0.78f

        boldPaint.textAlign = Paint.Align.LEFT
        boldPaint.textSize = bottomHeight * 0.32f
        canvas.drawText(
            "Objet : ${session.targetName}",
            bottomPad,
            firstLineBaseline,
            boldPaint
        )

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = bottomHeight * 0.22f
        canvas.drawText(
            "${session.capturedFrames} capturées • ${session.acceptedFrames} stackées",
            bottomPad,
            secondLineBaseline,
            textPaint
        )

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = bottomHeight * 0.28f
        canvas.drawText(
            formatGalleryDate(session.createdAt),
            outputWidth - bottomPad,
            firstLineBaseline,
            textPaint
        )

        textPaint.textSize = bottomHeight * 0.21f
        canvas.drawText(
            formatGalleryLocation(session),
            outputWidth - bottomPad,
            secondLineBaseline,
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
