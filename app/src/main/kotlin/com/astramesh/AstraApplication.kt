package com.astramesh

import android.app.Application
import com.astramesh.common.AstraLog
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AstraApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AstraLog.i("BUILD_VERIFICATION", "============================================================")
        AstraLog.i("BUILD_VERIFICATION", "ASTRA PRODUCTION BUILD MARKER: NLLB_PIPELINE_VERIFIED_V2")
        AstraLog.i("BUILD_VERIFICATION", "PACKAGE: \${packageName} | BUILD_ID: 2026-09-15_16:30_END_TO_END")
        AstraLog.i("BUILD_VERIFICATION", "============================================================")
        AstraLog.i("AstraApplication", "Initializing AstraMesh Decentralized Platform...")
    }
}
