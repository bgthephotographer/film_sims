package com.tqmane.filmsim.data

import android.content.Context

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
    
    private val brandDisplayNames = mapOf(
        "OnePlus" to "OnePlus",
        "Xiaomi" to "Xiaomi"
    )
    
    private val categoryDisplayNames = mapOf(
        // OnePlus - New structure
        "Cinestill" to "Cinestill",
        "Cyberpunk" to "Cyberpunk",
        "Fuji" to "Fujifilm",
        "GT_Series" to "GT Series",
        "Ilford" to "Ilford",
        "Kodak" to "Kodak",
        "Master_Filters" to "マスターフィルター",
        "Meishe_Internal" to "Meishe",
        "OnePlus_Oppo" to "OnePlus/Oppo",
        "Others" to "その他",
        "Ricoh_GR" to "Ricoh GR",
        "Social_Apps" to "SNS風",
        // Xiaomi
        "adjust" to "調整",
        "color_highlight" to "ハイライト",
        "color_shadow" to "シャドウ",
        "dolby" to "Dolby",
        "enhance" to "強調",
        "film" to "フィルム",
        "leica" to "Leica",
        "onekey" to "ワンキー",
        "popular" to "人気"
    )
    
    fun getLutBrands(context: Context): List<LutBrand> {
        val assetManager = context.assets
        val brands = mutableListOf<LutBrand>()
        
        try {
            val rootPath = "luts"
            val brandFolders = assetManager.list(rootPath) ?: return emptyList()
            
            for (brandName in brandFolders) {
                val brandPath = "$rootPath/$brandName"
                val categoryFolders = assetManager.list(brandPath) ?: continue
                
                val categories = mutableListOf<LutCategory>()
                
                for (categoryName in categoryFolders) {
                    val categoryPath = "$brandPath/$categoryName"
                    val files = assetManager.list(categoryPath) ?: continue
                    
                    val lutItems = files
                        .filter { it.endsWith(".cube", ignoreCase = true) }
                        .map { filename ->
                            LutItem(
                                name = filename.removeSuffix(".cube").removeSuffix(".CUBE").replace("_", " "),
                                assetPath = "$categoryPath/$filename"
                            )
                        }
                        .sortedBy { it.name.lowercase() }
                    
                    if (lutItems.isNotEmpty()) {
                        categories.add(
                            LutCategory(
                                name = categoryName,
                                displayName = categoryDisplayNames[categoryName] ?: categoryName.replace("_", " "),
                                items = lutItems
                            )
                        )
                    }
                }
                
                if (categories.isNotEmpty()) {
                    brands.add(
                        LutBrand(
                            name = brandName,
                            displayName = brandDisplayNames[brandName] ?: brandName,
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