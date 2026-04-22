package com.testapp.scombz_widgets.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ウィジェットUI更新用BroadcastReceiver。
 *
 * 受信するIntentのAction:
 *   - ACTION_WIDGET_TICK      : WidgetUpdateSchedulerからの1分ごとのアラーム
 *   - ACTION_BOOT_COMPLETED   : 端末再起動時（アラームを再スケジュール）
 *   - MY_PACKAGE_REPLACED     : APKアップデート後（アラームが消えるため再スケジュール）
 *
 * 【重要】このReceiverはUIの表示更新のみ行う。
 *         ScombZサーバーへのアクセス（データ同期）はDataSyncWorkerが担う。
 */
class WidgetUpdateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WidgetReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: action=${intent.action}")
        when (intent.action) {
            WidgetUpdateScheduler.ACTION_WIDGET_TICK -> {
                val active = WidgetUpdateScheduler.isAnyWidgetActive(context)
                Log.d(TAG, "TICK: anyActive=$active")
                if (active) {
                    updateAllWidgets(context)
                    // 次の1分後アラームをセット（自己スケジューリング）
                    WidgetUpdateScheduler.scheduleNext(context)
                }
                // ウィジェットが0枚ならアラームを再スケジュールしない（自動停止）
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "BOOT/REPLACED: 再スケジュール")
                if (WidgetUpdateScheduler.isAnyWidgetActive(context)) {
                    updateAllWidgets(context)
                    WidgetUpdateScheduler.start(context)
                }
            }
        }
    }

    private fun updateAllWidgets(context: Context) {
        Log.d(TAG, "updateAllWidgets: 開始")
        // goAsync() でReceiverのライフサイクル外でコルーチンを実行
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // 各ウィジェットを個別に try-catch して一方の失敗が他方に影響しないようにする
                try { TimetableWidget.updateWidget(context) }
                catch (e: Exception) { Log.e(TAG, "TimetableWidget 更新失敗", e) }

                try { AssignmentWidget.updateWidget(context) }
                catch (e: Exception) { Log.e(TAG, "AssignmentWidget 更新失敗", e) }

                try { BusWidget.updateWidget(context) }
                catch (e: Exception) { Log.e(TAG, "BusWidget 更新失敗", e) }

                Log.d(TAG, "updateAllWidgets: 完了")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
