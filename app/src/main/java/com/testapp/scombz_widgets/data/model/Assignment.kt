package com.testapp.scombz_widgets.data.model

import java.time.LocalDateTime
import java.time.Duration

/**
 * 課題の取得元
 */
enum class AssignmentSource {
    /** ScombZ から取得した通常の課題 */
    SCOMBZ,

    /** ユーザーが手動で登録したエクストラ課題（繰り返しスケジュールから自動生成） */
    EXTRA
}

/**
 * 課題1件を表すデータクラス
 */
data class Assignment(
    val title: String,
    val courseName: String,
    val deadline: LocalDateTime,
    val courseId: String = "",
    val isSubmitted: Boolean = false,
    val source: AssignmentSource = AssignmentSource.SCOMBZ,
    /** エクストラ課題の場合に紐づくスケジュールID (UI操作用) */
    val extraScheduleId: String? = null
) {
    /**
     * 残り時間を人間が読みやすい形式で返す
     */
    fun remainingTimeText(now: LocalDateTime = LocalDateTime.now()): String {
        val duration = Duration.between(now, deadline)
        if (duration.isNegative) return "期限切れ"

        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60

        return when {
            days > 0 -> "${days}日${hours}時間"
            hours > 0 -> "${hours}時間${minutes}分"
            else -> "${minutes}分"
        }
    }

    /**
     * 残り時間に応じた緊急度 (0=余裕, 1=注意, 2=危険)
     */
    fun urgencyLevel(now: LocalDateTime = LocalDateTime.now()): Int {
        val duration = Duration.between(now, deadline)
        if (duration.isNegative) return 2
        val hours = duration.toHours()
        return when {
            hours < 24 -> 2
            hours < 72 -> 1
            else -> 0
        }
    }
}
