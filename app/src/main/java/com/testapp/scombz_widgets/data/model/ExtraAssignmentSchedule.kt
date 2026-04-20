package com.testapp.scombz_widgets.data.model

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/**
 * エクストラ課題の繰り返しスケジュール定義。
 *
 * 「毎週X曜日H:M から Y曜日H:M まで（期限）」というパターンで課題を自動生成する。
 * 例: 「統計学基礎」木曜 9:00 → 金曜 23:00 (duration = 38時間)
 *
 * @property id              一意ID (UUID)
 * @property title           課題タイトル (例: "統計学基礎課題")
 * @property courseName      科目名 (例: "統計学基礎")
 * @property startDayOfWeek  表示開始曜日 (1=月曜 … 7=日曜、java.time.DayOfWeek準拠)
 * @property startHour       表示開始 時(0-23)
 * @property startMinute     表示開始 分(0-59)
 * @property durationMinutes 表示期間の長さ(分)。endDateTime = startDateTime + durationMinutes
 * @property enabled         有効フラグ (falseなら生成しない)
 * @property note            ユーザー任意メモ (null可)
 * @property createdAt       作成日時 (ISOフォーマット文字列)
 */
data class ExtraAssignmentSchedule(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val courseName: String,
    val startDayOfWeek: Int,
    val startHour: Int,
    val startMinute: Int,
    val durationMinutes: Long,
    val enabled: Boolean = true,
    val note: String? = null,
    val createdAt: String = LocalDateTime.now().toString()
) {
    /**
     * 開始曜日の列挙型表現
     */
    fun startDayOfWeekEnum(): DayOfWeek = DayOfWeek.of(startDayOfWeek)

    /**
     * 開始時刻の LocalTime 表現
     */
    fun startTime(): LocalTime = LocalTime.of(startHour, startMinute)

    /**
     * 終了曜日(1-7)を派生計算
     */
    fun endDayOfWeek(): Int {
        val startWeekMin = (startDayOfWeek - 1) * 24L * 60L + startHour * 60L + startMinute
        val endWeekMin = startWeekMin + durationMinutes
        val endDayIndex = ((endWeekMin / (24 * 60)) % 7).toInt()
        return endDayIndex + 1
    }

    /**
     * 終了時刻 (HH:MM)
     */
    fun endTime(): LocalTime {
        val startWeekMin = (startDayOfWeek - 1) * 24L * 60L + startHour * 60L + startMinute
        val endWeekMin = (startWeekMin + durationMinutes) % (7L * 24L * 60L)
        val endHour = ((endWeekMin / 60L) % 24).toInt()
        val endMin = (endWeekMin % 60L).toInt()
        return LocalTime.of(endHour, endMin)
    }

    companion object {
        /**
         * 開始と終了から durationMinutes を計算するヘルパー。
         * 同一時刻の場合は 7日間(168時間)とする。
         */
        fun computeDuration(
            startDayOfWeek: Int, startHour: Int, startMinute: Int,
            endDayOfWeek: Int, endHour: Int, endMinute: Int
        ): Long {
            val startMin = (startDayOfWeek - 1) * 24L * 60L + startHour * 60L + startMinute
            val endMin = (endDayOfWeek - 1) * 24L * 60L + endHour * 60L + endMinute
            val weekMin = 7L * 24L * 60L
            val diff = ((endMin - startMin) % weekMin + weekMin) % weekMin
            return if (diff == 0L) weekMin else diff
        }
    }
}
