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
        description: String?
    ): String = withContext(Dispatchers.IO) {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-04-17:generateContent?key=$apiKey")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        // Improved prompt with clear structure, security focus, and Vietnamese output
        val prompt = """
            Hãy phân tích ứng dụng Android dưới đây với vai trò là chuyên gia bảo mật di động:
            
            TÊN ỨNG DỤNG: $appName
            QUYỀN TRUY CẬP: ${permissions.joinToString(", ")}
            ${if (!description.isNullOrBlank()) "MÔ TẢ: $description" else ""}
            
            Hãy phân tích mức độ an toàn của ứng dụng này dựa trên tên và quyền truy cập nó yêu cầu.
            
            Yêu cầu:
            1. Xác định xem các quyền yêu cầu có phù hợp với chức năng mà ứng dụng có thể thực hiện dựa trên tên của nó không
            2. Xác định bất kỳ quyền nguy hiểm nào có thể bị lạm dụng
            3. Đưa ra đánh giá rủi ro trong MỘT trong các danh mục sau: AN TOÀN, CÓ THỂ NGUY HIỂM, hoặc NGUY HIỂM
            4. Đưa ra lời giải thích ngắn gọn, tập trung vào các quyền đáng lo ngại nhất
            5. Phản hồi ngắn gọn nhưng đầy đủ thông tin, tránh dài dòng
            
            Định dạng phản hồi của bạn như sau:
            MỨC ĐỘ RỦI RO: [mức độ]
            PHÂN TÍCH: [giải thích ngắn gọn]
            CÁC VẤN ĐỀ CHÍNH: [danh sách các quyền đáng lo ngại nhất]
            
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