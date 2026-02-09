package com.tqmane.filmsim.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Applies Honor-style watermarks to exported images.
 *
 * Faithfully reproduces the Honor watermark templates (FrameWatermark & TextWatermark)
 * using the exact font (HONORSansVFCN.ttf), dimensions, and layout from content.json.
 *
 * All dimensions are scaled proportionally based on a 6144px reference width
 * (matching the original Honor template `baseOnValue`).
 */
object WatermarkProcessor {

    enum class WatermarkStyle {
        NONE, FRAME, TEXT, FRAME_YG, TEXT_YG
    }

    data class WatermarkConfig(
        val style: WatermarkStyle = WatermarkStyle.NONE,
        val deviceName: String? = null,   // e.g. "HONOR Magic6 Pro"
        val timeText: String? = null,
        val locationText: String? = null,
        val lensInfo: String? = null       // e.g. "27mm  f/1.9  1/100s  ISO1600"
    )

    // Reference width from Honor template baseOnValue
    private const val BASE_WIDTH = 6144f
    // Frame border height at reference width (from backgroundElements)
    private const val FRAME_BORDER_HEIGHT = 688f

    // Cached typeface
    private var honorTypeface: Typeface? = null

    /**
     * Load the HONORSansVFCN.ttf from assets, with caching.
     */
    private fun getHonorTypeface(context: Context): Typeface {
        honorTypeface?.let { return it }
        return try {
            Typeface.createFromAsset(context.assets, "fonts/HONORSansVFCN.ttf").also {
                honorTypeface = it
            }
        } catch (e: Exception) {
            Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

    /**
     * Create a Paint with the Honor font at a specific weight.
     * HONORSansVFCN.ttf is a variable font; weight 300 = Light, weight 400 = Regular.
     */
    private fun createHonorPaint(context: Context, weight: Int): Paint {
        val baseTypeface = getHonorTypeface(context)
        val typeface = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            Typeface.create(baseTypeface, weight, false)
        } else {
            baseTypeface
        }
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
        }
    }

    /**
     * Apply watermark to the given bitmap. Returns a new bitmap with watermark applied.
     * The source bitmap is NOT modified or recycled.
     */
    fun applyWatermark(
        context: Context,
        source: Bitmap,
        config: WatermarkConfig
    ): Bitmap {
        return when (config.style) {
            WatermarkStyle.NONE -> source
            WatermarkStyle.FRAME -> applyFrameWatermark(context, source, config)
            WatermarkStyle.TEXT -> applyTextWatermark(context, source, config)
            WatermarkStyle.FRAME_YG -> applyFrameWatermarkYG(context, source, config)
            WatermarkStyle.TEXT_YG -> applyTextWatermarkYG(context, source, config)
        }
    }

    /**
     * Frame watermark: adds a white border at the bottom of the image.
     * Layout faithfully follows FrameWatermark/content.json.
     *
     * Right side block (right|bottom, marginRight=192):
     *   With logo: [logo (h=388, centered)] [88px gap] [text column]
     *   Without logo: [text column at right|bottom, marginBottom=184/220]
     *
     * Text column:
     *   Narrow (device<=2680): lens size=136/baseline=126, secondary size=104/baseline=110
     *   Wide   (device>2680):  lens size=120/baseline=126, secondary size=93/baseline=110
     *
     * Left side: device name (height=416, margin=[192,0,0,136])
     */
    private fun applyFrameWatermark(
        context: Context,
        source: Bitmap,
        config: WatermarkConfig
    ): Bitmap {
        val imgWidth = source.width
        val imgHeight = source.height
        val scale = imgWidth / BASE_WIDTH

        val borderHeight = (FRAME_BORDER_HEIGHT * scale).toInt()
        val totalHeight = imgHeight + borderHeight

        val result = Bitmap.createBitmap(imgWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // Draw original image
        canvas.drawBitmap(source, 0f, 0f, null)

        // Draw white border (backgroundElements: color=#FFFFFF, alpha=1)
        val borderPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, imgHeight.toFloat(), imgWidth.toFloat(), totalHeight.toFloat(), borderPaint)

        // Load Honor logo
        val logoBitmap = try {
            context.assets.open("watermark/Honor/FrameWatermark/logo.png").use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            null
        }

        // Determine layout variant based on device element width range
        val isWideLayout = imgWidth > (2680 * scale)

        // Template dimensions (at BASE_WIDTH=6144)
        val marginRight = 192f
        val logoHeight = 388f
        val logoMarginGap = 88f

        // Text dimensions from template
        val lensFontSize: Float
        val lensBaseline: Float
        val lensBlockHeight: Float
        val lensTopMargin: Float
        val secondaryFontSize: Float
        val secondaryBaseline: Float
        val secondaryTopMargin: Float
        val timeLocationGap = 46f

        // Dimensions for lens-only without logo variant
        val lensOnlyFontSize: Float
        val lensOnlyBaseline: Float

        if (isWideLayout) {
            lensFontSize = 120f; lensBaseline = 126f; lensBlockHeight = 140f; lensTopMargin = 192f
            secondaryFontSize = 93f; secondaryBaseline = 110f; secondaryTopMargin = 24f
            lensOnlyFontSize = 116f; lensOnlyBaseline = 123f
        } else {
            lensFontSize = 136f; lensBaseline = 126f; lensBlockHeight = 159f; lensTopMargin = 201f
            secondaryFontSize = 104f; secondaryBaseline = 110f; secondaryTopMargin = 4f
            lensOnlyFontSize = 150f; lensOnlyBaseline = 159f
        }

        // Create paints with Honor font
        val lensPaint = createHonorPaint(context, 400).apply {
            color = Color.BLACK
            textSize = lensFontSize * scale
            textAlign = Paint.Align.LEFT
        }

        val secondaryPaint = createHonorPaint(context, 300).apply {
            color = Color.parseColor("#999999")
            textSize = secondaryFontSize * scale
            textAlign = Paint.Align.LEFT
        }

        // Prepare text content
        val lensText = config.lensInfo ?: ""
        val timeText = config.timeText ?: ""
        val locText = config.locationText ?: ""

        val hasLens = lensText.isNotEmpty()
        val hasTime = timeText.isNotEmpty()
        val hasLoc = locText.isNotEmpty()
        val hasSecondary = hasTime || hasLoc
        val hasLogo = logoBitmap != null

        // Measure text widths
        val lensWidth = if (hasLens) lensPaint.measureText(lensText) else 0f
        val timeWidth = if (hasTime) secondaryPaint.measureText(timeText) else 0f
        val gapWidth = if (hasTime && hasLoc) timeLocationGap * scale else 0f
        val locWidth = if (hasLoc) secondaryPaint.measureText(locText) else 0f
        val secondaryTotalWidth = timeWidth + gapWidth + locWidth
        val textBlockWidth = maxOf(lensWidth, secondaryTotalWidth)

        val scaledMarginRight = marginRight * scale
        val borderTop = imgHeight.toFloat()

        if (hasLogo && (hasLens || hasSecondary)) {
            // --- Layout: logo + text column, right|bottom aligned ---
            val textBlockRight = imgWidth - scaledMarginRight
            val textBlockLeft = textBlockRight - textBlockWidth

            // Draw logo (vertically centered in border)
            val scaledLogoHeight = logoHeight * scale
            val logoScale = scaledLogoHeight / logoBitmap!!.height.toFloat()
            val logoDrawWidth = logoBitmap.width * logoScale

            val logoX = textBlockLeft - (logoMarginGap * scale) - logoDrawWidth
            val logoY = borderTop + (borderHeight - scaledLogoHeight) / 2f

            val logoRect = Rect(
                logoX.toInt(), logoY.toInt(),
                (logoX + logoDrawWidth).toInt(), (logoY + scaledLogoHeight).toInt()
            )
            canvas.drawBitmap(logoBitmap, null, logoRect, Paint(Paint.FILTER_BITMAP_FLAG))
            logoBitmap.recycle()

            // Draw lens text (using baseline from template)
            if (hasLens) {
                val lensY = borderTop + (lensTopMargin * scale) + (lensBaseline * scale)
                canvas.drawText(lensText, textBlockLeft, lensY, lensPaint)
            }

            // Draw time and location
            if (hasSecondary) {
                val secondaryY = if (hasLens) {
                    borderTop + (lensTopMargin * scale) + (lensBlockHeight * scale) + (secondaryTopMargin * scale) + (secondaryBaseline * scale)
                } else {
                    // Center vertically if no lens
                    borderTop + borderHeight / 2f + (secondaryBaseline * scale) / 3f
                }

                var currentX = textBlockLeft
                if (hasTime) {
                    canvas.drawText(timeText, currentX, secondaryY, secondaryPaint)
                    currentX += timeWidth + gapWidth
                }
                if (hasLoc) {
                    canvas.drawText(locText, currentX, secondaryY, secondaryPaint)
                }
            }
        } else if (!hasLogo && hasLens) {
            // --- Layout: text only, no logo (vertical layout, right|bottom) ---
            // Template: margin=[0,0,192,184/220], layout_gravity=right|bottom
            val noLogoBottomMargin = (if (isWideLayout) 220f else 184f) * scale

            if (hasSecondary) {
                // Lens + secondary text
                val lensY = borderTop + borderHeight - noLogoBottomMargin - (lensBlockHeight * scale) - (secondaryTopMargin * scale) + (lensBaseline * scale)
                lensPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(lensText, imgWidth - scaledMarginRight, lensY, lensPaint)

                val secondaryY = borderTop + borderHeight - noLogoBottomMargin + (secondaryBaseline * scale)
                secondaryPaint.textAlign = Paint.Align.RIGHT
                var currentX = imgWidth - scaledMarginRight
                // Right-align, draw location first then time to the left
                if (hasLoc && hasTime) {
                    canvas.drawText(locText, currentX, secondaryY, secondaryPaint)
                    currentX -= locWidth + gapWidth
                    canvas.drawText(timeText, currentX, secondaryY, secondaryPaint)
                } else if (hasTime) {
                    canvas.drawText(timeText, currentX, secondaryY, secondaryPaint)
                } else if (hasLoc) {
                    canvas.drawText(locText, currentX, secondaryY, secondaryPaint)
                }
            } else {
                // Lens only: use larger standalone font
                val lensOnlyPaint = createHonorPaint(context, 400).apply {
                    color = Color.BLACK
                    textSize = lensOnlyFontSize * scale
                    textAlign = Paint.Align.RIGHT
                }
                val lensOnlyMarginBottom = (if (isWideLayout) 263f else 239f) * scale
                val lensY = borderTop + borderHeight - lensOnlyMarginBottom
                canvas.drawText(lensText, imgWidth - scaledMarginRight, lensY, lensOnlyPaint)
            }
        } else if (hasLogo) {
            // Logo only (lens-only with logo uses bigger font)
            val scaledLogoHeight = logoHeight * scale
            val logoScale = scaledLogoHeight / logoBitmap!!.height.toFloat()
            val logoDrawWidth = logoBitmap.width * logoScale

            val logoX = imgWidth - scaledMarginRight - logoDrawWidth
            val logoY = borderTop + (borderHeight - scaledLogoHeight) / 2f

            val logoRect = Rect(
                logoX.toInt(), logoY.toInt(),
                (logoX + logoDrawWidth).toInt(), (logoY + scaledLogoHeight).toInt()
            )
            canvas.drawBitmap(logoBitmap, null, logoRect, Paint(Paint.FILTER_BITMAP_FLAG))
            logoBitmap.recycle()
        }

        // Draw device name on left side
        // Template: device height=416, margin=[192,0,0,136], layout_gravity=left|bottom
        // Element is vertically centered in the 688px border (136 top + 416 element + 136 bottom)
        if (!config.deviceName.isNullOrEmpty()) {
            val deviceMarginLeft = 192f * scale

            val devicePaint = createHonorPaint(context, 800).apply {
                color = Color.BLACK
                textSize = 150f * scale
                textAlign = Paint.Align.LEFT
            }

            // Vertically center text within the 416px element box
            val elementTop = borderTop + 136f * scale
            val elementBottom = totalHeight.toFloat() - 136f * scale
            val elementCenterY = (elementTop + elementBottom) / 2f
            val deviceY = elementCenterY - (devicePaint.ascent() + devicePaint.descent()) / 2f
            canvas.drawText(config.deviceName, deviceMarginLeft, deviceY, devicePaint)
        }

        return result
    }

    /**
     * Text watermark: overlays time and location on the bottom-right of the image.
     * Faithfully follows TextWatermark/content.json.
     *
     * Narrow (<=3072): time size=168 baseline=156, location size=152 baseline=161,
     *                  margin=[0,0,304,112], locationMarginTop=-21
     * Wide   (>3072):  time size=144 baseline=134, location size=128 baseline=136,
     *                  margin=[0,0,304,152], locationMarginTop=-4
     * Device: left|bottom, height=464, margin=[304,0,0,176]
     */
    private fun applyTextWatermark(
        context: Context,
        source: Bitmap,
        config: WatermarkConfig
    ): Bitmap {
        val imgWidth = source.width
        val imgHeight = source.height
        val scale = imgWidth / BASE_WIDTH

        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        // Determine layout variant based on device width range
        val isWideLayout = imgWidth > (3072 * scale)

        // Template values from content.json
        val timeFontSize: Float
        val timeBaseline: Float
        val timeBlockHeight: Float
        val locationFontSize: Float
        val locationBaseline: Float
        val marginRight = 304f
        val marginBottom: Float
        val locationMarginTop: Float

        if (isWideLayout) {
            timeFontSize = 144f; timeBaseline = 134f; timeBlockHeight = 169f
            locationFontSize = 128f; locationBaseline = 136f
            marginBottom = 152f; locationMarginTop = -4f
        } else {
            timeFontSize = 168f; timeBaseline = 156f; timeBlockHeight = 197f
            locationFontSize = 152f; locationBaseline = 161f
            marginBottom = 112f; locationMarginTop = -21f
        }

        val timeText = config.timeText ?: ""
        val locText = config.locationText ?: ""

        // Time paint with Honor font (wght 300, #FFFFFF)
        val timePaint = createHonorPaint(context, 300).apply {
            color = Color.WHITE
            textSize = timeFontSize * scale
            textAlign = Paint.Align.RIGHT
            setShadowLayer(4 * scale, 1 * scale, 1 * scale, Color.argb(80, 0, 0, 0))
        }

        // Location paint with Honor font (wght 300, #FFFFFF)
        val locationPaint = createHonorPaint(context, 300).apply {
            color = Color.WHITE
            textSize = locationFontSize * scale
            textAlign = Paint.Align.RIGHT
            setShadowLayer(4 * scale, 1 * scale, 1 * scale, Color.argb(80, 0, 0, 0))
        }

        val rightX = imgWidth - (marginRight * scale)

        // Vertical layout: time on top, location below (layout_gravity=right|bottom)
        if (timeText.isNotEmpty() && locText.isNotEmpty()) {
            // Location element bottom aligns to marginBottom from image bottom
            // Location baseline is at top of location element + locationBaseline
            // Time+Location vertical stack: time block (height) + locationMarginTop + location block
            
            // Calculate location block bottom
            val locBlockBottom = imgHeight - (marginBottom * scale)
            val locBaselineY = locBlockBottom - locationPaint.descent()
            canvas.drawText(locText, rightX, locBaselineY, locationPaint)

            // Time block sits above location, with locationMarginTop gap
            val timeBlockBottom = locBlockBottom - locationPaint.textSize + (locationMarginTop * scale)
            val timeBaselineY = timeBlockBottom - timePaint.descent()
            canvas.drawText(timeText, rightX, timeBaselineY, timePaint)
        } else if (timeText.isNotEmpty()) {
            // Time only: use larger size variant from template
            val timeOnlyPaint = createHonorPaint(context, 300).apply {
                color = Color.WHITE
                textSize = (if (isWideLayout) 144f else 184f) * scale
                textAlign = Paint.Align.RIGHT
                setShadowLayer(4 * scale, 1 * scale, 1 * scale, Color.argb(80, 0, 0, 0))
            }
            val bottomMargin = (if (isWideLayout) 232f else 192f) * scale
            canvas.drawText(timeText, rightX, imgHeight - bottomMargin, timeOnlyPaint)
        } else if (locText.isNotEmpty()) {
            // Location only: use larger size variant from template
            val locOnlyPaint = createHonorPaint(context, 300).apply {
                color = Color.WHITE
                textSize = (if (isWideLayout) 140f else 184f) * scale
                textAlign = Paint.Align.RIGHT
                setShadowLayer(4 * scale, 1 * scale, 1 * scale, Color.argb(80, 0, 0, 0))
            }
            val bottomMargin = (if (isWideLayout) 216f else 192f) * scale
            canvas.drawText(locText, rightX, imgHeight - bottomMargin, locOnlyPaint)
        }

        // Draw device name on left side
        // Template: device height=464, margin=[304,0,0,176], left|bottom
        // Vertically center text within the 464px element box
        if (!config.deviceName.isNullOrEmpty()) {
            val deviceMarginLeft = 304f * scale

            val devicePaint = createHonorPaint(context, 1000).apply {
                color = Color.WHITE
                textSize = 140f * scale
                textAlign = Paint.Align.LEFT
                setShadowLayer(4 * scale, 1 * scale, 1 * scale, Color.argb(80, 0, 0, 0))
            }

            val elementBottom = imgHeight.toFloat() - 176f * scale
            val elementTop = elementBottom - 464f * scale
            val elementCenterY = (elementTop + elementBottom) / 2f
            val deviceY = elementCenterY - (devicePaint.ascent() + devicePaint.descent()) / 2f
            canvas.drawText(config.deviceName, deviceMarginLeft, deviceY, devicePaint)
        }

        return result
    }

    /**
     * Generates a default time string matching Honor format.
     */
    fun getDefaultTimeString(): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }

    /**
     * Generates a time string from EXIF datetime.
     */
    fun formatExifDateTime(exifDateTime: String?): String? {
        if (exifDateTime.isNullOrEmpty()) return null
        return try {
            val inputFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
            val outputFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
            val date = inputFormat.parse(exifDateTime)
            date?.let { outputFormat.format(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Builds lens info string from EXIF data.
     * Format: "27mm  f/1.9  1/100s  ISO1600"
     */
    fun buildLensInfoFromExif(
        focalLength: String?,
        fNumber: String?,
        exposureTime: String?,
        iso: String?
    ): String {
        val parts = mutableListOf<String>()
        focalLength?.let { parts.add("${it}mm") }
        fNumber?.let { parts.add("f/$it") }
        exposureTime?.let { parts.add("${it}s") }
        iso?.let { parts.add("ISO$it") }
        return parts.joinToString("  ")
    }

    /**
     * Frame watermark YG variant (Harcourt Touch Paris collaboration).
     * Based on FrameWatermarkYG/content.json:
     *   - White border at bottom (688px at 6144 base, same as standard Frame)
     *   - Device name on left: height=416, margin=[192,0,0,136], left|bottom
     *   - YG logo (672×504 @6144 width) at right-bottom, margin=[0,0,188,92]
     */
    private fun applyFrameWatermarkYG(
        context: Context,
        source: Bitmap,
        config: WatermarkConfig
    ): Bitmap {
        val imgWidth = source.width
        val imgHeight = source.height
        val scale = imgWidth / BASE_WIDTH

        val borderHeight = (FRAME_BORDER_HEIGHT * scale).toInt()
        val totalHeight = imgHeight + borderHeight

        val result = Bitmap.createBitmap(imgWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // Draw original image
        canvas.drawBitmap(source, 0f, 0f, null)

        // Draw white border
        val borderPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, imgHeight.toFloat(), imgWidth.toFloat(), totalHeight.toFloat(), borderPaint)

        val borderTop = imgHeight.toFloat()

        // Draw device name on left (same positioning as standard FrameWatermark)
        // Template: device height=416, margin=[192,0,0,136], layout_gravity=left|bottom
        if (!config.deviceName.isNullOrEmpty()) {
            val devicePaint = createHonorPaint(context, 1000).apply {
                color = Color.BLACK
                textSize = 150f * scale
                textAlign = Paint.Align.LEFT
            }
            val deviceMarginLeft = 192f * scale
            val elementTop = borderTop + 136f * scale
            val elementBottom = totalHeight.toFloat() - 136f * scale
            val elementCenterY = (elementTop + elementBottom) / 2f
            val deviceY = elementCenterY - (devicePaint.ascent() + devicePaint.descent()) / 2f
            canvas.drawText(config.deviceName, deviceMarginLeft, deviceY, devicePaint)
        }

        // Load and draw YG logo
        // From content.json: width=672, height=504, margin=[0,0,188,92], right|bottom
        val ygBitmap = try {
            context.assets.open("watermark/Honor/FrameWatermarkYG/yg.png").use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            null
        }

        ygBitmap?.let { yg ->
            val logoDrawWidth = 672f * scale
            val logoDrawHeight = 504f * scale
            val marginRight = 188f * scale
            val marginBottom = 92f * scale

            val logoX = imgWidth - logoDrawWidth - marginRight
            val logoY = totalHeight - logoDrawHeight - marginBottom

            val logoRect = Rect(
                logoX.toInt(), logoY.toInt(),
                (logoX + logoDrawWidth).toInt(), (logoY + logoDrawHeight).toInt()
            )
            canvas.drawBitmap(yg, null, logoRect, Paint(Paint.FILTER_BITMAP_FLAG))
            yg.recycle()
        }

        return result
    }

    /**
     * Text watermark YG variant (Harcourt Touch Paris collaboration).
     * Based on TextWatermarkYG/content.json:
     *   - Device name on left (overlaid on image): height=464, margin=[304,0,0,176], left|bottom
     *   - YG logo (672×504 @6144 width) at right-bottom, margin=[0,0,299,86]
     */
    private fun applyTextWatermarkYG(
        context: Context,
        source: Bitmap,
        config: WatermarkConfig
    ): Bitmap {
        val imgWidth = source.width
        val imgHeight = source.height
        val scale = imgWidth / BASE_WIDTH

        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        // Draw device name on left (same positioning as standard TextWatermark)
        // Template: device height=464, margin=[304,0,0,176], left|bottom
        if (!config.deviceName.isNullOrEmpty()) {
            val devicePaint = createHonorPaint(context, 1000).apply {
                color = Color.WHITE
                textSize = 140f * scale
                textAlign = Paint.Align.LEFT
                setShadowLayer(4 * scale, 1 * scale, 1 * scale, Color.argb(80, 0, 0, 0))
            }
            val deviceMarginLeft = 304f * scale
            val elementBottom = imgHeight.toFloat() - 176f * scale
            val elementTop = elementBottom - 464f * scale
            val elementCenterY = (elementTop + elementBottom) / 2f
            val deviceY = elementCenterY - (devicePaint.ascent() + devicePaint.descent()) / 2f
            canvas.drawText(config.deviceName, deviceMarginLeft, deviceY, devicePaint)
        }

        // Load and draw YG logo
        // From content.json: width=672, height=504, margin=[0,0,299,86], right|bottom
        val ygBitmap = try {
            context.assets.open("watermark/Honor/TextWatermarkYG/yg.png").use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            null
        }

        ygBitmap?.let { yg ->
            val logoDrawWidth = 672f * scale
            val logoDrawHeight = 504f * scale
            val marginRight = 299f * scale
            val marginBottom = 86f * scale

            val logoX = imgWidth - logoDrawWidth - marginRight
            val logoY = imgHeight - logoDrawHeight - marginBottom

            val logoRect = Rect(
                logoX.toInt(), logoY.toInt(),
                (logoX + logoDrawWidth).toInt(), (logoY + logoDrawHeight).toInt()
            )
            canvas.drawBitmap(yg, null, logoRect, Paint(Paint.FILTER_BITMAP_FLAG))
            yg.recycle()
        }

        return result
    }
}
