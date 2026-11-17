package com.standup.app

import android.app.Application
import com.standup.app.core.Notifications
import com.standup.app.data.Store

class StandUpApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Notifications.ensureChannels(this)
    }
}
