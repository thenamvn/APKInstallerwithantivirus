package com.alphawolf.apkinstallerwithantivirus.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GeminiApiHelper {
    suspend fun analyzeWithGemini(
        apiKey: String,
        appName: String,
        permissions: List<String>,
        description: String?,
        packageName: String
    ): String = withContext(Dispatchers.IO) {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-04-17:generateContent?key=$apiKey")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        // Improved prompt with clear structure, security focus, and Vietnamese output
        val prompt = """
        Bạn là chuyên gia phân tích an ninh ứng dụng Android. Hãy phân tích ứng dụng dưới đây:

        =====================
        THÔNG TIN ỨNG DỤNG
        =====================
        - TÊN ỨNG DỤNG: $appName
        - PACKAGE NAME: $packageName
        - QUYỀN TRUY CẬP: ${permissions.joinToString(", ")}
        ${if (!description.isNullOrBlank()) "- MÔ TẢ: $description" else ""}

        =====================
        YÊU CẦU PHÂN TÍCH
        =====================
        Dựa trên kiến thức của bạn về các ứng dụng phổ biến, hãy:
        1. Phân loại loại ứng dụng này có thể là gì (game, camera, công cụ, xã hội…)
        2. Đánh giá liệu tên, package name và mô tả có khớp với loại ứng dụng đó không
        3. Đánh giá các quyền có phù hợp với chức năng dự kiến không
        4. Xác định các quyền KHÔNG cần thiết cho chức năng chính

        =====================
        HƯỚNG DẪN ĐÁNH GIÁ
        =====================
        - Các quyền bất thường chỉ đáng lo nếu trái với chức năng dự kiến
        - Ứng dụng chỉnh sửa ảnh, game… có thể hợp lý khi dùng quyền lưu trữ, đọc media
        - Các ứng dụng “nhẹ” như đèn pin, máy tính không nên yêu cầu quyền gửi SMS hay đọc danh bạ
        - Package name đáng tin cậy thường trùng với tên nhà phát triển thật

        =====================
        PHẢN HỒI THEO MẪU SAU
        =====================

        MỨC ĐỘ RỦI RO: [AN TOÀN/NGUY HIỂM]  
        PHÂN LOẠI LOẠI ỨNG DỤNG: [...]  
        PHÂN TÍCH: [...]  
        CÁC VẤN ĐỀ CHÍNH: [nếu có, liệt kê các quyền bất thường]

        Trả lời hoàn toàn bằng tiếng Việt.
        """.trimIndent()

        val requestBody = JSONObject(
            mapOf(
                "contents" to listOf(
                    mapOf("parts" to listOf(mapOf("text" to prompt)))
                ),
                "generationConfig" to mapOf(
                    "temperature" to 0.2
                )
            )
        ).toString()

        OutputStreamWriter(connection.outputStream).use { it.write(requestBody) }

        try {
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonResponse = JSONObject(connection.inputStream.bufferedReader().readText())
                val content = jsonResponse.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                
                content // This will only return the AI's response, not the prompt
            } else {
                val errorStream = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                "Phân tích thất bại (mã lỗi $responseCode): Vui lòng thử lại sau"
            }
        } catch (e: Exception) {
            "Lỗi phân tích: ${e.message}"
        } finally {
            connection.disconnect()
        }
    }
}