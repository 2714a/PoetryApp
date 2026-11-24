package com.poetry.app

/**
 * 详情页左右滑/上下滑切换下一首的「当前浏览列表」上下文。
 * 进入详情时由 MainActivity 记录当前列表与位置，详情页据此加载相邻条目。
 */
object PoemNav {
    private var list: List<PoemIndex> = emptyList()
    private var position: Int = 0

    fun set(list: List<PoemIndex>, position: Int) {
        this.list = list
        this.position = position
    }

    /** 当前条目 */
    fun current(): PoemIndex? = list.getOrNull(position)

    /** 上一条（滑动向右/上） */
    fun prev(): PoemIndex? =
        if (position > 0) { position -= 1; list.getOrNull(position) } else null

    /** 下一条（滑动向左/下） */
    fun next(): PoemIndex? =
        if (position < list.size - 1) { position += 1; list.getOrNull(position) } else null

    fun hasPrev(): Boolean = position > 0
    fun hasNext(): Boolean = position < list.size - 1
    fun pos(): Int = position
    fun size(): Int = list.size
}