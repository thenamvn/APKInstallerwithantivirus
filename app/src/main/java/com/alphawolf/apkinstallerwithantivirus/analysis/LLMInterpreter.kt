package com.alphawolf.apkinstallerwithantivirus.analysis

import com.alphawolf.apkinstallerwithantivirus.BuildConfig
import com.alphawolf.apkinstallerwithantivirus.utils.GeminiApiHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LLMInterpreter {
    
    suspend fun interpret(
        metadata: AppMetadata,
        actualBehavior: ActualBehavior,
        expectedBehavior: ExpectedBehavior,
        mismatches: List<Mismatch>,
        riskResult: RiskResult
    ): String = withContext(Dispatchers.IO) {
        
        val prompt = buildPrompt(metadata, actualBehavior, expectedBehavior, mismatches, riskResult)
        
        try {
            GeminiApiHelper.analyzeWithGemini(
                apiKey = BuildConfig.GEMINI_API_KEY,
                appName = metadata.appName,
                packageName = metadata.packageName,
                permissions = actualBehavior.permissions,
                description = metadata.description ?: ""
            )
        } catch (e: Exception) {
            "Lỗi phân tích LLM: ${e.message}"
        }
    }
    
    private fun buildPrompt(
        metadata: AppMetadata,
        actualBehavior: ActualBehavior,
        expectedBehavior: ExpectedBehavior,
        mismatches: List<Mismatch>,
        riskResult: RiskResult
    ): String {
        return """
            📱 TÊN ỨNG DỤNG: ${metadata.appName}
            📄 MÔ TẢ: ${metadata.description ?: "Không có"}
            🔍 LOẠI APP DỰ KIẾN: ${expectedBehavior.appType}
            
            🔐 QUYỀN THỰC TẾ: ${actualBehavior.permissions.joinToString(", ")}
            ✅ QUYỀN DỰ KIẾN: ${expectedBehavior.expectedPermissions.joinToString(", ")}
            
            ⚙️ API THỰC TẾ: ${actualBehavior.apiCalls.joinToString(", ")}
            ✅ API DỰ KIẾN: ${expectedBehavior.expectedApis.joinToString(", ")}
            
            ${if (actualBehavior.obfuscationSignals.isNotEmpty()) 
                "🧠 DẤU HIỆU OBFUSCATION: ${actualBehavior.obfuscationSignals.joinToString(", ")}" 
              else ""}
            
            🎯 RISK SCORE: ${riskResult.score}
            📋 CÁC VẤN ĐỀ PHÁT HIỆN:
            ${riskResult.factors.joinToString("\n") { "- $it" }}
            
            📌 Hãy đánh giá tính hợp lý giữa chức năng dự kiến và hành vi thực tế của ứng dụng này.
            Phân tích xem các quyền và API có phù hợp với loại ứng dụng không.
            
            Định dạng phản hồi:
            MỨC ĐỘ RỦI RO: [AN TOÀN/TRUNG BÌNH/NGUY HIỂM]
            GIẢI THÍCH: [phân tích chi tiết về sự phù hợp/lệch chuẩn]
            CÁC VẤN ĐỀ CHÍNH: [liệt kê các vấn đề nghiêm trọng nếu có]
        """.trimIndent()
    }
}