package com.poetry.app

import java.io.Serializable

/**
 * 诗词索引条目：常驻内存用于列表/搜索，正文按需随机读取。
 */
class PoemIndex(
    val title: String,
    val author: String,
    val dynasty: String,
    val rhythmic: String,
    val chapter: String,
    val section: String,
    val lineNo: Int,   // 在 .cnt 文件中的行号（0 基）
    val category: String,
    val summary: String = ""   // 正文摘要（前40字），用于列表预览与名句搜索
) : Serializable {
    val isCi: Boolean get() = rhythmic.isNotBlank()

    /** 唯一键：分类#行号（与 FavStore/PoemNav 一致） */
    val key: String get() = "$category#$lineNo"

    /** 展示标题：词牌与标题相同则不重复，避免“导引·导引” */
    val displayTitle: String
        get() = when {
            !isCi -> title
            rhythmic == title -> title
            title.startsWith(rhythmic) -> title
            else -> "${rhythmic}·$title"
        }
}

/** 含正文的完整诗词 */
class Poem(
    val index: PoemIndex,
    val paragraphs: List<String>,
    val analysis: String = ""   // 译文/赏析/注释（有则展示）
) : Serializable {
    val title: String get() = index.title
    val author: String get() = index.author
    val dynasty: String get() = index.dynasty
    val category: String get() = index.category
    val rhythmic: String get() = index.rhythmic
    val section: String get() = index.section
    val body: String get() = paragraphs.joinToString("\n")
    val isCi: Boolean get() = index.isCi
    val displayTitle: String get() = index.displayTitle
    val hasAnalysis: Boolean get() = analysis.isNotBlank()
}