package com.poetry.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * 收藏管理：按“分类#行号”唯一键，存储索引信息以便离线还原列表。
 */
object FavStore {
    private const val PREF = "poetry_fav2"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun key(idx: PoemIndex): String = "${idx.category}#${idx.lineNo}"

    fun isFav(ctx: Context, idx: PoemIndex): Boolean =
        sp(ctx).contains(key(idx))

    /** 加入/取消收藏，返回是否已收藏 */
    fun toggle(ctx: Context, idx: PoemIndex): Boolean {
        val s = sp(ctx)
        val k = key(idx)
        return if (s.contains(k)) {
            s.edit().remove(k).apply(); false
        } else {
            val arr = JSONArray()
            arr.put(idx.title); arr.put(idx.author); arr.put(idx.dynasty)
            arr.put(idx.rhythmic); arr.put(idx.chapter); arr.put(idx.section)
            arr.put(idx.lineNo); arr.put(idx.category); arr.put(idx.summary)
            s.edit().putString(k, arr.toString()).apply(); true
        }
    }

    /** 清空全部收藏 */
    fun clear(ctx: Context) {
        sp(ctx).edit().clear().apply()
    }

    /** 收藏列表（还原为索引对象） */
    fun all(ctx: Context): List<PoemIndex> {
        val all = sp(ctx).all
        val out = ArrayList<PoemIndex>()
        for ((_, v) in all) {
            try {
                val arr = JSONArray(v as String)
                out.add(PoemIndex(
                    arr.optString(0), arr.optString(1), arr.optString(2),
                    arr.optString(3), arr.optString(4), arr.optString(5),
                    arr.optInt(6), arr.optString(7), arr.optString(8)
                ))
            } catch (_: Exception) {}
        }
        return out
    }
}