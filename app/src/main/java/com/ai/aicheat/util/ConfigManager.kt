package com.ai.aicheat.util

import android.content.Context
import android.content.SharedPreferences
import com.ai.aicheat.App

/**
 * 配置存储工具
 * 用于持久化保存API配置等信息
 */
object ConfigManager {
    
    private const val PREF_NAME = "aicheat_config"
    private const val KEY_API_URL = "api_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model"
    private const val KEY_PROMPT = "prompt"
    
    private val prefs: SharedPreferences by lazy {
        App.instance.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    var apiUrl: String
        get() = prefs.getString(KEY_API_URL, "https://api.openai.com/v1/chat/completions") ?: ""
        set(value) = prefs.edit().putString(KEY_API_URL, value).apply()
    
    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()
    
    var model: String
        get() = prefs.getString(KEY_MODEL, "gpt-4o") ?: ""
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()
    
    var prompt: String
        get() = prefs.getString(KEY_PROMPT, "请仔细分析这张图片，如果是题目请给出答案，如果是其他内容请简要描述。回答要简洁。") ?: ""
        set(value) = prefs.edit().putString(KEY_PROMPT, value).apply()
    
    /**
     * 检查配置是否完整
     */
    fun isConfigured(): Boolean {
        return apiUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
    }
    
    /**
     * 清除所有配置
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
