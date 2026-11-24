package com.poetry.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 「我的」页面：收藏、王朝历史、关于声明、清空收藏等入口（非 Tab，独立页）。
 */
class MineActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mine)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "我的"

        findViewById<View>(R.id.mineFav).setOnClickListener {
            startActivity(Intent(this, FavActivity::class.java))
        }
        findViewById<View>(R.id.mineHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<View>(R.id.mineDisclaimer).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        updateStats()
    }

    private fun updateStats() {
        val favCount = FavStore.all(this).size
        val dynCount = HistoryData.DYNASTIES.size
        findViewById<TextView>(R.id.mineStats).text =
            "共收藏 $favCount 首 · 收录 $dynCount 个王朝 · 9 万余首古诗词"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }
}