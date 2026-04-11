package com.apofeoz.shiftmanager

import android.app.Application
import com.apofeoz.shiftmanager.core.di.AppContainer

class ShiftManagerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}
