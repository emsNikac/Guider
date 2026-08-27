package com.example.guider.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.guider.GuiderApplication

class WakeUpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CONFIRM_WAKE_UP) return
        val application = context.applicationContext as GuiderApplication
        val pendingResult = goAsync()
        application.runWhenReady {
            try {
                sleepRepository.finishHibernation(System.currentTimeMillis())
                hibernationNotificationManager.cancelActiveSession()
                hibernationPromptScheduler.cancel()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_CONFIRM_WAKE_UP = "com.example.guider.action.CONFIRM_WAKE_UP"
    }
}
