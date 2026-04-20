package com.testapp.scombz_widgets.data.model

/**
 * 授業1件を表すデータクラス
 */
data class Course(
    val name: String,
    val baseName: String,
    val courseId: String,
    val teachers: List<String>,
    val classroom: String,
    val qPeriod: String?,
    val day: String,      // "月","火","水","木","金","土"
    val period: String    // "１限"〜"７限"
)

/**
 * 1年度・1学期分の時間割全体
 */
data class TimetableSheet(
    val year: Int,
    val term: String, // "前期" or "後期"
    val grid: Map<String, Map<String, List<Course>>> // period -> day -> courses
)

/**
 * 時限の時間帯定義（芝浦工業大学）
 */
enum class PeriodTime(
    val label: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
) {
    PERIOD_1("１限", 9, 0, 10, 30),
    PERIOD_2("２限", 10, 40, 12, 10),
    PERIOD_3("３限", 13, 0, 14, 30),
    PERIOD_4("４限", 14, 40, 16, 10),
    PERIOD_5("５限", 16, 20, 17, 50),
    PERIOD_6("６限", 18, 0, 19, 30),
    PERIOD_7("７限", 19, 40, 21, 10);

    fun startMinuteOfDay(): Int = startHour * 60 + startMinute
    fun endMinuteOfDay(): Int = endHour * 60 + endMinute

    companion object {
        private val DAY_LABELS = listOf("月", "火", "水", "木", "金", "土")

        fun fromLabel(label: String): PeriodTime? =
            entries.find { it.label == label }

        fun getCurrentOrNext(currentMinuteOfDay: Int): PeriodTime? {
            // 現在進行中の時限
            entries.find {
                currentMinuteOfDay in it.startMinuteOfDay()..it.endMinuteOfDay()
            }?.let { return it }
            // 次の時限
            return entries.find { it.startMinuteOfDay() > currentMinuteOfDay }
        }

        fun dayOfWeekToLabel(javaDayOfWeek: Int): String? {
            // java.time.DayOfWeek: MONDAY=1 ... SATURDAY=6, SUNDAY=7
            return if (javaDayOfWeek in 1..6) DAY_LABELS[javaDayOfWeek - 1] else null
        }
    }
}
