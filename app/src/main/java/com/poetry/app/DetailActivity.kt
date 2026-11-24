package com.poetry.app

import android.os.Bundle
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 诗词详情页：竖版居中展示标题/作者/正文，支持收藏，
 * 以及左右滑 / 上下滑手势切换上一篇 / 下一篇。
 */
class DetailActivity : AppCompatActivity() {

    private lateinit var poem: Poem
    private lateinit var idx: PoemIndex
    private lateinit var analysisCard: View
    private lateinit var analysisText: TextView
    private lateinit var answerCard: View
    private lateinit var answerText: TextView
    private lateinit var revealHint: TextView
    private var loading = false

    // 手势检测：左右滑 / 上下滑切换上下篇
    private lateinit var gesture: GestureDetector
    private val swipeSlop = 120          // 最小滑动距离(px)
    private val swipeVelocity = 200f     // 最小速度

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // 初始化 View 字段
        analysisCard = findViewById(R.id.analysisCard)
        analysisText = findViewById(R.id.analysisText)
        answerCard = findViewById(R.id.answerCard)
        answerText = findViewById(R.id.answerText)
        revealHint = findViewById(R.id.revealHint)

        gesture = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                val absX = kotlin.math.abs(dx)
                val absY = kotlin.math.abs(dy)
                // 横向：左滑=下一篇，右滑=上一篇
                if (absX > swipeSlop && absX > absY && kotlin.math.abs(vx) > swipeVelocity) {
                    if (dx < 0) nextPoem() else prevPoem()
                    return true
                }
                // 纵向：上滑=下一篇，下滑=上一篇
                if (absY > swipeSlop && absY > absX && kotlin.math.abs(vy) > swipeVelocity) {
                    if (dy < 0) nextPoem() else prevPoem()
                    return true
                }
                return false
            }
        })

        // 首次进入：优先用 PoemNav 当前位置；若无上下文则用传入的 poem
        if (PoemNav.current() != null) {
            renderCurrent()
        } else {
            poem = intent.getSerializableExtra("poem") as Poem
            idx = poem.index
            showPoem(poem)
        }
    }

    /** 渲染 PoemNav 当前位置对应的诗词 */
    private fun renderCurrent() {
        val cur = PoemNav.current() ?: run { finish(); return }
        loading = true
        val p = PoetryRepo.loadBodySync(this, cur)
        idx = cur
        poem = p
        loading = false
        showPoem(p)
    }

    private fun nextPoem() {
        if (loading) return
        val n = PoemNav.next()
        if (n != null) renderCurrent()
        else Toast.makeText(this, "已经是最后一篇", Toast.LENGTH_SHORT).show()
    }

    private fun prevPoem() {
        if (loading) return
        val p = PoemNav.prev()
        if (p != null) renderCurrent()
        else Toast.makeText(this, "已经是第一篇", Toast.LENGTH_SHORT).show()
    }

    /** 把 poem 渲染到界面 */
    private fun showPoem(p: Poem) {
        supportActionBar?.title = p.category
        val titleView = findViewById<TextView>(R.id.detailTitle)
        val authorView = findViewById<TextView>(R.id.detailAuthor)
        val bodyView = findViewById<TextView>(R.id.detailBody)

        titleView.text = p.displayTitle
        setupDongwuImage(p)
        var authorLine = "〔${p.dynasty}〕${p.author}"
        if (p.section.isNotBlank() && p.category == "楚辞") authorLine += " · ${p.section}"
        if (PoetryRepo.isFun(p.category)) {
            authorLine = p.category
            authorView.text = authorLine
            bodyView.text = p.body
            if (p.hasAnalysis) {
                answerCard.visibility = View.VISIBLE
                answerText.text = p.analysis
                answerText.visibility = View.GONE
                revealHint.visibility = View.VISIBLE
                answerCard.setOnClickListener {
                    if (answerText.visibility == View.GONE) {
                        answerText.visibility = View.VISIBLE
                        revealHint.visibility = View.GONE
                    } else {
                        answerText.visibility = View.GONE
                        revealHint.visibility = View.VISIBLE
                    }
                }
            } else {
                answerCard.visibility = View.GONE
            }
        } else {
            authorView.text = authorLine
            bodyView.text = p.body
            if (p.hasAnalysis) {
                analysisCard.visibility = View.VISIBLE
                analysisText.text = p.analysis
            } else {
                analysisCard.visibility = View.GONE
            }
        }
        // 位置指示：标题栏显示 x/y
        supportActionBar?.subtitle =
            if (PoemNav.size() > 1) "${PoemNav.pos() + 1}/${PoemNav.size()}" else null
        invalidateOptionsMenu()
    }

    /** 动物类目：从 assets 加载对应图片，无图则隐藏 */
    private fun setupDongwuImage(p: Poem) {
        val img = findViewById<ImageView>(R.id.dongwuImage)
        val file = dongwuImageFile(p.displayTitle, p.category)
        if (file == null) {
            img.visibility = View.GONE
            return
        }
        try {
            val sub = when {
                p.category.startsWith("四大发明·") -> "sifa"
                p.category.startsWith("草本植物·") -> "caoben"
                p.category.startsWith("水果·") -> "shuiguo"
                p.category.startsWith("花卉·") -> "huahui"
                else -> "dongwu"
            }
            val stream = assets.open("img/$sub/$file")
            val bmp = android.graphics.BitmapFactory.decodeStream(stream)
            stream.close()
            if (bmp != null) {
                img.setImageBitmap(bmp)
                img.visibility = View.VISIBLE
            } else {
                img.visibility = View.GONE
            }
        } catch (e: Exception) {
            img.visibility = View.GONE
        }
    }

    /** 动物中文名 -> assets 图片文件名（null 表示无图） */
    private fun dongwuImageFile(title: String, category: String): String? {
        val map = mapOf(
            "大熊猫" to "giant_panda.jpg",
            "东北虎" to "siberian_tiger.jpg",
            "金丝猴" to "golden_snub_nosed_monkey.jpg",
            "雪豹" to "snow_leopard.jpg",
            "白鱀豚" to "baiji.jpg",
            "藏羚羊" to "tibetan_antelope.jpg",
            "长江江豚" to "yangtze_finless_porpoise.jpg",
            "中华穿山甲" to "chinese_pangolin.jpg",
            "麋鹿" to "peres_david_deer.jpg",
            "亚洲象" to "asian_elephant.jpg",
            "野牦牛" to "wild_yak.jpg",
            "羚牛" to "takin.jpg",
            "丹顶鹤" to "red_crowned_crane.jpg",
            "朱鹮" to "crested_ibis.jpg",
            "黑颈鹤" to "black_necked_crane.jpg",
            "褐马鸡" to "brown_eared_pheasant.jpg",
            "黄腹角雉" to "yellow_bellied_tragopan.jpg",
            "中华秋沙鸭" to "chinese_merganser.jpg",
            "绿孔雀" to "green_peafowl.jpg",
            "扬子鳄" to "chinese_alligator.jpg",
            "蟒蛇" to "burmese_python.jpg",
            "大鲵" to "chinese_giant_salamander.jpg",
            "四爪陆龟" to "four_toed_tortoise.jpg",
            "中华鲟" to "chinese_sturgeon.jpg",
            "长江白鲟" to "chinese_paddlefish.jpg",
            "达氏鲟" to "daba_sturgeon.jpg",
            "松江鲈鱼" to "songjiang_bass.jpg",
            "黄唇鱼" to "yellow_lipped_sea_bream.jpg",
            "金斑喙凤蝶" to "golden_kaiser_butterfly.jpg",
            "中华鲎" to "chinese_horseshoe_crab.jpg",
            "红珊瑚" to "red_coral.jpg",
            "鹦鹉螺" to "nautilus.jpg"
        )
        // 仅动物类目才返回图
        val cat = category
        if (cat.startsWith("四大发明·")) {
            val sifaMap = mapOf(
                "造纸术" to "zaozhi.jpg",
                "印刷术" to "yinshua.jpg",
                "火药" to "huoyao.jpg",
                "指南针" to "zhinanzhen.jpg"
            )
            // 只在"总述"条目（即标题恰为发明名）显示配图
            return if (title == cat.substringAfter("·")) sifaMap[title] else null
        }
        if (cat.startsWith("草本植物·")) {
            val caobenMap = mapOf(
                "人参" to "renshen.jpg",
                "甘草" to "gancao.jpg",
                "黄芪" to "huangqi.jpg",
                "当归" to "danggui.jpg",
                "益母草" to "yimucao.jpg",
                "枸杞" to "gouqi.jpg",
                "金银花" to "jinyinhua.jpg",
                "菊花" to "juhua.jpg",
                "薄荷" to "bohe.jpg",
                "艾草" to "aicao.jpg",
                "蒲公英" to "pugongying.jpg",
                "紫苏" to "zisu.jpg",
            )
            return if (title == cat.substringAfter("·")) caobenMap[title] else null
        }
        if (cat.startsWith("水果·")) {
            val m = mapOf(
                "苹果" to "apple.jpg.jpg",
                "梨" to "pear.jpg.jpg",
                "香蕉" to "banana.jpg.jpg",
                "葡萄" to "grape.jpg.jpg",
                "橙子" to "orange.jpg.jpg",
                "西瓜" to "watermelon.jpg.jpg",
                "芒果" to "mango.jpg.jpg",
                "桃" to "peach.jpg.jpg",
                "草莓" to "strawberry.jpg.jpg",
                "菠萝" to "pineapple.jpg.jpg",
            )
            return if (title == cat.substringAfter("·")) m[title] else null
        }
        if (cat.startsWith("花卉·")) {
            val m = mapOf(
                "牡丹" to "peony.jpg.jpg",
                "月季" to "rose.jpg.jpg",
                "梅花" to "plum.jpg.jpg",
                "兰花" to "orchid.jpg.jpg",
                "菊花" to "chrysanthemum.jpg.jpg",
                "荷花" to "lotus.jpg.jpg",
                "百合" to "lily.jpg.jpg",
                "山茶花" to "camellia.jpg.jpg",
                "桂花" to "osmanthus.jpg.jpg",
                "郁金香" to "tulip.jpg.jpg",
            )
            return if (title == cat.substringAfter("·")) m[title] else null
        }
        if (cat != "动物·哺乳动物" && cat != "动物·鸟类" && cat != "动物·爬行两栖" &&
            cat != "动物·鱼类" && cat != "动物·昆虫及其他") return null
        return map[title]
    }

    /** 让整页响应手势 */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gesture.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_detail, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val item = menu.findItem(R.id.action_fav)
        item.title = if (FavStore.isFav(this, idx)) "已收藏 ★" else "收藏 ☆"
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_fav -> {
                val added = FavStore.toggle(this, idx)
                Toast.makeText(this,
                    if (added) "已加入收藏" else "已取消收藏",
                    Toast.LENGTH_SHORT).show()
                invalidateOptionsMenu()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}