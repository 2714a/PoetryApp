package com.poetry.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * 诗词仓库（索引/内容分离 + 分页 + 随机访问）。
 * .idx 每行: 标题|作者|朝代|词牌|章|节|cnt行号|段落数
 * .cnt 每行: 一首诗的全部段落（\x01 分隔）
 * 首次启动把 assets/data 复制到 filesDir 以支持 RandomAccessFile 随机读取正文。
 */
object PoetryRepo {

    /** 分类 -> 文件基名 */
    /** 首页大类 -> 子类列表（子目录）。仅用于首页折叠导航，数据仍按子类名存 datasets。 */
    val HOME_ROOTS: List<Pair<String, List<String>>> = listOf(
        "古诗词" to listOf("唐诗", "宋词", "元曲", "诗经", "楚辞", "曹操", "纳兰性德"),
        "趣味" to listOf("歇后语", "谜语", "脑筋急转弯"),
        "众生之来历" to listOf("辣椒的来历", "番茄的来历", "土豆的来历", "红薯的来历"),
        "民间小故事" to listOf("长犄角的国王")
    )
    /** 大类下所有子类对应的 datasets 键（= 子类名本身） */
    fun homeChildren(root: String): List<String> =
        HOME_ROOTS.firstOrNull { it.first == root }?.second ?: emptyList()

    val CATEGORIES: List<Pair<String, String>> = listOf(
        "唐诗" to "tangshi",
        "宋词" to "songci",
        "元曲" to "yuanqu",
        "诗经" to "shijing",
        "楚辞" to "chuci",
        "曹操" to "caocao",
        "纳兰性德" to "nalan",
        "歇后语" to "xiehouyu",
        "谜语" to "riddle",
        "脑筋急转弯" to "brain",
        "辣椒的来历" to "lajiao",
        "番茄的来历" to "fanqie",
        "土豆的来历" to "tudou",
        "红薯的来历" to "hongshu",
        "长犄角的国王" to "gushi"
    )

    /** 百科类目（底部 Tab 专用，不进入首页顶部分类标签）。
     *  键=类目名，值=相对 assets/data 的基名（可含子目录）。
     *  后续扩展：加 "植物" to "baike/baike_zhiwu" 即可。 */
    val BAIKE_CATEGORIES: List<Pair<String, String>> = listOf(
        // ===== 动物（5 个子类）=====
        "动物·哺乳动物" to "baike/baike_dongwu_buru",
        "动物·鸟类" to "baike/baike_dongwu_niaolei",
        "动物·爬行两栖" to "baike/baike_dongwu_paxing",
        "动物·鱼类" to "baike/baike_dongwu_yulei",
        "动物·昆虫及其他" to "baike/baike_dongwu_kunchong",
        // ===== 历史（23 个朝代，按时间顺序）=====
        "历史·夏朝" to "baike/baike_his_xia",
        "历史·商朝" to "baike/baike_his_shang",
        "历史·西周" to "baike/baike_his_xizhou",
        "历史·东周春秋" to "baike/baike_his_chunqiu",
        "历史·东周战国" to "baike/baike_his_zhanguo",
        "历史·秦朝" to "baike/baike_qinshi",
        "历史·西汉" to "baike/baike_his_xihan",
        "历史·新朝" to "baike/baike_his_xin",
        "历史·东汉" to "baike/baike_his_donghan",
        "历史·三国" to "baike/baike_his_sanguo",
        "历史·西晋" to "baike/baike_his_xijin",
        "历史·东晋" to "baike/baike_his_dongjin",
        "历史·南北朝" to "baike/baike_his_nanbei",
        "历史·隋朝" to "baike/baike_his_sui",
        "历史·唐朝" to "baike/baike_his_tang",
        "历史·五代十国" to "baike/baike_his_wudai",
        "历史·北宋" to "baike/baike_his_beisong",
        "历史·南宋" to "baike/baike_his_nansong",
        "历史·辽朝" to "baike/baike_his_liao",
        "历史·金朝" to "baike/baike_his_jin",
        "历史·元朝" to "baike/baike_his_yuan",
        "历史·明朝" to "baike/baike_his_ming",
        "历史·清朝" to "baike/baike_his_qing",
        // ===== 近代史（清朝灭亡至今，接续清朝）=====
        "历史·晚清民国" to "baike/baike_his_wanqing",
        "历史·新中国" to "baike/baike_his_xinzhongguo",
        "历史·改革开放" to "baike/baike_his_gaigekaifang",
        // ===== 四大名著（每本名著一个子类，内含作者年份/故事来由/人物/情节）=====
        "四大名著·红楼梦" to "baike/baike_sida_hlm",
        "四大名著·西游记" to "baike/baike_sida_xyj",
        "四大名著·水浒传" to "baike/baike_sida_shz",
        "四大名著·三国演义" to "baike/baike_sida_sgy",
        // ===== 四大发明（每个发明一个子类，内含总述/背景/工艺/传播）=====
        "四大发明·造纸术" to "baike/baike_sifa_zaozhi",
        "四大发明·印刷术" to "baike/baike_sifa_yinshua",
        "四大发明·火药" to "baike/baike_sifa_huoyao",
        "四大发明·指南针" to "baike/baike_sifa_zhinanzhen",

"草本植物·人参" to "baike/baike_caoben_renshen",
"草本植物·甘草" to "baike/baike_caoben_gancao",
"草本植物·黄芪" to "baike/baike_caoben_huangqi",
"草本植物·当归" to "baike/baike_caoben_danggui",
"草本植物·益母草" to "baike/baike_caoben_yimucao",
"草本植物·枸杞" to "baike/baike_caoben_gouqi",
"草本植物·金银花" to "baike/baike_caoben_jinyinhua",
"草本植物·菊花" to "baike/baike_caoben_juhua",
"草本植物·薄荷" to "baike/baike_caoben_bohe",
"草本植物·艾草" to "baike/baike_caoben_aicao",
"草本植物·蒲公英" to "baike/baike_caoben_pugongying",
"草本植物·紫苏" to "baike/baike_caoben_zisu",
"水果·苹果" to "baike/baike_shuiguo_apple",
"水果·梨" to "baike/baike_shuiguo_pear",
"水果·香蕉" to "baike/baike_shuiguo_banana",
"水果·葡萄" to "baike/baike_shuiguo_grape",
"水果·橙子" to "baike/baike_shuiguo_orange",
"水果·西瓜" to "baike/baike_shuiguo_watermelon",
"水果·芒果" to "baike/baike_shuiguo_mango",
"水果·桃" to "baike/baike_shuiguo_peach",
"水果·草莓" to "baike/baike_shuiguo_strawberry",
"水果·菠萝" to "baike/baike_shuiguo_pineapple",
"花卉·牡丹" to "baike/baike_huahui_peony",
"花卉·月季" to "baike/baike_huahui_rose",
"花卉·梅花" to "baike/baike_huahui_plum",
"花卉·兰花" to "baike/baike_huahui_orchid",
"花卉·菊花" to "baike/baike_huahui_chrysanthemum",
"花卉·荷花" to "baike/baike_huahui_lotus",
"花卉·百合" to "baike/baike_huahui_lily",
"花卉·山茶花" to "baike/baike_huahui_camellia",
"花卉·桂花" to "baike/baike_huahui_osmanthus",
"花卉·郁金香" to "baike/baike_huahui_tulip",
        "昆虫·常见昆虫" to "baike/baike_kunchong",
        "海洋生物·常见海洋生物" to "baike/baike_haiyang"
    )

    /** 百科大类（用于第一层 Chip）：从 BAIKE_CATEGORIES 的键中提取「·」前的部分 */
    val BAIKE_ROOTS: List<String> = BAIKE_CATEGORIES.map { it.first.substringBefore('·') }.distinct()

    /** 趣味类目（非诗词，详情页用答案揭示式展示） */
    val FUN_CATEGORIES = setOf("歇后语", "谜语", "脑筋急转弯")
    fun isFun(cat: String): Boolean = FUN_CATEGORIES.contains(cat)

    /** 每类数据集 */
    private class Dataset(val name: String) {
        var indexLines: List<String> = emptyList()   // 常驻内存（可搜索）
        var contentFile: File? = null                 // 随机访问正文
        var analysisFile: File? = null                // 解析（译文/赏析）文件，按标题键查
        var offsets: LongArray = LongArray(0)         // 每个 cnt 行的起始字节偏移
        var count: Int = 0
    }

    private val datasets = LinkedHashMap<String, Dataset>()
    private val mutex = Mutex()
    private var initialized = false

    /** 正文缓存（LRU，容量 200） */
    private val bodyCache = object : LinkedHashMap<String, List<String>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>): Boolean =
            size > 200
    }

    /** 解析缓存（LRU，容量 100） */
    private val analysisCache = object : LinkedHashMap<String, String>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean =
            size > 100
    }

    /** assets 下某文件是否存在（rel 为相对 assets/data 的路径，可含子目录） */
    private fun hasAsset(ctx: Context, rel: String): Boolean =
        try { ctx.assets.open("data/$rel").close(); true } catch (e: Exception) { false }

    /** 初始化：复制 assets 并加载全部索引（诗词类 + 百科类） */
    suspend fun init(ctx: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (initialized) return@withLock
            val dir = File(ctx.filesDir, "poetry").apply { mkdirs() }
            for ((cat, base) in CATEGORIES) {
                val idxFile = ensureExtracted(ctx, dir, "$base.idx")
                val cntFile = ensureExtracted(ctx, dir, "$base.cnt")
                // 解析文件可选（存在才复制）
                var anFile: File? = null
                if (hasAsset(ctx, "$base.an")) {
                    anFile = ensureExtracted(ctx, dir, "$base.an")
                }
                val ds = Dataset(cat)
                ds.indexLines = idxFile.readLines()
                ds.contentFile = cntFile
                ds.analysisFile = anFile
                ds.count = ds.indexLines.size
                buildOffsets(ds)
                datasets[cat] = ds
            }
            // 百科类目（可含子目录基名）
            for ((cat, base) in BAIKE_CATEGORIES) {
                if (!hasAsset(ctx, "$base.idx")) continue
                val idxFile = ensureExtracted(ctx, dir, "$base.idx")
                val cntFile = ensureExtracted(ctx, dir, "$base.cnt")
                val ds = Dataset(cat)
                ds.indexLines = idxFile.readLines()
                ds.contentFile = cntFile
                ds.analysisFile = null
                ds.count = ds.indexLines.size
                buildOffsets(ds)
                datasets[cat] = ds
            }
            initialized = true
        }
    }

    /** 把 assets 文件释放到 filesDir（若不存在或大小不一致）。rel 可含子目录，输出平铺到同一 dir。 */
    private fun ensureExtracted(ctx: Context, dir: File, rel: String): File {
        val out = File(dir, rel.substringAfterLast('/'))   // 输出文件名取基名，平铺
        val assetSize = try {
            ctx.assets.openFd("data/$rel").length
        } catch (e: Exception) {
            -1L
        }
        if (!out.exists() || (assetSize > 0 && out.length() != assetSize)) {
            ctx.assets.open("data/$rel").use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return out
    }

    /** 构建 cnt 文件每行起始偏移，用于随机读取 */
    private fun buildOffsets(ds: Dataset) {
        val f = ds.contentFile ?: return
        RandomAccessFile(f, "r").use { raf ->
            val offs = ArrayList<Long>(ds.count)
            offs.add(0L)
            var pos = 0L
            val buf = ByteArray(65536)
            while (true) {
                val n = raf.read(buf)
                if (n <= 0) break
                for (i in 0 until n) {
                    if (buf[i] == '\n'.code.toByte()) {
                        pos += 1
                        offs.add(pos)
                    } else {
                        pos += 1
                    }
                }
            }
            ds.offsets = offs.toLongArray()
        }
    }

    /** 某分类总数 */
    fun count(cat: String): Int = datasets[cat]?.count ?: 0

    /** 分页读取某分类的索引片段 */
    fun page(cat: String, start: Int, size: Int): List<PoemIndex> {
        val ds = datasets[cat] ?: return emptyList()
        if (start >= ds.count) return emptyList()
        val end = minOf(start + size, ds.count)
        val out = ArrayList<PoemIndex>(end - start)
        for (i in start until end) {
            out.add(parseIndex(ds.indexLines[i], cat))
        }
        return out
    }

    private fun parseIndex(line: String, cat: String): PoemIndex {
        val p = line.split('|')
        // 字段: 标题|作者|朝代|词牌|章|节|行号|段落数(末字段保留，行号取倒数第2)
        val title = p.getOrElse(0) { "" }
        val author = p.getOrElse(1) { "佚名" }
        val dynasty = p.getOrElse(2) { "" }
        val rhy = p.getOrElse(3) { "" }
        val ch = p.getOrElse(4) { "" }
        val sec = p.getOrElse(5) { "" }
        val lineNo = p.getOrElse(6) { "0" }.toIntOrNull() ?: 0
        val summary = p.getOrElse(8) { "" }
        return PoemIndex(title, author, dynasty, rhy, ch, sec, lineNo, cat, summary)
    }

    /** 读取某索引的正文 + 解析（译文/赏析）— 协程版（兼容旧调用） */
    suspend fun loadBody(ctx: Context, idx: PoemIndex): Poem = withContext(Dispatchers.IO) {
        loadBodySync(ctx, idx)
    }

    /** 同步版：读正文 + 解析（滑动切换/收藏页直接调用，无协程） */
    fun loadBodySync(ctx: Context, idx: PoemIndex): Poem {
        val key = "${idx.category}#${idx.lineNo}"
        val analysis = loadAnalysisSync(ctx, idx)
        synchronized(bodyCache) {
            bodyCache[key]?.let { cached ->
                return Poem(idx, cached,
                    if (PoetryRepo.isFun(idx.category) && cached.size >= 2) cached[1] else analysis)
            }
        }
        val ds = datasets[idx.category]
        val paras = if (ds != null && idx.lineNo < ds.offsets.size) {
            val start = ds.offsets[idx.lineNo]
            val end = if (idx.lineNo + 1 < ds.offsets.size) ds.offsets[idx.lineNo + 1] else ds.contentFile?.length() ?: start
            val len = (end - start).toInt().coerceAtLeast(0)
            RandomAccessFile(ds.contentFile, "r").use { raf ->
                raf.seek(start)
                val buf = ByteArray(len)
                raf.readFully(buf)
                String(buf, Charsets.UTF_8).trimEnd('\n', '\r')
            }.split('\u0001').filter { it.isNotBlank() }
        } else emptyList()
        // 趣味类目：cnt 第1段=题面，第2段=答案；把答案注入 analysis 供答案卡片折叠展示
        val effectiveAnalysis = if (analysis.isNotBlank()) analysis
            else if (PoetryRepo.isFun(idx.category) && paras.size >= 2) paras[1]
            else ""
        val effectiveParas = if (PoetryRepo.isFun(idx.category) && paras.isNotEmpty()) listOf(paras[0]) else paras
        synchronized(bodyCache) { bodyCache[key] = effectiveParas }
        return Poem(idx, effectiveParas, effectiveAnalysis)
    }

    /**
     * 读取解析（译文/赏析/注释）。解析文件 .an 每条记录为多行文本，
     * 格式: 首行=标题键，后续行=解析正文，记录间用单行 "===" 分隔。
     * 按标题精确匹配；找不到返回空串。
     */
    private fun loadAnalysisSync(ctx: Context, idx: PoemIndex): String {
        val cacheKey = "${idx.category}#${idx.title}"
        synchronized(analysisCache) {
            analysisCache[cacheKey]?.let { return it }
        }
        val ds = datasets[idx.category] ?: return ""
        val an = ds.analysisFile ?: return ""
        val titleKey = idx.displayTitle.trim()
        val raw = an.readText()
        val records = raw.split(Regex("^===\\s*$", RegexOption.MULTILINE))
        var found = ""
        for (rec in records) {
            val lines = rec.trim().lines()
            if (lines.isEmpty()) continue
            val head = lines[0].trim()
            if (head == titleKey || head == idx.title.trim() || titleKey.startsWith(head)) {
                found = lines.drop(1).joinToString("\n").trim()
                break
            }
        }
        synchronized(analysisCache) { analysisCache[cacheKey] = found }
        return found
    }

/** 简体<->繁体 字对表（偶数位=简体，奇数位=繁体；源自 OpenCC STCharacters，用于繁简兼容搜索，全唐诗为繁体） */
    private const val ST_PAIRS =
        "㐷傌㐹㑶㐽偑㑇㑳㑈倲㑔㑯㑩儸㓆𠗣㓥劏㓰劃㔉劚㖊噚㖞喎㘎㘚㚯㜄㛀媰㛟𡞵㛠𡢃㛣㜏㛤孋㛿𡠹㟆㠏㟜𡾱㟥嵾㡎幓㤘㥮㤽懤㥪慺㧏掆㧐㩳㧑撝㧟擓㧰擽㨫㩜㭎棡㭏椲㭣𣙎㭤樢㭴樫㱩殰㱮殨㲿瀇㳔濧㳕灡㳠澾㳡濄㳢𣾷㳽瀰㴋潚㶉鸂㶶燶㶽煱㺍獱㻅璯㻏𤫩㻘𤪺䀥䁻䁖瞜䂵碽䃅磾䅉稏䅟穇䅪𥢢䇲筴䉤籔䌶䊷䌷紬䌸縳䌹絅䌺䋙䌻䋚䌼綐䌽綵䌾䋻䌿䋹䍀繿䍁繸䍠䍦䎬䎱䏝膞䑽𦪙䓓薵䓕薳䓖藭䓨罃䗖螮䘛𧝞䘞𧜗䙊𧜵䙌䙡䙓襬䜣訢䜤鿁䜥𧩙䜧䜀䜩讌䝙貙䞌𧵳䞍䝼䞎𧶧䞐賰䟢躎䢀𨊰䢁𨊸䢂𨋢䥺釾䥽鏺䥾䥱䥿𨯅䦀𨦫䦁𨧜䦂䥇䦃鐯䦅鐥䦆钁䦶䦛䦷䦟䩄靦䭪𩞯䯃𩣑䯄騧䯅䯀䲝䱽䲞𩶘䲟鮣䲠鰆䲡鰌䲢鰧䲣䱷䴓鳾䴔鵁䴕鴷䴖鶄䴗鶪䴘鷉䴙鸊䶮龑万萬与與丑醜专專业業丛叢东東丝絲丢丟两兩严嚴丧喪个個丰豐临臨为爲丽麗举舉么麼义義乌烏乐樂乔喬习習乡鄉书書买買乱亂了了争爭于於亏虧云雲亘亙亚亞产產亩畝亲親亵褻亸嚲亿億仅僅仆僕仇仇从從仑侖仓倉仪儀们們价價仿仿众衆优優伙夥会會伛傴伞傘伟偉传傳伡俥伣俔伤傷伥倀" +
        "伦倫伧傖伪僞伫佇体體余餘佛佛佣傭佥僉侠俠侣侶侥僥侦偵侧側侨僑侩儈侪儕侬儂侭儘俊俊俣俁俦儔俨儼俩倆俪儷俫倈俭儉修修借借债債倾傾偬傯偻僂偾僨偿償傤儎傥儻傧儐储儲傩儺僵僵儿兒克克兑兌兖兗党黨兰蘭关關兴興具具兹茲养養兽獸冁囅内內冈岡册冊写寫军軍农農冢冢冬冬冯馮冲衝决決况況冻凍净淨凄悽准準凉涼凌凌减減凑湊凛凜几幾凤鳳凫鳧凭憑凯凱凶兇出出击擊凿鑿刍芻划劃刘劉则則刚剛创創删刪别別刬剗刭剄刮刮制制刹剎刽劊刾㓨刿劌剀剴剂劑剐剮剑劍剥剝剧劇劝勸办辦务務劢勱动動励勵劲勁劳勞势勢勋勳勚勩匀勻匦匭匮匱区區医醫千千升升华華协協单單卖賣卜卜占佔卢盧卤滷卧臥卫衛却卻卷卷卺巹厂廠厅廳历歷厉厲压壓厌厭厍厙厐龎厕廁厘釐厢廂厣厴厦廈厨廚厩廄厮廝县縣叁叄参參叆靉叇靆双雙发發变變叙敘叠疊只只台臺叶葉号號叹嘆叽嘰吁籲吃喫合合吊吊同同后後向向吓嚇吕呂吗嗎吣吣吨噸听聽启啓吴吳呆呆呐吶呒嘸呓囈呕嘔呖嚦呗唄员員呙咼呛嗆呜嗚周周咏詠咙嚨咛嚀咝噝" +
        "咤吒咨諮咸鹹咽咽哄哄响響哑啞哒噠哓嘵哔嗶哕噦哗譁哙噲哜嚌哝噥哟喲唇脣唛嘜唝嗊唠嘮唡啢唢嗩唤喚啧嘖啬嗇啭囀啮齧啯嘓啰囉啴嘽啸嘯喂喂喷噴喽嘍喾嚳嗫囁嗳噯嘘噓嘤嚶嘱囑噜嚕噪噪嚣囂回回团團园園困困囱囪围圍囵圇国國图圖圆圓圣聖圹壙场場坏壞坐坐块塊坚堅坛壇坜壢坝壩坞塢坟墳坠墜坯坯垄壟垅壠垆壚垒壘垦墾垩堊垫墊垭埡垯墶垱壋垲塏垴堖埘塒埙壎埚堝堑塹堕墮堤堤塆壪墙牆壮壯声聲壳殼壶壺壸壼处處备備复復够夠夫夫头頭夸誇夹夾夺奪奁奩奂奐奋奮奖獎奥奧奸奸妆妝妇婦妈媽妩嫵妪嫗妫嬀姗姍姜姜姹奼娄婁娅婭娆嬈娇嬌娈孌娘娘娱娛娲媧娴嫺婳嫿婴嬰婵嬋婶嬸媪媼媭嬃嫒嬡嫔嬪嫱嬙嬷嬤孙孫学學孪孿宁寧它它宝寶实實宠寵审審宪憲宫宮家家宽寬宾賓寝寢对對寻尋导導寿壽将將尔爾尘塵尝嘗尧堯尴尷尸屍尽盡局局层層屃屓屉屜届屆属屬屡屢屦屨屿嶼岁歲岂豈岖嶇岗崗岘峴岚嵐岛島岩巖岭嶺岳嶽岽崬岿巋峃嶨峄嶧峡峽峣嶢峤嶠峥崢峦巒峰峯崂嶗崃崍崄嶮崭嶄嵘嶸嵚嶔嵝嶁巅巔巨巨" +
        "巩鞏巯巰币幣布布帅帥师師帏幃帐帳帘簾帜幟带帶帧幀席席帮幫帱幬帻幘帼幗幂冪干幹并並幸幸广廣庄莊庆慶床牀庐廬庑廡库庫应應庙廟庞龐废廢庵庵庼廎廪廩开開异異弃棄弑弒张張弥彌弦弦弪弳弯彎弹彈强強归歸当當录錄彟彠彦彥彨彲彩彩彻徹征徵径徑徕徠御御忆憶忏懺志志忧憂念念忾愾怀懷态態怂慫怃憮怄慪怅悵怆愴怜憐总總怼懟怿懌恋戀恒恆恤恤恳懇恶惡恸慟恹懨恺愷恻惻恼惱恽惲悦悅悫愨悬懸悭慳悮悞悯憫惊驚惧懼惨慘惩懲惫憊惬愜惭慚惮憚惯慣愈愈愠慍愤憤愦憒愿願慑懾慭憖懑懣懒懶懔懍戆戇戋戔戏戲戗戧战戰戚戚戬戩戯戱户戶扇扇才才扎扎扑撲托托扣扣执執扩擴扪捫扫掃扬揚扰擾折折抚撫抛拋抟摶抠摳抡掄抢搶护護报報抵抵担擔拐拐拟擬拢攏拣揀拥擁拦攔拧擰拨撥择擇拿拿挂掛挚摯挛攣挜掗挝撾挞撻挟挾挠撓挡擋挢撟挣掙挤擠挥揮挦撏挨挨挽挽捆捆捍捍捝挩捞撈损損捡撿换換捣搗据據掳擄掴摑掷擲掸撣掺摻掼摜揽攬揾搵揿撳搀攙搁擱搂摟搄揯搅攪搜搜携攜摄攝摅攄摆擺摇搖摈擯摊攤" +
        "撄攖撑撐撵攆撷擷撸擼撺攛擜㩵擞擻攒攢敌敵敚敓敛斂敩斆数數斋齋斓斕斗鬥斤斤斩斬斫斫断斷旋旋无無旧舊时時旷曠旸暘昆昆昙曇昵暱昼晝昽曨显顯晋晉晒曬晓曉晔曄晕暈晖暉暂暫暅𣈶暗暗暧曖曲曲札札术術朱朱朴樸机機杀殺杂雜权權杆杆杠槓条條来來杨楊杩榪杯杯杰傑松松板板极極构構果果枞樅枢樞枣棗枥櫪枧梘枨棖枪槍枫楓枭梟柜櫃柠檸柽檉栀梔栅柵标標栈棧栉櫛栊櫳栋棟栌櫨栎櫟栏欄树樹栖棲栗栗样樣核核栾欒桠椏桡橈桢楨档檔桤榿桥橋桦樺桧檜桨槳桩樁桪樳梁梁梦夢梼檮梾棶梿槤检檢棁梲棂欞棱棱椁槨椝槼椟櫝椠槧椢槶椤欏椫樿椭橢椮槮楼樓榄欖榅榲榇櫬榈櫚榉櫸榝樧槚檟槛檻槟檳槠櫧横橫樯檣樱櫻橥櫫橱櫥橹櫓橼櫞檗檗檩檁欢歡欤歟欧歐欲欲歼殲殁歿殇殤残殘殒殞殓殮殚殫殡殯殴毆毁毀毂轂毕畢毙斃毡氈毵毿毶𣯶氇氌气氣氢氫氩氬氲氳汇匯汉漢汤湯汹洶沄澐沈沈沟溝没沒沣灃沤漚沥瀝沦淪沧滄沨渢沩潙沪滬沾沾泛泛泞濘注注泪淚泶澩泷瀧泸瀘泺濼泻瀉泼潑泽澤泾涇洁潔洒灑洼窪浃浹" +
        "浅淺浆漿浇澆浈湞浉溮浊濁测測浍澮济濟浏瀏浐滻浑渾浒滸浓濃浔潯浕濜浚浚涂塗涌湧涚涗涛濤涝澇涞淶涟漣涠潿涡渦涢溳涣渙涤滌润潤涧澗涨漲涩澀淀澱渊淵渌淥渍漬渎瀆渐漸渑澠渔漁渖瀋渗滲温溫游遊湾灣湿溼溁濚溃潰溅濺溆漵溇漊溪溪滗潷滚滾滞滯滟灩滠灄满滿滢瀅滤濾滥濫滦灤滨濱滩灘滪澦漓漓潆瀠潇瀟潋瀲潍濰潜潛潴瀦澄澄澛瀂澜瀾濑瀨濒瀕灏灝灭滅灯燈灵靈灶竈灾災灿燦炀煬炉爐炖燉炜煒炝熗点點炼煉炽熾烁爍烂爛烃烴烛燭烟煙烦煩烧燒烨燁烩燴烫燙烬燼热熱焕煥焖燜焘燾焰焰煴熅熏燻爱愛爷爺牍牘牦犛牵牽牺犧犊犢状狀犷獷犸獁犹猶狈狽狝獮狞獰独獨狭狹狮獅狯獪狰猙狱獄狲猻狸狸猃獫猎獵猕獼猡玀猪豬猫貓猬蝟献獻獭獺玑璣玙璵玚瑒玛瑪玩玩玮瑋环環现現玱瑲玺璽珐琺珑瓏珰璫珲琿琅琅琎璡琏璉琐瑣琼瓊瑶瑤瑷璦瑸璸璇璇璎瓔瓒瓚瓮甕瓯甌电電画畫畅暢畴疇疖癤疗療疟瘧疠癘疡瘍疬癧疭瘲疮瘡疯瘋疱皰疴痾症症痈癰痉痙痒癢痖瘂痨癆痪瘓痫癇痴癡瘅癉瘆瘮瘗瘞瘘瘻瘪癟瘫癱瘾癮" +
        "瘿癭癞癩癣癬癫癲皂皁皑皚皱皺皲皸盏盞盐鹽监監盖蓋盗盜盘盤眍瞘眦眥眬矓睁睜睐睞睑瞼瞆瞶瞒瞞瞩矚矩矩矫矯矶磯矾礬矿礦砀碭码碼砖磚砗硨砚硯砜碸砺礪砻礱砾礫础礎硁硜硕碩硖硤硗磽硙磑硚礄确確硵磠硷硷碍礙碛磧碜磣碱鹼礼禮祃禡祎禕祢禰祯禎祷禱祸禍禀稟禄祿禅禪离離私私秃禿秆稈秋秋种種秘祕积積称稱秽穢秾穠稆穭税稅稣穌稳穩穑穡穗穗穞穭穷窮窃竊窍竅窎窵窑窯窜竄窝窩窥窺窦竇窭窶竖豎竞競笃篤笋筍笔筆笕筧笺箋笼籠笾籩筑築筚篳筛篩筜簹筝箏筱筱筹籌筼篔签籤筿篠简簡箓籙箦簀箧篋箨籜箩籮箪簞箫簫篑簣篓簍篮籃篯籛篱籬簖籪籁籟籴糴类類籼秈粜糶粝糲粤粵粪糞粮糧粽糉糁糝糇餱糊糊糍餈系系紧緊累累絷縶緼縕縆緪纟糹纠糾纡紆红紅纣紂纤纖纥紇约約级級纨紈纩纊纪紀纫紉纬緯纭紜纮紘纯純纰紕纱紗纲綱纳納纴紝纵縱纶綸纷紛纸紙纹紋纺紡纻紵纼紖纽紐纾紓线線绀紺绁紲绂紱练練组組绅紳细細织織终終绉縐绊絆绋紼绌絀绍紹绎繹经經绐紿绑綁绒絨结結绔絝绕繞绖絰绗絎绘繪" +
        "给給绚絢绛絳络絡绝絕绞絞统統绠綆绡綃绢絹绣繡绤綌绥綏绦絛继繼绨綈绩績绪緒绫綾绬緓续續绮綺绯緋绰綽绱鞝绲緄绳繩维維绵綿绶綬绷繃绸綢绹綯绺綹绻綣综綜绽綻绾綰绿綠缀綴缁緇缂緙缃緗缄緘缅緬缆纜缇緹缈緲缉緝缊縕缋繢缌緦缍綞缎緞缏緶缐線缑緱缒縋缓緩缔締缕縷编編缗緡缘緣缙縉缚縛缛縟缜縝缝縫缞縗缟縞缠纏缡縭缢縊缣縑缤繽缥縹缦縵缧縲缨纓缩縮缪繆缫繅缬纈缭繚缮繕缯繒缰繮缱繾缲繰缳繯缴繳缵纘罂罌网網罗羅罚罰罢罷罴羆羁羈羟羥羡羨群羣翘翹翙翽翚翬耇耇耢耮耧耬耸聳耻恥聂聶聋聾职職聍聹联聯聩聵聪聰肃肅肠腸肤膚肮骯肴餚肾腎肿腫胀脹胁脅胄胄胆膽背背胜勝胡胡胧朧胨腖胪臚胫脛胶膠脉脈脍膾脏髒脐臍脑腦脓膿脔臠脚腳脱脫脶腡脸臉腊臘腌醃腘膕腭齶腻膩腼靦腽膃腾騰膑臏膻羶臜臢致致舆輿舍舍舣艤舰艦舱艙舻艫艰艱艳豔艺藝节節芈羋芗薌芜蕪芦蘆芸芸苁蓯苇葦苈藶苋莧苌萇苍蒼苎苧苏蘇苔苔苧薴苹蘋范範茎莖茏蘢茑蔦茔塋茕煢茧繭荆荊荐薦荙薘荚莢荛蕘荜蓽荝萴" +
        "荞蕎荟薈荠薺荡蕩荣榮荤葷荥滎荦犖荧熒荨蕁荩藎荪蓀荫蔭荬蕒荭葒荮葤药藥莅蒞莱萊莲蓮莳蒔莴萵莶薟获獲莸蕕莹瑩莺鶯莼蓴萚蘀萝蘿萤螢营營萦縈萧蕭萨薩葱蔥蒀蒕蒇蕆蒉蕢蒋蔣蒌蔞蒏醟蒙蒙蓝藍蓟薊蓠蘺蓣蕷蓥鎣蓦驀蔂虆蔑蔑蔷薔蔹蘞蔺藺蔼藹蕰薀蕲蘄蕴蘊薮藪藓蘚藤藤藴蘊蘖櫱虏虜虑慮虚虛虫蟲虬虯虮蟣虱蝨虽雖虾蝦虿蠆蚀蝕蚁蟻蚂螞蚃蠁蚕蠶蚝蠔蚬蜆蛊蠱蛎蠣蛏蟶蛮蠻蛰蟄蛱蛺蛲蟯蛳螄蛴蠐蜕蛻蜗蝸蜡蠟蝇蠅蝈蟈蝉蟬蝎蠍蝼螻蝾蠑螀螿螨蟎蟏蠨衅釁衔銜补補表表衬襯衮袞袄襖袅嫋袆褘袜襪袭襲袯襏装裝裆襠裈褌裢褳裣襝裤褲裥襉褛褸褴襤襕襴见見观觀觃覎规規觅覓视視觇覘览覽觉覺觊覬觋覡觌覿觍覥觎覦觏覯觐覲觑覷觞觴触觸觯觶訚誾詟讋誉譽誊謄讠訁计計订訂讣訃认認讥譏讦訐讧訌讨討让讓讪訕讫訖讬託训訓议議讯訊记記讱訒讲講讳諱讴謳讵詎讶訝讷訥许許讹訛论論讻訩讼訟讽諷设設访訪诀訣证證诂詁诃訶评評诅詛识識诇詗诈詐诉訴诊診诋詆诌謅词詞诎詘诏詔诐詖译譯诒詒诓誆诔誄" +
        "试試诖詿诗詩诘詰诙詼诚誠诛誅诜詵话話诞誕诟詬诠詮诡詭询詢诣詣诤諍该該详詳诧詫诨諢诩詡诪譸诫誡诬誣语語诮誚误誤诰誥诱誘诲誨诳誑说說诵誦诶誒请請诸諸诹諏诺諾读讀诼諑诽誹课課诿諉谀諛谁誰谂諗调調谄諂谅諒谆諄谇誶谈談谉讅谊誼谋謀谌諶谍諜谎謊谏諫谐諧谑謔谒謁谓謂谔諤谕諭谖諼谗讒谘諮谙諳谚諺谛諦谜謎谝諞谞諝谟謨谠讜谡謖谢謝谣謠谤謗谥諡谦謙谧謐谨謹谩謾谪謫谫譾谬謬谭譚谮譖谯譙谰讕谱譜谲譎谳讞谴譴谵譫谶讖谷谷豆豆象象豮豶贝貝贞貞负負贠貟贡貢财財责責贤賢败敗账賬货貨质質贩販贪貪贫貧贬貶购購贮貯贯貫贰貳贱賤贲賁贳貰贴貼贵貴贶貺贷貸贸貿费費贺賀贻貽贼賊贽贄贾賈贿賄赀貲赁賃赂賂赃贓资資赅賅赆贐赇賕赈賑赉賚赊賒赋賦赌賭赍齎赎贖赏賞赐賜赑贔赒賙赓賡赔賠赕賧赖賴赗賵赘贅赙賻赚賺赛賽赜賾赝贗赞贊赟贇赠贈赡贍赢贏赣贛赪赬赵趙赶趕趋趨趱趲趸躉跃躍跄蹌跖蹠跞躒践踐跶躂跷蹺跸蹕跹躚跻躋踌躊踪蹤踬躓踯躑蹑躡蹒蹣蹰躕蹿躥躏躪躜躦躯軀" +
        "輼轀车車轧軋轨軌轩軒轪軑轫軔转轉轭軛轮輪软軟轰轟轱軲轲軻轳轤轴軸轵軹轶軼轷軤轸軫轹轢轺軺轻輕轼軾载載轾輊轿轎辀輈辁輇辂輅较較辄輒辅輔辆輛辇輦辈輩辉輝辊輥辋輞辌輬辍輟辎輜辏輳辐輻辑輯辒轀输輸辔轡辕轅辖轄辗輾辘轆辙轍辚轔辞辭辟闢辩辯辫辮边邊辽遼达達迁遷过過迈邁运運还還这這进進远遠违違连連迟遲迩邇迳逕迹跡适適选選逊遜递遞逦邐逻邏遗遺遥遙邓鄧邝鄺邬鄔邮郵邹鄒邺鄴邻鄰郁鬱郏郟郐鄶郑鄭郓鄆郦酈郧鄖郸鄲酂酇酝醞酦醱酱醬酸酸酽釅酾釃酿釀醖醞采採释釋里裏鉴鑑銮鑾錾鏨钅釒钆釓钇釔针針钉釘钊釗钋釙钌釕钍釷钎釺钏釧钐釤钑鈒钒釩钓釣钔鍆钕釹钖鍚钗釵钘鈃钙鈣钚鈈钛鈦钜鉅钝鈍钞鈔钟鍾钠鈉钡鋇钢鋼钣鈑钤鈐钥鑰钦欽钧鈞钨鎢钩鉤钪鈧钫鈁钬鈥钭鈄钮鈕钯鈀钰鈺钱錢钲鉦钳鉗钴鈷钵鉢钶鈳钷鉕钸鈽钹鈸钺鉞钻鑽钼鉬钽鉭钾鉀钿鈿铀鈾铁鐵铂鉑铃鈴铄鑠铅鉛铆鉚铇鉋铈鈰铉鉉铊鉈铋鉍铌鈮铍鈹铎鐸铏鉶铐銬铑銠铒鉺铓鋩铔錏铕銪铖鋮铗鋏铘鋣铙鐃铚銍铛鐺" +
        "铜銅铝鋁铞銱铟銦铠鎧铡鍘铢銖铣銑铤鋌铥銩铦銛铧鏵铨銓铩鎩铪鉿铫銚铬鉻铭銘铮錚铯銫铰鉸铱銥铲鏟铳銃铴鐋铵銨银銀铷銣铸鑄铹鐒铺鋪铻鋙铼錸铽鋱链鏈铿鏗销銷锁鎖锂鋰锃鋥锄鋤锅鍋锆鋯锇鋨锈鏽锉銼锊鋝锋鋒锌鋅锍鋶锎鐦锏鐧锐銳锑銻锒鋃锓鋟锔鋦锕錒锖錆锗鍺锘鍩错錯锚錨锛錛锜錡锝鍀锞錁锟錕锠錩锡錫锢錮锣鑼锤錘锥錐锦錦锧鑕锨鍁锩錈锪鍃锫錇锬錟锭錠键鍵锯鋸锰錳锱錙锲鍥锳鍈锴鍇锵鏘锶鍶锷鍔锸鍤锹鍬锺鍾锻鍛锼鎪锽鍠锾鍰锿鎄镀鍍镁鎂镂鏤镃鎡镄鐨镅鎇镆鏌镇鎮镈鎛镉鎘镊鑷镋钂镌鐫镍鎳镎鎿镏鎦镐鎬镑鎊镒鎰镓鎵镔鑌镕鎔镖鏢镗鏜镘鏝镙鏍镚鏰镛鏞镜鏡镝鏑镞鏃镟鏇镠鏐镡鐔镢钁镣鐐镤鏷镥鑥镦鐓镧鑭镨鐠镩鑹镪鏹镫鐙镬鑊镭鐳镮鐶镯鐲镰鐮镱鐿镲鑔镳鑣镴鑞镵鑱镶鑲长長门門闩閂闪閃闫閆闬閈闭閉问問闯闖闰閏闱闈闲閒闳閎间間闵閔闶閌闷悶闸閘闹鬧闺閨闻聞闼闥闽閩闾閭闿闓阀閥阁閣阂閡阃閫阄鬮阅閱阆閬阇闍阈閾阉閹阊閶阋鬩阌閿阍閽阎閻阏閼阐闡阑闌阒闃阓闠" +
        "阔闊阕闋阖闔阗闐阘闒阙闕阚闞阛闤队隊阳陽阴陰阵陣阶階际際陆陸陇隴陈陳陉陘陕陝陦隯陧隉陨隕险險随隨隐隱隶隸隽雋难難雇僱雏雛雕雕雠讎雳靂雾霧霁霽霉黴霡霢霭靄靓靚靔靝静靜面面靥靨鞑韃鞒鞽鞯韉鞲韝韦韋韧韌韨韍韩韓韪韙韫韞韬韜韵韻页頁顶頂顷頃顸頇项項顺順须須顼頊顽頑顾顧顿頓颀頎颁頒颂頌颃頏预預颅顱领領颇頗颈頸颉頡颊頰颋頲颌頜颍潁颎熲颏頦颐頤频頻颒頮颓頹颔頷颕頴颖穎颗顆题題颙顒颚顎颛顓颜顏额額颞顳颟顢颠顛颡顙颢顥颣纇颤顫颥顬颦顰颧顴风風飏颺飐颭飑颮飒颯飓颶飔颸飕颼飖颻飗飀飘飄飙飆飚飈飞飛飨饗餍饜饣飠饤飣饥飢饦飥饧餳饨飩饩餼饪飪饫飫饬飭饭飯饮飲饯餞饰飾饱飽饲飼饳飿饴飴饵餌饶饒饷餉饸餄饹餎饺餃饻餏饼餅饽餑饾餖饿餓馀餘馁餒馂餕馃餜馄餛馅餡馆館馇餷馈饋馉餶馊餿馋饞馌饁馍饃馎餺馏餾馐饈馑饉馒饅馓饊馔饌馕饢马馬驭馭驮馱驯馴驰馳驱驅驲馹驳駁驴驢驵駔驶駛驷駟驸駙驹駒驺騶驻駐驼駝驽駑驾駕驿驛骀駘骁驍骂罵骃駰骄驕骅驊骆駱" +
        "骇駭骈駢骉驫骊驪骋騁验驗骍騂骎駸骏駿骐騏骑騎骒騍骓騅骔騌骕驌骖驂骗騙骘騭骙騤骚騷骛騖骜驁骝騮骞騫骟騸骠驃骡騾骢驄骣驏骤驟骥驥骦驦骧驤髅髏髋髖髌髕鬓鬢鬶鬹魇魘魉魎鱼魚鱽魛鱾魢鱿魷鲀魨鲁魯鲂魴鲃䰾鲄魺鲅鮁鲆鮃鲇鮎鲈鱸鲉鮋鲊鮓鲋鮒鲌鮊鲍鮑鲎鱟鲏鮍鲐鮐鲑鮭鲒鮚鲓鮳鲔鮪鲕鮞鲖鮦鲗鰂鲘鮜鲙鱠鲚鱭鲛鮫鲜鮮鲝鮺鲞鯗鲟鱘鲠鯁鲡鱺鲢鰱鲣鰹鲤鯉鲥鰣鲦鰷鲧鯀鲨鯊鲩鯇鲪鮶鲫鯽鲬鯒鲭鯖鲮鯪鲯鯕鲰鯫鲱鯡鲲鯤鲳鯧鲴鯝鲵鯢鲶鯰鲷鯛鲸鯨鲹鰺鲺鯴鲻鯔鲼鱝鲽鰈鲾鰏鲿鱨鳀鯷鳁鰮鳂鰃鳃鰓鳄鱷鳅鰍鳆鰒鳇鰉鳈鰁鳉鱂鳊鯿鳋鰠鳌鰲鳍鰭鳎鰨鳏鰥鳐鰩鳑鰟鳒鰜鳓鰳鳔鰾鳕鱈鳖鱉鳗鰻鳘鰵鳙鱅鳚䲁鳛鰼鳜鱖鳝鱔鳞鱗鳟鱒鳠鱯鳡鱤鳢鱧鳣鱣鳤䲘鸟鳥鸠鳩鸡雞鸢鳶鸣鳴鸤鳲鸥鷗鸦鴉鸧鶬鸨鴇鸩鴆鸪鴣鸫鶇鸬鸕鸭鴨鸮鴞鸯鴦鸰鴒鸱鴟鸲鴝鸳鴛鸴鷽鸵鴕鸶鷥鸷鷙鸸鴯鸹鴰鸺鵂鸻鴴鸼鵃鸽鴿鸾鸞鸿鴻鹀鵐鹁鵓鹂鸝鹃鵑鹄鵠鹅鵝鹆鵒鹇鷳鹈鵜鹉鵡鹊鵲鹋鶓鹌鵪鹍鵾鹎鵯鹏鵬鹐鵮鹑鶉鹒鶊鹓鵷鹔鷫鹕鶘" +
        "鹖鶡鹗鶚鹘鶻鹙鶖鹚鷀鹛鶥鹜鶩鹝鷊鹞鷂鹟鶲鹠鶹鹡鶺鹢鷁鹣鶼鹤鶴鹥鷖鹦鸚鹧鷓鹨鷚鹩鷯鹪鷦鹫鷲鹬鷸鹭鷺鹮䴉鹯鸇鹰鷹鹱鸌鹲鸏鹳鸛鹴鸘鹾鹺麦麥麸麩麹麴麺麪麽麼黄黃黉黌黡黶黩黷黪黲黾黽鼋黿鼌鼂鼍鼉鼹鼴齐齊齑齏齿齒龀齔龁齕龂齗龃齟龄齡龅齙龆齠龇齜龈齦龉齬龊齪龋齲龌齷龙龍龚龔龛龕龟龜鿎䃮鿏䥑鿒鿓鿔鎶𠀾𠁞𠆲儣𠆿𠌥𠇹俓𠉂㒓𠉗𠏢𠋆儭𠚳𠠎𠛅剾𠛆𠞆𠛾𪟖𠡠勑𠮶嗰𠯟哯𠯠噅𠰱㘉𠰷嚧𠱞囃𠲥𡅏𠴛𡃕𠴢𡄔𠵸𡄣𠵾㗲𡋀𡓾𡋗𡑭𡋤壗𡍣𡔖𡒄壈𡝠㜷𡞋㜗𡞱㜢𡠟孎𡥧孻𡭜𡮉𡭬𡮣𡳃𡳳𡳒𦘧𡶴嵼𡸃𡽗𡺃嶈𡺄嶘𢋈㢝𢗓㦛𢘙𢤱𢘝𢣚𢘞𢣭𢙏愻𢙐憹𢙑𢠼𢙒憢𢙓懀𢛯㦎𢠁懎𢢐𤢻𢧐戰𢫊𢷮𢫞𢶫𢫬摋𢬍擫𢬦𢹿𢭏擣𢽾斅𣃁斸𣆐曥𣈣𣋋𣍨𦢈𣍯腪𣍰脥𣎑臗𣏢槫𣐕桱𣐤欍𣑶𣠲𣒌楇𣓿橯𣔌樤𣗊樠𣗋欓𣗙㰙𣘐㯤𣘓𣞻𣘴檭𣘷𣝕𣚚欘𣞎𣠩𣨼殢𣭤𣯴𣯣𣯩𣱝氭𣲗湋𣲘潕𣳆㵗𣶩澅𣶫𣿉𣶭𪷓𣷷𤅶𣸣濆𣺼灙𣺽𤁣𣽷瀃𤆡熓𤆢㷍𤇃爄𤇄熌𤇭爖𤇹熚𤈶熉𤈷㷿𤊀𤒎𤊰𤓩𤋏熡𤎺𤓎𤎻𤑳𤙯𤛮𤝢𤢟𤞃獩𤞤玁𤠋㺏𤦀瓕𤩽瓛𤳄𤳸𤶊癐𤶧𤸫𤻊㿗𤽯㿧𤾀皟𤿲麬𥁢䀉𥅘𥌃" +
        "𥅴䀹𥅿𥊝𥆧瞤𥇢䁪𥎝䂎𥐟礒𥐯𥖅𥐰𥕥𥐻碙𥞦𥞵𥧂𥨐𥩟竚𥩺𥪂𥫣籅𥬀䉙𥬞籋𥬠篘𥭉𥵊𥮋𥸠𥮜䉲𥮾篸𥱔𥵃𥹥𥼽𥺅䊭𥺇𥽖𦈈𥿊𦈉緷𦈋綇𦈌綀𦈎繟𦈏緍𦈐縺𦈑緸𦈒𦂅𦈓䋿𦈔縎𦈕緰𦈖䌈𦈗𦃄𦈘䌋𦈙䌰𦈚縬𦈛繓𦈜䌖𦈝繏𦈞䌟𦈟䌝𦈠䌥𦈡繻𦍠䍽𦛨朥𦝼膢𦟗𦣎𦨩𦪽𦰏蓧𦰴䕳𦶟爇𦶻𦾟𦻕蘟𧉐𧕟𧉞䗿𧌥𧎈𧏖蠙𧏗蠀𧑏蠾𧒭𧔥𧜭䙱𧝝襰𧝧𧟀𧮪詀𧳕𧳟𧹑䞈𧹒買𧹓𧶔𧹔賬𧹕䝻𧹖賟𧹗贃𧿈𨇁𨀁躘𨀱𨄣𨁴𨅍𨂺𨈊𨄄𨈌𨅛䠱𨅫𨇞𨅬躝𨉗軉𨐅軗𨐆𨊻𨐇𨏠𨐈輄𨐉𨎮𨐊𨏥𨑹䢨𨟳𨣞𨠨𨣧𨡙𨢿𨡺𨣈𨤰𨤻𨰾鎷𨰿釳𨱀𨥛𨱁鈠𨱂鈋𨱃鈲𨱄鈯𨱅鉁𨱆龯𨱇銶𨱈鋉𨱉鍄𨱊𨧱𨱋錂𨱌鏆𨱍鎯𨱎鍮𨱏鎝𨱐𨫒𨱑鐄𨱒鏉𨱓鐎𨱔鐏𨱕𨮂𨱖䥩𨷿䦳𨸀𨳕𨸁𨳑𨸂閍𨸃閐𨸄䦘𨸅𨴗𨸆𨵩𨸇𨵸𨸉𨶀𨸊𨶏𨸋𨶲𨸌𨶮𨸎𨷲𨸘𨽏𨸟䧢𩏼䪏𩏽𩏪𩏾𩎢𩏿䪘𩐀䪗𩓋顂𩖕𩓣𩖖顃𩖗䫴𩙥颰𩙦𩗀𩙧䬞𩙨𩘹𩙩𩘀𩙪颷𩙫颾𩙬𩘺𩙭𩘝𩙮䬘𩙯䬝𩙰𩙈𩟿𩚛𩠀𩚥𩠁𩚵𩠂𩛆𩠃𩛩𩠅𩟐𩠆𩜦𩠇䭀𩠈䭃𩠉𩜇𩠊𩜵𩠋𩝔𩠌餸𩠎𩞄𩠏𩞦𩠠𩠴𩡖𩡣𩧦𩡺𩧨駎𩧩𩤊𩧪䮾𩧫駚𩧬𩢡𩧭䭿𩧮𩢾𩧯驋𩧰䮝𩧱𩥉𩧲駧𩧳𩢸𩧴駩𩧵𩢴𩧶𩣏𩧸𩣫𩧺駶𩧻𩣵𩧼𩣺𩧿䮠" +
        "𩨀騔𩨁䮞𩨂驄𩨃騝𩨄騪𩨅𩤸𩨆𩤙𩨇䮫𩨈騟𩨉𩤲𩨊騚𩨋𩥄𩨌𩥑𩨍𩥇𩨎龭𩨏䮳𩨐𩧆𩩈䯤𩬣𩭙𩬤𩰀𩭹鬖𩯒𩯳𩰰𩰹𩲒𩳤𩴌𩴵𩽹魥𩽺𩵩𩽻𩵹𩽼鯶𩽽𩶱𩽾鮟𩽿𩶰𩾁鯄𩾂䲖𩾃鮸𩾄𩷰𩾅𩸃𩾆𩸦𩾇鯱𩾈䱙𩾊䱬𩾋䱰𩾌鱇𩾎𩽇𪉂䲰𪉃鳼𪉄𩿪𪉅𪀦𪉆鴲𪉈鴜𪉉𪁈𪉊鷨𪉋𪀾𪉌𪁖𪉍鵚𪉎𪂆𪉏𪃏𪉐𪃍𪉑鷔𪉒𪄕𪉔𪄆𪉕𪇳𪎈䴬𪎉麲𪎊麨𪎋䴴𪎌麳𪑅䵳𪔭𪔵𪚏𪘀𪚐𪘯𪜎𠿕𪞝凙𪟎㔋𪟝勣𪠀𧷎𪠟㓄𪠡𠬙𪠳唓𪠵㖮𪠸嚛𪠺𠽃𪠽噹𪡀嘺𪡃嘪𪡋噞𪡏嗹𪡛㗿𪡞嘳𪡺𡃄𪢌㘓𪢐𡃤𪢒𡂡𪢕嚽𪢖𡅯𪢠囒𪢮圞𪢸墲𪣆埬𪣒堚𪣻塿𪤄𡓁𪤚壣𪥠𧹈𪥫孇𪥰嬣𪥿嬻𪧀孾𪧘寠𪨊㞞𪨗屩𪨧崙𪨩𡸗𪨶輋𪨷巗𪨹𡹬𪩇㟺𪩎巊𪩘巘𪩛𡿖𪩷幝𪩸幩𪪏廬𪪑㢗𪪞廧𪪴𢍰𪪼彃𪫌徿𪫡𢤩𪫷㦞𪫺憸𪬚𢣐𪬯𢤿𪭝𢯷𪭢摐𪭧擟𪭯𢶒𪭵掚𪭾撊𪮃㨻𪮋㩋𪮖撧𪮳𢺳𪮶攋𪯋㪎𪰶曊𪱥膹𪱷梖𪲎櫅𪲔欐𪲛檵𪲮櫠𪳍欇𪳗𣜬𪴙欑𪵑毊𪵣霼𪵱濿𪶄溡𪶒𤄷𪶮𣽏𪷍㵾𪷽灒𪸕熂𪸩煇𪹀𤑹𪹠𤓌𪹳爥𪹹𤒻𪺣𤘀𪺪𤜆𪺭犞𪺷獊𪺸𤠮𪺻㺜𪺽猌𪻐瑽𪻨瓄𪻲瑻𪻺璝𪼋㻶𪼴𤬅𪽈畼𪽝𤳷𪽪痮𪽭𤷃𪽮㿖𪽴𤺔𪽷瘱𪾔盨𪾢睍𪾣眝𪾦矑𪾸矉𪿊𥏝𪿞𥖲𪿫礮𪿵𥗇𫀌𥜰𫀓𥜐" +
        "𫀨䅐𫀬䅳𫀮𥢷𫁂䆉𫁟竱𫁡鴗𫁱𥶽𫁲䉑𫁳𥯤𫁷䉶𫁺𥴼𫂃簢𫂆簂𫂈䉬𫂖𥴨𫂿𥻦𫃗𩏷𫄙糺𫄚䊺𫄛紟𫄜䋃𫄝𥾯𫄞䋔𫄟絁𫄠絙𫄡絧𫄢絥𫄣繷𫄤繨𫄥纚𫄦𦀖𫄧綖𫄨絺𫄩䋦𫄪𦅇𫄫綟𫄬緤𫄭緮𫄮䋼𫄯𦃩𫄰縍𫄱繬𫄲縸𫄳縰𫄴繂𫄵𦅈𫄶繈𫄷繶𫄸纁𫄹纗𫅅䍤𫅗羵𫅥𦒀𫅭䎙𫅼𦔖𫆏聻𫆝𦟼𫆫𦡝𫇘𦧺𫇛艣𫇪𦱌𫇭蔿𫇴蒭𫇽蕽𫈉蕳𫈎葝𫈟蔯𫈵蕝𫉁薆𫉄藷𫊪䗅𫊮蠦𫊸蟜𫊹𧒯𫊻蟳𫋇蟂𫋌蟘𫋲䙔𫋷襗𫋹襓𫋻襘𫌀襀𫌇襵𫌋𧞫𫌨覼𫌪覛𫌫𧡴𫌬𧢄𫌭覹𫌯䚩𫍐𧭹𫍙訑𫍚訞𫍛訜𫍜詓𫍝諫𫍞𧦝𫍟𧦧𫍠䛄𫍡詑𫍢譊𫍣詷𫍤譑𫍥誂𫍦譨𫍧誺𫍨誫𫍩諣𫍪誋𫍫䛳𫍬誷𫍭𧩕𫍮誳𫍯諴𫍰諰𫍱諯𫍲謏𫍳諥𫍴謱𫍵謸𫍶𧩼𫍷謉𫍸謆𫍹謯𫍺𧫝𫍻譆𫍼𧬤𫍽譞𫍾𧭈𫍿譾𫎆豵𫎌貗𫎦贚𫎧䝭𫎨𧸘𫎩賝𫎪䞋𫎫贉𫎬贑𫎭䞓𫎱䟐𫎳䟆𫎸𧽯𫎺䟃𫏃䠆𫏆蹳𫏋蹻𫏌𨂐𫏐蹔𫏑𨇽𫏕𨆪𫏞𨇰𫏨𨇤𫐄軏𫐅軕𫐆轣𫐇軜𫐈軷𫐉軨𫐊軬𫐋𨎌𫐌軿𫐍𨌈𫐎輢𫐏輖𫐐輗𫐑輨𫐒輷𫐓輮𫐔𨍰𫐕轊𫐖轇𫐗轐𫐘轗𫐙轠𫐷遱𫑘鄟𫑡鄳𫑷醶𫓥釟𫓦釨𫓧鈇𫓨鈛𫓩鏦𫓪鈆𫓫𨥟𫓬鉔𫓭鉠𫓮𨪕𫓯銈𫓰銊𫓱鐈𫓲銁𫓳𨰋𫓴鉾𫓵鋠𫓶鋗𫓷𫒡𫓸錽𫓹錤" +
        "𫓺鐪𫓻錜𫓼𨨛𫓽錝𫓾錥𫓿𨨢𫔀鍊𫔁鐼𫔂鍉𫔃𨰲𫔄鍒𫔅鎍𫔆䥯𫔇鎞𫔈鎙𫔉𨰃𫔊鏥𫔋䥗𫔌鏾𫔍鐇𫔎鐍𫔏𨬖𫔐𨭸𫔑𨭖𫔒𨮳𫔓𨯟𫔔鑴𫔕𨰥𫔖𨲳𫔭開𫔮閒𫔯閗𫔰閞𫔲𨴹𫔴閵𫔵䦯𫔶闑𫔽𨼳𫕚𩀨𫕥霣𫕨𩅙𫖃靧𫖅䪊𫖇鞾𫖑𩎖𫖒韠𫖓𩏂𫖔韛𫖕韝𫖖𩏠𫖪𩑔𫖫䪴𫖬䪾𫖭𩒎𫖮顗𫖯頫𫖰䫂𫖱䫀𫖲䫟𫖳頵𫖴𩔳𫖵𩓥𫖶顅𫖷𩔑𫖸願𫖹顣𫖺䫶𫗇䫻𫗈𩗓𫗉𩗴𫗊䬓𫗋飋𫗚𩟗𫗞飦𫗟䬧𫗠餦𫗡𩚩𫗢飵𫗣飶𫗤𩛌𫗥餫𫗦餔𫗧餗𫗨𩛡𫗩饠𫗪餧𫗫餬𫗬餪𫗭餵𫗮餭𫗯餱𫗰䭔𫗱䭑𫗳𩝽𫗴饘𫗵饟𫘛馯𫘜馼𫘝駃𫘞駞𫘟駊𫘠駤𫘡駫𫘣駻𫘤騃𫘥騉𫘦騊𫘧騄𫘨騠𫘩騜𫘪騵𫘫騴𫘬騱𫘭騻𫘮䮰𫘯驓𫘰驙𫘱驨𫘽鬠𫙂𩯁𫚈鱮𫚉魟𫚊鰑𫚋鱄𫚌魦𫚍魵𫚎𩶁𫚏䱁𫚐䱀𫚑鮅𫚒鮄𫚓鮤𫚔鮰𫚕鰤𫚖鮆𫚗鮯𫚘𩻮𫚙鯆𫚚鮿𫚛鮵𫚜䲅𫚝𩸄𫚞鯬𫚟𩸡𫚠䱧𫚡鯞𫚢鰋𫚣鯾𫚤鰦𫚥鰕𫚦鰫𫚧鰽𫚨𩻗𫚩𩻬𫚪鱊𫚫鱢𫚬𩼶𫚭鱲𫛚鳽𫛛鳷𫛜鴀𫛝鴅𫛞鴃𫛟鸗𫛠𩿤𫛡鴔𫛢鸋𫛣鴥𫛤鴐𫛥鵊𫛦鴮𫛧𪀖𫛨鵧𫛩鴳𫛪鴽𫛫鶰𫛬䳜𫛭鵟𫛮䳤𫛯鶭𫛰䳢𫛱鵫𫛲鵰𫛳鵩𫛴鷤𫛵鶌𫛶鶒𫛷鶦𫛸鶗𫛹𪃧𫛺䳧𫛻𪃒𫛼䳫𫛽鷅𫛾𪆷𫜀鷐𫜁鷩𫜂𪅂𫜃鷣𫜄鷷" +
        "𫜅䴋𫜊𪉸𫜑麷𫜒䴱𫜓𪌭𫜔䴽𫜕𪍠𫜙䵴𫜟𪓰𫜨䶕𫜩齧𫜪齩𫜫𫜦𫜬齰𫜭齭𫜮齴𫜯𪙏𫜰齾𫜲龓𫜳䶲𫝈㑮𫝋𠐊𫝦㛝𫝧㜐𫝨媈𫝩嬦𫝪𡟫𫝫婡𫝬嬇𫝭孆𫝮孄𫝵嶹𫞅𦠅𫞗潣𫞚澬𫞛㶆𫞝灍𫞠爧𫞡爃𫞢𤛱𫞣㹽𫞥珼𫞦璾𫞧𤩂𫞨璼𫞩璊𫞷𥢶𫟃絍𫟄綋𫟅綡𫟆緟𫟇𦆲𫟑䖅𫟕䕤𫟞訨𫟟詊𫟠譂𫟡誴𫟢䜖𫟤䡐𫟥䡩𫟦䡵𫟫𨞺𫟬𨟊𫟲釚𫟳釲𫟴鈖𫟵鈗𫟶銏𫟷鉝𫟸鉽𫟹鉷𫟺䤤𫟻銂𫟼鐽𫟽𨧰𫟾𨩰𫟿鎈𫠀䥄𫠁鑉𫠂閝𫠅韚𫠆頍𫠇𩖰𫠈䫾𫠊䮄𫠋騼𫠌𩦠𫠏𩵦𫠐魽𫠑䱸𫠒鱆𫠖𩿅𫠜齯𫢸僤𫧃𣍐𫧮𪋿𫫇噁𫬐㘔𫭟塸𫭢埨𫭼𡑍𫮃墠𫰛娙𫵷㠣𫶇嵽𫷷廞𫸩彄𬀩暐𬀪晛𬂩梜𬃊櫍𬇕澫𬇙浿𬇹漍𬉼熰𬊈燖𬊤燀𬍛瓅𬍡璗𬍤璕𬒈礐𬒗𥗽𬕂篢𬘓紃𬘘紞𬘡絪𬘩綎𬘫綄𬘬綪𬘭綝𬘯綧𬙂縯𬙊纆𬙋纕𬜬蔄𬜯䓣𬞟蘋𬟁虉𬟽蝀𬣙訏𬣞詝𬣡諓𬣳詪𬤇諲𬤊諟𬤝譓𬨂軝𬨎輶𬩽鄩𬪩醲𬬩釴𬬭錀𬬮鋹𬬱釿𬬸鉥𬬹鉮𬬻鑪𬬿鉊𬭁鉧𬭊𨧀𬭎鋐𬭚錞𬭛𨨏𬭤鍭𬭩鎓𬭬鏏𬭭鏚𬭯䥕𬭳𨭎𬭶𨭆𬭸鏻𬭼鐩𬮱闉𬮿隑𬯀隮𬯎隤𬱖頔𬱟頠𬳵駓𬳶駉𬳽駪𬳿駼𬴂騑𬴃騞𬴊驎𬶋鮈𬶍鮀𬶏鮠𬶐鮡𬶟鯻𬶠鰊𬶨鱀𬶭鰶𬶮鱚𬷕鵏𬸘鶠𬸚鸑𬸣鶱𬸦鷟" +
        "𬸪鷭𬸯鷿𬹼齘𬺈齮𬺓齼𰬸繐𰰨菕𰶎譅𰻝𰻞𰾄鋂𰾭鑀𱊜𪈼"

    private val s2tMap: Map<Char, Char> by lazy { buildCharMap(0) }
    private val t2sMap: Map<Char, Char> by lazy { buildCharMap(1) }

    /** 按码点两两配对构建繁简映射；跳过非 BMP 增补汉字字对，避免 UTF-16 代理对导致索引错位越界。
      * which=0 建 繁->简？不，源表为 简在前繁在后，故 which=0 得 简->繁，which=1 得 繁->简。 */
    private fun buildCharMap(which: Int): Map<Char, Char> {
        val m = HashMap<Char, Char>()
        val cps = ST_PAIRS.codePoints().toArray()
        var i = 0
        while (i + 1 < cps.size) {
            val a = cps[i]
            val b = cps[i + 1]
            if (a < 0x10000 && b < 0x10000) {
                if (which == 0) m[a.toChar()] = b.toChar() else m[b.toChar()] = a.toChar()
            }
            i += 2
        }
        return m
    }

    /** 生成繁简候选关键词（去重，保序：原词优先；简体词可命中繁体数据） */
    private fun kwVariants(kw: String): List<String> {
        val out = LinkedHashSet<String>()
        out.add(kw)
        val trad = kw.map { s2tMap[it] ?: it }.joinToString(""); if (trad != kw) out.add(trad)
        val simp = kw.map { t2sMap[it] ?: it }.joinToString(""); if (simp != kw) out.add(simp)
        return out.toList()
    }

    private fun containsAny(text: String, variants: List<String>): Boolean =
        variants.any { text.contains(it) }

    /** 搜索：标题/作者/词牌/正文摘要 整行匹配（繁简兼容 + 命中优先排序）；fullText=true 时后台扫描正文全文 */
    suspend fun search(ctx: Context, keyword: String, cat: String?, limit: Int = 500, fullText: Boolean = false): List<PoemIndex> =
        withContext(Dispatchers.IO) {
            if (keyword.isBlank()) return@withContext emptyList()
            val variants = kwVariants(keyword.trim())
            val targets = if (cat != null) listOf(cat) else CATEGORIES.map { it.first } + BAIKE_CATEGORIES.map { it.first }
            data class Hit(val idx: PoemIndex, val score: Int)
            val hits = ArrayList<Hit>(limit)
            // 第一轮：索引匹配（标题>作者/词牌/摘要，带权重）
            loop@ for (c in targets) {
                val ds = datasets[c] ?: continue
                for (line in ds.indexLines) {
                    val p = line.split('|')
                    val title = p.getOrElse(0) { "" }
                    val author = p.getOrElse(1) { "" }
                    val rhy = p.getOrElse(3) { "" }
                    val summary = p.getOrElse(8) { "" }
                    val catName = p.getOrElse(2) { "" }
                    var score = 0
                    if (containsAny(title, variants)) score += 6           // 标题命中最高
                    if (containsAny(author, variants)) score += 4
                    if (containsAny(rhy, variants)) score += 3
                    if (containsAny(catName, variants)) score += 2
                    if (containsAny(summary, variants)) score += 5           // 名句摘要
                    if (score > 0) {
                        hits.add(Hit(parseIndex(line, c), score))
                        if (hits.size >= limit) break@loop
                    }
                }
            }
            // 第二轮：全文搜索兜底（扫描正文，用于「句子搜整首诗」）。
            // 触发条件：开启全文，或索引命中很少（<=30，说明很可能是诗句正文而非标题/名句）
            val needFullText = fullText || hits.size <= 30
            if (needFullText && hits.size < limit) {
                val seen = HashSet<String>()
                hits.forEach { seen.add("${it.idx.category}#${it.idx.lineNo}") }
                val fullLimit = limit - hits.size
                outer@ for (c in targets) {
                    val ds = datasets[c] ?: continue
                    val cf = ds.contentFile ?: continue
                    // 手动逐行读取（避免 forEachLine 无法 break 且计数易错）
                    val reader = cf.bufferedReader(Charsets.UTF_8)
                    var lineNo = 0
                    var added = 0
                    try {
                        while (true) {
                            val body = reader.readLine() ?: break
                            if (containsAny(body, variants)) {
                                val key = "$c#$lineNo"
                                if (lineNo < ds.indexLines.size && !seen.contains(key)) {
                                    seen.add(key)
                                    hits.add(Hit(parseIndex(ds.indexLines[lineNo], c), 2))  // 正文命中
                                    added += 1
                                    if (added >= fullLimit) break
                                }
                            }
                            lineNo += 1
                        }
                    } finally {
                        reader.close()
                    }
                    if (hits.size >= limit) break@outer
                }
            }
            // 命中优先排序：标题/名句命中在前
            hits.sortByDescending { it.score }
            hits.take(limit).map { it.idx }
        }

    /** 筛选：按朝代（限定分类，或全部） */
    suspend fun filterByDynasty(dynasty: String, cat: String?, limit: Int = 2000): List<PoemIndex> =
        withContext(Dispatchers.IO) {
            val targets = if (cat != null) listOf(cat) else CATEGORIES.map { it.first } + BAIKE_CATEGORIES.map { it.first }
            val result = ArrayList<PoemIndex>()
            for (c in targets) {
                val ds = datasets[c] ?: continue
                for (line in ds.indexLines) {
                    val p = line.split('|')
                    if (p.getOrElse(2) { "" } == dynasty) {
                        result.add(parseIndex(line, c))
                        if (result.size >= limit) return@withContext result
                    }
                }
            }
            result
        }

    /** 筛选：按作者（限定分类，或全部） */
    suspend fun filterByAuthor(author: String, cat: String?, limit: Int = 2000): List<PoemIndex> =
        withContext(Dispatchers.IO) {
            val targets = if (cat != null) listOf(cat) else CATEGORIES.map { it.first } + BAIKE_CATEGORIES.map { it.first }
            val result = ArrayList<PoemIndex>()
            for (c in targets) {
                val ds = datasets[c] ?: continue
                for (line in ds.indexLines) {
                    val p = line.split('|')
                    if (p.getOrElse(1) { "" } == author) {
                        result.add(parseIndex(line, c))
                        if (result.size >= limit) return@withContext result
                    }
                }
            }
            result
        }

    /** 枚举某分类（或全部）的朝代列表（带计数） */
    fun listDynasties(cat: String?): List<Pair<String, Int>> {
        val targets = if (cat != null) listOf(cat) else CATEGORIES.map { it.first } + BAIKE_CATEGORIES.map { it.first }
        val counter = LinkedHashMap<String, Int>()
        for (c in targets) {
            val ds = datasets[c] ?: continue
            for (line in ds.indexLines) {
                val d = line.split('|').getOrElse(2) { "" }
                if (d.isNotBlank()) counter[d] = (counter[d] ?: 0) + 1
            }
        }
        return counter.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    /** 枚举某分类（或全部）的作者列表（带计数，仅取产量前 N 位，避免 9 万条全列） */
    fun listAuthors(cat: String?, topN: Int = 80): List<Pair<String, Int>> {
        val targets = if (cat != null) listOf(cat) else CATEGORIES.map { it.first } + BAIKE_CATEGORIES.map { it.first }
        val counter = LinkedHashMap<String, Int>()
        for (c in targets) {
            val ds = datasets[c] ?: continue
            for (line in ds.indexLines) {
                val a = line.split('|').getOrElse(1) { "" }
                if (a.isNotBlank() && a != "佚名") counter[a] = (counter[a] ?: 0) + 1
            }
        }
        return counter.entries.sortedByDescending { it.value }.take(topN).map { it.key to it.value }
    }

    /** 随机取 n 个索引（限定分类） */
    fun random(cat: String, n: Int): List<PoemIndex> {
        val ds = datasets[cat] ?: return emptyList()
        if (ds.count == 0) return emptyList()
        val idxs = (0 until ds.count).shuffled().take(n)
        return idxs.map { parseIndex(ds.indexLines[it], cat) }
    }

    /** 每日一句：按日期种子稳定取一首 */
    /** 每日一句：跨全部古诗词大类合并池，按天自动轮换一首。 */
    suspend fun daily(ctx: Context): PoemIndex? {
        val pool = ArrayList<Pair<String, Int>>()   // (类目名, 全局序号)
        for ((cat, _) in CATEGORIES) {
            if (isFun(cat)) continue
            val ds = datasets[cat] ?: continue
            for (i in 0 until ds.count) pool.add(cat to i)
        }
        if (pool.isEmpty()) return null
        val daySeed = System.currentTimeMillis() / (1000L * 60 * 60 * 24)
        val (cat, i) = pool[(daySeed % pool.size).toInt()]
        val ds = datasets[cat] ?: return null
        return parseIndex(ds.indexLines[i], cat)
    }
}