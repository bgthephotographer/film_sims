package com.tqmane.filmsim.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import com.tqmane.filmsim.R
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
        NONE, FRAME, TEXT, FRAME_YG, TEXT_YG,
        MEIZU_NORM, MEIZU_PRO,
        MEIZU_Z1, MEIZU_Z2, MEIZU_Z3, MEIZU_Z4, MEIZU_Z5, MEIZU_Z6, MEIZU_Z7,
        VIVO_ZEISS, VIVO_CLASSIC, VIVO_PRO, VIVO_IQOO
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
            Typeface.createFromAsset(context.assets, context.getString(R.string.honor_font_path)).also {
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
            WatermarkStyle.MEIZU_NORM -> applyMeizuNorm(context, source, config)
            WatermarkStyle.MEIZU_PRO -> applyMeizuPro(context, source, config)
            WatermarkStyle.MEIZU_Z1 -> applyMeizuZ1(context, source, config)
            WatermarkStyle.MEIZU_Z2 -> applyMeizuZ2(context, source, config)
            WatermarkStyle.MEIZU_Z3 -> applyMeizuZ3(context, source, config)
            WatermarkStyle.MEIZU_Z4 -> applyMeizuZ4(context, source, config)
            WatermarkStyle.MEIZU_Z5 -> applyMeizuZ5(context, source, config)
            WatermarkStyle.MEIZU_Z6 -> applyMeizuZ6(context, source, config)
            WatermarkStyle.MEIZU_Z7 -> applyMeizuZ7(context, source, config)
            WatermarkStyle.VIVO_ZEISS -> applyVivoZeiss(context, source, config)
            WatermarkStyle.VIVO_CLASSIC -> applyVivoClassic(context, source, config)
            WatermarkStyle.VIVO_PRO -> applyVivoPro(context, source, config)
            WatermarkStyle.VIVO_IQOO -> applyVivoIqoo(context, source, config)
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

    // ==================== Meizu Watermarks ====================

    // Cached Meizu typefaces
    private var meizuDeviceTypeface: Typeface? = null  // MEIZUCamera-Medium (typeface="-1")
    private var meizuTextMedium: Typeface? = null       // TT Fors Medium
    private var meizuTextRegular: Typeface? = null      // TT Fors Regular

    private fun getMeizuDeviceTypeface(context: Context): Typeface {
        meizuDeviceTypeface?.let { return it }
        return try {
            Typeface.createFromAsset(context.assets, "watermark/Meizu/fonts/MEIZUCamera-Medium.otf").also {
                meizuDeviceTypeface = it
            }
        } catch (_: Exception) {
            Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
    }

    private fun getMeizuTextMedium(context: Context): Typeface {
        meizuTextMedium?.let { return it }
        return try {
            Typeface.createFromAsset(context.assets, "watermark/Meizu/fonts/TTForsMedium.ttf").also {
                meizuTextMedium = it
            }
        } catch (_: Exception) {
            Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
    }

    private fun getMeizuTextRegular(context: Context): Typeface {
        meizuTextRegular?.let { return it }
        return try {
            Typeface.createFromAsset(context.assets, "watermark/Meizu/fonts/TTForsRegular.ttf").also {
                meizuTextRegular = it
            }
        } catch (_: Exception) {
            Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

    private fun loadMeizuLogo(context: Context, name: String): Bitmap? {
        return try {
            context.assets.open("watermark/Meizu/logos/$name").use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) { null }
    }

    /** Meizu brand accent: small filled red circle. */
    private fun drawMeizuRedDot(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 65, 50)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, 8f * s, dotPaint)
    }

    /**
     * Split lens info into discrete parts for separator-style rendering.
     * Splits by double-space or explicit "|" delimiter in user input.
     */
    private fun splitDiscreteParts(lensInfo: String?): List<String> {
        if (lensInfo.isNullOrBlank()) return emptyList()
        // If user already included "|", split by that
        if ("|" in lensInfo) return lensInfo.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        // Otherwise split by 2+ spaces
        return lensInfo.split(Regex("\\s{2,}")).filter { it.isNotEmpty() }
    }

    /**
     * Draws discrete text parts separated by thin "|" lines, centered horizontally.
     * Used by z3, z5, z7 for lensInfo_discrete / lensInfo_location rendering.
     * @param separatorColor color for the "|" line
     */
    private fun drawDiscreteText(
        canvas: Canvas,
        parts: List<String>,
        centerX: Float,
        baselineY: Float,
        textPaint: Paint,
        s: Float,
        separatorColor: Int = Color.parseColor("#D9D9D9"),
        gap: Float = 20f * s  // gap on each side of the separator
    ) {
        if (parts.isEmpty()) return
        if (parts.size == 1) {
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(parts[0], centerX, baselineY, textPaint)
            return
        }

        val sepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = separatorColor
            strokeWidth = 1f * s
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        // Measure total width
        textPaint.textAlign = Paint.Align.LEFT
        var totalWidth = 0f
        for (i in parts.indices) {
            totalWidth += textPaint.measureText(parts[i])
            if (i < parts.size - 1) totalWidth += gap * 2f // gap + sep + gap
        }

        // Draw from left, centered
        var x = centerX - totalWidth / 2f
        val sepTop = baselineY + textPaint.ascent() * 0.7f  // top of separator line
        val sepBottom = baselineY + textPaint.descent() * 0.3f // bottom of separator line

        for (i in parts.indices) {
            canvas.drawText(parts[i], x, baselineY, textPaint)
            x += textPaint.measureText(parts[i])
            if (i < parts.size - 1) {
                x += gap
                canvas.drawLine(x, sepTop, x, sepBottom, sepPaint)
                x += gap
            }
        }
    }

    // ---- Norm ----
    /**
     * Norm: Transparent overlay on image bottom. Horizontal container with
     * device (44sp, white, bold) + nickName (30sp, white 0.8α) + time (30sp, white 0.8α)
     * XML: type=1, basePortWidth=1530, container height=122, transY=-122, marginL/R=78
     */
    private fun applyMeizuNorm(
        context: Context, source: Bitmap, config: WatermarkConfig
    ): Bitmap {
        val imgWidth = source.width
        val imgHeight = source.height
        val s = imgWidth / 1530f

        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val containerH = 122f * s
        val marginLR = 78f * s
        val containerTop = imgHeight - containerH

        val deviceText = config.deviceName ?: ""
        val timeText = config.timeText ?: ""

        val devicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getMeizuDeviceTypeface(context)
            textSize = 44f * s
            color = Color.WHITE
            textAlign = Paint.Align.LEFT
            setShadowLayer(4f * s, 1f * s, 1f * s, Color.argb(80, 0, 0, 0))
        }

        val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getMeizuTextRegular(context)
            textSize = 30f * s
            color = Color.WHITE
            alpha = (255 * 0.8f).toInt()
            textAlign = Paint.Align.LEFT
            setShadowLayer(4f * s, 1f * s, 1f * s, Color.argb(80, 0, 0, 0))
        }

        val centerY = containerTop + containerH / 2f
        var currentX = marginLR
        val itemGap = 8f * s

        if (deviceText.isNotEmpty()) {
            val y = centerY - (devicePaint.ascent() + devicePaint.descent()) / 2f
            canvas.drawText(deviceText, currentX, y, devicePaint)
            currentX += devicePaint.measureText(deviceText) + itemGap
        }

        if (timeText.isNotEmpty()) {
            val y = centerY - (secondaryPaint.ascent() + secondaryPaint.descent()) / 2f
            canvas.drawText(timeText, currentX, y, secondaryPaint)
        }

        return result
    }

    // ---- Pro ----
    /**
     * Pro: White bottom bar. Left: device (45sp, black, MEIZUCamera) + red dot.
     * Right: vertical stack of lensInfo (35sp, black) + time (24sp, #A6A6A6 letterSpacing=0.1).
     * XML: type=1, basePortWidth=1530, background=#FFFFFF, container height=160, marginL/R=85
     */
    private fun applyMeizuPro(
        context: Context, source: Bitmap, config: WatermarkConfig
    ): Bitmap {
        val imgWidth = source.width
        val imgHeight = source.height
        val s = imgWidth / 1530f

        val barHeight = (160f * s).toInt()
        val totalHeight = imgHeight + barHeight

        val result = Bitmap.createBitmap(imgWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(source, 0f, 0f, null)

        // White bar
        val whitePaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        canvas.drawRect(0f, imgHeight.toFloat(), imgWidth.toFloat(), totalHeight.toFloat(), whitePaint)

        val barTop = imgHeight.toFloat()
        val marginL = 85f * s
        val marginR = 85f * s
        val barCenterY = barTop + barHeight / 2f

        // Device (left, vertically centered)
        val deviceText = config.deviceName ?: ""
        val devicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getMeizuDeviceTypeface(context)
            textSize = 45f * s
            color = Color.BLACK
            textAlign = Paint.Align.LEFT
        }

        var deviceEndX = marginL
        if (deviceText.isNotEmpty()) {
            val y = barCenterY - (devicePaint.ascent() + devicePaint.descent()) / 2f
            canvas.drawText(deviceText, marginL, y, devicePaint)
            deviceEndX = marginL + devicePaint.measureText(deviceText) + 20f * s
            // Red dot after device name
            drawMeizuRedDot(canvas, deviceEndX, barCenterY, s)
        }

        // Right column: lensInfo (top) + time (below)
        val lensText = config.lensInfo ?: ""
        val timeText = config.timeText ?: ""
        val rightX = imgWidth - marginR

        val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getMeizuTextMedium(context)
            textSize = 35f * s
            color = Color.BLACK
            textAlign = Paint.Align.RIGHT
        }

        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getMeizuTextRegular(context)
            textSize = 24f * s
            color = Color.parseColor("#A6A6A6")
            letterSpacing = 0.1f
            textAlign = Paint.Align.RIGHT
        }

        if (lensText.isNotEmpty() && timeText.isNotEmpty()) {
            val lensH = lensPaint.descent() - lensPaint.ascent()
            val gapH = 3f * s
            val timeH = timePaint.descent() - timePaint.ascent()
            val totalH = lensH + gapH + timeH
            val groupTop = barTop + (barHeight - totalH) / 2f

            canvas.drawText(lensText, rightX, groupTop - lensPaint.ascent(), lensPaint)
            canvas.drawText(timeText, rightX, groupTop + lensH + gapH - timePaint.ascent(), timePaint)
        } else if (lensText.isNotEmpty()) {
            val y = barCenterY - (lensPaint.ascent() + lensPaint.descent()) / 2f
            canvas.drawText(lensText, rightX, y, lensPaint)
        } else if (timeText.isNotEmpty()) {
            val y = barCenterY - (timePaint.ascent() + timePaint.descent()) / 2f
            canvas.drawText(timeText, rightX, y, timePaint)
        }

        return result
    }

    // ---- Z1 ----
    /**
     * Z1: White frame, photo inset. Centered device name + lens info below.
     * XML: type=2, basePortWidth=1470, image margin L/T/R=30,
     *      device marginT=40 size=45 black, lensInfo marginT=16 marginB=51 size=32 gray 0.6α
     * Reference: upper text row (device, black bold) + lower text row (lens, light gray 4 sub-groups)
     */
    private fun applyMeizuZ1(
        context: Context, source: Bitmap, config: WatermarkConfig
    ): Bitmap {
        val s = source.width / 1470f
        val marginSide = 30f * s
        val marginTop = 30f * s

        val deviceText = config.deviceName ?: ""
        val lensText = config.lensInfo ?: ""

        val devicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getMeizuDeviceTypeface(context)
            textSize = 45f * s
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }
        val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getMeizuTextRegular(context)
            textSize = 32f * s
            color = Color.parseColor("#49454F")
            alpha = (255 * 0.6f).toInt()
            textAlign = Paint.Align.CENTER
        }

        val deviceH = if (deviceText.isNotEmpty()) (devicePaint.descent() - devicePaint.ascent()) else 0f
        val lensH = if (lensText.isNotEmpty()) (lensPaint.descent() - lensPaint.ascent()) else 0f
        val textAreaH = 40f * s + deviceH +
            (if (lensText.isNotEmpty()) 16f * s + lensH else 0f) + 51f * s

        val photoW = (source.width - 2 * marginSide).toInt()
        val photoH = (source.height * photoW / source.width.toFloat()).toInt()
        val totalW = source.width
        val totalH = (marginTop + photoH + textAreaH).toInt()

        val result = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val photoRect = Rect(marginSide.toInt(), marginTop.toInt(),
            (marginSide + photoW).toInt(), (marginTop + photoH).toInt())
        canvas.drawBitmap(source, null, photoRect, Paint(Paint.FILTER_BITMAP_FLAG))

        val centerX = totalW / 2f
        var currentY = marginTop + photoH + 40f * s

        if (deviceText.isNotEmpty()) {
            currentY -= devicePaint.ascent()
            canvas.drawText(deviceText, centerX, currentY, devicePaint)
            currentY += devicePaint.descent()
        }

        if (lensText.isNotEmpty()) {
            currentY += 16f * s
            currentY -= lensPaint.ascent()
            canvas.drawText(lensText, centerX, currentY, lensPaint)
        }

        return result
    }

    // ---- Z2 ----
    /**
     * Z2: Polaroid-style wide white frame. Adaptive icon (left) + lens info (right) at bottom.
     * XML: type=2, basePortWidth=1130, image margin=200,
     *      bottom container marginT=247 marginB=245 marginLR=200,
     *      adaptiveIcon 462×48 left, lensInfo 28sp #3C3C43 0.6α right
     * Reference: large uniform frame, device text (left) + light gray lens groups (right)
     */
    private fun applyMeizuZ2(
        context: Context, source: Bitmap, config: WatermarkConfig
    ): Bitmap {
        val s = source.width / 1130f
        val margin = 200f * s
        val marginT = 200f * s

        val lensText = config.lensInfo ?: ""
        val deviceText = config.deviceName ?: ""

        val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getMeizuTextRegular(context)
            textSize = 28f * s
            color = Color.parseColor("#3C3C43")
            alpha = (255 * 0.6f).toInt()
            textAlign = Paint.Align.RIGHT
        }

        // Device as substitute for encrypted adaptive icon (462×48)
        val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getMeizuDeviceTypeface(context)
            textSize = 38f * s
            color = Color.BLACK
            textAlign = Paint.Align.LEFT
        }

        val iconH = 48f * s
        val bottomBarH = 247f * s + iconH + 245f * s

        val photoW = (source.width - 2 * margin).toInt()
        val photoH = (source.height * photoW / source.width.toFloat()).toInt()
        val totalW = source.width
        val totalH = (marginT + photoH + bottomBarH).toInt()

        val result = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val photoRect = Rect(margin.toInt(), marginT.toInt(),
            (margin + photoW).toInt(), (marginT + photoH).toInt())
        canvas.drawBitmap(source, null, photoRect, Paint(Paint.FILTER_BITMAP_FLAG))

        // Bottom: device text (left) + lens info (right), vertically centered in icon row
        val barCenterY = marginT + photoH + 247f * s + iconH / 2f

        if (deviceText.isNotEmpty()) {
            val y = barCenterY - (logoPaint.ascent() + logoPaint.descent()) / 2f
            canvas.drawText(deviceText, margin, y, logoPaint)
        }

        if (lensText.isNotEmpty()) {
            val y = barCenterY - (lensPaint.ascent() + lensPaint.descent()) / 2f
            canvas.drawText(lensText, totalW - margin, y, lensPaint)
        }

        return result
    }

    // ---- Z3 ----
    /**
     * Z3: White frame, photo inset. Device name (50sp, black, centered) above.
     * Discrete lens info with "|" separators below (30sp, gray 0.6α).
     * XML: type=2, basePortWidth=1470, image margin L/T/R=30,
     *      device marginT=53 size=50, lensInfo_discrete w=859 h=107 marginT=53 marginB=75 size=30
     * Reference: TEXT ····· TEXT | TEXT | TEXT pattern with 1px gray separators
     */
    private fun applyMeizuZ3(
        context: Context, source: Bitmap, config: WatermarkConfig
    ): Bitmap {
        val s = source.width / 1470f
        val marginSide = 30f * s
        val marginTop = 30f * s

        val deviceText = config.deviceName ?: ""
        val lensParts = splitDiscreteParts(config.lensInfo)

        val devicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getMeizuDeviceTypeface(context)
            textSize = 50f * s
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }
        val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getMeizuTextRegular(context)
            textSize = 30f * s
            color = Color.parseColor("#49454F")
            alpha = (255 * 0.6f).toInt()
        }

        val deviceH = if (deviceText.isNotEmpty()) (devicePaint.descent() - devicePaint.ascent()) else 0f
        val discreteAreaH = 107f * s
        val textAreaH = 53f * s + deviceH +
            (if (lensParts.isNotEmpty()) 53f * s + discreteAreaH else 0f) + 75f * s

        val photoW = (source.width - 2 * marginSide).toInt()
        val photoH = (source.height * photoW / source.width.toFloat()).toInt()
        val totalW = source.width
        val totalH = (marginTop + photoH + textAreaH).toInt()

        val result = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val photoRect = Rect(marginSide.toInt(), marginTop.toInt(),
            (marginSide + photoW).toInt(), (marginTop + photoH).toInt())
        canvas.drawBitmap(source, null, photoRect, Paint(Paint.FILTER_BITMAP_FLAG))

        val centerX = totalW / 2f
        var currentY = marginTop + photoH + 53f * s

        if (deviceText.isNotEmpty()) {
            currentY -= devicePaint.ascent()
            canvas.drawText(deviceText, centerX, currentY, devicePaint)
            currentY += devicePaint.descent()
        }

        if (lensParts.isNotEmpty()) {
            currentY += 53f * s
            val baselineY = currentY + discreteAreaH / 2f - (lensPaint.ascent() + lensPaint.descent()) / 2f
            drawDiscreteText(canvas, lensParts, centerX, baselineY, lensPaint, s)
        }

        return result
    }

    // ---- Z4 ----
    /**
     * Z4: Photo fills left side, white panel on right with rotated text + red dot.
     * XML: type=2, basePortWidth=1530, background=#FFFFFF, orientation=horizontal,
     *      lensInfo marginB=100 marginL=40 size=32 rotation=90 gray 0.6α,
     *      device marginB=143 marginL=15 marginR=40 size=45 black rotation=90
     * Reference: 50px right panel at 383px scale, text reads bottom-to-top,
     *            device (black) + lens (black) stacked, red dot at bottom.
     * Right panel width = marginL(40) + lensTextH(32) + gap(15) + deviceTextH(45) + marginR(40) = 172
     */
    private fun applyMeizuZ4(
        context: Context, source: Bitmap, config: WatermarkConfig
    ): Bitmap {
        val s = source.width / 1530f   // NOTE: scale from basePortWidth minus panel would be more accurate
        // but using full base width for consistent scaling

        // Right panel dimensions
        val panelMarginL = 40f * s    // gap between photo and first text
        val lensTextH = 32f * s       // font size → horizontal width when rotated
        val textGap = 15f * s         // gap between lens and device text columns
        val deviceTextH = 45f * s
        val panelMarginR = 40f * s
        val panelWidth = panelMarginL + lensTextH + textGap + deviceTextH + panelMarginR

        // Photo occupies the remaining width
        val photoW = (source.width - panelWidth).toInt()
        val photoH = (source.height * photoW / source.width.toFloat()).toInt()
        val totalW = source.width
        val totalH = photoH

        val result = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        // Photo on left
        val photoRect = Rect(0, 0, photoW, photoH)
        canvas.drawBitmap(source, null, photoRect, Paint(Paint.FILTER_BITMAP_FLAG))

        val deviceText = config.deviceName ?: ""
        val lensText = config.lensInfo ?: ""

        val devicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getMeizuDeviceTypeface(context)
            textSize = 45f * s
            color = Color.BLACK
            textAlign = Paint.Align.LEFT
        }
        val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getMeizuTextRegular(context)
            textSize = 32f * s
            color = Color.parseColor("#49454F")
            alpha = (255 * 0.6f).toInt()
            textAlign = Paint.Align.LEFT
        }

        // Device text: rotated 90° CW, reads bottom-to-top
        // Positioned at rightmost column, marginB=143 from bottom, marginR=40 from right
        // Device text: rotated 90° CW, reads bottom-to-top
        // In rightmost column, marginB=143 from bottom, marginR=40 from right
        if (deviceText.isNotEmpty()) {
            canvas.save()
            val colCenterX = totalW - panelMarginR - deviceTextH / 2f
            val textStartY = totalH - 143f * s
            canvas.translate(colCenterX, textStartY)
            canvas.rotate(-90f)
            val centerOffset = -(devicePaint.ascent() + devicePaint.descent()) / 2f
            canvas.drawText(deviceText, 0f, centerOffset, devicePaint)
            canvas.restore()
        }

        // Lens text: rotated 90° CW, to the LEFT of device column
        // marginB=100, marginL=40 from image
        if (lensText.isNotEmpty()) {
            canvas.save()
            val colCenterX = totalW - panelMarginR - deviceTextH - textGap - lensTextH / 2f
            val textStartY = totalH - 100f * s
            canvas.translate(colCenterX, textStartY)
            canvas.rotate(-90f)
            val centerOffset = -(lensPaint.ascent() + lensPaint.descent()) / 2f
            canvas.drawText(lensText, 0f, centerOffset, lensPaint)
            canvas.restore()
        }

        // Red dot near bottom of right panel, centered in device column
        val dotColX = totalW - panelMarginR - deviceTextH / 2f
        drawMeizuRedDot(canvas, dotColX, totalH - 40f * s, s)

        return result
    }

    // ---- Z5 ----
    /**
     * Z5: White frame, photo inset. Device name centered + lens+location info below.
     * XML: type=2, basePortWidth=1220, image marginLR=155 marginT=170,
     *      device marginT=150 size=45 black, lensInfo_location marginT=16 marginB=183 size=32 gray 0.6α
     * Reference: 6 light gray text groups below a thin decorative line,
     *            lens parts + gap + location parts
     */
    private fun applyMeizuZ5(
        context: Context, source: Bitmap, config: WatermarkConfig
    ): Bitmap {
        val s = source.width / 1220f
        val marginSide = 155f * s
        val marginTop = 170f * s

        val deviceText = config.deviceName ?: ""
        // Build combined parts: lens info parts + location
        val lensParts = splitDiscreteParts(config.lensInfo)
        val locationParts = if (!config.locationText.isNullOrBlank())
            listOf(config.locationText!!) else emptyList()
        val allParts = lensParts + locationParts

        val devicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getMeizuDeviceTypeface(context)
            textSize = 45f * s
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }
        val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getMeizuTextRegular(context)
            textSize = 32f * s
            color = Color.parseColor("#49454F")
            alpha = (255 * 0.6f).toInt()
        }

        val deviceH = if (deviceText.isNotEmpty()) (devicePaint.descent() - devicePaint.ascent()) else 0f
        val infoH = if (allParts.isNotEmpty()) (infoPaint.descent() - infoPaint.ascent()) else 0f
        val textAreaH = 150f * s + deviceH +
            (if (allParts.isNotEmpty()) 16f * s + infoH else 0f) + 183f * s

        val photoW = (source.width - 2 * marginSide).toInt()
        val photoH = (source.height * photoW / source.width.toFloat()).toInt()
        val totalW = source.width
        val totalH = (marginTop + photoH + textAreaH).toInt()

        val result = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val photoRect = Rect(marginSide.toInt(), marginTop.toInt(),
            (marginSide + photoW).toInt(), (marginTop + photoH).toInt())
        canvas.drawBitmap(source, null, photoRect, Paint(Paint.FILTER_BITMAP_FLAG))

        val centerX = totalW / 2f
        var currentY = marginTop + photoH + 150f * s

        if (deviceText.isNotEmpty()) {
            currentY -= devicePaint.ascent()
            canvas.drawText(deviceText, centerX, currentY, devicePaint)
            currentY += devicePaint.descent()
        }

        if (allParts.isNotEmpty()) {
            currentY += 16f * s
            currentY -= infoPaint.ascent()
            drawDiscreteText(canvas, allParts, centerX, currentY, infoPaint, s)
        }

        return result
    }

    // ---- Z6 ----
    /**
     * Z6: Thin uniform white frame. Flyme logo (white) + lens info OVERLAID on photo.
     * XML: type=2, basePortWidth=1530, image margin=38 on all sides,
     *      icon flyme.png 321×60 center_horizontal transY=-200,
     *      lensInfo center_horizontal transY=-124 size=32 gray 0.6α
     * transY is relative offset from natural position (below image).
     * Negative transY pushes the element upward, overlaying on the photo.
     * Reference: pure thin white frame, logo/text invisible at thumbnail scale (white on photo).
     */
    private fun applyMeizuZ6(
        context: Context, source: Bitmap, config: WatermarkConfig
    ): Bitmap {
        val s = source.width / 1530f
        val margin = 38f * s

        val lensText = config.lensInfo ?: ""

        val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getMeizuTextRegular(context)
            textSize = 32f * s
            color = Color.WHITE   // White text overlaid on photo
            alpha = (255 * 0.7f).toInt()
            textAlign = Paint.Align.CENTER
            setShadowLayer(3f * s, 0f, 1f * s, Color.argb(60, 0, 0, 0))
        }

        val flymeLogo = loadMeizuLogo(context, "flyme_z6.png")  // white logo for dark overlay

        val photoW = (source.width - 2 * margin).toInt()
        val photoH = (source.height * photoW / source.width.toFloat()).toInt()
        val totalW = source.width
        // Thin uniform frame: margin on all 4 sides
        val totalH = (margin + photoH + margin).toInt()

        val result = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val photoTop = margin
        val photoRect = Rect(margin.toInt(), photoTop.toInt(),
            (margin + photoW).toInt(), (photoTop + photoH).toInt())
        canvas.drawBitmap(source, null, photoRect, Paint(Paint.FILTER_BITMAP_FLAG))

        val centerX = totalW / 2f
        // Natural Y position of elements = bottom of photo + margin (at the watermark bottom)
        val naturalY = photoTop + photoH + margin

        // Flyme logo overlaid on photo: transY=-200 from natural position
        flymeLogo?.let { logo ->
            val logoW = 321f * s
            val logoH = 60f * s
            val logoCenterY = naturalY - 200f * s
            val logoRect = Rect(
                (centerX - logoW / 2f).toInt(), (logoCenterY - logoH / 2f).toInt(),
                (centerX + logoW / 2f).toInt(), (logoCenterY + logoH / 2f).toInt()
            )
            canvas.drawBitmap(logo, null, logoRect, Paint(Paint.FILTER_BITMAP_FLAG))
            logo.recycle()
        }

        // Lens info overlaid on photo: transY=-124 from natural position
        if (lensText.isNotEmpty()) {
            val textY = naturalY - 124f * s
            canvas.drawText(lensText, centerX, textY, lensPaint)
        }

        return result
    }

    // ---- Z7 ----
    /**
     * Z7: Flyme logo (black) at top + photo + discrete lens info at bottom.
     * XML: type=2, basePortWidth=1470, icon flyme.png 321×60 marginT=134,
     *      image marginLR=30 marginT=106, lensInfo_discrete w=859 h=107 marginT=92 marginB=100 size=30
     *      textColor=#FF000000 alpha=0.6
     * Reference: top has 75px border with centered black logo text,
     *            bottom has 4 text groups separated by 3 "|" separators
     */
    private fun applyMeizuZ7(
        context: Context, source: Bitmap, config: WatermarkConfig
    ): Bitmap {
        val s = source.width / 1470f
        val lensParts = splitDiscreteParts(config.lensInfo)

        val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getMeizuTextRegular(context)
            textSize = 30f * s
            color = Color.BLACK
            alpha = (255 * 0.6f).toInt()
        }

        val flymeLogo = loadMeizuLogo(context, "flyme_z7.png")  // black logo

        val logoTopMargin = 134f * s
        val logoH = 60f * s
        val photoTopMargin = 106f * s
        val photoMarginSide = 30f * s

        val photoW = (source.width - 2 * photoMarginSide).toInt()
        val photoH = (source.height * photoW / source.width.toFloat()).toInt()

        val discreteAreaH = 107f * s
        val lensMarginT = if (lensParts.isNotEmpty()) 92f * s else 0f
        val lensMarginB = if (lensParts.isNotEmpty()) 100f * s else 30f * s

        val totalW = source.width
        val totalH = (logoTopMargin + logoH + photoTopMargin + photoH +
            lensMarginT + discreteAreaH + lensMarginB).toInt()

        val result = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val centerX = totalW / 2f

        // Flyme logo at top
        var currentY = logoTopMargin
        flymeLogo?.let { logo ->
            val logoW = 321f * s
            val logoRect = Rect(
                (centerX - logoW / 2f).toInt(), currentY.toInt(),
                (centerX + logoW / 2f).toInt(), (currentY + logoH).toInt()
            )
            canvas.drawBitmap(logo, null, logoRect, Paint(Paint.FILTER_BITMAP_FLAG))
            logo.recycle()
        }
        currentY += logoH + photoTopMargin

        // Photo
        val photoRect = Rect(photoMarginSide.toInt(), currentY.toInt(),
            (photoMarginSide + photoW).toInt(), (currentY + photoH).toInt())
        canvas.drawBitmap(source, null, photoRect, Paint(Paint.FILTER_BITMAP_FLAG))
        currentY += photoH

        // Discrete lens info with separators
        if (lensParts.isNotEmpty()) {
            currentY += lensMarginT
            val baselineY = currentY + discreteAreaH / 2f - (lensPaint.ascent() + lensPaint.descent()) / 2f
            drawDiscreteText(canvas, lensParts, centerX, baselineY, lensPaint, s,
                separatorColor = Color.parseColor("#B0B0B0"))
        }

        return result
    }

    // ==================== vivo Watermarks ====================

    // Cached vivo typefaces
    private var vivoRegularTypeface: Typeface? = null
    private var vivoCameraTypeface: Typeface? = null
    private var zeissBoldTypeface: Typeface? = null
    private var iqooBoldTypeface: Typeface? = null
    private var robotoBoldTypeface: Typeface? = null

    private fun getVivoRegular(context: Context): Typeface {
        vivoRegularTypeface?.let { return it }
        return try {
            Typeface.createFromAsset(context.assets, "watermark/vivo/fonts/vivo-Regular.otf").also {
                vivoRegularTypeface = it
            }
        } catch (_: Exception) { Typeface.create("sans-serif", Typeface.NORMAL) }
    }

    private fun getVivoCamera(context: Context): Typeface {
        vivoCameraTypeface?.let { return it }
        return try {
            Typeface.createFromAsset(context.assets, "watermark/vivo/fonts/vivoCameraVF.ttf").also {
                vivoCameraTypeface = it
            }
        } catch (_: Exception) { Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    }

    private fun getZeissBold(context: Context): Typeface {
        zeissBoldTypeface?.let { return it }
        return try {
            Typeface.createFromAsset(context.assets, "watermark/vivo/fonts/ZEISSFrutigerNextW1G-Bold.ttf").also {
                zeissBoldTypeface = it
            }
        } catch (_: Exception) { Typeface.create("sans-serif-medium", Typeface.BOLD) }
    }

    private fun getIqooBold(context: Context): Typeface {
        iqooBoldTypeface?.let { return it }
        return try {
            Typeface.createFromAsset(context.assets, "watermark/vivo/fonts/IQOOTYPE-Bold.ttf").also {
                iqooBoldTypeface = it
            }
        } catch (_: Exception) { Typeface.create("sans-serif", Typeface.BOLD) }
    }

    private fun getRobotoBold(context: Context): Typeface {
        robotoBoldTypeface?.let { return it }
        return try {
            Typeface.createFromAsset(context.assets, "watermark/vivo/fonts/Roboto-Bold.ttf").also {
                robotoBoldTypeface = it
            }
        } catch (_: Exception) { Typeface.create("sans-serif", Typeface.BOLD) }
    }

    private fun loadVivoLogo(context: Context, name: String): Bitmap? {
        return try {
            context.assets.open("watermark/vivo/logos/$name").use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) { null }
    }

    // vivo reference base width (typical vivo photo output)
    private const val VIVO_BASE = 4000f

    // ---- ZEISS ----
    /**
     * ZEISS branded watermark: white bottom bar with "ZEISS" branding on left,
     * device name + lens info stacked on right. Separator line between sides.
     * Based on vivo X-series ZEISS partnership watermark style.
     */
    private fun applyVivoZeiss(
        context: Context, source: Bitmap, config: WatermarkConfig
    ): Bitmap {
        val imgW = source.width
        val imgH = source.height
        val s = imgW / VIVO_BASE

        val barH = (160f * s).toInt()
        val totalH = imgH + barH

        val result = Bitmap.createBitmap(imgW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(source, 0f, 0f, null)

        // White bar
        val barPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        canvas.drawRect(0f, imgH.toFloat(), imgW.toFloat(), totalH.toFloat(), barPaint)

        val barTop = imgH.toFloat()
        val barCenterY = barTop + barH / 2f
        val marginLR = 80f * s

        // Left: "ZEISS" text in ZEISS brand font
        val zeissColor = Color.parseColor("#003B7A")  // ZEISS brand blue
        val zeissPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getZeissBold(context)
            textSize = 52f * s
            color = zeissColor
            textAlign = Paint.Align.LEFT
            letterSpacing = 0.08f
        }
        val zeissText = "ZEISS"
        val zeissY = barCenterY - (zeissPaint.ascent() + zeissPaint.descent()) / 2f
        canvas.drawText(zeissText, marginLR, zeissY, zeissPaint)

        // Thin vertical separator
        val zeissEndX = marginLR + zeissPaint.measureText(zeissText) + 30f * s
        val sepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CCCCCC")
            strokeWidth = 1.5f * s
        }
        canvas.drawLine(zeissEndX, barTop + 35f * s, zeissEndX, totalH - 35f * s, sepPaint)

        // Right: device name (bold) + lens info (regular)
        val rightX = imgW - marginLR
        val deviceText = config.deviceName ?: ""
        val lensText = config.lensInfo ?: ""

        val devicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getRobotoBold(context)
            textSize = 36f * s
            color = Color.BLACK
            textAlign = Paint.Align.RIGHT
        }
        val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getVivoRegular(context)
            textSize = 26f * s
            color = Color.parseColor("#888888")
            textAlign = Paint.Align.RIGHT
        }

        if (deviceText.isNotEmpty() && lensText.isNotEmpty()) {
            val deviceH = devicePaint.descent() - devicePaint.ascent()
            val gap = 4f * s
            val lensH = lensPaint.descent() - lensPaint.ascent()
            val totalTextH = deviceH + gap + lensH
            val groupTop = barTop + (barH - totalTextH) / 2f
            canvas.drawText(deviceText, rightX, groupTop - devicePaint.ascent(), devicePaint)
            canvas.drawText(lensText, rightX, groupTop + deviceH + gap - lensPaint.ascent(), lensPaint)
        } else if (deviceText.isNotEmpty()) {
            canvas.drawText(deviceText, rightX, zeissY, devicePaint)
        } else if (lensText.isNotEmpty()) {
            val y = barCenterY - (lensPaint.ascent() + lensPaint.descent()) / 2f
            canvas.drawText(lensText, rightX, y, lensPaint)
        }

        return result
    }

    // ---- vivo Classic ----
    /**
     * Classic overlay watermark: vivo logo (white) on bottom-left corner of photo,
     * time/date text on bottom-right. No frame added — elements overlay directly on image.
     */
    private fun applyVivoClassic(
        context: Context, source: Bitmap, config: WatermarkConfig
    ): Bitmap {
        val imgW = source.width
        val imgH = source.height
        val s = imgW / VIVO_BASE

        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val marginLR = 80f * s
        val marginB = 60f * s

        // vivo logo (white) on bottom-left
        val logo = loadVivoLogo(context, "vivo_logo_wm_xml.png")
        logo?.let {
            val logoH = 50f * s
            val logoW = logoH * it.width / it.height
            val logoRect = Rect(
                marginLR.toInt(),
                (imgH - marginB - logoH).toInt(),
                (marginLR + logoW).toInt(),
                (imgH - marginB).toInt()
            )
            val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                setShadowLayer(6f * s, 0f, 2f * s, Color.argb(90, 0, 0, 0))
            }
            canvas.drawBitmap(it, null, logoRect, logoPaint)
            it.recycle()
        }

        // Bottom-right: time text (white, with shadow)
        val timeText = config.timeText ?: ""
        if (timeText.isNotEmpty()) {
            val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = getVivoRegular(context)
                textSize = 32f * s
                color = Color.WHITE
                textAlign = Paint.Align.RIGHT
                setShadowLayer(4f * s, 1f * s, 1f * s, Color.argb(100, 0, 0, 0))
            }
            canvas.drawText(timeText, imgW - marginLR,
                imgH - marginB - 10f * s, timePaint)
        }

        // Device name below logo (smaller)
        val deviceText = config.deviceName ?: ""
        if (deviceText.isNotEmpty()) {
            val devPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = getVivoCamera(context)
                textSize = 24f * s
                color = Color.WHITE
                alpha = (255 * 0.85f).toInt()
                textAlign = Paint.Align.LEFT
                setShadowLayer(4f * s, 1f * s, 1f * s, Color.argb(100, 0, 0, 0))
            }
            canvas.drawText(deviceText, marginLR,
                imgH - marginB + 30f * s, devPaint)
        }

        return result
    }

    // ---- vivo Pro ----
    /**
     * Pro watermark: white bottom bar with vivo logo on left, device name + lens info
     * stacked on right, time in gray below lens info.
     */
    private fun applyVivoPro(
        context: Context, source: Bitmap, config: WatermarkConfig
    ): Bitmap {
        val imgW = source.width
        val imgH = source.height
        val s = imgW / VIVO_BASE

        val barH = (180f * s).toInt()
        val totalH = imgH + barH

        val result = Bitmap.createBitmap(imgW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(source, 0f, 0f, null)

        // White bar
        val barPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        canvas.drawRect(0f, imgH.toFloat(), imgW.toFloat(), totalH.toFloat(), barPaint)

        val barTop = imgH.toFloat()
        val barCenterY = barTop + barH / 2f
        val marginLR = 80f * s

        // Left: vivo logo (tinted to black for white bar)
        val logo = loadVivoLogo(context, "vivo_logo_wm_xml.png")
        logo?.let {
            val logoH = 52f * s
            val logoW = logoH * it.width / it.height
            val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = android.graphics.PorterDuffColorFilter(
                    Color.BLACK, android.graphics.PorterDuff.Mode.SRC_IN
                )
            }
            val logoY = barCenterY - logoH / 2f
            val logoRect = Rect(
                marginLR.toInt(), logoY.toInt(),
                (marginLR + logoW).toInt(), (logoY + logoH).toInt()
            )
            canvas.drawBitmap(it, null, logoRect, tintPaint)
            it.recycle()
        }

        // Thin vertical separator
        val sepX = marginLR + 180f * s
        val sepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DDDDDD")
            strokeWidth = 1.5f * s
        }
        canvas.drawLine(sepX, barTop + 40f * s, sepX, totalH - 40f * s, sepPaint)

        // Right side: device name (top) + lens info (below) + time (smallest)
        val rightX = imgW - marginLR
        val deviceText = config.deviceName ?: ""
        val lensText = config.lensInfo ?: ""
        val timeText = config.timeText ?: ""

        val devicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getVivoCamera(context)
            textSize = 34f * s
            color = Color.BLACK
            textAlign = Paint.Align.RIGHT
        }
        val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getVivoRegular(context)
            textSize = 24f * s
            color = Color.parseColor("#666666")
            textAlign = Paint.Align.RIGHT
        }
        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getVivoRegular(context)
            textSize = 22f * s
            color = Color.parseColor("#AAAAAA")
            textAlign = Paint.Align.RIGHT
        }

        // Stack available text lines
        val lines = mutableListOf<Pair<String, Paint>>()
        if (deviceText.isNotEmpty()) lines.add(deviceText to devicePaint)
        if (lensText.isNotEmpty()) lines.add(lensText to lensPaint)
        if (timeText.isNotEmpty()) lines.add(timeText to timePaint)

        if (lines.isNotEmpty()) {
            val gap = 4f * s
            var totalTextH = 0f
            for ((_, p) in lines) totalTextH += p.descent() - p.ascent()
            totalTextH += gap * (lines.size - 1)
            var y = barTop + (barH - totalTextH) / 2f
            for ((text, paint) in lines) {
                y -= paint.ascent()
                canvas.drawText(text, rightX, y, paint)
                y += paint.descent() + gap
            }
        }

        return result
    }

    // ---- iQOO ----
    /**
     * iQOO gaming brand watermark: white bottom bar with iQOO logo (black) on left,
     * device name + lens info on right in bold style.
     */
    private fun applyVivoIqoo(
        context: Context, source: Bitmap, config: WatermarkConfig
    ): Bitmap {
        val imgW = source.width
        val imgH = source.height
        val s = imgW / VIVO_BASE

        val barH = (160f * s).toInt()
        val totalH = imgH + barH

        val result = Bitmap.createBitmap(imgW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(source, 0f, 0f, null)

        // White bar
        val barPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        canvas.drawRect(0f, imgH.toFloat(), imgW.toFloat(), totalH.toFloat(), barPaint)

        val barTop = imgH.toFloat()
        val barCenterY = barTop + barH / 2f
        val marginLR = 80f * s

        // Left: iQOO logo (black on transparent)
        val logo = loadVivoLogo(context, "iqoo_logo_wm_xml.png")
        logo?.let {
            val logoH = 52f * s
            val logoW = logoH * it.width / it.height
            val logoY = barCenterY - logoH / 2f
            val logoRect = Rect(
                marginLR.toInt(), logoY.toInt(),
                (marginLR + logoW).toInt(), (logoY + logoH).toInt()
            )
            canvas.drawBitmap(it, null, logoRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            it.recycle()
        }

        // Thin vertical separator
        val sepX = marginLR + 200f * s
        val sepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CCCCCC")
            strokeWidth = 1.5f * s
        }
        canvas.drawLine(sepX, barTop + 35f * s, sepX, totalH - 35f * s, sepPaint)

        // Right: device name (bold) + lens info
        val rightX = imgW - marginLR
        val deviceText = config.deviceName ?: ""
        val lensText = config.lensInfo ?: ""

        val devicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getIqooBold(context)
            textSize = 38f * s
            color = Color.BLACK
            textAlign = Paint.Align.RIGHT
        }
        val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getVivoRegular(context)
            textSize = 26f * s
            color = Color.parseColor("#888888")
            textAlign = Paint.Align.RIGHT
        }

        if (deviceText.isNotEmpty() && lensText.isNotEmpty()) {
            val deviceH = devicePaint.descent() - devicePaint.ascent()
            val gap = 4f * s
            val lensH = lensPaint.descent() - lensPaint.ascent()
            val totalTextH = deviceH + gap + lensH
            val groupTop = barTop + (barH - totalTextH) / 2f
            canvas.drawText(deviceText, rightX, groupTop - devicePaint.ascent(), devicePaint)
            canvas.drawText(lensText, rightX, groupTop + deviceH + gap - lensPaint.ascent(), lensPaint)
        } else if (deviceText.isNotEmpty()) {
            val y = barCenterY - (devicePaint.ascent() + devicePaint.descent()) / 2f
            canvas.drawText(deviceText, rightX, y, devicePaint)
        } else if (lensText.isNotEmpty()) {
            val y = barCenterY - (lensPaint.ascent() + lensPaint.descent()) / 2f
            canvas.drawText(lensText, rightX, y, lensPaint)
        }

        return result
    }
}
