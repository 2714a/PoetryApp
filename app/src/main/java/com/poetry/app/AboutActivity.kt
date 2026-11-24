package com.poetry.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 关于与声明页：APP 介绍、权限说明、数据来源、免责声明。
 */
class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "关于与声明"

        findViewById<TextView>(R.id.aboutTitle).text = "诗词歌赋"
        findViewById<TextView>(R.id.aboutBody).text = ABOUT_TEXT
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }

    companion object {
        val ABOUT_TEXT = """
【应用简介】
「诗词歌赋」是一款离线中文古典诗词与传统文化合集应用，收录全唐诗、全宋词、元曲、诗经、楚辞、曹操诗集、纳兰性德词，以及歇后语、谜语、脑筋急转弯等趣味内容，共计九万余条，并附带中国上下五千年王朝演变时间轴。所有数据离线内置，无需联网即可使用。

【主要功能】
• 分类浏览：按诗词类别、朝代、作者筛选浏览。
• 全文搜索：支持标题、作者、词牌、名句检索，简体繁体均可命中。
• 收藏管理：一键收藏喜爱作品，集中查看。
• 语音朗读：调用系统 TTS 引擎朗读诗文。
• 滑动切换：详情页左右滑或上下滑自动切换上一篇 / 下一篇。
• 王朝演变：一览华夏五千年王朝更替脉络。

【权限说明】
• 本应用为纯离线应用，AndroidManifest 中未声明任何危险权限——不请求定位、相机、麦克风、通讯录、短信、通话记录、存储等敏感权限。
• 网络权限：未申请。全部诗词、译文、王朝数据均离线内置，运行时不联网、不上传任何数据。
• 语音朗读：调用系统已安装的 TTS（文字转语音）服务。该服务为系统组件，属跨应用调用，无需本应用额外授权；若设备未安装 TTS 引擎，朗读功能不可用，但不影响其他功能。
• 数据存储：仅使用应用私有目录（无需存储权限）。诗词数据、译文缓存存放于应用内部缓存区；收藏记录存放于应用私有 SharedPreferences，均不读取也不写入公共存储（如相册、文档），卸载应用即一并清除。
• 首次启动同意记录：使用应用私有 SharedPreferences 保存「是否已同意用户须知」，属应用内部数据，不涉及个人信息。
• 通知权限：未申请，应用不发送任何通知。
• 后台运行 / 开机自启：未申请，应用不驻留后台、不监听设备状态。
• 个人信息：本应用不收集、不使用、不共享任何个人隐私信息，无账号体系，无埋点统计。

【数据来源与版权】
• 诗词文本来自公开的 chinese-poetry 等开源古籍数据集，属公有领域古典文献。
• 歇后语数据来自新华字典开源项目。
• 王朝历史资料为通识性史实整理，仅供参考学习。

【免责声明】
• 本应用数据由公开来源整理而成，虽经校对，仍可能存在个别讹误，内容仅供学习、欣赏与参考，不作为学术考据的唯一依据。
• 历史叙述为高度概括，具体史实细节请以权威史学研究为准。
• 若发现数据错漏或侵犯您的合法权益，欢迎反馈，我们将及时修正。

【开源致谢】
感谢 chinese-poetry、新华字典开源项目等社区贡献者。

诗词歌赋 · 传承经典
""".trim()
    }
}