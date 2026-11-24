package com.poetry.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 我的收藏页：展示已收藏诗词，点击进入详情，可取消收藏。
 */
class FavActivity : AppCompatActivity() {

    private lateinit var adapter: PoemAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: View
    private var list: List<PoemIndex> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fav)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "我的收藏"

        recycler = findViewById(R.id.favRecycler)
        emptyView = findViewById(R.id.favEmpty)
        recycler.layoutManager = LinearLayoutManager(this)

        adapter = PoemAdapter(
            emptyList(),
            onItemClick = { idx ->
                val pos = list.indexOfFirst { it.key == idx.key }
                PoemNav.set(list, if (pos >= 0) pos else 0)
                val poem = PoetryRepo.loadBodySync(this, idx)
                val it2 = Intent(this, DetailActivity::class.java)
                it2.putExtra("poem", poem)
                startActivity(it2)
            },
            onFavClick = { idx, _ ->
                FavStore.toggle(this, idx)
                Toast.makeText(this, "已取消收藏", Toast.LENGTH_SHORT).show()
                reload()
            }
        )
        recycler.adapter = adapter
        reload()
    }

    private fun reload() {
        list = FavStore.all(this)
        adapter.setData(list)
        emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }
}