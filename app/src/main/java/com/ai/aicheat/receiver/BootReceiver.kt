package com.ai.aicheat.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ai.aicheat.service.VolumeKeyService

/**
 * 开机自启接收器
 * 确保设备重启后服务自动启动
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, starting VolumeKeyService")
            VolumeKeyService.start(context)
        }
    }
}
