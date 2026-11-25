package com.poetry.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * 应用内自动更新管理器。
 *
 * 工作原理：
 *   1. 请求 GitHub Releases 接口拿到最新版本的 tag（如 v1.1.0）与 APK 下载地址。
 *   2. 与本地 versionName（如 1.0.0）做语义化版本比较。
 *   3. 远程更新则弹窗（含更新说明 + 下载进度条），下载完成自动调起安装。
 *
 * 发布新版的流程（你手动操作即可）：
 *   - 编译出新的签名 APK。
 *   - 在 GitHub 新建 Release，tag 用 v<新版本号>（如 v1.1.0），
 *     Release 标题里带上版本号（如「学习百科大全 v1.1.0」），
 *     上传签名 APK，Release 正文写更新说明。
 *   - 老用户打开 APP 即会收到更新提示。
 */
object UpdateManager {

    /** GitHub 最新 Release 接口（公开仓库无需 token） */
    private const val RELEASES_API = "https://api.github.com/repos/2714a/PoetryApp/releases/latest"
    /** APK 下载缓存子目录 */
    private const val UPDATE_DIR = "updates"

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 检查更新并按需弹窗。
     * @param silent true=静默（无更新不提示，启动自动检查用）；
     *               false=手动（关于页按钮用，无更新也提示「已是最新」）。
     */
    fun checkAndPrompt(activity: Activity, silent: Boolean = true) {
        thread {
            try {
                val update = fetchIfNewer(activity)
                if (update != null) {
                    mainHandler.post { if (!activity.isFinishing) showUpdateDialog(activity, update) }
                } else if (!silent) {
                    mainHandler.post {
                        if (activity.isFinishing) return@post
                        AlertDialog.Builder(activity)
                            .setTitle("检查更新")
                            .setMessage("当前已是最新版本 v" + getLocalVersion(activity))
                            .setPositiveButton("确定", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                if (!silent) {
                    mainHandler.post {
                        if (activity.isFinishing) return@post
                        AlertDialog.Builder(activity)
                            .setTitle("检查更新")
                            .setMessage("网络错误，无法连接更新服务器。请检查网络后重试。")
                            .setPositiveButton("确定", null)
                            .show()
                    }
                }
            }
        }
    }

    /** 取远程最新版，若比本地新则返回 UpdateInfo，否则 null */
    private fun fetchIfNewer(activity: Activity): UpdateInfo? {
        val remote = fetchLatestRelease() ?: return null
        if (isNewer(remote.version, getLocalVersion(activity))) return remote
        return null
    }

    /** 语义化版本比较：remote 是否比 local 更新。"1.0.1" > "1.0.0" = true */
    private fun isNewer(remote: String, local: String): Boolean {
        val r = parseVersion(remote)
        val l = parseVersion(local)
        val n = maxOf(r.size, l.size)
        for (i in 0 until n) {
            val ri = r.getOrElse(i) { 0 }
            val li = l.getOrElse(i) { 0 }
            if (ri > li) return true
            if (ri < li) return false
        }
        return false
    }

    /** 把 "v1.2.3" 解析为 [1,2,3] */
    private fun parseVersion(v: String): List<Int> {
        return Regex("\\d+").findAll(v).mapNotNull { it.value.toIntOrNull() }.toList()
    }

    /** 读取本地 versionName，如 "1.0.0" */
    private fun getLocalVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        } catch (e: Exception) { "0.0.0" }
    }

    /** 请求 GitHub API，解析最新 Release */
    private fun fetchLatestRelease(): UpdateInfo? {
        val conn = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "PoetryApp-Updater")
        }
        try {
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = json.optString("tag_name", "")
            if (tag.isBlank()) return null
            // 纯版本号：优先用 tag，去掉 v 前缀
            var version = tag.removePrefix("v").removePrefix("V")
            val title = json.optString("name", "")
            if (title.isNotBlank()) {
                var t = title
                for (pre in listOf("学习百科大全 ", "诗词歌赋 ")) t = t.removePrefix(pre)
                t = t.removePrefix("v").removePrefix("V")
                if (Regex("^\\d+(\\.\\d+)*$").matches(t)) version = t
            }
            val notes = json.optString("body", "")
            // 找第一个 .apk 资产作为下载地址
            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                if (a.optString("name", "").endsWith(".apk", ignoreCase = true)) {
                    val url = a.optString("browser_download_url", "")
                    if (url.isNotBlank()) return UpdateInfo(version, notes, url)
                }
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    // ==================== 弹窗 + 下载 + 安装 ====================

    private var dialog: AlertDialog? = null
    private var progressBar: ProgressBar? = null
    private var progressText: TextView? = null

    private fun showUpdateDialog(activity: Activity, info: UpdateInfo) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_update, null)
        val notesView = view.findViewById<TextView>(R.id.updateNotes)
        val progress = view.findViewById<ProgressBar>(R.id.updateProgress)
        val progressTxt = view.findViewById<TextView>(R.id.updateProgressText)
        notesView.text = info.notes.ifBlank { "本次更新带来了功能改进与问题修复。" }
        progress.visibility = View.GONE
        progressTxt.visibility = View.GONE
        progressBar = progress
        progressText = progressTxt

        dialog = AlertDialog.Builder(activity)
            .setTitle("发现新版本 v" + info.version)
            .setView(view)
            .setPositiveButton("立即更新") { _, _ -> startDownload(activity, info) }
            .setNegativeButton("稍后再说", null)
            .setCancelable(true)
            .create()
        dialog?.show()
    }

    private fun startDownload(activity: Activity, info: UpdateInfo) {
        val progress = progressBar ?: return
        val progressTxt = progressText ?: return
        progress.visibility = View.VISIBLE
        progressTxt.visibility = View.VISIBLE
        progressTxt.text = "准备下载..."

        thread {
            try {
                val dir = File(activity.cacheDir, UPDATE_DIR)
                dir.mkdirs()
                val dest = File(dir, "update.apk")
                if (dest.exists()) dest.delete()

                val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "PoetryApp-Updater")
                    instanceFollowRedirects = true
                }
                try {
                    // 跟随重定向到实际下载地址
                    var resp = conn.responseCode
                    if (resp in 301..308) {
                        val loc = conn.getHeaderField("Location") ?: throw Exception("重定向缺失")
                        conn.disconnect()
                        val c2 = (URL(loc).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 15000
                            readTimeout = 30000
                            setRequestProperty("User-Agent", "PoetryApp-Updater")
                        }
                        return@thread downloadFrom(activity, c2, dest, progress, progressTxt)
                    }
                    if (resp != 200) {
                        mainHandler.post { Toast.makeText(activity, "下载失败：HTTP $resp", Toast.LENGTH_LONG).show() }
                        return@thread
                    }
                    downloadFrom(activity, conn, dest, progress, progressTxt)
                } catch (e: Exception) {
                    mainHandler.post {
                        Toast.makeText(activity, "下载失败：" + (e.message ?: "未知错误"), Toast.LENGTH_LONG).show()
                        progress.visibility = View.GONE
                        progressTxt.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    Toast.makeText(activity, "下载失败：" + (e.message ?: "未知错误"), Toast.LENGTH_LONG).show()
                    progress.visibility = View.GONE
                    progressText?.visibility = View.GONE
                }
            }
        }
    }

    /** 从已打开的连接写文件并实时更新进度，完成后调起安装 */
    private fun downloadFrom(
        activity: Activity,
        conn: HttpURLConnection,
        dest: File,
        progress: ProgressBar,
        progressTxt: TextView
    ) {
        try {
            if (conn.responseCode != 200) {
                mainHandler.post { Toast.makeText(activity, "下载失败：HTTP " + conn.responseCode, Toast.LENGTH_LONG).show() }
                return
            }
            val total = conn.contentLength
            val buf = ByteArray(8192)
            var read = 0L
            var lastPct = -1
            conn.inputStream.use { ins ->
                dest.outputStream().use { out ->
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            val pct = (read * 100 / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                val mb = String.format("%.1f", read / 1048576.0)
                                val totalMb = String.format("%.1f", total / 1048576.0)
                                mainHandler.post {
                                    progress.progress = pct
                                    progressTxt.text = "下载中 $pct%（$mb/$totalMb MB）"
                                }
                            }
                        }
                    }
                }
            }
            mainHandler.post {
                progressTxt.text = "下载完成，正在打开安装..."
                installApk(activity, dest)
            }
        } catch (e: Exception) {
            mainHandler.post {
                Toast.makeText(activity, "下载失败：" + (e.message ?: "未知错误"), Toast.LENGTH_LONG).show()
                progress.visibility = View.GONE
                progressTxt.visibility = View.GONE
            }
        }
    }

    /** 通过 FileProvider 调起系统安装器安装更新 APK */
    private fun installApk(activity: Activity, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            AlertDialog.Builder(activity)
                .setTitle("需要安装权限")
                .setMessage("请允许本应用「安装未知来源应用」，返回后重新点击更新即可安装。")
                .setPositiveButton("去设置") { _, _ ->
                    activity.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.packageName))
                    )
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }
        val uri = FileProvider.getUriForFile(activity, activity.packageName + ".fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(activity, "无法打开安装程序：" + e.message, Toast.LENGTH_LONG).show()
        }
    }

    /** 关闭更新对话框（如页面销毁时） */
    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    /** 远程版本信息 */
    data class UpdateInfo(
        val version: String,
        val notes: String,
        val apkUrl: String
    )
}
