package com.testapp.scombz_widgets.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.testapp.scombz_widgets.data.network.SessionExpiredException
import com.testapp.scombz_widgets.data.repository.BusRepository
import com.testapp.scombz_widgets.data.repository.ScombzRepository
import com.testapp.scombz_widgets.widget.AssignmentWidget
import com.testapp.scombz_widgets.widget.BusWidget
import com.testapp.scombz_widgets.widget.TimetableWidget
import java.util.concurrent.TimeUnit

/**
 * 定期的にScombZからデータを同期するWorker
 */
class DataSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // バスデータはログイン不要のため、常に先に同期する
        val busRepository = BusRepository(applicationContext)
        busRepository.syncBusData()
        BusWidget.updateWidget(applicationContext)

        val repository = ScombzRepository(applicationContext)

        // Cookie が保存されていなければバス同期のみで終了
        repository.loadCookies() ?: return Result.success()

        var hasError = false

        // 時間割の同期
        repository.syncTimetable().onFailure { e ->
            if (e is SessionExpiredException) {
                // セッション切れ：Cookie はリポジトリ側でクリア済み。リトライ不要
                TimetableWidget.updateWidget(applicationContext)
                AssignmentWidget.updateWidget(applicationContext)
                return Result.success()
            }
            hasError = true
        }

        // 課題の同期
        repository.syncAssignments().onFailure { e ->
            if (e is SessionExpiredException) {
                TimetableWidget.updateWidget(applicationContext)
                AssignmentWidget.updateWidget(applicationContext)
                return Result.success()
            }
            hasError = true
        }

        // ウィジェットの更新
        TimetableWidget.updateWidget(applicationContext)
        AssignmentWidget.updateWidget(applicationContext)

        return if (hasError) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "scombz_data_sync"

        /**
         * 定期同期をスケジュール（1時間ごと）
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DataSyncWorker>(
                1, TimeUnit.HOURS,
                15, TimeUnit.MINUTES // flex interval
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE, // 既存ワーカーのスケジュールも即時更新
                request
            )
        }

        /**
         * 定期同期をキャンセル
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
