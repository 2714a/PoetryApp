package com.poetry.app

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * 诗词列表适配器：列表项基于 PoemIndex（轻量，支持 9 万条分页）。
 * 点击项回调 onItemClick(idx)；星标回调 onFavClick(idx, position)。
 */
class PoemAdapter(
    private var poems: List<PoemIndex>,
    private val onItemClick: (PoemIndex) -> Unit,
    private val onFavClick: (PoemIndex, Int) -> Unit
) : RecyclerView.Adapter<PoemAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.itemTitle)
        val author: TextView = v.findViewById(R.id.itemAuthor)
        val preview: TextView = v.findViewById(R.id.itemPreview)
        val fav: TextView = v.findViewById(R.id.itemFav)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_poem, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = poems.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val idx = poems[position]
        val ctx = holder.itemView.context
        holder.title.text = idx.displayTitle
        holder.author.text = "〔${idx.dynasty}〕${idx.author}"
        // 预览：优先用正文摘要（前40字），保证列表信息量且滑动流畅
        holder.preview.text = idx.summary.ifBlank {
            when {
                idx.isCi && idx.rhythmic != idx.title -> idx.rhythmic
                idx.section.isNotBlank() -> idx.section
                else -> idx.category
            }
        }
        val fav = FavStore.isFav(ctx, idx)
        holder.fav.text = if (fav) "★" else "☆"
        holder.itemView.setOnClickListener { onItemClick(idx) }
        holder.fav.setOnClickListener { onFavClick(idx, position) }
    }

    fun setData(list: List<PoemIndex>) {
        poems = list
        notifyDataSetChanged()
    }

    /** 当前已加载的索引列表（供详情页滑动导航使用） */
    fun currentList(): List<PoemIndex> = poems

    fun appendData(list: List<PoemIndex>) {
        val old = poems.size
        poems = poems + list
        notifyItemRangeInserted(old, list.size)
    }
}