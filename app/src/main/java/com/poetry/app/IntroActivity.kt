package com.poetry.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity

/**
 * 首次启动介绍/同意页：展示应用介绍、声明与权限说明，需勾选同意后才能进入。
 * 同意状态持久化，之后启动不再显示（由 MainActivity 启动前判断）。
 */
class IntroActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 已同意过则直接进入主界面，不再显示介绍页
        if (isAgreed(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_intro)

        val agree = findViewById<CheckBox>(R.id.introAgree)
        val enter = findViewById<Button>(R.id.introEnter)

        agree.setOnCheckedChangeListener { _, checked ->
            enter.isEnabled = checked
        }
        enter.setOnClickListener {
            if (!agree.isChecked) return@setOnClickListener
            setAgreed(this)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }

    companion object {
        private const val PREF = "intro_pref"
        private const val KEY_AGREED = "agreed"

        /** 是否已同意（同意过则不再显示） */
        fun isAgreed(ctx: Context): Boolean =
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getBoolean(KEY_AGREED, false)

        private fun setAgreed(ctx: Context) {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_AGREED, true).apply()
        }
    }
}