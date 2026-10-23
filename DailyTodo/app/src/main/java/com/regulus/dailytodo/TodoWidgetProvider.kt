package com.regulus.dailytodo

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId

class TodoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, mgr, it) }
        mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        scheduleMidnight(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE -> {
                val taskId = intent.getLongExtra(EXTRA_TASK, -1L)
                val subId = intent.getLongExtra(EXTRA_SUB, -1L)
                if (taskId > 0) {
                    TaskStore.toggle(context, taskId, subId)
                    refreshAll(context)
                }
            }
            ACTION_MIDNIGHT -> refreshAll(context)
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.regulus.dailytodo.TOGGLE"
        const val ACTION_MIDNIGHT = "com.regulus.dailytodo.MIDNIGHT"
        const val EXTRA_TASK = "task_id"
        const val EXTRA_SUB = "sub_id"

        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TodoWidgetProvider::class.java))
            if (ids.isEmpty()) return
            ids.forEach { updateWidget(ctx, mgr, it) }
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
            scheduleMidnight(ctx)
        }

        private fun updateWidget(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val tasks = TaskStore.load(ctx)
            val views = RemoteViews(ctx.packageName, R.layout.widget_todo)
            views.setTextViewText(R.id.widget_date, TaskStore.dateLabel())
            views.setTextViewText(
                R.id.widget_count,
                if (tasks.isEmpty()) "" else "${tasks.count { it.done }}/${tasks.size}"
            )

            val svc = Intent(ctx, TodoWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_list, svc)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            // 点列表中的某一行 → 切换完成状态
            val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val toggle = Intent(ctx, TodoWidgetProvider::class.java).setAction(ACTION_TOGGLE)
            views.setPendingIntentTemplate(
                R.id.widget_list,
                PendingIntent.getBroadcast(ctx, 1, toggle, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
            )

            // 点标题 → 打开 App
            val open = PendingIntent.getActivity(
                ctx, 2, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header, open)
            views.setOnClickPendingIntent(R.id.widget_empty, open)

            mgr.updateAppWidget(id, views)
        }

        /** 0 点过 5 秒刷新一次，让每日任务在桌面上也自动重置 */
        private fun scheduleMidnight(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val at = LocalDate.now().plusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 5_000L
            val pi = PendingIntent.getBroadcast(
                ctx, 3,
                Intent(ctx, TodoWidgetProvider::class.java).setAction(ACTION_MIDNIGHT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.set(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}
