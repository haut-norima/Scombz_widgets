package com.testapp.scombz_widgets.data.model

/**
 * バス時刻表の1時間分のエントリ
 *
 * [toSchoolIsOnDemand] / [fromSchoolIsOnDemand] は、
 * num1・num2・memo1・memo2 のいずれかに「適時運行」が含まれる場合 true。
 * num フィールドにテキストとして「適時運行」が入るケースも検出するために
 * parseMinutes とは別に判定する。
 */
data class BusHourEntry(
    val hour: Int,
    val toSchoolMinutes: List<Int>,       // 駅→学校 の定刻（分）
    val fromSchoolMinutes: List<Int>,     // 学校→駅 の定刻（分）
    val toSchoolMemo: String,             // 補足テキスト
    val fromSchoolMemo: String,
    val toSchoolIsOnDemand: Boolean = false,   // 駅→学校 に適時運行あり
    val fromSchoolIsOnDemand: Boolean = false  // 学校→駅 に適時運行あり
)

/**
 * バス時刻表1枚分のデータ
 */
data class BusTimesheet(
    val title: String,
    val tsId: String,
    val calText: String,          // 適用開始日 or カレンダーテキスト
    val note: String,             // 補足テキスト
    val entries: List<BusHourEntry>
)

/**
 * 次のバス情報
 */
data class NextBusInfo(
    val minutesUntil: Int,
    val departureHour: Int,
    val departureMinute: Int,
    val memo: String? = null      // 適時運行時のメモ
) {
    val isShuttleOnDemand: Boolean get() = memo != null

    fun displayText(): String {
        return if (isShuttleOnDemand) {
            "適時運行"
        } else {
            "${minutesUntil}分後"
        }
    }

    fun timeText(): String {
        return "${departureHour}:${departureMinute.toString().padStart(2, '0')}"
    }
}
