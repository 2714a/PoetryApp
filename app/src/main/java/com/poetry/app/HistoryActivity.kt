package com.poetry.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 华夏王朝演变页：以时间轴列表展示中国上下五千年王朝更替。
 */
class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "王朝演变"

        val recycler = findViewById<RecyclerView>(R.id.historyRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = DynAdapter(HistoryData.DYNASTIES)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }
}

class DynAdapter(private val list: List<Dynasty>) :
    RecyclerView.Adapter<DynAdapter.VH>() {

    inner class VH(v: android.view.View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.dynName)
        val period: TextView = v.findViewById(R.id.dynPeriod)
        val founder: TextView = v.findViewById(R.id.dynFounder)
        val capital: TextView = v.findViewById(R.id.dynCapital)
        val desc: TextView = v.findViewById(R.id.dynDesc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dynasty, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val d = list[position]
        holder.name.text = d.name
        holder.period.text = d.period
        holder.founder.text = "建立者：${d.founder}"
        holder.capital.text = "都城：${d.capital}"
        holder.desc.text = d.desc
    }
}