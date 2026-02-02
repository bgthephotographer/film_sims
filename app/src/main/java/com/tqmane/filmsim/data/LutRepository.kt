package com.tqmane.filmsim.data

import android.content.Context
import com.tqmane.filmsim.R

data class LutItem(
    val name: String,
    val assetPath: String
)

data class LutCategory(
    val name: String,
    val displayName: String,
    val items: List<LutItem>
)

data class LutBrand(
    val name: String,
    val displayName: String,
    val categories: List<LutCategory>
)

object LutRepository {
    
    // Supported LUT file extensions
    private val lutExtensions = listOf(".cube", ".png", ".bin")
    
    // Category name to string resource ID mapping
    private fun getCategoryDisplayName(context: Context, categoryName: String): String {
        return when (categoryName) {
            // OnePlus categories
            "App Filters" -> context.getString(R.string.category_app_filters)
            "Artistic" -> context.getString(R.string.category_artistic)
            "Black & White" -> context.getString(R.string.category_black_white)
            "Cinematic Movie" -> context.getString(R.string.category_cinematic_movie)
            "Cool Tones" -> context.getString(R.string.category_cool_tones)
            "Food" -> context.getString(R.string.category_food)
            "Golden Touch" -> context.getString(R.string.category_golden_touch)
            "Instagram Filters" -> context.getString(R.string.category_instagram_filters)
            "Japanese Style" -> context.getString(R.string.category_japanese_style)
            "Landscape" -> context.getString(R.string.category_landscape)
            "Night" -> context.getString(R.string.category_night)
            "Portrait" -> context.getString(R.string.category_portrait)
            "Uncategorized" -> context.getString(R.string.category_uncategorized)
            "Vintage-Retro" -> context.getString(R.string.category_vintage_retro)
            "Warm Tones" -> context.getString(R.string.category_warm_tones)
            // Xiaomi categories
            "Cinematic" -> context.getString(R.string.category_cinematic)
            "Film Simulation" -> context.getString(R.string.category_film_simulation)
            "Monochrome" -> context.getString(R.string.category_monochrome)
            "Nature-Landscape" -> context.getString(R.string.category_nature_landscape)
            "Portrait-Soft" -> context.getString(R.string.category_portrait_soft)
            "Special Effects" -> context.getString(R.string.category_special_effects)
            "Vivid-Natural" -> context.getString(R.string.category_vivid_natural)
            "Warm-Vintage" -> context.getString(R.string.category_warm_vintage)
            // Leica_lux categories
            "Leica Looks" -> context.getString(R.string.category_leica_looks)
            "Artist Looks" -> context.getString(R.string.category_artist_looks)
            // Common/Nothing
            "_all" -> context.getString(R.string.category_all)
            // Fallback - keep original name for Fujifilm, Kodak Film, etc.
            else -> categoryName.replace("_", " ").replace("-", " - ")
        }
    }
    
    // Brand name to display name mapping
    private fun getBrandDisplayName(context: Context, brandName: String): String {
        return when (brandName) {
            "Leica_lux" -> context.getString(R.string.brand_leica_lux)
            else -> brandName
        }
    }
    
    // Leica_lux filter filename to localized display name
    private fun getLeicaLuxFilterName(context: Context, fileName: String): String {
        return when {
            fileName.contains("Classic_sRGB") -> context.getString(R.string.lut_leica_classic)
            fileName.contains("Contemporary_sRGB") -> context.getString(R.string.lut_leica_contemporary)
            fileName.contains("Leica-Filter_Monochrome") -> context.getString(R.string.lut_leica_monochrome_natural)
            fileName.contains("Leica-Filter_Natural") -> context.getString(R.string.lut_leica_natural)
            fileName.contains("Leica-Looks_Blue") -> context.getString(R.string.lut_leica_blue)
            fileName.contains("Leica-Looks_Eternal") -> context.getString(R.string.lut_leica_eternal)
            fileName.contains("Leica-Looks_Selenium") -> context.getString(R.string.lut_leica_selenium)
            fileName.contains("Leica-Looks_Sepia") -> context.getString(R.string.lut_leica_sepia)
            fileName.contains("Leica-Looks_Silver") -> context.getString(R.string.lut_leica_silver)
            fileName.contains("Leica-Looks_Teal") -> context.getString(R.string.lut_leica_teal)
            fileName.contains("Leica_Bleach") -> context.getString(R.string.lut_leica_bleach)
            fileName.contains("Leica_Brass") -> context.getString(R.string.lut_leica_brass)
            fileName.contains("Leica_Monochrome_High_Contrast") -> context.getString(R.string.lut_leica_high_contrast)
            fileName.contains("Leica_Vivid") -> context.getString(R.string.lut_leica_vivid)
            fileName.contains("Tyson_100yearsMono") -> context.getString(R.string.lut_100_years_mono)
            fileName.contains("Tyson_GregWilliams_Sepia0") -> context.getString(R.string.lut_greg_williams_sepia_0)
            fileName.contains("Tyson_GregWilliams_Sepia100") -> context.getString(R.string.lut_greg_williams_sepia_100)
            fileName.contains("Tyson_Leica_Base_V3") -> context.getString(R.string.lut_leica_standard)
            fileName.contains("Tyson_Leica_Chrome") -> context.getString(R.string.lut_leica_chrome)
            else -> fileName.replace("_", " ")
        }
    }
    
    fun getLutBrands(context: Context): List<LutBrand> {
        val assetManager = context.assets
        val brands = mutableListOf<LutBrand>()
        
        try {
            val rootPath = "luts"
            val brandFolders = assetManager.list(rootPath) ?: return emptyList()
            
            for (brandName in brandFolders) {
                val brandPath = "$rootPath/$brandName"
                val contents = assetManager.list(brandPath) ?: continue
                
                val categories = mutableListOf<LutCategory>()
                val isLeicaLux = brandName == "Leica_lux"
                
                // Check if brand has flat structure (LUT files directly in brand folder)
                val directLutFiles = contents.filter { file -> 
                    lutExtensions.any { ext -> file.endsWith(ext, ignoreCase = true) }
                }
                
                if (directLutFiles.isNotEmpty()) {
                    // Flat structure (e.g., Nothing) - create a single "All" category
                    val lutItems = directLutFiles.map { filename ->
                        val baseName = lutExtensions.fold(filename) { acc, ext -> 
                            acc.removeSuffix(ext).removeSuffix(ext.uppercase())
                        }
                        val displayName = if (isLeicaLux) {
                            getLeicaLuxFilterName(context, baseName)
                        } else {
                            baseName.replace("_", " ")
                        }
                        LutItem(
                            name = displayName,
                            assetPath = "$brandPath/$filename"
                        )
                    }.sortedBy { it.name.lowercase() }
                    
                    categories.add(
                        LutCategory(
                            name = "_all",
                            displayName = getCategoryDisplayName(context, "_all"),
                            items = lutItems
                        )
                    )
                }
                
                // Check for subdirectories (category folders)
                val categoryFolders = contents.filter { name ->
                    !lutExtensions.any { ext -> name.endsWith(ext, ignoreCase = true) }
                }
                
                for (categoryName in categoryFolders) {
                    val categoryPath = "$brandPath/$categoryName"
                    val files = assetManager.list(categoryPath) ?: continue
                    
                    val lutItems = files
                        .filter { file -> 
                            lutExtensions.any { ext -> file.endsWith(ext, ignoreCase = true) }
                        }
                        .map { filename ->
                            val baseName = lutExtensions.fold(filename) { acc, ext -> 
                                acc.removeSuffix(ext).removeSuffix(ext.uppercase())
                            }
                            val displayName = if (isLeicaLux) {
                                getLeicaLuxFilterName(context, baseName)
                            } else {
                                baseName.replace("_", " ")
                            }
                            LutItem(
                                name = displayName,
                                assetPath = "$categoryPath/$filename"
                            )
                        }
                        .sortedBy { it.name.lowercase() }
                    
                    if (lutItems.isNotEmpty()) {
                        categories.add(
                            LutCategory(
                                name = categoryName,
                                displayName = getCategoryDisplayName(context, categoryName),
                                items = lutItems
                            )
                        )
                    }
                }
                
                if (categories.isNotEmpty()) {
                    brands.add(
                        LutBrand(
                            name = brandName,
                            displayName = getBrandDisplayName(context, brandName),
                            categories = categories.sortedBy { it.displayName }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return brands.sortedBy { it.displayName }
    }
    
    // Legacy support
    fun getLutGenres(context: Context): List<LutGenre> {
        val brands = getLutBrands(context)
        return brands.flatMap { brand ->
            brand.categories.map { category ->
                LutGenre(
                    name = "${brand.displayName} - ${category.displayName}",
                    items = category.items
                )
            }
        }
    }
}

// Legacy data class for compatibility
data class LutGenre(
    val name: String,
    val items: List<LutItem>
)