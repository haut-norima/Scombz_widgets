package com.testapp.scombz_widgets.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.testapp.scombz_widgets.data.model.ExtraAssignmentSchedule

/**
 * エクストラ課題スケジュールの永続化リポジトリ。
 * SharedPreferences にJSON配列として格納する（軽量・シンプル）。
 *
 * 全APIがsuspendでないのは、I/Oが非常に軽量なため。
 * ウィジェットからも同期的に呼び出せるようにしている。
 */
class ExtraAssignmentRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREF_NAME = "extra_assignments"
        private const val KEY_SCHEDULES = "schedules_json"
    }

    /**
     * すべてのスケジュールを取得する（保存順）。
     */
    fun loadAll(): List<ExtraAssignmentSchedule> {
        val json = prefs.getString(KEY_SCHEDULES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ExtraAssignmentSchedule>>() {}.type
            gson.fromJson<List<ExtraAssignmentSchedule>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 有効なスケジュールだけを取得する。
     */
    fun loadEnabled(): List<ExtraAssignmentSchedule> = loadAll().filter { it.enabled }

    /**
     * 全スケジュールを上書き保存する。
     */
    private fun saveAll(schedules: List<ExtraAssignmentSchedule>) {
        prefs.edit()
            .putString(KEY_SCHEDULES, gson.toJson(schedules))
            .apply()
    }

    /**
     * 追加または更新（同一IDがあれば置き換え、なければ末尾に追加）。
     */
    fun upsert(schedule: ExtraAssignmentSchedule) {
        val list = loadAll().toMutableList()
        val idx = list.indexOfFirst { it.id == schedule.id }
        if (idx >= 0) list[idx] = schedule else list.add(schedule)
        saveAll(list)
    }

    /**
     * IDを指定して削除。
     */
    fun delete(id: String) {
        val list = loadAll().filterNot { it.id == id }
        saveAll(list)
    }

    /**
     * 有効/無効を切り替え。
     */
    fun setEnabled(id: String, enabled: Boolean) {
        val list = loadAll().map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        saveAll(list)
    }

    /**
     * すべて削除（デバッグ・テスト用途）。
     */
    fun clearAll() {
        prefs.edit().remove(KEY_SCHEDULES).apply()
    }
}
