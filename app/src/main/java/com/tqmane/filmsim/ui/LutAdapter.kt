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
import java.util.concurrent.ConcurrentHashMap

class LutAdapter(
    private var items: List<LutItem>,
    private val context: Context,
    private val onLutSelected: (LutItem) -> Unit
) : RecyclerView.Adapter<LutAdapter.LutViewHolder>() {

    private var selectedPosition = -1
    private var sourceThumbnail: Bitmap? = null
    
    // In-memory LUT cache (aggressive caching)
    private val lutCache = ConcurrentHashMap<String, CubeLUT>()
    
    // Thumbnail cache
    private val thumbnailCache = ConcurrentHashMap<String, Bitmap>()
    
    // Use all available cores for parallel processing
    private val adapterScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Pre-loading jobs
    private var preloadJob: Job? = null
    private var lutPreloadJob: Job? = null

    fun setSourceBitmap(bitmap: Bitmap?) {
        this.sourceThumbnail = bitmap
        thumbnailCache.clear()
        notifyDataSetChanged()
        
        if (bitmap != null) {
            // Aggressively pre-load ALL thumbnails in parallel
            preloadAllThumbnails(bitmap)
        }
    }
    
    /**
     * Pre-load ALL LUTs into memory for instant access
     */
    fun preloadAllLuts() {
        lutPreloadJob?.cancel()
        lutPreloadJob = adapterScope.launch {
            // Load ALL LUTs in parallel batches
            val batchSize = Runtime.getRuntime().availableProcessors() * 2
            items.chunked(batchSize).forEach { batch ->
                val jobs = batch.map { item ->
                    async {
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
                jobs.awaitAll()
            }
        }
    }
    
    private fun preloadAllThumbnails(source: Bitmap) {
        preloadJob?.cancel()
        preloadJob = adapterScope.launch {
            // Process ALL items in parallel batches
            val batchSize = Runtime.getRuntime().availableProcessors() * 2
            
            items.chunked(batchSize).forEachIndexed { batchIdx, batch ->
                val jobs = batch.mapIndexed { idx, item ->
                    async {
                        if (!thumbnailCache.containsKey(item.assetPath)) {
                            try {
                                val lut = lutCache.getOrPut(item.assetPath) {
                                    CubeLUTParser.parse(context, item.assetPath) ?: return@async null
                                }
                                val result = LutBitmapProcessor.applyLutToBitmap(source, lut)
                                thumbnailCache[item.assetPath] = result
                                result
                            } catch (e: Exception) {
                                null
                            }
                        } else {
                            thumbnailCache[item.assetPath]
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
        holder.cardContainer.isSelected = (position == selectedPosition)
        
        holder.imageView.setImageDrawable(null)
        holder.imageView.setBackgroundColor(0xFF333333.toInt())

        holder.loadJob?.cancel()

        val currentThumb = sourceThumbnail ?: return
        
        // Check cache immediately
        val cached = thumbnailCache[item.assetPath]
        if (cached != null) {
            holder.imageView.setImageBitmap(cached)
        } else {
            // Generate if not cached (fallback for racing condition)
            holder.loadJob = adapterScope.launch(Dispatchers.Default) {
                val result = try {
                    val lut = lutCache.getOrPut(item.assetPath) {
                        CubeLUTParser.parse(context, item.assetPath) ?: return@launch
                    }
                    LutBitmapProcessor.applyLutToBitmap(currentThumb, lut)
                } catch (e: Exception) {
                    null
                }
                
                if (result != null) {
                    thumbnailCache[item.assetPath] = result
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
        thumbnailCache.clear()
        // Keep LUT cache for faster re-use
    }
}
