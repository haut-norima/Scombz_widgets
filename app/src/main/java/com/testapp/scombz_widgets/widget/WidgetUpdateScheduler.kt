package com.testapp.scombz_widgets.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

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

    private const val TAG = "WidgetScheduler"
    private const val REQUEST_CODE = 9001

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
     * 次の「:00秒」ちょうどにアラームをセットする（WidgetUpdateReceiver内からも呼ばれる）。
     * 現在時刻に関わらず常に次の分の00秒に揃えるため、複数回呼んでも自己補正される。
     */
    fun scheduleNext(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(context) ?: run {
            Log.e(TAG, "scheduleNext: PendingIntent の作成に失敗")
            return
        }
        // 次の :00 秒ちょうどを計算（例: 12:34:45 → 12:35:00）
        val now = System.currentTimeMillis()
        val triggerAt = (now / 60_000L + 1L) * 60_000L

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pi)
                Log.d(TAG, "scheduleNext: 正確なアラームをセット（次の:00秒 = $triggerAt）")
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pi)
                Log.d(TAG, "scheduleNext: 不正確アラームをセット（権限なし）")
            }
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pi)
            Log.d(TAG, "scheduleNext: 正確なアラームをセット（次の:00秒 = $triggerAt）")
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
        // setComponent() で完全明示的インテントにする。
        // setPackage() のみの場合 Android 8+ の暗黙的ブロードキャスト制限に引っかかる可能性がある。
        val intent = Intent(ACTION_WIDGET_TICK).apply {
            component = ComponentName(
                context.packageName,
                WidgetUpdateReceiver::class.java.name
            )
        }
        val flags = extraFlags or PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
