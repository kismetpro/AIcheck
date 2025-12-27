package com.ai.aicheat

import android.app.Application
import android.util.Log

/**
 * Application类
 * 用于全局初始化
 */
class App : Application() {
    
    companion object {
        private const val TAG = "App"
        
        lateinit var instance: App
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "App initialized")
    }
}
