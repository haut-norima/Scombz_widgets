package com.testapp.scombz_widgets.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import androidx.compose.ui.graphics.Color
import com.testapp.scombz_widgets.R
import com.testapp.scombz_widgets.MainActivity
import com.testapp.scombz_widgets.data.model.Course
import com.testapp.scombz_widgets.data.model.PeriodTime
import com.testapp.scombz_widgets.data.repository.ScombzRepository
import java.time.LocalDateTime

class TimetableWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = ScombzRepository(context)
        val now = LocalDateTime.now()
        val dayLabel = PeriodTime.dayOfWeekToLabel(now.dayOfWeek.value)
        val currentMinute = now.hour * 60 + now.minute
        val currentOrNextPeriod = PeriodTime.getCurrentOrNext(currentMinute)

        val sheet = repository.loadTimetableLocal()
        val todayClasses = if (sheet != null && dayLabel != null) {
            PeriodTime.entries
                .filter { it.startMinuteOfDay() >= currentMinute - 90 }
                .mapNotNull { period ->
                    val courses = sheet.grid[period.label]?.get(dayLabel)
                    if (!courses.isNullOrEmpty()) {
                        val course = selectForQuarter(courses, now.monthValue)
                        period to course
                    } else null
                }
        } else emptyList()

        provideContent {
            GlanceTheme {
                TimetableWidgetContent(
                    todayClasses = todayClasses,
                    dayLabel = dayLabel,
                    currentPeriod = currentOrNextPeriod,
                    currentMinute = currentMinute
                )
            }
        }
    }

    private fun selectForQuarter(courses: List<Course>, month: Int): Course {
        if (courses.size <= 1) return courses.first()
        val currentQ = when (month) {
            in 4..6 -> "１Q"; in 7..8 -> "２Q"
            in 9..11 -> "３Q"; else -> "４Q"
        }
        return courses.find { it.qPeriod == currentQ }
            ?: courses.find { it.qPeriod == null }
            ?: courses.first()
    }

    companion object {
        suspend fun updateWidget(context: Context) {
            TimetableWidget().updateAll(context)
        }
    }
}

// --- Color Definitions ---
private val WidgetBgLight = Color(0xFFF5F5F5)
private val WidgetBgDark = Color(0xFF1E1E1E)
private val AccentBlue = Color(0xFF1A73E8)
private val AccentBlueDark = Color(0xFF8AB4F8)
private val SubText = Color(0xFF666666)
private val SubTextDark = Color(0xFFAAAAAA)
private val CardLight = Color(0xFFFFFFFF)
private val CardDark = Color(0xFF2C2C2C)
private val CurrentBg = Color(0xFFE3F2FD)
private val CurrentBgDark = Color(0xFF1A3A5C)
private val NextBg = Color(0xFFFFF3E0)
private val NextBgDark = Color(0xFF3E2A10)
private val GreenInd = Color(0xFF4CAF50)
private val OrangeInd = Color(0xFFFF9800)
private val GrayInd = Color(0xFFBDBDBD)
private val TextPrimary = Color(0xFF212121)
private val TextPrimaryDark = Color(0xFFE0E0E0)
private val TextSecondary = Color(0xFF757575)
private val TextSecondaryDark = Color(0xFF9E9E9E)
private val MutedText = Color(0xFF888888)
private val PeriodText = Color(0xFF555555)
private val PeriodTextDark = Color(0xFFCCCCCC)

@Composable
private fun TimetableWidgetContent(
    todayClasses: List<Pair<PeriodTime, Course>>,
    dayLabel: String?,
    currentPeriod: PeriodTime?,
    currentMinute: Int
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(ColorProvider(WidgetBgLight, WidgetBgDark))
            .clickable(actionStartActivity<MainActivity>())
            .padding(16.dp),
    ) {
        // Header
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "  時間割",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = ColorProvider(AccentBlue, AccentBlueDark)
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = dayLabel?.let { "${it}曜日" } ?: "",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = ColorProvider(SubText, SubTextDark)
                )
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Image(
                provider = ImageProvider(R.drawable.ic_widget_refresh),
                contentDescription = "時間割を更新",
                colorFilter = androidx.glance.ColorFilter.tint(
                    ColorProvider(SubText, SubTextDark)
                ),
                modifier = GlanceModifier
                    .size(22.dp)
                    .clickable(actionRunCallback<TimetableRefreshAction>())
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        if (todayClasses.isEmpty()) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (dayLabel == null) "今日は休日です" else "今日の授業はありません",
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = ColorProvider(MutedText, SubTextDark)
                    )
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(todayClasses) { (period, course) ->
                    val isCurrent = period == currentPeriod &&
                            currentMinute >= period.startMinuteOfDay() &&
                            currentMinute <= period.endMinuteOfDay()
                    val isNext = period == currentPeriod && currentMinute < period.startMinuteOfDay()

                    ClassItem(period = period, course = course, isCurrent = isCurrent, isNext = isNext)
                    Spacer(modifier = GlanceModifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun ClassItem(
    period: PeriodTime,
    course: Course,
    isCurrent: Boolean,
    isNext: Boolean
) {
    val bgColor = when {
        isCurrent -> ColorProvider(CurrentBg, CurrentBgDark)
        isNext -> ColorProvider(NextBg, NextBgDark)
        else -> ColorProvider(CardLight, CardDark)
    }

    val statusText = when {
        isCurrent -> "● "; isNext -> "▶ "; else -> "○ "
    }

    val statusColor = when {
        isCurrent -> ColorProvider(GreenInd, GreenInd)
        isNext -> ColorProvider(OrangeInd, OrangeInd)
        else -> ColorProvider(GrayInd, GrayInd)
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(12.dp)
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = statusText,
                style = TextStyle(fontSize = 8.sp, color = statusColor)
            )
            Text(
                text = period.label,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ColorProvider(PeriodText, PeriodTextDark)
                )
            )
        }

        Spacer(modifier = GlanceModifier.width(12.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = course.baseName,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = ColorProvider(TextPrimary, TextPrimaryDark)
                ),
                maxLines = 1
            )
            if (course.classroom.isNotBlank()) {
                Text(
                    text = course.classroom,
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = ColorProvider(TextSecondary, TextSecondaryDark)
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

class TimetableWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimetableWidget()

    /** 最初の時間割ウィジェットが追加されたとき → スケジューラ開始 */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateScheduler.start(context)
    }

    /** 最後の時間割ウィジェットが削除されたとき → 全ウィジェット確認後に停止 */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (!WidgetUpdateScheduler.isAnyWidgetActive(context)) {
            WidgetUpdateScheduler.stop(context)
        }
    }
}
