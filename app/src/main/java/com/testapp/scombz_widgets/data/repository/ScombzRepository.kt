package com.testapp.scombz_widgets.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.testapp.scombz_widgets.data.ExtraAssignmentGenerator
import com.testapp.scombz_widgets.data.model.Assignment
import com.testapp.scombz_widgets.data.model.AssignmentSource
import com.testapp.scombz_widgets.data.model.Course
import com.testapp.scombz_widgets.data.model.PeriodTime
import com.testapp.scombz_widgets.data.model.TimetableSheet
import com.testapp.scombz_widgets.data.network.ScombzClient
import com.testapp.scombz_widgets.data.network.SessionExpiredException
import com.testapp.scombz_widgets.data.parser.AssignmentParser
import com.testapp.scombz_widgets.data.parser.TimetableParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scombz_prefs")

class ScombzRepository(private val context: Context) {

    private val gson = Gson()
    private val client = ScombzClient()

    private val widgetPrefs: SharedPreferences
        get() = context.getSharedPreferences("widget_data", Context.MODE_PRIVATE)

    companion object {
        private val KEY_COOKIES = stringPreferencesKey("cookies")
        private val KEY_YEAR = stringPreferencesKey("selected_year")
        private val KEY_TERM = stringPreferencesKey("selected_term")

        private const val PREF_TIMETABLE = "timetable_json"
        private const val PREF_ASSIGNMENTS = "assignments_json"
        private const val PREF_LAST_SYNC = "last_sync_time"
        private const val PREF_DEBUG_INFO = "last_sync_debug"

        // 学期コード
        const val TERM_FIRST = 10   // 前期
        const val TERM_SECOND = 20  // 後期
    }

    // --- Cookie管理 ---

    suspend fun saveCookies(cookieString: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_COOKIES] = cookieString
        }
        client.setCookies(cookieString)
    }

    suspend fun loadCookies(): String? {
        val prefs = context.dataStore.data.first()
        val cookies = prefs[KEY_COOKIES]
        if (cookies != null) {
            client.setCookies(cookies)
        }
        return cookies
    }

    suspend fun clearCookies() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_COOKIES)
        }
        client.setCookieMap(emptyMap()) // メモリ上のCookieも即座にクリア
    }

    fun isLoggedIn(): Boolean = client.hasCookies()

    // --- 年度・学期設定 ---

    suspend fun saveYearAndTerm(year: Int, termCode: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_YEAR] = year.toString()
            prefs[KEY_TERM] = termCode.toString()
        }
    }

    suspend fun getYear(): Int {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_YEAR]?.toIntOrNull() ?: LocalDateTime.now().year
    }

    suspend fun getTermCode(): Int {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_TERM]?.toIntOrNull() ?: run {
            val month = LocalDateTime.now().monthValue
            if (month in 4..9) TERM_FIRST else TERM_SECOND
        }
    }

    // --- 時間割データ ---

    /**
     * ScombZから時間割を取得してローカルに保存
     */
    suspend fun syncTimetable(): Result<TimetableSheet> = withContext(Dispatchers.IO) {
        if (!client.hasCookies()) {
            loadCookies() ?: return@withContext Result.failure(
                Exception("ログインが必要です")
            )
        }

        val year = getYear()
        val termCode = getTermCode()
        val termName = if (termCode == TERM_FIRST) "前期" else "後期"

        val htmlResult = client.fetchTimetable(year, termCode)

        // セッション切れなら自動ログアウト
        val timetableException = htmlResult.exceptionOrNull()
        if (timetableException is SessionExpiredException) {
            clearCookies()
            return@withContext Result.failure(timetableException)
        }

        htmlResult.fold(
            onSuccess = { html ->
                val sheet = TimetableParser.parse(html, year, termName)
                saveTimetableLocal(sheet)
                Result.success(sheet)
            },
            onFailure = { Result.failure(it) }
        )
    }

    /**
     * ローカルに保存された時間割を読み込む
     */
    fun loadTimetableLocal(): TimetableSheet? {
        val json = widgetPrefs.getString(PREF_TIMETABLE, null) ?: return null
        return try {
            gson.fromJson(json, TimetableSheet::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private fun saveTimetableLocal(sheet: TimetableSheet) {
        widgetPrefs.edit()
            .putString(PREF_TIMETABLE, gson.toJson(sheet))
            .putString(PREF_LAST_SYNC, LocalDateTime.now().toString())
            .apply()
    }

    /**
     * 次の授業を取得
     */
    fun getNextClass(): Course? {
        val sheet = loadTimetableLocal() ?: return null
        val now = LocalDateTime.now()
        val dayLabel = PeriodTime.dayOfWeekToLabel(now.dayOfWeek.value) ?: return null
        val currentMinute = now.hour * 60 + now.minute

        val nextPeriod = PeriodTime.getCurrentOrNext(currentMinute) ?: return null

        // 現在の時限以降で授業がある最初の時限を探す
        val periods = PeriodTime.entries
        val startIndex = periods.indexOf(nextPeriod)

        for (i in startIndex until periods.size) {
            val period = periods[i]
            val courses = sheet.grid[period.label]?.get(dayLabel)
            if (!courses.isNullOrEmpty()) {
                // Q期の授業の場合、現在の月に合うものを優先
                return selectCourseForCurrentQuarter(courses, now.monthValue)
            }
        }
        return null
    }

    /**
     * Q期授業がある場合、現在のクォーターに合う授業を選ぶ
     */
    private fun selectCourseForCurrentQuarter(courses: List<Course>, month: Int): Course {
        if (courses.size <= 1) return courses.first()

        val currentQ = when (month) {
            in 4..6 -> "１Q"
            in 7..8 -> "２Q"
            in 9..11 -> "３Q"
            else -> "４Q"
        }

        return courses.find { it.qPeriod == currentQ }
            ?: courses.find { it.qPeriod == null }
            ?: courses.first()
    }

    // --- 課題データ ---

    /**
     * ScombZから課題を取得してローカルに保存。
     * /lms/task ページを優先し、取得できなければホームページにフォールバック。
     */
    suspend fun syncAssignments(): Result<List<Assignment>> = withContext(Dispatchers.IO) {
        if (!client.hasCookies()) {
            loadCookies() ?: return@withContext Result.failure(
                Exception("ログインが必要です")
            )
        }

        // 1. /lms/task ページから取得を試みる
        val taskPageResult = client.fetchTaskPage()

        // セッション切れなら自動ログアウトして終了（ホームページへのフォールバック不要）
        val taskException = taskPageResult.exceptionOrNull()
        if (taskException is SessionExpiredException) {
            saveDebugInfo("taskPage fetch FAILED: SessionExpired")
            clearCookies()
            return@withContext Result.failure(taskException)
        }

        taskPageResult.onSuccess { html ->
            val parsed = AssignmentParser.parseTaskPage(html)
            val now = LocalDateTime.now()
            val filtered = parsed.filter { !it.isSubmitted && it.deadline.isAfter(now) }
            saveDebugInfo("taskPage: htmlLen=${html.length}, parsed=${parsed.size}, filtered=${filtered.size}")
            if (filtered.isNotEmpty()) {
                saveAssignmentsLocal(filtered)
                return@withContext Result.success(filtered)
            }
        }
        taskPageResult.onFailure { e ->
            saveDebugInfo("taskPage fetch FAILED: ${e.javaClass.simpleName}: ${e.message}")
        }

        // 2. /lms/task で取得できなければホームページにフォールバック
        val homeResult = client.fetchHomePage()

        // ホームページもセッション切れなら自動ログアウト
        val homeException = homeResult.exceptionOrNull()
        if (homeException is SessionExpiredException) {
            clearCookies()
            return@withContext Result.failure(homeException)
        }

        return@withContext homeResult.fold(
            onSuccess = { html ->
                val parsed = AssignmentParser.parseHomePage(html)
                val now = LocalDateTime.now()
                val filtered = parsed.filter { !it.isSubmitted && it.deadline.isAfter(now) }
                val prev = widgetPrefs.getString(PREF_DEBUG_INFO, "") ?: ""
                saveDebugInfo("$prev | homePage: htmlLen=${html.length}, parsed=${parsed.size}, filtered=${filtered.size}")
                saveAssignmentsLocal(filtered)
                Result.success(filtered)
            },
            onFailure = { e ->
                val prev = widgetPrefs.getString(PREF_DEBUG_INFO, "") ?: ""
                saveDebugInfo("$prev | homePage FAILED: ${e.message}")
                // どちらも失敗した場合、最初のエラーを返す
                taskPageResult.exceptionOrNull()?.let { err -> Result.failure(err) }
                    ?: Result.failure(e)
            }
        )
    }

    fun getDebugInfo(): String? = widgetPrefs.getString(PREF_DEBUG_INFO, null)

    private fun saveDebugInfo(info: String) {
        widgetPrefs.edit().putString(PREF_DEBUG_INFO, info).apply()
    }

    /**
     * ローカルに保存された課題を読み込む
     */
    fun loadAssignmentsLocal(): List<Assignment> {
        val json = widgetPrefs.getString(PREF_ASSIGNMENTS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AssignmentJson>>() {}.type
            val list: List<AssignmentJson> = gson.fromJson(json, type)
            list.mapNotNull { it.toAssignment() }
                .filter { it.deadline.isAfter(LocalDateTime.now()) }
                .sortedBy { it.deadline }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAssignmentsLocal(assignments: List<Assignment>) {
        val jsonList = assignments.map { AssignmentJson.from(it) }
        widgetPrefs.edit()
            .putString(PREF_ASSIGNMENTS, gson.toJson(jsonList))
            .apply()
    }

    fun getLastSyncTime(): String? {
        return widgetPrefs.getString(PREF_LAST_SYNC, null)
    }

    /**
     * ScombZ課題 + 現在アクティブなエクストラ課題 をマージしたリストを返す。
     * ウィジェット・画面表示の両方で使用。
     *
     *  - 期限内のもののみ
     *  - 期限の昇順でソート
     *  - 同じスケジュールIDの重複は発生しない（ジェネレータは1サイクルにつき1件のみ生成）
     */
    fun loadMergedAssignments(now: LocalDateTime = LocalDateTime.now()): List<Assignment> {
        val scombz = loadAssignmentsLocal() // 既にフィルタ・ソート済み
        val extraRepo = ExtraAssignmentRepository(context)
        val extras = ExtraAssignmentGenerator.generateAll(extraRepo.loadEnabled(), now)
        return (scombz + extras)
            .filter { it.deadline.isAfter(now) }
            .sortedBy { it.deadline }
    }
}

/**
 * Assignment の JSON シリアライゼーション用DTO
 * (LocalDateTime は Gson で直接扱えないため)
 */
private data class AssignmentJson(
    val title: String,
    val courseName: String,
    val deadline: String,
    val courseId: String,
    val isSubmitted: Boolean
) {
    fun toAssignment(): Assignment? {
        val dt = try {
            LocalDateTime.parse(deadline)
        } catch (_: Exception) {
            return null
        }
        return Assignment(title, courseName, dt, courseId, isSubmitted)
    }

    companion object {
        fun from(a: Assignment) = AssignmentJson(
            title = a.title,
            courseName = a.courseName,
            deadline = a.deadline.toString(),
            courseId = a.courseId,
            isSubmitted = a.isSubmitted
        )
    }
}
