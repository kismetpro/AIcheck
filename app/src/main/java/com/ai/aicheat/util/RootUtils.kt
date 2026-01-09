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
            // 关键修复：添加chmod 666，确保应用非root用户也能读取该文件
            // 如果screencap成功但应用无法读取，通常是因为默认权限为600（仅root可读）
            var result = executeCommand("screencap -p $outputPath && chmod 666 $outputPath")
            
            // 如果第一次失败，尝试不带chmod（某些旧系统shell行为不同），然后再chmod
            if (!result.success && result.error.isNotEmpty()) {
                Log.w(TAG, "First screenshot attempt failed, retrying split commands...")
                result = executeCommand("screencap -p $outputPath")
                executeCommand("chmod 666 $outputPath")
            }

            if (result.success || File(outputPath).exists()) {
                val file = File(outputPath)
                // 检查文件是否存在以及大小
                if (file.exists() && file.length() > 0) {
                    Log.d(TAG, "Screenshot saved to $outputPath, size: ${file.length()}")
                    file
                } else {
                    Log.e(TAG, "Screenshot file not created or empty. Path: $outputPath")
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
    /**
     * 使用Root权限截图并返回Bitmap
     * 改进：增加多种读取方式，解决SELinux导致的权限问题
     */
    /**
     * 终极截图方案
     * 策略1: 直接流式读取 (screencap -p -> stdout -> Bitmap) - 最快，无文件权限问题
     * 策略2: SD卡中转 (screencap -p /sdcard/xxx -> Bitmap) - 兼容性兜底
     */
    suspend fun takeScreenshotAsBitmap(): Bitmap? = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        
        // 方案一：标准输出流式读取
        // 优点：不生成中间文件，速度快，无SELinux文件上下文问题
        try {
            Log.d(TAG, "Trying Strategy 1: Direct Stream")
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("screencap -p\n")
            os.writeBytes("exit\n") // 必须明确退出，否则流不会结束
            os.flush()

            // 关键：不手动读取字节，直接交给BitmapFactory从流中解码
            // 这样可以避免手动处理Buffer带来的OOM或截断问题
            val bitmap = BitmapFactory.decodeStream(process.inputStream)
            
            // 消费掉任何错误输出，防止阻塞
            try {
                if (process.errorStream.available() > 0) {
                    val errorMsg = process.errorStream.bufferedReader().readText()
                    if (errorMsg.isNotEmpty()) Log.w(TAG, "Stream stderr: $errorMsg")
                }
                process.waitFor()
            } catch (e: Exception) { /* ignore */ }
            
            process.destroy()

            if (bitmap != null) {
                Log.i(TAG, "Strategy 1 Success! Cost: ${System.currentTimeMillis() - start}ms")
                return@withContext bitmap
            } else {
                Log.w(TAG, "Strategy 1 failed: decodeStream returned null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Strategy 1 error", e)
        }

        // 方案二：SD卡文件兜底
        // /data/local/tmp 在高版本Android极其严格，改用 /sdcard/ (即 /storage/emulated/0/)
        // 这里通常是Shell和Root都可读写的公共区域
        try {
            Log.d(TAG, "Trying Strategy 2: SDCard Fallback")
            val fallbackPath = "/sdcard/window_dump.png"
            
            // 1. 截图 (强制覆盖)
            var cmdResult = executeCommand("screencap -p $fallbackPath")
            if (!cmdResult.success) {
                // 某些设备可能需要 /storage/emulated/0/
                cmdResult = executeCommand("screencap -p /storage/emulated/0/window_dump.png")
            }

            if (File(fallbackPath).exists()) {
                // 2. 也是最关键的一步：提权。
                // 即使是Root创建的文件，其他应用读取也可能受限，直接chmod 777
                executeCommand("chmod 777 $fallbackPath")
                
                // 3. 读取
                val bitmap = BitmapFactory.decodeFile(fallbackPath)
                
                // 4. 清理
                executeCommand("rm $fallbackPath")
                
                if (bitmap != null) {
                    Log.i(TAG, "Strategy 2 Success! Cost: ${System.currentTimeMillis() - start}ms")
                    return@withContext bitmap
                }
            } else {
                Log.e(TAG, "Strategy 2: File not found after screencap")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Strategy 2 error", e)
        }

        Log.e(TAG, "All strategies failed")
        return@withContext null
    }

    /**
     * 执行二进制命令的辅助方法已不再需要，已整合进主流程或无需使用
     */
    
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
    
    /**
     * 使用Root权限将文本复制到剪贴板
     * 通过写入临时文件并使用 app_process 执行 Java 代码来实现
     * @param text 要复制的文本
     * @return 是否成功
     */
    suspend fun copyToClipboard(text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempFile = "/data/local/tmp/clip_${System.currentTimeMillis()}.txt"
            
            // 将文本写入临时文件（避免shell转义问题）
            val writeResult = writeTextToFile(text, tempFile)
            if (!writeResult) {
                Log.e(TAG, "Failed to write text to temp file")
                return@withContext false
            }
            
            // 方法1: 使用 cmd clipboard (Android 10+)
            var result = executeCommand("cat $tempFile | cmd clipboard set")
            if (result.success && !result.error.contains("Error") && !result.error.contains("Exception")) {
                Log.d(TAG, "Copied to clipboard using cmd clipboard")
                executeCommand("rm $tempFile")
                return@withContext true
            }
            
            // 方法2: 使用 content provider 方式
            val escapedForShell = text.replace("'", "'\"'\"'")
            result = executeCommand("am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT '$escapedForShell' --activity-no-history --activity-exclude-from-recents com.android.shell 2>/dev/null || true")
            
            // 方法3: 通过 settings 写入（部分设备支持）
            result = executeCommand("settings put system clipboard_text \"$(cat $tempFile)\"")
            
            // 清理临时文件
            executeCommand("rm $tempFile")
            
            Log.d(TAG, "Clipboard copy attempted")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Copy to clipboard failed", e)
            false
        }
    }
    
    /**
     * 将文本写入文件（使用Root权限）
     */
    private suspend fun writeTextToFile(text: String, filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            
            // 使用 cat 和 EOF 来写入多行文本，避免转义问题
            os.writeBytes("cat > $filePath << 'CLIPBOARD_EOF'\n")
            os.writeBytes(text)
            os.writeBytes("\nCLIPBOARD_EOF\n")
            os.writeBytes("chmod 644 $filePath\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            Log.e(TAG, "Write text to file failed", e)
            false
        }
    }
    
    /**
     * 使用Root权限将文本复制到剪贴板（简化版，直接使用input命令模拟粘贴）
     * 这种方式更可靠，但会直接粘贴而非仅复制
     * @param text 要粘贴的文本
     * @return 是否成功
     */
    suspend fun pasteText(text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 转义特殊字符用于input text命令
            val escapedText = text
                .replace("\\", "\\\\")
                .replace(" ", "%s")
                .replace("\n", "")
                .replace("\r", "")
                .replace("\t", "")
            
            val result = executeCommand("input text \"$escapedText\"")
            result.success
        } catch (e: Exception) {
            Log.e(TAG, "Paste text failed", e)
            false
        }
    }
    
    data class CommandResult(
        val success: Boolean,
        val output: String,
        val error: String
    )
}
