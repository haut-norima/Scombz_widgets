package com.testapp.scombz_widgets.data.parser

import com.testapp.scombz_widgets.data.model.Course
import com.testapp.scombz_widgets.data.model.TimetableSheet
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * ScombZの時間割HTMLをパースして TimetableSheet を生成する。
 * info.md に記載された HTML 構造に基づく実装。
 */
object TimetableParser {

    private val DAYS_JP = listOf("月", "火", "水", "木", "金", "土")
    private val DAY_REGEX = Regex("""(\d+)-yobicol""")
    private val Q_PERIOD_REGEX = Regex("""\(([１２３４]Q)\)$""")

    /**
     * 時間割ページの HTML をパースして TimetableSheet を返す
     */
    fun parse(html: String, year: Int, term: String): TimetableSheet {
        val doc = Jsoup.parse(html)
        val grid = mutableMapOf<String, MutableMap<String, MutableList<Course>>>()

        // div[class*="-yobicol"] を全て取得
        doc.select("div[class*=-yobicol]").forEach { cell ->
            val day = getDay(cell) ?: return@forEach
            val period = getPeriod(cell) ?: return@forEach
            val courses = parseCourses(cell, day, period)

            if (courses.isNotEmpty()) {
                grid.getOrPut(period) { mutableMapOf() }
                    .getOrPut(day) { mutableListOf() }
                    .addAll(courses)
            }
        }

        return TimetableSheet(year, term, grid)
    }

    /**
     * セル要素のクラス名から曜日を取得
     * "2-yobicol" → "火"
     */
    private fun getDay(cell: Element): String? {
        val classes = cell.className()
        val match = DAY_REGEX.find(classes) ?: return null
        val index = match.groupValues[1].toIntOrNull()?.minus(1) ?: return null
        return DAYS_JP.getOrNull(index)
    }

    /**
     * セルの親要素を遡り、時限テキストを取得
     * .div-table-data-row > .div-table-colomn-period のテキスト
     */
    private fun getPeriod(cell: Element): String? {
        var parent = cell.parent()
        while (parent != null) {
            if (parent.hasClass("div-table-data-row")) {
                return parent.selectFirst(".div-table-colomn-period")?.text()?.trim()
            }
            parent = parent.parent()
        }
        return null
    }

    /**
     * セル内の授業一覧をパース
     */
    private fun parseCourses(cell: Element, day: String, period: String): List<Course> {
        return cell.select(".clearfix").mapNotNull { courseDiv ->
            parseSingleCourse(courseDiv, day, period)
        }
    }

    /**
     * 授業1件分のHTMLをパース
     */
    private fun parseSingleCourse(div: Element, day: String, period: String): Course? {
        val nameElement = div.selectFirst(".timetable-course-top-btn") ?: return null
        val rawName = nameElement.text().trim()
        if (rawName.isBlank()) return null

        val courseId = nameElement.attr("id")

        // 教員名の取得
        val teachers = div.select(".div-table-cell-detail span")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }

        // 教室名の取得 (data-toggle="tooltip" の title 属性)
        val classroom = div.selectFirst("[data-toggle=tooltip]")
            ?.attr("title")
            ?.trim()
            ?: ""

        // Q期の判定
        val qMatch = Q_PERIOD_REGEX.find(rawName)
        val qPeriod = qMatch?.groupValues?.get(1)
        val baseName = if (qMatch != null) {
            rawName.replace(Q_PERIOD_REGEX, "").trim()
        } else {
            rawName
        }

        return Course(
            name = rawName,
            baseName = baseName,
            courseId = courseId,
            teachers = teachers,
            classroom = classroom,
            qPeriod = qPeriod,
            day = day,
            period = period
        )
    }
}
