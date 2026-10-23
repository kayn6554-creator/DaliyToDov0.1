package com.regulus.dailytodo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.random.Random

/**
 * 本地存储（SharedPreferences + JSON）。
 * 每次读取时检查日期：跨天则
 *   1) 把前一天的每日任务 + 已完成待办写进历史；
 *   2) 每日任务（含子任务）的完成状态和完成情况清空；
 *   3) 已完成的一次性待办移出列表（已在历史里）。
 */
object TaskStore {
    private const val PREFS = "todo_store"
    private const val KEY_TASKS = "tasks"
    private const val KEY_DATE = "last_date"
    private const val KEY_HISTORY = "history"
    private const val MAX_HISTORY_DAYS = 180

    private val WEEK = arrayOf("一", "二", "三", "四", "五", "六", "日")

    fun today(): String = LocalDate.now().toString()

    fun dateLabel(): String {
        val d = LocalDate.now()
        return "${d.monthValue}月${d.dayOfMonth}日  周${WEEK[d.dayOfWeek.value - 1]}"
    }

    fun newId(): Long = System.currentTimeMillis() * 1000 + Random.nextInt(1000)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun load(ctx: Context): MutableList<Task> {
        val p = prefs(ctx)
        val list = parse(p.getString(KEY_TASKS, "[]") ?: "[]")
        val last = p.getString(KEY_DATE, null)
        when {
            last == null -> p.edit().putString(KEY_DATE, today()).apply()
            last != today() -> rollover(ctx, list, last)
        }
        return list
    }

    @Synchronized
    fun save(ctx: Context, list: List<Task>) {
        prefs(ctx).edit().putString(KEY_TASKS, serialize(list)).apply()
    }

    @Synchronized
    fun toggle(ctx: Context, taskId: Long, subId: Long) {
        val list = load(ctx)
        val t = list.find { it.id == taskId } ?: return
        if (subId < 0) {
            t.setDoneAll(!t.done)
        } else {
            t.subs.find { it.id == subId }?.let { it.done = !it.done }
            t.syncFromSubs()
        }
        save(ctx, list)
    }

    fun history(ctx: Context): List<JSONObject> {
        val arr = JSONArray(prefs(ctx).getString(KEY_HISTORY, "[]") ?: "[]")
        return (0 until arr.length()).map { arr.getJSONObject(it) }.reversed()
    }

    private fun rollover(ctx: Context, list: MutableList<Task>, last: String) {
        val p = prefs(ctx)
        val hist = JSONArray(p.getString(KEY_HISTORY, "[]") ?: "[]")

        val snapshot = JSONArray()
        list.filter { it.daily || it.done }.forEach { snapshot.put(it.toJson()) }
        if (snapshot.length() > 0) {
            hist.put(JSONObject().put("date", last).put("tasks", snapshot))
        }
        val trimmed = JSONArray()
        for (i in maxOf(0, hist.length() - MAX_HISTORY_DAYS) until hist.length()) {
            trimmed.put(hist.get(i))
        }

        list.removeAll { !it.daily && it.done }
        list.filter { it.daily }.forEach { t ->
            t.done = false
            t.note = ""
            t.subs.forEach { s -> s.done = false; s.note = "" }
        }

        p.edit()
            .putString(KEY_HISTORY, trimmed.toString())
            .putString(KEY_TASKS, serialize(list))
            .putString(KEY_DATE, today())
            .apply()
    }

    private fun parse(s: String): MutableList<Task> = try {
        val arr = JSONArray(s)
        (0 until arr.length()).map { Task.fromJson(arr.getJSONObject(it)) }.toMutableList()
    } catch (e: Exception) {
        mutableListOf()
    }

    private fun serialize(list: List<Task>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }
}
