package com.testapp.scombz_widgets.data.parser

import android.util.Log
import com.testapp.scombz_widgets.data.model.Assignment
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDateTime

/**
 * ScombZ 課題一覧ページ (/lms/task) から課題情報をパースする。
 *
 * 実際のHTML構造（info_tasks.md より）:
 *
 *   #taskList.result_list
 *     └── .result_list_content.sortTaskParent
 *           └── .result_list_line.sortTaskBlock  ← 課題1件ごと
 *                 ├── .tasklist-course              科目名
 *                 ├── .tasklist-contents.online-mobile-hide a  コンテンツ種別（課題/テスト）
 *                 ├── .tasklist-title.online-mobile-hide a     タイトル
 *                 └── .tasklist-deadline
 *                       └── .deadline              期限（YYYY/MM/DD HH:MM:SS）
 *
 * 注意: クラス名はアンダースコア区切り（result_list_line, tasklist_course 等）
 */
object AssignmentParser {

    private const val TAG = "AssignmentParser"

    // 日時パターン: "2026/04/16 23:00:00" または "2026/04/16 23:00"
    private val DATE_REGEX = Regex(
        """(\d{4})[/\-年](\d{1,2})[/\-月](\d{1,2})日?[\s\u3000]*(\d{1,2}):(\d{2})"""
    )

    // ──────────────────────────────────────────────────────────────────────
    // メインエントリポイント
    // ──────────────────────────────────────────────────────────────────────

    /**
     * /lms/task ページのHTMLから課題一覧をパース。
     * Strategy 1（正確なセレクタ）を優先し、取得できなければフォールバック戦略を試みる。
     */
    fun parseTaskPage(html: String): List<Assignment> {
        val doc = Jsoup.parse(html)
        logPageStructure(doc)

        // Strategy 1: 実際のScombZクラス名（result_list_line、アンダースコア）
        parseByScombzClasses(doc).takeIf { it.isNotEmpty() }?.let { return it }

        // Strategy 2: テーブル行
        parseByTableRows(doc).takeIf { it.isNotEmpty() }?.let {
            Log.d(TAG, "Fell back to table-rows strategy")
            return it
        }

        // Strategy 3: 日付テキストを持つ要素（汎用フォールバック）
        parseByDeadlineText(doc).takeIf { it.isNotEmpty() }?.let {
            Log.d(TAG, "Fell back to deadline-text strategy")
            return it
        }

        Log.w(TAG, "All strategies failed. HTML may have unexpected structure.")
        return emptyList()
    }

    /**
     * ホームページHTMLから課題一覧をパース（フォールバック用）。
     */
    fun parseHomePage(html: String): List<Assignment> {
        val doc = Jsoup.parse(html)
        // ホームページでも result_list_line を持つ場合がある
        parseByScombzClasses(doc).takeIf { it.isNotEmpty() }?.let { return it }
        return parseByDeadlineText(doc)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Strategy 1: ScombZ の実際のHTML構造に基づく正確なパーサー
    //   - .result_list_line（アンダースコア）を選択
    //   - .tasklist-course で科目名
    //   - .tasklist-title.online-mobile-hide a でタイトル（PC表示用を優先）
    //   - .deadline で期限日時
    // ──────────────────────────────────────────────────────────────────────

    private fun parseByScombzClasses(doc: Document): List<Assignment> {
        val results = mutableListOf<Assignment>()
        val seen = mutableSetOf<String>()

        // result_list_line（アンダースコア）が実際のScombZのクラス名
        // result-list-line（ハイフン）はフォールバック
        val rows = doc.select(".result_list_line, .result-list-line")

        Log.d(TAG, "result_list_line rows found: ${rows.size}")

        for (row in rows) {
            // ヘッダー行（result_list_tag）はスキップ
            if (row.hasClass("result_list_tag") ||
                row.parent()?.hasClass("result_list_tag") == true) continue

            extractFromScombzRow(row, seen)?.let { results.add(it) }
        }

        Log.d(TAG, "Strategy1(ScombZ classes) found: ${results.size}")
        return results.sortedBy { it.deadline }
    }

    private fun extractFromScombzRow(row: Element, seen: MutableSet<String>): Assignment? {
        // 科目名: .tasklist-course
        val course = row.selectFirst(".tasklist-course")?.text()?.trim() ?: ""

        // タイトル: PCレイアウト用（online-mobile-hide）を優先
        //           なければモバイル用（online-display-hide内）を使用
        val titleEl = row.selectFirst(".tasklist-title.online-mobile-hide a")
            ?: row.selectFirst(".online-mobile-hide .tasklist-title a")
            ?: row.selectFirst(".tasklist-title a")
        val title = titleEl?.text()?.trim() ?: ""

        // コンテンツ種別（課題/テスト）
        val contentType = (
            row.selectFirst(".tasklist-contents.online-mobile-hide a")
                ?: row.selectFirst(".online-mobile-hide .tasklist-contents a")
                ?: row.selectFirst(".tasklist-contents a")
        )?.text()?.trim() ?: ""

        // 科目名・タイトルが両方空ならスキップ（ヘッダー行等）
        if (course.isEmpty() && title.isEmpty()) return null

        // 期限: .deadline クラス（.tasklist-mobile-deadline は「期限：」ラベルなので不要）
        val deadlineText = row.selectFirst(".deadline")?.text()?.trim()
            ?: row.selectFirst(".tasklist-deadline")?.text()?.trim()
            ?: ""

        val deadline = extractDateTimeFromText(deadlineText) ?: run {
            // .deadline が見つからない場合は行全体のテキストから抽出
            extractDateTimeFromText(row.text())
        } ?: return null

        val actualTitle = title.ifBlank { contentType }.ifBlank { return null }

        val key = "$actualTitle|$deadline"
        if (seen.contains(key)) return null
        seen.add(key)

        Log.d(TAG, "  Found: course='$course' type='$contentType' title='$actualTitle' deadline='$deadline'")
        return Assignment(
            title = actualTitle,
            courseName = course,
            deadline = deadline
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // Strategy 2: テーブル行（<table><tr>形式のフォールバック）
    // ──────────────────────────────────────────────────────────────────────

    private fun parseByTableRows(doc: Document): List<Assignment> {
        val results = mutableListOf<Assignment>()
        val seen = mutableSetOf<String>()

        doc.select("table tr").forEach { tr ->
            val cells = tr.select("td")
            if (cells.size < 2) return@forEach
            val link = tr.selectFirst("a") ?: return@forEach
            val title = link.text().trim().ifBlank { return@forEach }
            val allText = cells.joinToString(" ") { it.text() }
            val deadline = extractDateTimeFromText(allText) ?: return@forEach

            val key = "$title|$deadline"
            if (seen.contains(key)) return@forEach
            seen.add(key)

            val course = cells.firstOrNull { cell ->
                !cell.select("a").any { it.text().trim() == title } &&
                cell.text().isNotBlank() &&
                extractDateTimeFromText(cell.text()) == null
            }?.text()?.trim() ?: ""

            results.add(Assignment(title = title, courseName = course, deadline = deadline))
        }

        Log.d(TAG, "Strategy2(table-rows) found: ${results.size}")
        return results.sortedBy { it.deadline }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Strategy 3: 日付テキストを含む要素の汎用スキャン
    //             el.text()（子要素含む）で判定する修正版
    // ──────────────────────────────────────────────────────────────────────

    private fun parseByDeadlineText(doc: Document): List<Assignment> {
        val results = mutableListOf<Assignment>()
        val seen = mutableSetOf<String>()

        val contentArea = doc.selectFirst(
            ".contents-detail, .main-content, #content, main, body"
        ) ?: doc.body() ?: return emptyList()

        // el.text()（子要素含む）で日付を判定 + 適切なサイズの要素に絞る
        contentArea.allElements.filter { el ->
            val text = el.text()
            text.length in 10..500 &&
            DATE_REGEX.containsMatchIn(text) &&
            el.select("a").isNotEmpty()
        }.take(30).forEach { el ->
            // 最初の <a> ではなく、コンテンツっぽいリンクを優先
            val link = el.select("a").firstOrNull { a ->
                val t = a.text().trim()
                t.isNotBlank() && t != "課題" && t != "テスト" && t.length > 1
            } ?: el.selectFirst("a") ?: return@forEach
            val title = link.text().trim().ifBlank { return@forEach }

            val deadline = extractDateTimeFromText(el.text()) ?: return@forEach

            val key = "$title|$deadline"
            if (seen.contains(key)) return@forEach
            seen.add(key)

            val course = el.selectFirst(".tasklist-course, .course-name, .subject")
                ?.text()?.trim() ?: ""

            Log.d(TAG, "  Fallback found: title='$title' course='$course' deadline='$deadline'")
            results.add(Assignment(title = title, courseName = course, deadline = deadline))
        }

        Log.d(TAG, "Strategy3(deadline-text) found: ${results.size}")
        return results.sortedBy { it.deadline }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 日時パース
    // ──────────────────────────────────────────────────────────────────────

    /**
     * テキストから日時を抽出する。
     * ScombZ の形式: "2026/04/16 23:00:00" または "2026/04/16 23:00"
     */
    fun extractDateTimeFromText(text: String): LocalDateTime? {
        if (text.isBlank()) return null
        // 全角スペースを半角に正規化
        val normalized = text.replace('\u3000', ' ')

        DATE_REGEX.find(normalized)?.let { match ->
            return try {
                LocalDateTime.of(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    match.groupValues[3].toInt(),
                    match.groupValues[4].toInt(),
                    match.groupValues[5].toInt()
                )
            } catch (_: Exception) { null }
        }
        return null
    }

    // ──────────────────────────────────────────────────────────────────────
    // デバッグ: ページ構造をlogcatに出力
    // ──────────────────────────────────────────────────────────────────────

    private fun logPageStructure(doc: Document) {
        Log.d(TAG, "=== AssignmentParser debug ===")
        Log.d(TAG, "Title: ${doc.title()}")

        // result_list_line（アンダースコア・ハイフン両方確認）
        val underscoreRows = doc.select(".result_list_line")
        val hyphenRows = doc.select(".result-list-line")
        Log.d(TAG, "  .result_list_line (underscore): ${underscoreRows.size}")
        Log.d(TAG, "  .result-list-line (hyphen): ${hyphenRows.size}")

        // tasklist系クラスの確認
        listOf("tasklist-course", "tasklist-title", "tasklist-deadline", "deadline").forEach { cls ->
            val cnt = doc.select(".$cls").size
            if (cnt > 0) Log.d(TAG, "  .$cls: $cnt elements")
        }

        // テーブル
        val tables = doc.select("table")
        Log.d(TAG, "  tables: ${tables.size}")

        // 最初の result_list_line 要素のHTML（あれば）
        underscoreRows.firstOrNull()?.let { row ->
            Log.d(TAG, "  First row HTML:\n${row.outerHtml().take(500)}")
        }

        // 日付パターンを含む <tr>
        val trWithDate = doc.select("tr").filter { DATE_REGEX.containsMatchIn(it.text()) }
        Log.d(TAG, "  tr with date: ${trWithDate.size}")

        // ページbodyの先頭5000文字
        val bodyHtml = doc.body()?.html()?.take(5000) ?: ""
        Log.d(TAG, "=== Body (first 5000 chars) ===\n$bodyHtml")
    }
}
