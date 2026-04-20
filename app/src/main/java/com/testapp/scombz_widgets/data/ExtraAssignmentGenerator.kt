package com.testapp.scombz_widgets.data

import com.testapp.scombz_widgets.data.model.Assignment
import com.testapp.scombz_widgets.data.model.AssignmentSource
import com.testapp.scombz_widgets.data.model.ExtraAssignmentSchedule
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * エクストラ課題スケジュールを具体的な Assignment インスタンスに変換するロジック。
 *
 * 「今アクティブか？」「期限はいつか？」を現在時刻から導き、
 * 既存の Assignment と同じ表示フローに乗せられるようにする。
 */
object ExtraAssignmentGenerator {

    /**
     * 1つのスケジュールを現在時刻に対して評価し、
     * アクティブウィンドウ中ならその Assignment を返す。
     * アクティブでない場合は null。
     *
     * アクティブ判定ロジック:
     *  - 直近過去の開始日時 `startDt` を算出
     *  - `endDt = startDt + duration`
     *  - `startDt <= now && now < endDt` の場合にアクティブ
     */
    fun generateActive(
        schedule: ExtraAssignmentSchedule,
        now: LocalDateTime
    ): Assignment? {
        if (!schedule.enabled) return null

        val startDt = mostRecentStart(schedule, now)
        val endDt = startDt.plusMinutes(schedule.durationMinutes)

        return if (now.isBefore(endDt)) {
            Assignment(
                title = schedule.title,
                courseName = schedule.courseName,
                deadline = endDt,
                courseId = "",
                isSubmitted = false,
                source = AssignmentSource.EXTRA,
                extraScheduleId = schedule.id
            )
        } else {
            null
        }
    }

    /**
     * 複数スケジュールからアクティブな Assignment をまとめて生成する。
     */
    fun generateAll(
        schedules: List<ExtraAssignmentSchedule>,
        now: LocalDateTime = LocalDateTime.now()
    ): List<Assignment> {
        return schedules.mapNotNull { generateActive(it, now) }
    }

    /**
     * 次に「開始されるタイミング」を返す（プレビュー用途）。
     * スケジュールが現在アクティブ中であっても、次のサイクルの開始を返す。
     */
    fun nextStart(
        schedule: ExtraAssignmentSchedule,
        now: LocalDateTime = LocalDateTime.now()
    ): LocalDateTime {
        val recent = mostRecentStart(schedule, now)
        // recent が未来にあり得ないロジックだが、safeguard
        return if (recent.isAfter(now)) recent else recent.plusWeeks(1)
    }

    /**
     * now 以下で最も近い「開始日時」を算出する。
     *
     * 週サイクルで同じ曜日・時刻に必ず1つの開始が存在する。
     */
    private fun mostRecentStart(
        schedule: ExtraAssignmentSchedule,
        now: LocalDateTime
    ): LocalDateTime {
        val targetDow = schedule.startDayOfWeek      // 1..7
        val currentDow = now.dayOfWeek.value         // 1..7
        // 直近過去の該当曜日までの日差（0..6）
        val daysBack = ((currentDow - targetDow) % 7 + 7) % 7
        var candidate = now.toLocalDate()
            .minusDays(daysBack.toLong())
            .atTime(LocalTime.of(schedule.startHour, schedule.startMinute))
        // 同日だが時刻がまだ来ていない場合は、1週間前が直近
        if (candidate.isAfter(now)) {
            candidate = candidate.minusWeeks(1)
        }
        return candidate
    }
}
