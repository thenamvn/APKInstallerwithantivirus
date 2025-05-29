package com.alphawolf.apkinstallerwithantivirus.analysis

import com.alphawolf.apkinstallerwithantivirus.BuildConfig
import com.alphawolf.apkinstallerwithantivirus.utils.GeminiApiHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BehaviorInferencer {
    
    suspend fun inferExpectedBehavior(metadata: AppMetadata): ExpectedBehavior = withContext(Dispatchers.IO) {
        // Step 2.1: Predict app type
        val appType = predictAppType(metadata)
        
        // Step 2.2: Get expected behavior from both rule-based and LLM
        val ruleBasedBehavior = getRuleBasedBehavior(appType)
        val llmBehavior = getLLMBehavior(metadata, appType)
        
        // Combine both sources
        ExpectedBehavior(
            appType = appType,
            expectedPermissions = (ruleBasedBehavior.permissions + llmBehavior.permissions).distinct(),
            expectedApis = (ruleBasedBehavior.apis + llmBehavior.apis).distinct(),
            corePermissions = ruleBasedBehavior.corePermissions
        )
    }
    
    private suspend fun predictAppType(metadata: AppMetadata): String {
        val name = metadata.appName.lowercase()
        val packageName = metadata.packageName.lowercase()
        val description = metadata.description?.lowercase() ?: ""
        
        // Rule-based prediction first
        APP_TYPE_KEYWORDS.forEach { (type, keywords) ->
            if (keywords.any { keyword ->
                name.contains(keyword) || packageName.contains(keyword) || description.contains(keyword)
            }) {
                return type
            }
        }
        
        // Fallback to LLM
        return try {
            val prompt = """
                Dựa vào thông tin sau, hãy phân loại ứng dụng vào MỘT trong các loại:
                CAMERA, SOCIAL, GAME, PRODUCTIVITY, MUSIC, VIDEO, SHOPPING, NEWS, WEATHER, FINANCE, HEALTH, EDUCATION, TRAVEL, COMMUNICATION, UTILITY, OTHER
                
                Tên: ${metadata.appName}
                Package: ${metadata.packageName}
                Mô tả: ${metadata.description ?: "Không có"}
                
                Chỉ trả lời ĐÚNG MỘT từ khóa loại ứng dụng.
            """.trimIndent()
            
            val response = GeminiApiHelper.analyzeWithGemini(
                apiKey = BuildConfig.GEMINI_API_KEY,
                appName = metadata.appName,
                packageName = metadata.packageName,
                permissions = emptyList(),
                description = metadata.description ?: ""
            )
            
            extractAppTypeFromResponse(response) ?: "OTHER"
        } catch (e: Exception) {
            "OTHER"
        }
    }
    
    private fun getRuleBasedBehavior(appType: String): BehaviorTemplate {
        return BEHAVIOR_TEMPLATES[appType] ?: BEHAVIOR_TEMPLATES["OTHER"]!!
    }
    
    private suspend fun getLLMBehavior(metadata: AppMetadata, appType: String): BehaviorTemplate {
        return try {
            val prompt = """
                Với ứng dụng loại "$appType" có tên "${metadata.appName}", hãy liệt kê:
                1. Các quyền cần thiết (Android permissions)
                2. Các API thường dùng
                
                Chỉ liệt kê những gì THỰC SỰ cần thiết cho chức năng cốt lõi.
                
                Format:
                PERMISSIONS: permission1, permission2, ...
                APIS: api1, api2, ...
            """.trimIndent()
            
            val response = GeminiApiHelper.analyzeWithGemini(
                apiKey = BuildConfig.GEMINI_API_KEY,
                appName = metadata.appName,
                packageName = metadata.packageName,
                permissions = emptyList(),
                description = metadata.description ?: ""
            )
            
            parseLLMBehaviorResponse(response)
        } catch (e: Exception) {
            BehaviorTemplate(emptyList(), emptyList(), emptyList())
        }
    }
    
    private fun extractAppTypeFromResponse(response: String): String? {
        val validTypes = listOf("CAMERA", "SOCIAL", "GAME", "PRODUCTIVITY", "MUSIC", "VIDEO", 
                               "SHOPPING", "NEWS", "WEATHER", "FINANCE", "HEALTH", "EDUCATION", 
                               "TRAVEL", "COMMUNICATION", "UTILITY", "OTHER")
        
        return validTypes.find { response.uppercase().contains(it) }
    }
    
    private fun parseLLMBehaviorResponse(response: String): BehaviorTemplate {
        val permissions = mutableListOf<String>()
        val apis = mutableListOf<String>()
        
        val lines = response.split("\n")
        var currentSection = ""
        
        for (line in lines) {
            when {
                line.startsWith("PERMISSIONS:", ignoreCase = true) -> {
                    currentSection = "permissions"
                    val permissionsPart = line.substringAfter(":", "").trim()
                    if (permissionsPart.isNotEmpty()) {
                        permissions.addAll(permissionsPart.split(",").map { it.trim() })
                    }
                }
                line.startsWith("APIS:", ignoreCase = true) -> {
                    currentSection = "apis"
                    val apisPart = line.substringAfter(":", "").trim()
                    if (apisPart.isNotEmpty()) {
                        apis.addAll(apisPart.split(",").map { it.trim() })
                    }
                }
                currentSection == "permissions" && line.trim().isNotEmpty() -> {
                    permissions.addAll(line.split(",").map { it.trim() })
                }
                currentSection == "apis" && line.trim().isNotEmpty() -> {
                    apis.addAll(line.split(",").map { it.trim() })
                }
            }
        }
        
        return BehaviorTemplate(permissions, apis, emptyList())
    }
    
    data class BehaviorTemplate(
        val permissions: List<String>,
        val apis: List<String>,
        val corePermissions: List<String>
    )
    
    companion object {
        private val APP_TYPE_KEYWORDS = mapOf(
            "CAMERA" to listOf("camera", "photo", "picture", "selfie", "snap", "filter"),
            "SOCIAL" to listOf("chat", "social", "friend", "message", "facebook", "twitter", "instagram"),
            "GAME" to listOf("game", "play", "puzzle", "racing", "adventure", "strategy"),
            "MUSIC" to listOf("music", "audio", "player", "song", "sound"),
            "VIDEO" to listOf("video", "movie", "player", "youtube", "stream"),
            "PRODUCTIVITY" to listOf("office", "document", "pdf", "editor", "note"),
            "COMMUNICATION" to listOf("call", "sms", "email", "messenger", "whatsapp"),
            "FINANCE" to listOf("bank", "money", "payment", "wallet", "finance"),
            "HEALTH" to listOf("health", "fitness", "medical", "doctor", "exercise"),
            "SHOPPING" to listOf("shop", "buy", "store", "cart", "purchase", "amazon"),
            "UTILITY" to listOf("utility", "tool", "manager", "cleaner", "battery")
        )
        
        private val BEHAVIOR_TEMPLATES = mapOf(
            "CAMERA" to BehaviorTemplate(
                permissions = listOf("CAMERA", "WRITE_EXTERNAL_STORAGE", "READ_MEDIA_IMAGES"),
                apis = listOf("android.hardware.Camera", "MediaStore", "BitmapFactory"),
                corePermissions = listOf("CAMERA")
            ),
            "SOCIAL" to BehaviorTemplate(
                permissions = listOf("INTERNET", "READ_CONTACTS", "WRITE_EXTERNAL_STORAGE", "CAMERA"),
                apis = listOf("HttpURLConnection", "ContactsContract", "MediaStore"),
                corePermissions = listOf("INTERNET")
            ),
            "GAME" to BehaviorTemplate(
                permissions = listOf("INTERNET", "WRITE_EXTERNAL_STORAGE", "READ_MEDIA_AUDIO"),
                apis = listOf("MediaPlayer", "SoundPool", "HttpURLConnection"),
                corePermissions = emptyList()
            ),
            "COMMUNICATION" to BehaviorTemplate(
                permissions = listOf("SEND_SMS", "CALL_PHONE", "READ_CONTACTS", "INTERNET"),
                apis = listOf("SmsManager", "TelecomManager", "ContactsContract"),
                corePermissions = listOf("SEND_SMS", "CALL_PHONE")
            ),
            "OTHER" to BehaviorTemplate(
                permissions = listOf("INTERNET"),
                apis = emptyList(),
                corePermissions = emptyList()
            )
        )
    }
}