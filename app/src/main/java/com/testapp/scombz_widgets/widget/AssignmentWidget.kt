package com.testapp.scombz_widgets.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
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
import com.testapp.scombz_widgets.MainActivity
import com.testapp.scombz_widgets.R
import com.testapp.scombz_widgets.data.model.Assignment
import com.testapp.scombz_widgets.data.model.AssignmentSource
import com.testapp.scombz_widgets.data.repository.ScombzRepository
import java.time.LocalDateTime

class AssignmentWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 描画のたびにアラームチェーンを再スケジュール（APK更新等で切れても自己修復）
        WidgetUpdateScheduler.start(context)

        val repository = ScombzRepository(context)
        val now = LocalDateTime.now()
        val assignments = repository.loadMergedAssignments(now)

        provideContent {
            GlanceTheme {
                AssignmentWidgetContent(assignments = assignments, now = now)
            }
        }
    }

    companion object {
        suspend fun updateWidget(context: Context) {
            AssignmentWidget().updateAll(context)
        }
    }
}

// --- Colors ---
private val WidgetBgLight = Color(0xFFF5F5F5)
private val WidgetBgDark = Color(0xFF1E1E1E)
private val HeaderOrange = Color(0xFFE65100)
private val HeaderOrangeDark = Color(0xFFFFB74D)
private val SubText = Color(0xFF666666)
private val SubTextDark = Color(0xFFAAAAAA)
private val MutedText = Color(0xFF888888)
private val MutedTextDark = Color(0xFF999999)
private val CardLight = Color(0xFFFFFFFF)
private val CardDark = Color(0xFF2C2C2C)
private val DangerBg = Color(0xFFFFEBEE)
private val DangerBgDark = Color(0xFF4A1515)
private val WarnBg = Color(0xFFFFF8E1)
private val WarnBgDark = Color(0xFF3E3310)
private val DangerText = Color(0xFFD32F2F)
private val DangerTextDark = Color(0xFFEF5350)
private val WarnText = Color(0xFFF57F17)
private val WarnTextDark = Color(0xFFFFD54F)
private val SafeText = Color(0xFF388E3C)
private val SafeTextDark = Color(0xFF81C784)
private val TextPrimary = Color(0xFF212121)
private val TextPrimaryDark = Color(0xFFE0E0E0)
private val TextSecondary = Color(0xFF757575)
private val TextSecondaryDark = Color(0xFF9E9E9E)
private val ExtraBadgeBg = Color(0xFF6A1B9A)
private val ExtraBadgeBgDark = Color(0xFFBA68C8)
private val ExtraBadgeText = Color(0xFFFFFFFF)

@Composable
private fun AssignmentWidgetContent(
    assignments: List<Assignment>,
    now: LocalDateTime
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
                text = "  課題",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = ColorProvider(HeaderOrange, HeaderOrangeDark)
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "${assignments.size}件",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = ColorProvider(SubText, SubTextDark)
                )
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Image(
                provider = ImageProvider(R.drawable.ic_widget_refresh),
                contentDescription = "課題を更新",
                colorFilter = androidx.glance.ColorFilter.tint(
                    ColorProvider(SubText, SubTextDark)
                ),
                modifier = GlanceModifier
                    .size(32.dp)
                    .clickable(actionRunCallback<AssignmentRefreshAction>())
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        if (assignments.isEmpty()) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "未提出の課題はありません",
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = ColorProvider(MutedText, MutedTextDark)
                    )
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(assignments) { assignment ->
                    AssignmentItem(assignment = assignment, now = now)
                    Spacer(modifier = GlanceModifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun AssignmentItem(
    assignment: Assignment,
    now: LocalDateTime
) {
    val urgency = assignment.urgencyLevel(now)
    val remainingText = assignment.remainingTimeText(now)

    val bgColor = when (urgency) {
        2 -> ColorProvider(DangerBg, DangerBgDark)
        1 -> ColorProvider(WarnBg, WarnBgDark)
        else -> ColorProvider(CardLight, CardDark)
    }

    val remainingColor = when (urgency) {
        2 -> ColorProvider(DangerText, DangerTextDark)
        1 -> ColorProvider(WarnText, WarnTextDark)
        else -> ColorProvider(SafeText, SafeTextDark)
    }

    val urgencyIcon = when (urgency) {
        2 -> "! "; 1 -> "~ "; else -> "  "
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(12.dp)
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = urgencyIcon,
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = remainingColor
            )
        )

        Spacer(modifier = GlanceModifier.width(4.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (assignment.source == AssignmentSource.EXTRA) {
                    // エクストラ課題バッジ
                    Text(
                        text = " 外部 ",
                        style = TextStyle(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(ExtraBadgeText, ExtraBadgeText)
                        ),
                        modifier = GlanceModifier
                            .cornerRadius(4.dp)
                            .background(ColorProvider(ExtraBadgeBg, ExtraBadgeBgDark))
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                }
                Text(
                    text = assignment.title,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ColorProvider(TextPrimary, TextPrimaryDark)
                    ),
                    maxLines = 1
                )
            }
            if (assignment.courseName.isNotBlank()) {
                Text(
                    text = assignment.courseName,
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = ColorProvider(TextSecondary, TextSecondaryDark)
                    ),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = GlanceModifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "残り",
                style = TextStyle(
                    fontSize = 9.sp,
                    color = ColorProvider(MutedText, MutedTextDark)
                )
            )
            Text(
                text = remainingText,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = remainingColor
                )
            )
        }
    }
}

class AssignmentWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AssignmentWidget()

    /** APKインストール・更新後にシステムが呼ぶ → アラームを再起動 */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetUpdateScheduler.start(context)
    }

    /** 最初の課題ウィジェットが追加されたとき → スケジューラ開始 */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateScheduler.start(context)
    }

    /** 最後の課題ウィジェットが削除されたとき → 全ウィジェット確認後に停止 */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (!WidgetUpdateScheduler.isAnyWidgetActive(context)) {
            WidgetUpdateScheduler.stop(context)
        }
    }
}
