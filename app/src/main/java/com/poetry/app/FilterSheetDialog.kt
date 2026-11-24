package com.poetry.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * 筛选面板：按朝代/作者筛选 + 全文搜索开关。
 * 选择后回调 onFilter(type, value)：type = "dynasty" / "author" / "fulltext" / "clear"
 */
class FilterSheetDialog(
    private val currentCat: String,
    private val isFullText: Boolean,
    private val onFilter: (String, String) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val scroll = android.widget.ScrollView(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 40, 48, 64)
            setBackgroundColor(ctx.getColor(R.color.paper))
        }
        scroll.addView(root)
        scroll.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )

        fun addTitle(text: String) {
            val tv = TextView(ctx).apply {
                this.text = text
                textSize = 15f
                setTextColor(ctx.getColor(R.color.cinnabar))
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 24, 0, 12)
            }
            root.addView(tv)
        }

        fun addItem(label: String, sub: String, onClick: () -> Unit) {
            val item = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(24, 20, 24, 20)
                setBackgroundResource(R.drawable.bg_card)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 6, 0, 6) }
                setOnClickListener { onClick(); dismiss() }
            }
            val tv = TextView(ctx).apply {
                text = label
                textSize = 15f
                setTextColor(ctx.getColor(R.color.ink))
            }
            val sv = TextView(ctx).apply {
                text = sub
                textSize = 13f
                setTextColor(ctx.getColor(R.color.ink_faint))
                setPadding(0, 0, 16, 0)
            }
            item.addView(sv)
            item.addView(tv)
            root.addView(item)
        }

        // 全文搜索开关
        addTitle("搜索范围")
        addItem(
            if (isFullText) "☑ 全文搜索已开启" else "☐ 全文搜索已关闭",
            if (isFullText) "慢" else "快",
            { onFilter("fulltext", if (isFullText) "off" else "on") }
        )

        // 清空筛选
        addTitle("操作")
        addItem("清空筛选", "", { onFilter("clear", "") })

        // 朝代
        addTitle("按朝代筛选 · ${if (currentCat == "全部") "全部" else currentCat}")
        val dynasties = PoetryRepo.listDynasties(if (currentCat == "全部") null else currentCat)
        for ((d, n) in dynasties.take(30)) {
            addItem(d, "$n 首", { onFilter("dynasty", d) })
        }
        if (dynasties.isEmpty()) {
            root.addView(TextView(ctx).apply {
                text = "（该分类暂无朝代信息）"
                setTextColor(ctx.getColor(R.color.ink_faint))
                setPadding(0, 8, 0, 8)
            })
        }

        // 作者
        addTitle("按作者筛选（产量前 30 位）")
        val authors = PoetryRepo.listAuthors(if (currentCat == "全部") null else currentCat, 30)
        for ((a, n) in authors) {
            addItem(a, "$n 首", { onFilter("author", a) })
        }

        return scroll
    }
}