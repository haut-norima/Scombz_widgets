package com.testapp.scombz_widgets.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.testapp.scombz_widgets.data.repository.BusRepository

/**
 * バスウィジェットの更新ボタンが押されたときのAction。
 * バス時刻データを再取得してウィジェットを更新する。
 */
class BusRefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val busRepository = BusRepository(context)
        busRepository.syncBusData()
        BusWidget.updateWidget(context)
    }
}
