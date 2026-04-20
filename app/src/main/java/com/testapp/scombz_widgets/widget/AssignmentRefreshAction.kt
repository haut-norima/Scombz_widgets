package com.testapp.scombz_widgets.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.testapp.scombz_widgets.data.repository.ScombzRepository

/**
 * 課題ウィジェットの更新ボタンが押されたときのAction。
 * 課題データをScombZから再取得してウィジェットを更新する。
 */
class AssignmentRefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val repository = ScombzRepository(context)
        if (repository.isLoggedIn()) {
            repository.loadCookies()
            repository.syncAssignments()
        }
        AssignmentWidget.updateWidget(context)
    }
}
