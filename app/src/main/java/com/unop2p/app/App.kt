package com.unop2p.app

import android.app.Application
import com.unop2p.app.common.AppLog

/** Application entry point. Holds no game state — sessions live in the ViewModels. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.i(AppLog.Area.UI, "Application started")
    }
}
