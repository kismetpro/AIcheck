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
        const val EXTRA_TEXT = "extra_text"
        
        private var instance: OverlayService? = null
        
        fun showText(context: Context, text: String) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_SHOW_TEXT
                putExtra(EXTRA_TEXT, text)
            }
            context.startService(intent)
        }
        
        fun hideText(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_HIDE_TEXT
            }
            context.startService(intent)
        }
        
        fun clearText(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_CLEAR_TEXT
            }
            context.startService(intent)
        }
    }
    
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var textView: TextView? = null
    private var isTextVisible = true
    private var currentText = ""
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        createOverlayView()
        Log.d(TAG, "OverlayService created")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_TEXT -> {
                val text = intent.getStringExtra(EXTRA_TEXT) ?: ""
                showOverlayText(text)
            }
            ACTION_HIDE_TEXT -> {
                toggleTextVisibility()
            }
            ACTION_CLEAR_TEXT -> {
                clearOverlayText()
            }
        }
        return START_STICKY
    }
    
    private fun createOverlayView() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // 创建TextView用于显示文本
        textView = TextView(this).apply {
            setTextColor(0x99FFFFFF.toInt())  // 半透明白色
            textSize = 12f
            setPadding(16, 8, 16, 8)
            setBackgroundColor(0x33000000)  // 非常透明的黑色背景
            gravity = Gravity.START
            maxLines = 10
        }
        
        overlayView = textView
        
        // 窗口参数 - 极其隐蔽
        val params = WindowManager.LayoutParams().apply {
            // 窗口类型
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            
            // 关键标志位
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or  // 不获取焦点
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or   // 不可触摸
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or  // 可以在状态栏内
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or  // 无边界限制
                    WindowManager.LayoutParams.FLAG_SECURE  // 关键：防止被截图！
            
            // 透明格式
            format = PixelFormat.TRANSLUCENT
            
            // 尺寸
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            
            // 位置 - 放在屏幕边缘，不显眼
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 100
        }
        
        try {
            windowManager?.addView(overlayView, params)
            overlayView?.visibility = View.GONE  // 初始隐藏
            Log.d(TAG, "Overlay view created with FLAG_SECURE")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create overlay", e)
        }
    }
    
    private fun showOverlayText(text: String) {
        currentText = text
        textView?.post {
            textView?.text = text
            if (isTextVisible) {
                overlayView?.visibility = View.VISIBLE
            }
        }
        Log.d(TAG, "Showing text: ${text.take(50)}...")
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
     * @param alpha 0.0f(完全透明) - 1.0f(不透明)
     */
    fun setOverlayAlpha(alpha: Float) {
        textView?.post {
            textView?.alpha = alpha
        }
    }
    
    /**
     * 设置悬浮窗位置
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
            windowManager?.removeView(overlayView)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay", e)
        }
        instance = null
        Log.d(TAG, "OverlayService destroyed")
    }
}
