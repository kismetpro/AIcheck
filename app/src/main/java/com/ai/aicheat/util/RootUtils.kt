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
     * 终极截图方案 v3 (文件拷贝法)
     * 之前的流式和SD卡方案可能受到系统限制。
     * 本方案：Root先截取到系统临时区 -> 拷贝到应用私有缓存区 -> 提权 -> 应用读取
     * @param savePath 应用私有目录下的目标路径，例如 context.cacheDir.seconds + "/scr.png"
     */
    suspend fun takeScreenshotAsBitmap(savePath: String): Bitmap? = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val sysTempPath = "/data/local/tmp/s_${System.currentTimeMillis()}.png"
        
        try {
            Log.d(TAG, "Taking screenshot via file copy strategy...")
            
            // 1. 先截取到 /data/local/tmp (Root 环境最可靠的写入点)
            val capResult = executeCommand("screencap -p $sysTempPath")
            
            // 如果 screencap 没有任何输出且退出码非0，说明截图本身挂了
            if (!capResult.success && capResult.error.contains("inaccessible")) {
                Log.e(TAG, "Screencap failed: ${capResult.error}")
                return@withContext null
            }
            
            // 2. 检查文件是否生成
            val checkFile = executeCommand("ls -l $sysTempPath")
            if (checkFile.output.isBlank() || checkFile.output.contains("No such file")) {
                Log.e(TAG, "Screencap file not created at $sysTempPath")
                return@withContext null
            }

            // 3. 将文件拷贝到应用私有目录 (savePath)
            // 直接 screencap 到 savePath 可能会因为父目录权限问题失败，所以用 cp
            executeCommand("cp $sysTempPath $savePath")
            
            // 4. 修改应用私有目录下该文件的权限，确保应用层可读
            // (虽然在私有目录下，但如果是 Root cp 过去的，owner 还是 root)
            executeCommand("chmod 666 $savePath")
            
            // 5. 应用读取
            val bitmap = BitmapFactory.decodeFile(savePath)
            
            // 6. 清理系统临时文件
            executeCommand("rm $sysTempPath")
            
            // 确认结果
            if (bitmap != null) {
                Log.i(TAG, "Screenshot success! Size: ${bitmap.width}x${bitmap.height}, Cost: ${System.currentTimeMillis() - start}ms")
                return@withContext bitmap
            } else {
                Log.e(TAG, "Bitmap decode failed from $savePath. File size: ${File(savePath).length()}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Screenshot process error", e)
            try { executeCommand("rm $sysTempPath") } catch (_: Exception) {}
        }
        
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
