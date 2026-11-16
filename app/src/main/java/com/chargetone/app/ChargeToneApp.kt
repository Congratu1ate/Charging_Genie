package com.chargetone.app

import android.app.Application

class ChargeToneApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
