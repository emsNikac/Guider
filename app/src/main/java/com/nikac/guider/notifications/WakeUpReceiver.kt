package com.nikac.guider.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nikac.guider.AppFeature
import com.nikac.guider.GuiderApplication

class WakeUpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CONFIRM_WAKE_UP) return
        val application = context.applicationContext as GuiderApplication
        val pendingResult = goAsync()
        application.runWhenFeatureReady(AppFeature.SLEEP) {
            sleepRepository.finishHibernation(System.currentTimeMillis())
            hibernationNotificationManager.cancelActiveSession()
            hibernationPromptScheduler.cancel()
        }.invokeOnCompletion {
            pendingResult.finish()
        }
    }

    companion object {
        const val ACTION_CONFIRM_WAKE_UP = "com.nikac.guider.action.CONFIRM_WAKE_UP"
    }
}
