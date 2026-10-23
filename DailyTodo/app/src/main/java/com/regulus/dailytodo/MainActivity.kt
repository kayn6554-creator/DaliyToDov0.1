package com.regulus.dailytodo

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var container: LinearLayout
    private lateinit var header: TextView
    private lateinit var summary: TextView
    private var tasks = mutableListOf<Task>()
    private var loadedDate = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = C_BG
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(C_BG)
            setPadding(dp(28), dp(24), dp(28), 0)
        }
        header = TextView(this).apply {
            textSize = 26f
            setTextColor(C_TEXT)
            typeface = Typeface.DEFAULT_BOLD
        }
        summary = TextView(this).apply {
            textSize = 14f
            setTextColor(C_MUTED)
            setPadding(0, dp(4), 0, 0)
        }
        root.addView(header)
        root.addView(summary)

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, dp(8))
        }
        bar.addView(pill("＋ 每日任务", true) { editTask(null, true) })
        bar.addView(pill("＋ 待办", true) { editTask(null, false) })
        bar.addView(pill("历史记录", false) { showHistory() })
        root.addView(bar)

        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(48))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(container)
        }
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        tasks = TaskStore.load(this)
        loadedDate = TaskStore.today()
        render()
    }

    /** 保存 + 刷新桌面小组件 + 重绘 */
    private fun commit() {
        if (loadedDate != TaskStore.today()) {
            // 页面开着跨过了 0 点：先按新的一天重置，避免把昨天的状态写回去
            reload()
            TodoWidgetProvider.refreshAll(this)
            toast("已经是新的一天，每日任务已重置")
            return
        }
        TaskStore.save(this, tasks)
        TodoWidgetProvider.refreshAll(this)
        render()
    }

    // ---------------- 列表 ----------------

    private fun render() {
        header.text = TaskStore.dateLabel()
        summary.text = if (tasks.isEmpty()) "还没有任务"
        else "今日完成 ${tasks.count { it.done }} / ${tasks.size}"

        container.removeAllViews()
        section("每日任务", tasks.filter { it.daily }, "每天 0 点自动重置，适合打卡类任务")
        section("待办", tasks.filter { !it.daily }, "一次性任务，完成后第二天自动归档到历史")
    }

    private fun section(name: String, list: List<Task>, hint: String) {
        container.addView(TextView(this).apply {
            text = name
            textSize = 13f
            setTextColor(C_MUTED)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
            setPadding(dp(4), dp(16), 0, dp(8))
        })
        if (list.isEmpty()) {
            container.addView(TextView(this).apply {
                text = hint
                textSize = 13f
                setTextColor(C_MUTED)
                setPadding(dp(4), 0, 0, dp(8))
            })
            return
        }
        list.forEach { container.addView(taskCard(it)) }
    }

    private fun taskCard(t: Task): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(C_CARD, dp(14).toFloat())
            setPadding(dp(8), dp(6), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) }
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(check(t.done) {
            t.setDoneAll(!t.done)
            commit()
        })
        row.addView(TextView(this).apply {
            text = t.title
            textSize = 17f
            setTextColor(if (t.done) C_MUTED else C_TEXT)
            strike(this, t.done)
            setPadding(dp(4), dp(8), dp(8), dp(8))
            setOnClickListener { editTask(t, t.daily) }
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (t.subs.isNotEmpty()) {
            row.addView(TextView(this).apply {
                text = t.subProgress()
                textSize = 13f
                setTextColor(C_ACCENT)
                setPadding(dp(8), 0, dp(4), 0)
            })
        }
        row.addView(TextView(this).apply {
            text = "＋子任务"
            textSize = 13f
            setTextColor(C_ACCENT)
            setPadding(dp(10), dp(8), dp(8), dp(8))
            setOnClickListener { editSub(t, null) }
        })
        card.addView(row)

        if (t.note.isNotEmpty()) {
            card.addView(TextView(this).apply {
                text = "完成情况：${t.note}"
                textSize = 14f
                setTextColor(C_NOTE)
                setPadding(dp(48), 0, dp(8), dp(6))
                setOnClickListener { editTask(t, t.daily) }
            })
        }

        t.subs.forEach { s ->
            val sr = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(32), 0, 0, 0)
            }
            sr.addView(check(s.done) {
                s.done = !s.done
                t.syncFromSubs()
                commit()
            })
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4), dp(6), dp(8), dp(6))
                setOnClickListener { editSub(t, s) }
            }
            texts.addView(TextView(this).apply {
                text = s.title
                textSize = 15f
                setTextColor(if (s.done) C_MUTED else C_TEXT)
                strike(this, s.done)
            })
            if (s.note.isNotEmpty()) {
                texts.addView(TextView(this).apply {
                    text = s.note
                    textSize = 13f
                    setTextColor(C_NOTE)
                })
            }
            sr.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
            card.addView(sr)
        }
        return card
    }

    // ---------------- 编辑弹窗 ----------------

    private fun editTask(t: Task?, daily: Boolean) {
        val f = form()
        val title = input("任务名称", t?.title ?: "", false)
        val note = input("例如：读到第 3 章 / 跑了 5km / 进度 60%", t?.note ?: "", true)
        val repeat = CheckBox(this).apply {
            text = "每日重复（每天 0 点重置完成状态）"
            isChecked = t?.daily ?: daily
            buttonTintList = ColorStateList.valueOf(C_ACCENT)
        }
        f.addView(label("名称")); f.addView(title)
        f.addView(label("完成情况")); f.addView(note)
        f.addView(repeat)

        val dlgTitle = when {
            t != null -> "编辑任务"
            daily -> "新建每日任务"
            else -> "新建待办"
        }
        val b = AlertDialog.Builder(this)
            .setTitle(dlgTitle)
            .setView(f)
            .setPositiveButton("保存") { _, _ ->
                val name = title.text.toString().trim()
                if (name.isEmpty()) {
                    toast("名称不能为空")
                    return@setPositiveButton
                }
                val n = note.text.toString().trim()
                if (t == null) {
                    tasks.add(Task(TaskStore.newId(), name, repeat.isChecked, n))
                } else {
                    t.title = name
                    t.note = n
                    t.daily = repeat.isChecked
                }
                commit()
            }
            .setNegativeButton("取消", null)
        if (t != null) {
            b.setNeutralButton("删除") { _, _ ->
                confirm("删除「${t.title}」及其全部子任务？") {
                    tasks.remove(t)
                    commit()
                }
            }
        }
        b.show()
    }

    private fun editSub(parent: Task, s: SubTask?) {
        val f = form()
        val title = input("子任务名称", s?.title ?: "", false)
        val note = input("例如：完成 2 题 / 还差最后一节", s?.note ?: "", true)
        f.addView(label("名称")); f.addView(title)
        f.addView(label("完成情况")); f.addView(note)

        val b = AlertDialog.Builder(this)
            .setTitle(if (s == null) "给「${parent.title}」加子任务" else "编辑子任务")
            .setView(f)
            .setPositiveButton("保存") { _, _ ->
                val name = title.text.toString().trim()
                if (name.isEmpty()) {
                    toast("名称不能为空")
                    return@setPositiveButton
                }
                val n = note.text.toString().trim()
                if (s == null) {
                    parent.subs.add(SubTask(TaskStore.newId(), name, n))
                } else {
                    s.title = name
                    s.note = n
                }
                parent.syncFromSubs()
                commit()
            }
            .setNegativeButton("取消", null)
        if (s != null) {
            b.setNeutralButton("删除") { _, _ ->
                parent.subs.remove(s)
                parent.syncFromSubs()
                commit()
            }
        }
        b.show()
    }

    private fun showHistory() {
        val days = TaskStore.history(this)
        val sb = StringBuilder()
        if (days.isEmpty()) sb.append("还没有历史记录。\n每天 0 点后会自动记录前一天的完成情况。")
        days.forEach { d ->
            val arr = d.optJSONArray("tasks") ?: return@forEach
            val ts = (0 until arr.length()).map { Task.fromJson(arr.getJSONObject(it)) }
            sb.append("【").append(d.optString("date")).append("】  完成 ")
                .append(ts.count { it.done }).append("/").append(ts.size).append('\n')
            ts.forEach { t ->
                sb.append(if (t.done) "  ✓ " else "  ✗ ").append(t.title)
                if (t.subs.isNotEmpty()) sb.append("（").append(t.subProgress()).append("）")
                if (t.note.isNotEmpty()) sb.append(" — ").append(t.note)
                sb.append('\n')
                t.subs.forEach { s ->
                    sb.append(if (s.done) "      ✓ " else "      ✗ ").append(s.title)
                    if (s.note.isNotEmpty()) sb.append(" — ").append(s.note)
                    sb.append('\n')
                }
            }
            sb.append('\n')
        }
        val tv = TextView(this).apply {
            text = sb
            textSize = 14f
            setTextColor(C_TEXT)
            setLineSpacing(0f, 1.2f)
            setPadding(dp(24), dp(12), dp(24), dp(12))
            setTextIsSelectable(true)
        }
        AlertDialog.Builder(this)
            .setTitle("历史记录")
            .setView(ScrollView(this).apply { addView(tv) })
            .setPositiveButton("关闭", null)
            .show()
    }

    // ---------------- 小工具 ----------------

    private fun check(checked: Boolean, onClick: () -> Unit) = CheckBox(this).apply {
        isChecked = checked
        buttonTintList = ColorStateList.valueOf(C_ACCENT)
        setOnClickListener { onClick() }
    }

    private fun pill(text: String, primary: Boolean, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(if (primary) Color.WHITE else C_ACCENT)
        background = rounded(if (primary) C_ACCENT else C_PILL, dp(18).toFloat())
        setPadding(dp(16), dp(8), dp(16), dp(8))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(10) }
    }

    private fun form() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(8), dp(24), 0)
    }

    private fun label(s: String) = TextView(this).apply {
        text = s
        textSize = 12f
        setTextColor(C_MUTED)
        setPadding(dp(4), dp(10), 0, 0)
    }

    private fun input(hint: String, value: String, multi: Boolean) = EditText(this).apply {
        this.hint = hint
        setText(value)
        if (multi) {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            maxLines = 5
        } else {
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
    }

    private fun confirm(msg: String, onYes: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(msg)
            .setPositiveButton("删除") { _, _ -> onYes() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun strike(tv: TextView, on: Boolean) {
        tv.paintFlags = if (on) tv.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        else tv.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private val C_BG = 0xFFF4F1EA.toInt()
        private val C_CARD = 0xFFFFFFFF.toInt()
        private val C_TEXT = 0xFF1F1F1F.toInt()
        private val C_MUTED = 0xFF8A8580.toInt()
        private val C_NOTE = 0xFF9A7B4F.toInt()
        private val C_ACCENT = 0xFF2F6B4F.toInt()
        private val C_PILL = 0xFFE3E9E1.toInt()
    }
}
