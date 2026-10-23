package com.regulus.dailytodo

import org.json.JSONArray
import org.json.JSONObject

class SubTask(
    val id: Long,
    var title: String,
    var note: String = "",
    var done: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("note", note).put("done", done)

    companion object {
        fun fromJson(o: JSONObject) = SubTask(
            o.getLong("id"), o.optString("title"), o.optString("note"), o.optBoolean("done")
        )
    }
}

class Task(
    val id: Long,
    var title: String,
    var daily: Boolean,
    var note: String = "",
    var done: Boolean = false,
    val subs: MutableList<SubTask> = mutableListOf()
) {
    /** 勾选大任务时，子任务一起勾上/取消 */
    fun setDoneAll(v: Boolean) {
        done = v
        subs.forEach { it.done = v }
    }

    /** 子任务全部完成 → 大任务自动完成 */
    fun syncFromSubs() {
        if (subs.isNotEmpty()) done = subs.all { it.done }
    }

    fun subProgress(): String =
        if (subs.isEmpty()) "" else "${subs.count { it.done }}/${subs.size}"

    fun toJson(): JSONObject {
        val arr = JSONArray()
        subs.forEach { arr.put(it.toJson()) }
        return JSONObject().put("id", id).put("title", title).put("daily", daily)
            .put("note", note).put("done", done).put("subs", arr)
    }

    companion object {
        fun fromJson(o: JSONObject): Task {
            val subs = mutableListOf<SubTask>()
            val arr = o.optJSONArray("subs") ?: JSONArray()
            for (i in 0 until arr.length()) subs.add(SubTask.fromJson(arr.getJSONObject(i)))
            return Task(
                o.getLong("id"), o.optString("title"), o.optBoolean("daily"),
                o.optString("note"), o.optBoolean("done"), subs
            )
        }
    }
}
