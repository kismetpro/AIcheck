package com.ai.aicheat.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * 隐蔽悬浮窗服务
 * 特点：
 * 1. 极高透明度，难以察觉
 * 2. FLAG_SECURE防止被截图
 * 3. 不可触摸，不影响底层操作
 */
class OverlayService : Service() {
    
    companion object {
        private const val TAG = "OverlayService"
        
        const val ACTION_SHOW_TEXT = "com.ai.aicheat.SHOW_TEXT"
        const val ACTION_HIDE_TEXT = "com.ai.aicheat.HIDE_TEXT"
        const val ACTION_CLEAR_TEXT = "com.ai.aicheat.CLEAR_TEXT"
        const val ACTION_TRIGGER_SCREENSHOT = "com.ai.aicheat.TRIGGER_SCREENSHOT"
        const val ACTION_RESTORE_OVERLAY = "com.ai.aicheat.RESTORE_OVERLAY"
        const val EXTRA_TEXT = "extra_text"
    }

    private var triggerView: View? = null
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        createOverlayView()
        createTriggerView()
        Log.d(TAG, "OverlayService created")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_TEXT -> {
                val text = intent.getStringExtra(EXTRA_TEXT) ?: ""
                showOverlayText(text)
                // 显示文本时同时也确保Trigger可见（除非用户之前特意全部隐藏了，这里假设显示结果时恢复Trigger以便下次操作）
                showTrigger()
            }
            ACTION_HIDE_TEXT -> {
                toggleTextVisibility()
            }
            ACTION_CLEAR_TEXT -> {
                clearOverlayText()
            }
            ACTION_RESTORE_OVERLAY -> {
                showTrigger()
            }
        }
        return START_STICKY
    }
    
    // ... createOverlayView (text) stays mostly same, but make sure it doesn't overlap trigger if possible
    // For brevity, I'll rely on existing createOverlayView but ensure createTriggerView is added.
    
    private fun createOverlayView() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // --- 文本框 View ---
        textView = TextView(this).apply {
            setTextColor(0x99FFFFFF.toInt())
            textSize = 12f
            setPadding(16, 8, 16, 8)
            setBackgroundColor(0x33000000)
            gravity = Gravity.START
            maxLines = 10
        }
        
        overlayView = textView
        
        val paramsText = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_SECURE
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 100
        }
        
        try {
            windowManager?.addView(overlayView, paramsText)
            overlayView?.visibility = View.GONE
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create text overlay", e)
        }
    }

    private fun createTriggerView() {
        // --- 触发点 View ---
        val triggerDot = View(this).apply {
            // 设置背景为半透明小圆点
            background = ContextCompat.getDrawable(this@OverlayService, com.ai.aicheat.R.drawable.trigger_dot_background)
        }
        
        triggerView = triggerDot
        
        val paramsTrigger = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            // 注意：这里去掉了 FLAG_NOT_TOUCHABLE，因为不仅要看，还要点
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_SECURE // 同样防止被截图
            
            format = PixelFormat.TRANSLUCENT
            width = 40  // 约等于 15-20dp density dependent, hardcoded for now or use dp2px
            height = 40
            gravity = Gravity.CENTER_VERTICAL or Gravity.END // 默认靠右居中
            x = 0
            y = 0
        }
        
        // 点击事件：隐藏自己 -> 截图
        triggerDot.setOnClickListener {
            // 隐藏所有悬浮窗
            triggerView?.visibility = View.GONE
            overlayView?.visibility = View.GONE
            
            Log.d(TAG, "Trigger dot clicked, hiding and broadcasting shot request")
            
            // 发送广播触发截图
            sendBroadcast(Intent(ACTION_TRIGGER_SCREENSHOT).setPackage(packageName))
        }
        
        // 长按事件：隐藏并退出悬浮显示
        triggerDot.setOnLongClickListener {
            triggerView?.visibility = View.GONE
            overlayView?.visibility = View.GONE
            Log.d(TAG, "Trigger dot long clicked, hiding all")
            android.widget.Toast.makeText(this, "悬浮窗已隐藏，点击通知栏恢复", android.widget.Toast.LENGTH_SHORT).show()
            true
        }
        
        try {
            windowManager?.addView(triggerView, paramsTrigger)
            Log.d(TAG, "Trigger view created")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create trigger view", e)
        }
    }
    
    private fun showOverlayText(text: String) {
        currentText = text
        isTextVisible = true // 强制显示
        textView?.post {
            textView?.text = text
            overlayView?.visibility = View.VISIBLE
        }
        Log.d(TAG, "Showing text: ${text.take(50)}...")
    }

    private fun showTrigger() {
        triggerView?.post {
            triggerView?.visibility = View.VISIBLE
        }
    }
    
    private fun toggleTextVisibility() {
        isTextVisible = !isTextVisible
        textView?.post {
            overlayView?.visibility = if (isTextVisible && currentText.isNotEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        Log.d(TAG, "Text visibility toggled: $isTextVisible")
    }
    
    private fun clearOverlayText() {
        currentText = ""
        textView?.post {
            textView?.text = ""
            overlayView?.visibility = View.GONE
        }
        Log.d(TAG, "Text cleared")
    }
    
    /**
     * 设置悬浮窗透明度
     */
    fun setOverlayAlpha(alpha: Float) {
        textView?.post {
            textView?.alpha = alpha
        }
        triggerView?.post {
            triggerView?.alpha = alpha
        }
    }
    
    /**
     * 设置悬浮窗位置 (仅针对文本框，触发点暂固定)
     */
    fun setOverlayPosition(x: Int, y: Int) {
        val params = overlayView?.layoutParams as? WindowManager.LayoutParams
        params?.let {
            it.x = x
            it.y = y
            windowManager?.updateViewLayout(overlayView, it)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            if (overlayView != null) windowManager?.removeView(overlayView)
            if (triggerView != null) windowManager?.removeView(triggerView)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay", e)
        }
        instance = null
        Log.d(TAG, "OverlayService destroyed")
    }
}
