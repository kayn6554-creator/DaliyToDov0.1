package com.regulus.dailytodo

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class TodoWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    /** level: 0 = 分组标题, 1 = 大任务, 2 = 子任务 */
    private class Row(
        val taskId: Long,
        val subId: Long,
        val title: String,
        val note: String,
        val done: Boolean,
        val level: Int
    )

    private class Factory(private val ctx: Context) : RemoteViewsFactory {
        private var rows: List<Row> = emptyList()

        override fun onCreate() {}
        override fun onDestroy() {}

        override fun onDataSetChanged() {
            val tasks = TaskStore.load(ctx)
            val out = mutableListOf<Row>()
            fun group(name: String, list: List<Task>) {
                if (list.isEmpty()) return
                out.add(Row(-1, -1, name, "", false, 0))
                list.forEach { t ->
                    val title = if (t.subs.isEmpty()) t.title else "${t.title}  ${t.subProgress()}"
                    out.add(Row(t.id, -1, title, t.note, t.done, 1))
                    t.subs.forEach { s -> out.add(Row(t.id, s.id, s.title, s.note, s.done, 2)) }
                }
            }
            group("每日任务", tasks.filter { it.daily })
            group("待办", tasks.filter { !it.daily })
            rows = out
        }

        override fun getCount() = rows.size

        override fun getViewAt(position: Int): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_item)
            val r = rows.getOrNull(position) ?: return rv

            if (r.level == 0) {
                val s = SpannableString(r.title)
                s.setSpan(StyleSpan(Typeface.BOLD), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                rv.setViewVisibility(R.id.item_check, View.GONE)
                rv.setTextViewText(R.id.item_title, s)
                rv.setTextViewTextSize(R.id.item_title, TypedValue.COMPLEX_UNIT_SP, 12f)
                rv.setTextColor(R.id.item_title, MUTED)
                rv.setViewVisibility(R.id.item_note, View.GONE)
                rv.setViewPadding(R.id.item_root, px(6), px(if (position == 0) 2 else 10), px(6), px(2))
                return rv
            }

            rv.setViewVisibility(R.id.item_check, View.VISIBLE)
            rv.setTextViewText(R.id.item_check, if (r.done) "✓" else "○")
            rv.setTextColor(R.id.item_check, if (r.done) ACCENT else MUTED)

            val title = SpannableString(r.title)
            if (r.done) title.setSpan(StrikethroughSpan(), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            rv.setTextViewText(R.id.item_title, title)
            rv.setTextViewTextSize(R.id.item_title, TypedValue.COMPLEX_UNIT_SP, if (r.level == 1) 14f else 13f)
            rv.setTextColor(R.id.item_title, if (r.done) MUTED else TEXT)

            if (r.note.isNotEmpty()) {
                rv.setViewVisibility(R.id.item_note, View.VISIBLE)
                rv.setTextViewText(R.id.item_note, r.note)
            } else {
                rv.setViewVisibility(R.id.item_note, View.GONE)
            }

            val indent = if (r.level == 2) 26 else 6
            rv.setViewPadding(R.id.item_root, px(indent), px(5), px(6), px(5))

            val fill = Intent()
                .putExtra(TodoWidgetProvider.EXTRA_TASK, r.taskId)
                .putExtra(TodoWidgetProvider.EXTRA_SUB, r.subId)
            rv.setOnClickFillInIntent(R.id.item_root, fill)
            return rv
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false

        private fun px(dp: Int) = (dp * ctx.resources.displayMetrics.density + 0.5f).toInt()

        companion object {
            private val TEXT = 0xFF1F1F1F.toInt()
            private val MUTED = 0xFF8A8580.toInt()
            private val ACCENT = 0xFF2F6B4F.toInt()
        }
    }
}
