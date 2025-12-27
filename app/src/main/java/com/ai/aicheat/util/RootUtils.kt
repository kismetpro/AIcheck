package com.ai.aicheat.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File

/**
 * Root权限工具类
 * 用于执行需要Root权限的操作
 */
object RootUtils {
    private const val TAG = "RootUtils"
    
    /**
     * 检查是否有Root权限
     */
    suspend fun checkRootAccess(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("id\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            Log.e(TAG, "Root check failed", e)
            false
        }
    }
    
    /**
     * 执行Root命令
     */
    suspend fun executeCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            val exitValue = process.waitFor()
            
            CommandResult(exitValue == 0, output, error)
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed: $command", e)
            CommandResult(false, "", e.message ?: "Unknown error")
        }
    }
    
    /**
     * 使用Root权限截图
     * @param outputPath 截图保存路径
     * @return 截图文件，失败返回null
     */
    suspend fun takeScreenshot(outputPath: String): File? = withContext(Dispatchers.IO) {
        try {
            // 使用screencap命令截图
            val result = executeCommand("screencap -p $outputPath")
            if (result.success) {
                val file = File(outputPath)
                if (file.exists() && file.length() > 0) {
                    Log.d(TAG, "Screenshot saved to $outputPath")
                    file
                } else {
                    Log.e(TAG, "Screenshot file not created or empty")
                    null
                }
            } else {
                Log.e(TAG, "Screenshot command failed: ${result.error}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Take screenshot failed", e)
            null
        }
    }
    
    /**
     * 使用Root权限截图并返回Bitmap
     */
    suspend fun takeScreenshotAsBitmap(): Bitmap? = withContext(Dispatchers.IO) {
        val tempPath = "/data/local/tmp/screenshot_${System.currentTimeMillis()}.png"
        try {
            val file = takeScreenshot(tempPath)
            if (file != null) {
                val bitmap = BitmapFactory.decodeFile(tempPath)
                // 删除临时文件
                executeCommand("rm $tempPath")
                bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Take screenshot as bitmap failed", e)
            executeCommand("rm $tempPath")
            null
        }
    }
    
    /**
     * 使用Root权限监听按键事件
     * 通过getevent命令监听输入事件
     */
    fun getInputEventCommand(): String {
        return "getevent -l"
    }
    
    /**
     * 模拟按键事件（如需要恢复音量等）
     */
    suspend fun injectKeyEvent(keyCode: Int): Boolean = withContext(Dispatchers.IO) {
        val result = executeCommand("input keyevent $keyCode")
        result.success
    }
    
    data class CommandResult(
        val success: Boolean,
        val output: String,
        val error: String
    )
}
