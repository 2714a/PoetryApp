package com.poetry.app
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
/**
 * 启动动画页：一本书合着 → 封面像两扇门向两侧打开 → 内页再像两扇门向外打开 →
 * 显现标题 → 进入主页面（已同意则直达主界面，否则先进声明页）。
 * 作为 LAUNCHER 入口。
 */
class SplashActivity : AppCompatActivity() {
    private var done = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        val coverLeft = findViewById<FrameLayout>(R.id.coverLeft)
        val coverRight = findViewById<FrameLayout>(R.id.coverRight)
        val pageLeft = findViewById<FrameLayout>(R.id.pageLeft)
        val pageRight = findViewById<FrameLayout>(R.id.pageRight)
        val coverTitle = findViewById<TextView>(R.id.coverTitle)
        val spine = findViewById<View>(R.id.bookSpine)
        val center = findViewById<LinearLayout>(R.id.splashCenter)
        val container = findViewById<FrameLayout>(R.id.bookContainer)
        // 翻门轴心：左半绕左边缘，右半绕右边缘
        coverLeft.pivotX = 0f
        pageLeft.pivotX = 0f
        coverRight.pivotX = coverRight.width.toFloat()
        pageRight.pivotX = pageRight.width.toFloat()

        // 兜底：最长 7s 强制进入，避免动画异常卡住
        center.postDelayed({ goNext() }, 7000L)
        coverLeft.post { openCovers(coverLeft, coverRight, coverTitle, spine, pageLeft, pageRight, center, container) }
    }
    /** 第一步：封面像两扇门向两侧打开 */
    private fun openCovers(
        cl: View, cr: View, title: View, spine: View,
        pl: View, pr: View, center: View, container: View
    ) {
        var finished = 0
        val doneOne: () -> Unit = {
            finished++
            if (finished == 4) openPages(pl, pr, center, container)
        }
        cl.animate().rotationY(-92f).setDuration(1400L).setStartDelay(900L)
            .setListener(simpleEnd(doneOne)).start()
        cr.animate().rotationY(92f).setDuration(1400L).setStartDelay(900L)
            .setListener(simpleEnd(doneOne)).start()
        title.animate().alpha(0f).setDuration(700L).setStartDelay(900L)
            .setListener(simpleEnd(doneOne)).start()
        spine.animate().alpha(1f).setDuration(1100L).setStartDelay(900L)
            .setListener(simpleEnd(doneOne)).start()
    }
    /** 第二步：内页再像两扇门向外打开，露出中心标题 */
    private fun openPages(pl: View, pr: View, center: View, container: View) {
        var finished = 0
        val doneOne: () -> Unit = {
            finished++
            if (finished == 2) reveal(center, container)
        }
        pl.animate().rotationY(-95f).setDuration(1300L).setListener(simpleEnd(doneOne)).start()
        pr.animate().rotationY(95f).setDuration(1300L).setListener(simpleEnd(doneOne)).start()
    }
    /** 第三步：中心标题淡入，书本淡出，随后进入 */
    private fun reveal(center: View, container: View) {
        center.animate().alpha(1f).setDuration(600L).start()
        container.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(700L).start()
        center.postDelayed({ goNext() }, 1300L)
    }
    private fun simpleEnd(action: () -> Unit): AnimatorListenerAdapter =
        object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) { action() }
        }
    private fun goNext() {
        if (done) return
        done = true
        val target = if (IntroActivity.isAgreed(this)) MainActivity::class.java
                     else IntroActivity::class.java
        startActivity(Intent(this, target))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
