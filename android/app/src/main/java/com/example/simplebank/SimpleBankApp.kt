package com.example.simplebank

import android.app.Application

class SimpleBankApp : Application() {
    override fun onCreate() {
        super.onCreate()
        OtelSetup.init(this)
    }
}
