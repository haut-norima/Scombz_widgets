package com.testapp.scombz_widgets.data.model

/**
 * バス時刻表の1時間分のエントリ
 */
data class BusHourEntry(
    val hour: Int,
    val toSchoolMinutes: List<Int>,     // 駅→学校 (bus_right.num1 + num2)
    val fromSchoolMinutes: List<Int>,   // 学校→駅 (bus_left.num1 + num2)
    val toSchoolMemo: String,           // 適時運行メモなど
    val fromSchoolMemo: String
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
