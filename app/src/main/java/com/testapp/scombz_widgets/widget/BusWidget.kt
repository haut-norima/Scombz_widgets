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
import androidx.glance.unit.ColorProvider as ColorProviderType
import androidx.glance.layout.Alignment
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
import com.testapp.scombz_widgets.data.model.NextBusInfo
import com.testapp.scombz_widgets.data.parser.BusDataParser
import com.testapp.scombz_widgets.data.repository.BusRepository
import java.time.LocalDateTime

class BusWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 描画のたびにアラームチェーンを再スケジュール（APK更新等で切れても自己修復）
        WidgetUpdateScheduler.start(context)

        val repository = BusRepository(context)
        val now = LocalDateTime.now()

        // ネットワーク通信は行わずキャッシュのみを使用する。
        // これにより毎分確実に表示が更新される。
        // サーバーからのスケジュール取得は DataSyncWorker（1時間ごと）と
        // 手動リロードボタン（BusRefreshAction）が担う。
        var timesheets = repository.loadCachedData()
        if (timesheets.isEmpty()) {
            // キャッシュが空の場合（初回起動時）のみ同期取得する
            timesheets = repository.syncBusData().getOrDefault(emptyList())
        }

        val sheet = BusDataParser.selectTimesheetForToday(timesheets, now.dayOfWeek.value)
        val nextToSchool = sheet?.let {
            BusDataParser.findNextBus(it, now.hour, now.minute, toSchool = true)
        }
        val nextFromSchool = sheet?.let {
            BusDataParser.findNextBus(it, now.hour, now.minute, toSchool = false)
        }

        provideContent {
            GlanceTheme {
                BusWidgetContent(
                    nextToSchool = nextToSchool,
                    nextFromSchool = nextFromSchool
                )
            }
        }
    }

    companion object {
        suspend fun updateWidget(context: Context) {
            BusWidget().updateAll(context)
        }
    }
}

// --- Colors ---
private val WidgetBgLight = Color(0xFFF5F5F5)
private val WidgetBgDark = Color(0xFF1E1E1E)
private val HeaderGreen = Color(0xFF029727)
private val HeaderGreenDark = Color(0xFF66BB6A)
private val SubText = Color(0xFF666666)
private val SubTextDark = Color(0xFFAAAAAA)
private val MutedText = Color(0xFF888888)
private val MutedTextDark = Color(0xFF999999)
private val CardLight = Color(0xFFFFFFFF)
private val CardDark = Color(0xFF2C2C2C)
private val AccentBlue = Color(0xFF1565C0)
private val AccentBlueDark = Color(0xFF64B5F6)
private val AccentOrange = Color(0xFFE65100)
private val AccentOrangeDark = Color(0xFFFFB74D)
private val TextPrimary = Color(0xFF212121)
private val TextPrimaryDark = Color(0xFFE0E0E0)
private val TextSecondary = Color(0xFF757575)
private val TextSecondaryDark = Color(0xFF9E9E9E)
private val HighlightBg = Color(0xFFE8F5E9)
private val HighlightBgDark = Color(0xFF1B3A1B)

@Composable
private fun BusWidgetContent(
    nextToSchool: NextBusInfo?,
    nextFromSchool: NextBusInfo?
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(ColorProvider(WidgetBgLight, WidgetBgDark))
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        // Header
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "  バス時刻",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = ColorProvider(HeaderGreen, HeaderGreenDark)
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "大宮キャンパス",
                style = TextStyle(
                    fontSize = 9.sp,
                    color = ColorProvider(SubText, SubTextDark)
                )
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Image(
                provider = ImageProvider(R.drawable.ic_widget_refresh),
                contentDescription = "バス時刻を更新",
                colorFilter = androidx.glance.ColorFilter.tint(
                    ColorProvider(SubText, SubTextDark)
                ),
                modifier = GlanceModifier
                    .size(32.dp)
                    .clickable(actionRunCallback<BusRefreshAction>())
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        // 駅→学校
        BusDirectionCard(
            label = "駅 → 学校",
            directionIcon = "🏫",
            nextBus = nextToSchool,
            accentColor = ColorProvider(AccentBlue, AccentBlueDark)
        )

        Spacer(modifier = GlanceModifier.height(6.dp))

        // 学校→駅
        BusDirectionCard(
            label = "学校 → 駅",
            directionIcon = "🚉",
            nextBus = nextFromSchool,
            accentColor = ColorProvider(AccentOrange, AccentOrangeDark)
        )

    }
}

@Composable
private fun BusDirectionCard(
    label: String,
    directionIcon: String,
    nextBus: NextBusInfo?,
    accentColor: ColorProviderType
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(12.dp)
            .background(ColorProvider(CardLight, CardDark))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = "$directionIcon $label",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ColorProvider(TextSecondary, TextSecondaryDark)
                )
            )
            Spacer(modifier = GlanceModifier.height(3.dp))

            if (nextBus == null) {
                Text(
                    text = "本日の運行は終了",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = ColorProvider(MutedText, MutedTextDark)
                    )
                )
            } else if (nextBus.isShuttleOnDemand) {
                Text(
                    text = "適時運行中",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                )
            } else {
                Text(
                    text = nextBus.timeText() + " 発",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = ColorProvider(TextPrimary, TextPrimaryDark)
                    )
                )
            }
        }

        if (nextBus != null && !nextBus.isShuttleOnDemand) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = GlanceModifier
                    .cornerRadius(8.dp)
                    .background(ColorProvider(HighlightBg, HighlightBgDark))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "${nextBus.minutesUntil}",
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                )
                Text(
                    text = "分後",
                    style = TextStyle(
                        fontSize = 9.sp,
                        color = ColorProvider(TextSecondary, TextSecondaryDark)
                    )
                )
            }
        }
    }
}

class BusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BusWidget()

    /** APKインストール・更新後にシステムが呼ぶ → アラームを再起動 */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetUpdateScheduler.start(context)
    }

    /** 最初のバスウィジェットが追加されたとき → スケジューラ開始 */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateScheduler.start(context)
    }

    /** 最後のバスウィジェットが削除されたとき → 全ウィジェット確認後に停止 */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (!WidgetUpdateScheduler.isAnyWidgetActive(context)) {
            WidgetUpdateScheduler.stop(context)
        }
    }
}
