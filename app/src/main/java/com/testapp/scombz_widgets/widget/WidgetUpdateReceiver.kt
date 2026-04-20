package com.testapp.scombz_widgets.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ウィジェットUI更新用BroadcastReceiver。
 *
 * 受信するIntentのAction:
 *   - ACTION_WIDGET_TICK      : WidgetUpdateSchedulerからの1分ごとのアラーム
 *   - ACTION_BOOT_COMPLETED   : 端末再起動時（アラームを再スケジュール）
 *
 * 【重要】このReceiverはUIの表示更新のみ行う。
 *         ScombZサーバーへのアクセス（データ同期）はDataSyncWorkerが担う。
 */
class WidgetUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WidgetUpdateScheduler.ACTION_WIDGET_TICK -> {
                // ウィジェットが1枚以上配置されている場合のみ更新
                if (WidgetUpdateScheduler.isAnyWidgetActive(context)) {
                    updateAllWidgets(context)
                    // 次の1分後アラームをセット（自己スケジューリング）
                    WidgetUpdateScheduler.scheduleNext(context)
                }
                // ウィジェットが0枚ならアラームを再スケジュールしない（自動停止）
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                // 再起動後にウィジェットが配置されていればアラームを再開
                if (WidgetUpdateScheduler.isAnyWidgetActive(context)) {
                    updateAllWidgets(context)
                    WidgetUpdateScheduler.start(context)
                }
            }
        }
    }

    private fun updateAllWidgets(context: Context) {
        // goAsync() でReceiverのライフサイクル外でコルーチンを実行
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                TimetableWidget.updateWidget(context)
                AssignmentWidget.updateWidget(context)
                BusWidget.updateWidget(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
