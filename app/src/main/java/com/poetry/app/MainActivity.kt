package com.poetry.app

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 主界面：每日一句 + 分类浏览（含「全部」） + 搜索（索引/全文） + 朝代/作者筛选 + 下拉刷新。
 * 数据量大（9万+），列表仅加载索引，点击进入详情时按需读取正文。
 */
class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val PAGE = 60   // 每页条数

    private lateinit var recycler: RecyclerView
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var searchInput: EditText
    private lateinit var catGroup: ChipGroup
    private lateinit var subScroll: HorizontalScrollView
    private lateinit var subGroup: ChipGroup
    private lateinit var dailyHeader: LinearLayout
    private lateinit var dailyQuote: TextView
    private lateinit var dailyFrom: TextView
    private lateinit var loadingMore: TextView
    private lateinit var filterStatus: TextView
    private lateinit var adapter: PoemAdapter

    private var currentCat: String = "唐诗"
    private var currentRoot: String = "古诗词"   // 当前展开的大类
    private var rootExpanded: Boolean = true     // 子目录是否展开
    private var searchJob: Job? = null
    private var loadedCount = 0        // 当前已加载条数（浏览模式）
    private var isSearchMode = false
    private var isFullText = false     // 全文搜索开关
    private var filterType: String? = null   // "dynasty" / "author" / null
    private var filterValue: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recycler = findViewById(R.id.recycler)
        swipe = findViewById(R.id.swipe)
        searchInput = findViewById(R.id.searchInput)
        catGroup = findViewById(R.id.catGroup)
        subScroll = findViewById(R.id.subScroll)
        subGroup = findViewById(R.id.subGroup)
        dailyHeader = findViewById(R.id.dailyHeader)
        dailyQuote = findViewById(R.id.dailyQuote)
        dailyFrom = findViewById(R.id.dailyFrom)
        loadingMore = findViewById(R.id.loadingMore)
        filterStatus = findViewById(R.id.filterStatus)

        adapter = PoemAdapter(
            emptyList(),
            onItemClick = { idx -> openDetail(idx) },
            onFavClick = { idx, pos ->
                val added = FavStore.toggle(this, idx)
                Toast.makeText(this, if (added) "已加入收藏" else "已取消收藏", Toast.LENGTH_SHORT).show()
                adapter.notifyItemChanged(pos)
            }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        // 滚动到底部加载更多（仅浏览模式）
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (isSearchMode || filterValue.isNotEmpty()) return
                val lm = rv.layoutManager as LinearLayoutManager
                val total = lm.itemCount
                val last = lm.findLastVisibleItemPosition()
                if (last >= total - 20) loadMore()
            }
        })

        buildCategoryChips()

        swipe.setOnRefreshListener { onRefresh() }
        findViewById<ImageButton>(R.id.searchBtn).setOnClickListener { doSearch() }
        findViewById<ImageButton>(R.id.filterBtn).setOnClickListener { showFilter() }
        // 底部 Tab 切换（首页 / 王朝 / 我的）
        findViewById<View>(R.id.tabHome).setOnClickListener { switchTab(0) }
        findViewById<View>(R.id.tabDynasty).setOnClickListener { switchTab(1) }
        // 「我的」面板内入口跳转（收藏 / 声明权限说明）
        findViewById<View>(R.id.mineFav).setOnClickListener {
            startActivity(Intent(this, FavActivity::class.java))
        }
        findViewById<View>(R.id.mineDisclaimer).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        findViewById<View>(R.id.tabBaike).setOnClickListener { switchTab(2) }
        findViewById<View>(R.id.tabMine).setOnClickListener { switchTab(3) }
        searchInput.setOnEditorActionListener { _, actionId, event ->
            if ((event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            ) { doSearch(); true } else false
        }

        // 先初始化数据仓库，再加载首页
        swipe.isRefreshing = true
        scope.launch {
            PoetryRepo.init(this@MainActivity)
            loadDaily()
            loadCategory("唐诗")
            setupDynastyPanel()
            setupBaikePanel()
            switchTab(0)   // 启动后显示首页（布局各 panel 初始均 gone，需手动切到首页）
        }
    }

    /** 打开详情：记录当前浏览列表与位置，供详情页滑动切换上下篇 */
    private fun openDetail(idx: PoemIndex, fromList: List<PoemIndex>? = null) {
        swipe.isRefreshing = true
        scope.launch {
            // 记录导航上下文：用当前列表（搜索/筛选/浏览）+ 位置
            val navList = fromList ?: adapter.currentList()
            val pos = navList.indexOfFirst { it.key == idx.key }.coerceAtLeast(0)
            PoemNav.set(navList, pos)
            val poem = PoetryRepo.loadBody(this@MainActivity, idx)
            swipe.isRefreshing = false
            val intent = Intent(this@MainActivity, DetailActivity::class.java)
            intent.putExtra("poem", poem)
            startActivity(intent)
        }
    }

    private fun loadDaily() {
        scope.launch {
            val idx = PoetryRepo.daily(this@MainActivity)
            if (idx != null) {
                dailyQuote.text = "“${idx.summary.take(26)}”"
                dailyFrom.text = "—— 〔${idx.dynasty}〕${idx.author}《${idx.displayTitle}》"
                dailyHeader.visibility = View.VISIBLE
                dailyHeader.setOnClickListener { openDetail(idx, listOf(idx)) }
            }
        }
    }

    /** 生成首页大类标签（古诗词 / 趣味），点击展开或收起子目录 */
    private fun buildCategoryChips() {
        catGroup.removeAllViews()
        for ((root, _) in PoetryRepo.HOME_ROOTS) {
            val chip = Chip(this)
            chip.text = root
            chip.isCheckable = true
            chip.isCheckedIconVisible = false
            chip.isClickable = true
            chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(getColor(R.color.white))
            chip.setTextColor(getColor(R.color.ink_soft))
            chip.chipStrokeColor = android.content.res.ColorStateList.valueOf(getColor(R.color.divider))
            chip.chipStrokeWidth = 1f
            if (root == currentRoot && rootExpanded) {
                chip.setChipBackgroundColorResource(R.color.cinnabar)
                chip.setTextColor(getColor(R.color.white))
            }
            chip.setOnClickListener { onRootClick(root) }
            catGroup.addView(chip)
        }
    }

    /** 大类点击：已展开则收起，否则展开并默认选第一个子类 */
    private fun onRootClick(root: String) {
        if (root == currentRoot && rootExpanded) {
            rootExpanded = false
            subScroll.visibility = View.GONE
            highlightRootChips(null)
        } else {
            currentRoot = root
            rootExpanded = true
            highlightRootChips(root)
            buildSubChips(root)
            subScroll.visibility = View.VISIBLE
            val first = PoetryRepo.homeChildren(root).firstOrNull() ?: return
            currentCat = first
            searchInput.text.clear()
            isSearchMode = false
            clearFilter(false)
            loadCategory(first)
        }
    }

    /** 高亮大类标签；selected=null 表示全部不高亮（收起状态） */
    private fun highlightRootChips(selected: String?) {
        for (i in 0 until catGroup.childCount) {
            val c = catGroup.getChildAt(i) as Chip
            if (selected != null && c.text == selected) {
                c.setChipBackgroundColorResource(R.color.cinnabar)
                c.setTextColor(getColor(R.color.white))
            } else {
                c.setChipBackgroundColorResource(R.color.white)
                c.setTextColor(getColor(R.color.ink_soft))
            }
        }
    }

    /** 生成子目录横滚条 */
    private fun buildSubChips(root: String) {
        subGroup.removeAllViews()
        for (name in PoetryRepo.homeChildren(root)) {
            val chip = Chip(this)
            chip.text = name
            chip.isCheckable = true
            chip.isCheckedIconVisible = false
            chip.isClickable = true
            chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#F5EFE6"))
            chip.setTextColor(getColor(R.color.ink))
            chip.chipStrokeColor = android.content.res.ColorStateList.valueOf(getColor(R.color.divider))
            chip.chipStrokeWidth = 1f
            if (name == currentCat) {
                chip.setChipBackgroundColorResource(R.color.cinnabar)
                chip.setTextColor(getColor(R.color.white))
            }
            chip.setOnClickListener {
                currentCat = name
                highlightSubChips(name)
                searchInput.text.clear()
                isSearchMode = false
                clearFilter(false)
                loadCategory(name)
            }
            subGroup.addView(chip)
        }
    }

    /** 高亮子目录标签 */
    private fun highlightSubChips(selected: String) {
        for (i in 0 until subGroup.childCount) {
            val c = subGroup.getChildAt(i) as Chip
            if (c.text == selected) {
                c.setChipBackgroundColorResource(R.color.cinnabar)
                c.setTextColor(getColor(R.color.white))
            } else {
                c.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#F5EFE6"))
                c.setTextColor(getColor(R.color.ink))
            }
        }
    }

    /** 加载某分类第一页（浏览模式） */
    private fun loadCategory(cat: String) {
        loadedCount = 0
        adapter.setData(emptyList())
        appendPage()
    }

    /** 追加一页（浏览模式） */
    private fun appendPage() {
        scope.launch {
            val target = currentCat
            val page = PoetryRepo.page(target, loadedCount, PAGE)
            adapter.appendData(page)
            loadedCount += page.size
            val total = PoetryRepo.count(target)
            loadingMore.visibility = if (loadedCount < total) View.VISIBLE else View.GONE
            loadingMore.text = "已加载 ${loadedCount}/${total} · 上滑加载更多"
            swipe.isRefreshing = false
            recycler.scrollToPosition(loadedCount - page.size)
        }
    }

    private fun loadMore() {
        if (isSearchMode || filterValue.isNotEmpty()) return
        val target = currentCat
        val total = PoetryRepo.count(target)
        if (loadedCount >= total) return
        appendPage()
    }

    private fun doSearch() {
        val kw = searchInput.text.toString().trim()
        if (kw.isEmpty()) {
            isSearchMode = false
            dailyHeader.visibility = View.VISIBLE
            clearFilter(true)
            loadCategory(currentCat)
            return
        }
        swipe.isRefreshing = true
        dailyHeader.visibility = View.GONE
        loadingMore.visibility = View.GONE
        searchJob?.cancel()
        searchJob = scope.launch {
            // cat=null 表示跨全部分类；fullText=true 时扫描正文
            val list = PoetryRepo.search(this@MainActivity, kw, null, fullText = isFullText)
            isSearchMode = true
            clearFilter(false)
            adapter.setData(list)
            swipe.isRefreshing = false
            filterStatus.visibility = View.VISIBLE
            filterStatus.setOnClickListener { }
            filterStatus.text = "搜索「$kw」${if (isFullText) "（全文）" else ""} · 找到 ${list.size} 首"
            if (list.isEmpty()) {
                Toast.makeText(this@MainActivity, "未找到「$kw」相关诗词", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showFilter() {
        val dlg = FilterSheetDialog(currentCat, isFullText) { type, value ->
            when (type) {
                "fulltext" -> {
                    isFullText = (value == "on")
                    Toast.makeText(this,
                        if (isFullText) "已开启全文搜索（较慢，可搜正文）" else "已关闭全文搜索（仅标题/作者/名句）",
                        Toast.LENGTH_SHORT).show()
                }
                "clear" -> {
                    clearFilter(true)
                    searchInput.text.clear()
                    isSearchMode = false
                    dailyHeader.visibility = View.VISIBLE
                    loadCategory(currentCat)
                }
                "dynasty", "author" -> {
                    applyFilter(type, value)
                }
            }
        }
        dlg.show(supportFragmentManager, "filter")
    }

    private fun applyFilter(type: String, value: String) {
        swipe.isRefreshing = true
        dailyHeader.visibility = View.GONE
        loadingMore.visibility = View.GONE
        isSearchMode = false
        filterType = type
        filterValue = value
        searchJob?.cancel()
        searchJob = scope.launch {
            val list = if (type == "dynasty")
                PoetryRepo.filterByDynasty(value, null)
            else
                PoetryRepo.filterByAuthor(value, null)
            adapter.setData(list)
            swipe.isRefreshing = false
            filterStatus.visibility = View.VISIBLE
            val label = if (type == "dynasty") "朝代" else "作者"
            filterStatus.text = "${label}：$value · 共 ${list.size} 首（点击可重新浏览）"
            filterStatus.setOnClickListener { clearFilter(true); loadCategory(currentCat) }
            if (list.isEmpty()) Toast.makeText(this@MainActivity, "未找到该${label}的作品", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearFilter(updateStatus: Boolean) {
        filterType = null
        filterValue = ""
        if (updateStatus) {
            filterStatus.visibility = View.GONE
            filterStatus.setOnClickListener(null)
        }
    }

    private fun onRefresh() {
        val kw = searchInput.text.toString().trim()
        if (kw.isNotEmpty()) { doSearch() }
        else {
            isSearchMode = false
            clearFilter(true)
            dailyHeader.visibility = View.VISIBLE
            loadCategory(currentCat)
        }
    }

    /** 切换底部 Tab：0=首页 1=华夏王朝 2=百科 3=我的 */
    private fun switchTab(which: Int) {
        findViewById<View>(R.id.homePanel).visibility = if (which == 0) View.VISIBLE else View.GONE
        findViewById<View>(R.id.dynPanel).visibility = if (which == 1) View.VISIBLE else View.GONE
        findViewById<View>(R.id.baikePanel).visibility = if (which == 2) View.VISIBLE else View.GONE
        findViewById<View>(R.id.minePanel).visibility = if (which == 3) View.VISIBLE else View.GONE
        // Tab 高亮（选中项标题变亮、图标不透明；未选变暗）
        val on = 0xFFF7EED6.toInt()
        val off = 0xFF8A9099.toInt()
        findViewById<TextView>(R.id.tabHomeLabel).setTextColor(if (which == 0) on else off)
        findViewById<TextView>(R.id.tabDynastyLabel).setTextColor(if (which == 1) on else off)
        findViewById<TextView>(R.id.tabBaikeLabel).setTextColor(if (which == 2) on else off)
        findViewById<TextView>(R.id.tabMineLabel).setTextColor(if (which == 3) on else off)
        findViewById<TextView>(R.id.tabHomeIcon).alpha = if (which == 0) 1f else 0.5f
        findViewById<TextView>(R.id.tabDynastyIcon).alpha = if (which == 1) 1f else 0.5f
        findViewById<TextView>(R.id.tabBaikeIcon).alpha = if (which == 2) 1f else 0.5f
        findViewById<TextView>(R.id.tabMineIcon).alpha = if (which == 3) 1f else 0.5f
        // 切到「我的」刷新收藏统计；切到「王朝」滚动到顶部（可选）
        if (which == 3) refreshMineStats()
    }

    /** 中间 Tab：华夏王朝时间轴（复用 DynAdapter） */
    private fun setupDynastyPanel() {
        val rec = findViewById<RecyclerView>(R.id.dynRecycler)
        rec.layoutManager = LinearLayoutManager(this)
        rec.adapter = DynAdapter(HistoryData.DYNASTIES)
    }

/** 百科 Tab：大类 Chip（动物/历史…）→ 子类 Chip（秦朝…）→ 条目列表 */
    private lateinit var baikeAdapter: PoemAdapter
    private var currentBaikeRoot: String = ""      // 当前大类：动物 / 历史
    private var currentBaikeFilter: String = ""    // 当前数据集过滤键：动物 / 历史·秦朝

    private fun setupBaikePanel() {
        val rec = findViewById<RecyclerView>(R.id.baikeRecycler)
        rec.layoutManager = LinearLayoutManager(this)
        baikeAdapter = PoemAdapter(emptyList(), onItemClick = { idx -> openDetail(idx, baikeAdapter.currentList()) }, onFavClick = { _, _ -> })
        rec.adapter = baikeAdapter

        // 第一层：大类 Chip
        val group = findViewById<ChipGroup>(R.id.baikeCatGroup)
        group.removeAllViews()
        for (root in PoetryRepo.BAIKE_ROOTS) {
            val chip = Chip(this)
            chip.text = root
            chip.setTextColor(getColor(R.color.ink_soft))
            chip.chipStrokeColor = android.content.res.ColorStateList.valueOf(getColor(R.color.divider))
            chip.chipStrokeWidth = 1f
            chip.setOnClickListener {
                currentBaikeRoot = root
                currentBaikeFilter = ""
                highlightBaikeChips(group, root)
                onBaikeRootSelected(root)
            }
            group.addView(chip)
        }
        // 默认选第一个大类
        currentBaikeRoot = PoetryRepo.BAIKE_ROOTS.firstOrNull() ?: ""
        currentBaikeFilter = ""
        highlightBaikeChips(group, currentBaikeRoot)
        onBaikeRootSelected(currentBaikeRoot)
    }

    /** 选中某大类后的处理：有子类则显示子类 Chip，无子类直接加载条目 */
    private fun onBaikeRootSelected(root: String) {
        val subs = baikeSubFilters(root)
        val subGroup = findViewById<ChipGroup>(R.id.baikeSubGroup)
        val subScroll = findViewById<View>(R.id.baikeSubScroll)
        // 只要该大类带子类（键含「·」），就先显示子类 Chip 等用户点选
        val hasSub = subs.any { it.contains('·') }
        if (hasSub) {
            // 有子类（如 历史→秦朝）：显示子类 Chip，等待用户点选，列表先清空
            subScroll.visibility = View.VISIBLE
            subGroup.visibility = View.VISIBLE
            subGroup.removeAllViews()
            for (f in subs) {
                val chip = Chip(this)
                chip.text = f.substringAfter('·')   // 显示「秦朝」，去掉前缀
                chip.setTextColor(getColor(R.color.ink_soft))
                chip.chipStrokeColor = android.content.res.ColorStateList.valueOf(getColor(R.color.divider))
                chip.chipStrokeWidth = 1f
                chip.setOnClickListener {
                    currentBaikeFilter = f
                    highlightBaikeChips(subGroup, chip.text.toString())
                    loadBaikeCategory(f)
                }
                subGroup.addView(chip)
            }
            currentBaikeFilter = ""
            baikeAdapter.setData(emptyList())
        } else {
            // 无子类（如 动物）：隐藏子类 Chip，直接加载
            subScroll.visibility = View.GONE
            subGroup.visibility = View.GONE
            subGroup.removeAllViews()
            currentBaikeFilter = subs.firstOrNull() ?: ""
            loadBaikeCategory(currentBaikeFilter)
        }
    }

    /** 某大类下的数据集过滤键列表：无子类返回自身，有子类返回「大类·子类」 */
    private fun baikeSubFilters(root: String): List<String> =
        PoetryRepo.BAIKE_CATEGORIES.map { it.first }.filter { it.substringBefore('·') == root }

    private fun highlightBaikeChips(group: ChipGroup, selected: String) {
        for (i in 0 until group.childCount) {
            val c = group.getChildAt(i) as Chip
            if (c.text == selected) {
                c.setChipBackgroundColorResource(R.color.cinnabar)
                c.setTextColor(getColor(R.color.white))
            } else {
                c.setChipBackgroundColorResource(android.R.color.transparent)
                c.setTextColor(getColor(R.color.ink_soft))
            }
        }
    }

    private fun loadBaikeCategory(filter: String) {
        val list = PoetryRepo.page(filter, 0, PoetryRepo.count(filter).coerceAtLeast(1))
        baikeAdapter.setData(list)
    }

    /** 刷新「我的」面板的收藏计数 */
    private fun refreshMineStats() {
        val favCount = FavStore.all(this).size
        findViewById<TextView>(R.id.mineStats).text =
            "共收藏 $favCount 首 · 收录 ${HistoryData.DYNASTIES.size} 个王朝 · 9 万余首古诗词"
        findViewById<TextView>(R.id.mineFavCount).text = favCount.toString()
    }

    override fun onResume() {
        super.onResume()
        // 从收藏/历史页返回时刷新统计
        if (findViewById<View>(R.id.minePanel).visibility == View.VISIBLE) refreshMineStats()
    }

    override fun onDestroy() {
        super.onDestroy()
        searchJob?.cancel()
    }
}