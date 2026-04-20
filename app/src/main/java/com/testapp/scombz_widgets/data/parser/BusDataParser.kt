package com.testapp.scombz_widgets.data.parser

import com.google.gson.JsonParser
import com.testapp.scombz_widgets.data.model.BusHourEntry
import com.testapp.scombz_widgets.data.model.BusTimesheet
import com.testapp.scombz_widgets.data.model.NextBusInfo

/**
 * bus.shibaura-it.ac.jp/db/bus_data.json をパースする。
 *
 * JSON構造:
 * {
 *   "update": "...",
 *   "timesheet": [
 *     {
 *       "title": "【2026年度 平日】...",
 *       "ts_id": "...",
 *       "cal_text": "14",
 *       "text1": "...", "text2": "...", "text3": "...",
 *       "list": [
 *         {
 *           "time": "7",
 *           "bus_left":  {"num1": "53", "memo1": "", "num2": "", "memo2": ""},
 *           "bus_right": {"num1": "47.58", "memo1": "", "num2": "", "memo2": ""},
 *           ...
 *         }
 *       ]
 *     }
 *   ]
 * }
 *
 * 分の形式: "00.13.27.48.55" （ドット区切り）
 * プレフィックス文字 (a,b,d,g,h,j等) は行先/種別を示す。パース時に除去する。
 *
 * bus_left  = 駅→学校 方面
 * bus_right = 学校→駅 方面
 */
object BusDataParser {

    private val MINUTE_REGEX = Regex("""[a-z]*(\d+)""", RegexOption.IGNORE_CASE)

    /**
     * JSON文字列をパースしてTimesheetリストを返す
     */
    fun parse(json: String): List<BusTimesheet> {
        val root = JsonParser.parseString(json).asJsonObject
        val timesheets = root.getAsJsonArray("timesheet") ?: return emptyList()

        return timesheets.mapNotNull { element ->
            val obj = element.asJsonObject
            val status = obj.get("status")?.asString ?: ""
            if (status != "public") return@mapNotNull null

            val title = obj.get("title")?.asString ?: ""
            val tsId = obj.get("ts_id")?.asString ?: ""
            val calText = obj.get("cal_text")?.asString ?: ""
            val text1 = obj.get("text1")?.asString ?: ""
            val text2 = obj.get("text2")?.asString ?: ""
            val text3 = obj.get("text3")?.asString ?: ""
            val note = listOf(text1, text2, text3).filter { it.isNotBlank() }.joinToString(" / ")

            val list = obj.getAsJsonArray("list") ?: return@mapNotNull null
            val entries = list.map { entryElement ->
                val entry = entryElement.asJsonObject
                val hour = entry.get("time")?.asString?.toIntOrNull() ?: 0

                val busLeft = entry.getAsJsonObject("bus_left")
                val busRight = entry.getAsJsonObject("bus_right")

                val toSchoolNums = parseMinutes(
                    busLeft?.get("num1")?.asString,
                    busLeft?.get("num2")?.asString
                )
                val fromSchoolNums = parseMinutes(
                    busRight?.get("num1")?.asString,
                    busRight?.get("num2")?.asString
                )

                val toSchoolMemo = listOfNotNull(
                    busLeft?.get("memo1")?.asString?.takeIf { it.isNotBlank() },
                    busLeft?.get("memo2")?.asString?.takeIf { it.isNotBlank() }
                ).joinToString(" ")

                val fromSchoolMemo = listOfNotNull(
                    busRight?.get("memo1")?.asString?.takeIf { it.isNotBlank() },
                    busRight?.get("memo2")?.asString?.takeIf { it.isNotBlank() }
                ).joinToString(" ")

                BusHourEntry(
                    hour = hour,
                    toSchoolMinutes = toSchoolNums,
                    fromSchoolMinutes = fromSchoolNums,
                    toSchoolMemo = toSchoolMemo,
                    fromSchoolMemo = fromSchoolMemo
                )
            }

            BusTimesheet(
                title = title,
                tsId = tsId,
                calText = calText,
                note = note,
                entries = entries
            )
        }
    }

    /**
     * "00.13.a27.b48.55" のような文字列から分のリストを抽出
     * プレフィックス文字は除去する
     */
    private fun parseMinutes(vararg numStrings: String?): List<Int> {
        val minutes = mutableListOf<Int>()
        numStrings.forEach { numStr ->
            if (numStr.isNullOrBlank()) return@forEach
            numStr.split(".").forEach { part ->
                val match = MINUTE_REGEX.find(part.trim())
                if (match != null) {
                    match.groupValues[1].toIntOrNull()?.let { minutes.add(it) }
                }
            }
        }
        return minutes.sorted()
    }

    /**
     * 次のバスを計算する
     *
     * @param timesheet 使用する時刻表
     * @param currentHour 現在の時（0-23）
     * @param currentMinute 現在の分（0-59）
     * @param toSchool true=駅→学校, false=学校→駅
     */
    fun findNextBus(
        timesheet: BusTimesheet,
        currentHour: Int,
        currentMinute: Int,
        toSchool: Boolean
    ): NextBusInfo? {
        // 現在の時刻以降のエントリを探索
        for (entry in timesheet.entries) {
            if (entry.hour < currentHour) continue

            val minutes = if (toSchool) entry.toSchoolMinutes else entry.fromSchoolMinutes
            val memo = if (toSchool) entry.toSchoolMemo else entry.fromSchoolMemo

            if (entry.hour == currentHour) {
                // 同じ時間帯 → 現在の分より後の出発時刻を探す
                val nextMin = minutes.firstOrNull { it > currentMinute }
                if (nextMin != null) {
                    val until = (entry.hour - currentHour) * 60 + (nextMin - currentMinute)
                    return NextBusInfo(
                        minutesUntil = until,
                        departureHour = entry.hour,
                        departureMinute = nextMin
                    )
                }
                // この時間帯に次のバスがない場合、メモがあれば適時運行の可能性
                if (minutes.isEmpty() && memo.isNotBlank() && memo.contains("適時運行")) {
                    return NextBusInfo(
                        minutesUntil = 0,
                        departureHour = entry.hour,
                        departureMinute = currentMinute,
                        memo = memo
                    )
                }
            } else {
                // 次の時間帯
                if (minutes.isNotEmpty()) {
                    val firstMin = minutes.first()
                    val until = (entry.hour - currentHour) * 60 + (firstMin - currentMinute)
                    return NextBusInfo(
                        minutesUntil = until,
                        departureHour = entry.hour,
                        departureMinute = firstMin
                    )
                }
                if (memo.isNotBlank() && memo.contains("適時運行")) {
                    return NextBusInfo(
                        minutesUntil = (entry.hour - currentHour) * 60 - currentMinute,
                        departureHour = entry.hour,
                        departureMinute = 0,
                        memo = memo
                    )
                }
            }
        }
        return null // 本日のバスは終了
    }

    /**
     * タイトルから平日/土曜/休日を判定して、現在の曜日に合う時刻表を選ぶ
     */
    fun selectTimesheetForToday(
        timesheets: List<BusTimesheet>,
        dayOfWeek: Int // java.time.DayOfWeek: 1=MON...7=SUN
    ): BusTimesheet? {
        if (timesheets.isEmpty()) return null
        if (timesheets.size == 1) return timesheets.first()

        return when (dayOfWeek) {
            6 -> { // 土曜
                timesheets.find { it.title.contains("土") }
                    ?: timesheets.firstOrNull()
            }
            7 -> { // 日曜
                timesheets.find { it.title.contains("休日") || it.title.contains("日") }
                    ?: null // 日曜は運行なしの場合がある
            }
            else -> { // 平日
                timesheets.find { it.title.contains("平日") }
                    ?: timesheets.firstOrNull()
            }
        }
    }
}
