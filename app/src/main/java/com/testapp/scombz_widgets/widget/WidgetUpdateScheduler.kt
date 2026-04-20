package com.testapp.scombz_widgets.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * ウィジェットのUI表示を1分ごとに更新するAlarmManagerスケジューラ。
 *
 * 【役割の分離】
 *  - このスケジューラ → ウィジェットのUI表示のみ更新（残り時間、現在授業など）
 *  - DataSyncWorker    → ScombZサーバーからのデータ取得（6時間ごと）
 */
object WidgetUpdateScheduler {

    /** アラームで送信するIntentのAction */
    const val ACTION_WIDGET_TICK = "com.testapp.scombz_widgets.WIDGET_TICK"

    private const val REQUEST_CODE = 9001
    private const val INTERVAL_MS = 60_000L // 1分

    /**
     * スケジューラを開始する（最初のウィジェット追加時に呼ぶ）。
     */
    fun start(context: Context) {
        scheduleNext(context.applicationContext)
    }

    /**
     * スケジューラを停止する（全ウィジェット削除時に呼ぶ）。
     */
    fun stop(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(context, PendingIntent.FLAG_NO_CREATE) ?: return
        am.cancel(pi)
    }

    /**
     * 次の1分後アラームをセットする（WidgetUpdateReceiver内からも呼ばれる）。
     */
    fun scheduleNext(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(context) ?: return
        val triggerAt = System.currentTimeMillis() + INTERVAL_MS

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                // Android 12+: 正確なアラームが使える場合は使用、そうでなければ不正確アラーム
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pi)
                } else {
                    // SCHEDULE_EXACT_ALARM 権限がない場合のフォールバック
                    // 画面オフ時（Doze）でもなるべく発火する
                    am.setAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pi)
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                // Android 6–11: Doze対応の正確アラーム
                am.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pi)
            }
            else -> {
                am.setExact(AlarmManager.RTC, triggerAt, pi)
            }
        }
    }

    /**
     * いずれかのウィジェットがホーム画面に配置されているか確認する。
     */
    fun isAnyWidgetActive(context: Context): Boolean {
        val mgr = android.appwidget.AppWidgetManager.getInstance(context)
        return listOf(
            ComponentName(context, TimetableWidgetReceiver::class.java),
            ComponentName(context, AssignmentWidgetReceiver::class.java),
            ComponentName(context, BusWidgetReceiver::class.java),
        ).any { mgr.getAppWidgetIds(it).isNotEmpty() }
    }

    private fun buildPendingIntent(context: Context, extraFlags: Int = 0): PendingIntent? {
        val intent = Intent(ACTION_WIDGET_TICK).setPackage(context.packageName)
        val flags = extraFlags or PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
