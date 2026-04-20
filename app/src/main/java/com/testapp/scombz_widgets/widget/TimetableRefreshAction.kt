package com.testapp.scombz_widgets.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.testapp.scombz_widgets.data.repository.ScombzRepository

/**
 * 時間割ウィジェットの更新ボタンが押されたときのAction。
 * 時間割データをScombZから再取得してウィジェットを更新する。
 */
class TimetableRefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val repository = ScombzRepository(context)
        // Cookie がなければ表示更新のみ（ログイン不要のUI再描画）
        if (repository.isLoggedIn()) {
            repository.loadCookies()
            repository.syncTimetable()
        }
        TimetableWidget.updateWidget(context)
    }
}
