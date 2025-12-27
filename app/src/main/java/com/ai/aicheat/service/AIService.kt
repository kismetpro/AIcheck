package com.ai.aicheat.service

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.ai.aicheat.util.ConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * AI服务类
 * 负责将截图发送到AI接口并获取响应
 */
object AIService {
    private const val TAG = "AIService"
    
    // TODO: 请替换为您的AI API配置
    // 注意：现在使用ConfigManager持久化配置，以下默认值仅作为备份
    private const val API_URL = "https://api.openai.com/v1/chat/completions"
    private const val API_KEY = "YOUR_API_KEY_HERE"  // 替换为您的API Key
    private const val MODEL = "gpt-4o"  // 或其他支持视觉的模型
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    /**
     * 将Bitmap转换为Base64字符串
     */
    private fun bitmapToBase64(bitmap: Bitmap, quality: Int = 80): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
    
    /**
     * 发送截图到AI并获取响应（使用持久化配置）
     * @param bitmap 截图
     * @param prompt 提示词（可选）
     * @return AI响应文本
     */
    suspend fun analyzeScreenshot(
        bitmap: Bitmap,
        prompt: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 使用持久化配置
            val apiUrl = ConfigManager.apiUrl
            val apiKey = ConfigManager.apiKey
            val model = ConfigManager.model
            val finalPrompt = prompt ?: ConfigManager.prompt
            
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("请先配置API Key"))
            }
            
            val base64Image = bitmapToBase64(bitmap)
            
            // 构建请求体
            val messageContent = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", finalPrompt)
                })
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:image/jpeg;base64,$base64Image")
                        put("detail", "high")
                    })
                })
            }
            
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", messageContent)
                })
            }
            
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("max_tokens", 1000)
            }
            
            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            Log.d(TAG, "Sending request to AI API...")
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                val jsonResponse = JSONObject(responseBody)
                val content = jsonResponse
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                
                Log.d(TAG, "AI Response received: ${content.take(100)}...")
                Result.success(content)
            } else {
                val error = "API Error: ${response.code} - $responseBody"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI request failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * 配置API（运行时更新）
     */
    data class AIConfig(
        val apiUrl: String,
        val apiKey: String,
        val model: String
    )
    
    private var currentConfig: AIConfig? = null
    
    fun updateConfig(config: AIConfig) {
        currentConfig = config
    }
    
    /**
     * 使用自定义配置发送请求
     */
    suspend fun analyzeWithConfig(
        bitmap: Bitmap,
        config: AIConfig,
        prompt: String = "请仔细分析这张图片，如果是题目请直接给出答案，如果是其他内容请简要描述。回答要简洁。"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val base64Image = bitmapToBase64(bitmap)
            
            val messageContent = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", prompt)
                })
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:image/jpeg;base64,$base64Image")
                        put("detail", "high")
                    })
                })
            }
            
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", messageContent)
                })
            }
            
            val requestBody = JSONObject().apply {
                put("model", config.model)
                put("messages", messages)
                put("max_tokens", 1000)
            }
            
            val request = Request.Builder()
                .url(config.apiUrl)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                val jsonResponse = JSONObject(responseBody)
                val content = jsonResponse
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                
                Result.success(content)
            } else {
                Result.failure(Exception("API Error: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
