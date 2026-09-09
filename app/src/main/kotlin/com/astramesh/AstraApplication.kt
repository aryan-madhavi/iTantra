package com.astramesh

import android.app.Application
import com.astramesh.common.AstraLog
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AstraApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AstraLog.i("AstraApplication", "Initializing AstraMesh Decentralized Platform...")
    }
}
