package com.tqmane.filmsim.ui

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tqmane.filmsim.R
import com.tqmane.filmsim.data.LutItem
import com.tqmane.filmsim.util.CubeLUT
import com.tqmane.filmsim.util.CubeLUTParser
import com.tqmane.filmsim.util.LutBitmapProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap

class LutAdapter(
    private var items: List<LutItem>,
    private val context: Context,
    private val onLutSelected: (LutItem) -> Unit
) : RecyclerView.Adapter<LutAdapter.LutViewHolder>() {

    private var selectedPosition = -1
    private var sourceThumbnail: Bitmap? = null
    
    // In-memory LUT cache with size limit to prevent OOM
    private val lutCache = object : LinkedHashMap<String, CubeLUT>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CubeLUT>?): Boolean {
            return size > MAX_LUT_CACHE_SIZE
        }
    }
    
    // Thumbnail cache with size limit
    private val thumbnailCache = object : LinkedHashMap<String, Bitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            return size > MAX_THUMBNAIL_CACHE_SIZE
        }
    }
    
    // Use limited cores for parallel processing to avoid memory pressure
    private val adapterScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Pre-loading jobs
    private var preloadJob: Job? = null
    private var lutPreloadJob: Job? = null
    
    companion object {
        private const val MAX_LUT_CACHE_SIZE = 20 // Limit cached LUTs
        private const val MAX_THUMBNAIL_CACHE_SIZE = 50 // Limit cached thumbnails
    }

    fun setSourceBitmap(bitmap: Bitmap?) {
        this.sourceThumbnail = bitmap
        synchronized(thumbnailCache) { thumbnailCache.clear() }
        notifyDataSetChanged()
        
        if (bitmap != null) {
            // Pre-load thumbnails in batches
            preloadAllThumbnails(bitmap)
        }
    }
    
    /**
     * Pre-load LUTs with memory-conscious batch size
     */
    fun preloadAllLuts() {
        lutPreloadJob?.cancel()
        lutPreloadJob = adapterScope.launch {
            // Use smaller batch size to avoid memory pressure
            val batchSize = minOf(4, Runtime.getRuntime().availableProcessors())
            items.chunked(batchSize).forEach { batch ->
                val jobs = batch.map { item ->
                    async {
                        synchronized(lutCache) {
                            if (!lutCache.containsKey(item.assetPath)) {
                                try {
                                    val lut = CubeLUTParser.parse(context, item.assetPath)
                                    if (lut != null) {
                                        lutCache[item.assetPath] = lut
                                    }
                                } catch (e: Exception) {
                                    // Ignore
                                }
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }
        }
    }
    
    private fun preloadAllThumbnails(source: Bitmap) {
        preloadJob?.cancel()
        preloadJob = adapterScope.launch {
            // Use smaller batch size to avoid memory pressure
            val batchSize = minOf(4, Runtime.getRuntime().availableProcessors())
            
            items.chunked(batchSize).forEachIndexed { batchIdx, batch ->
                val jobs = batch.mapIndexed { idx, item ->
                    async {
                        val cached = synchronized(thumbnailCache) { thumbnailCache[item.assetPath] }
                        if (cached == null) {
                            try {
                                val lut = synchronized(lutCache) {
                                    lutCache[item.assetPath] ?: run {
                                        val parsed = CubeLUTParser.parse(context, item.assetPath)
                                        if (parsed != null) lutCache[item.assetPath] = parsed
                                        parsed
                                    }
                                } ?: return@async null
                                
                                val result = LutBitmapProcessor.applyLutToBitmap(source, lut)
                                synchronized(thumbnailCache) {
                                    thumbnailCache[item.assetPath] = result
                                }
                                result
                            } catch (e: Exception) {
                                null
                            }
                        } else {
                            cached
                        }
                    }
                }
                jobs.awaitAll()
                
                // Notify UI after each batch
                withContext(Dispatchers.Main) {
                    val startPos = batchIdx * batchSize
                    val endPos = minOf(startPos + batch.size, items.size)
                    notifyItemRangeChanged(startPos, endPos - startPos)
                }
            }
        }
    }

    class LutViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.lutName)
        val imageView: ImageView = view.findViewById(R.id.lutPreview)
        val cardContainer: View = view.findViewById(R.id.lutCardContainer)
        val glowView: View = view.findViewById(R.id.glowView)
        val selectionBorder: View = view.findViewById(R.id.selectionBorder)
        var loadJob: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LutViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lut, parent, false)
        return LutViewHolder(view)
    }

    override fun onBindViewHolder(holder: LutViewHolder, position: Int) {
        val item = items[position]
        holder.textView.text = item.name
        
        // Set selected state on the card container (which has the selector background)
        val isSelected = (position == selectedPosition)
        holder.cardContainer.isSelected = isSelected
        
        // Show/hide glow and border for selection
        holder.glowView.visibility = if (isSelected) View.VISIBLE else View.GONE
        holder.selectionBorder.visibility = if (isSelected) View.VISIBLE else View.GONE
        
        // Animate scale for selected state
        val targetScale = if (isSelected) 1.02f else 1.0f
        holder.itemView.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .setDuration(150)
            .start()
        
        holder.imageView.setImageDrawable(null)
        holder.imageView.setBackgroundColor(0xFF333333.toInt())

        holder.loadJob?.cancel()

        val currentThumb = sourceThumbnail ?: return
        
        // Check cache immediately
        val cached = synchronized(thumbnailCache) { thumbnailCache[item.assetPath] }
        if (cached != null) {
            holder.imageView.setImageBitmap(cached)
        } else {
            // Generate if not cached (fallback for racing condition)
            holder.loadJob = adapterScope.launch(Dispatchers.Default) {
                val result = try {
                    val lut = synchronized(lutCache) {
                        lutCache[item.assetPath] ?: run {
                            val parsed = CubeLUTParser.parse(context, item.assetPath)
                            if (parsed != null) lutCache[item.assetPath] = parsed
                            parsed
                        }
                    } ?: return@launch
                    LutBitmapProcessor.applyLutToBitmap(currentThumb, lut)
                } catch (e: Exception) {
                    null
                }
                
                if (result != null) {
                    synchronized(thumbnailCache) {
                        thumbnailCache[item.assetPath] = result
                    }
                    withContext(Dispatchers.Main) {
                        if (holder.adapterPosition == position) {
                            holder.imageView.setImageBitmap(result)
                        }
                    }
                }
            }
        }

        // Click listener - always set up fresh
        holder.itemView.setOnClickListener {
            val clickedPos = holder.adapterPosition
            if (clickedPos != RecyclerView.NO_POSITION) {
                val oldPos = selectedPosition
                selectedPosition = clickedPos
                if (oldPos >= 0) notifyItemChanged(oldPos)
                notifyItemChanged(selectedPosition)
                onLutSelected(items[clickedPos])
            }
        }
    }
    
    override fun onViewRecycled(holder: LutViewHolder) {
        super.onViewRecycled(holder)
        holder.loadJob?.cancel()
    }

    override fun getItemCount() = items.size
    
    fun updateItems(newItems: List<LutItem>) {
        items = newItems
        selectedPosition = -1
        notifyDataSetChanged()
        
        // Pre-load all LUTs immediately
        preloadAllLuts()
        
        // Pre-load thumbnails 
        sourceThumbnail?.let { preloadAllThumbnails(it) }
    }
    
    fun clearCache() {
        preloadJob?.cancel()
        lutPreloadJob?.cancel()
        adapterScope.coroutineContext.cancelChildren()
        synchronized(thumbnailCache) { thumbnailCache.clear() }
        synchronized(lutCache) { lutCache.clear() }
    }
}
