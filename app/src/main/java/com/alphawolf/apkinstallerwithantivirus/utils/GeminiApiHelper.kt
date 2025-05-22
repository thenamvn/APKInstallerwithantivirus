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
            
            Dựa trên kiến thức của bạn về các ứng dụng di động phổ biến, hãy xác định:
            
            1. Liệu tên và mô tả ứng dụng có phù hợp với một ứng dụng chính thống hoặc đã biết không
            2. Liệu các quyền yêu cầu có phù hợp với loại ứng dụng này không
            3. Liệu có quyền nào KHÔNG cần thiết cho chức năng cốt lõi của ứng dụng này không
            
            Lưu ý:
            - Số lượng quyền không phải là yếu tố quyết định; nhiều ứng dụng hợp pháp cần nhiều quyền
            - Các ứng dụng yêu cầu ít quyền, đặc biệt là quyền nguy hiểm thì thường đa số là an toàn
            - Ứng dụng camera cần quyền camera, ứng dụng bản đồ cần quyền vị trí, v.v. là bình thường
            - Kết hợp bất thường của các quyền mới đáng ngờ (ví dụ: ứng dụng đèn pin yêu cầu quyền SMS)
            - Một vài quyền nguy hiểm không nhất thiết có nghĩa là ứng dụng độc hại
            - [1] : Các ứng dụng chơi game hay ứng dụng mạng xã hội hoặc ứng dụng liên quan đến chụp ảnh, chỉnh sửa ảnh thường yêu cầu các quyền như quyền truy cập bộ nhớ (chẳng hạn như READ_MEDIA_AUDIO,READ_MEDIA_VIDEO,READ_MEDIA_IMAGES), ứng dụng game có thể yêu cầu cái đấy để share lại thành tích chơi game bằng cách lưu ảnh thành tích game vào bộ nhớ chẳng hạn. Do đó đa số không nguy hiểm
            - Các ứng dụng kiểu như [1], có thể dùng quyền READ_MEDIA_AUDIO,READ_MEDIA_VIDEO,READ_MEDIA_IMAGES để render đồ họa như hiệu ứng game, đồ họa hình ảnh từ data game lưu trong bộ nhớ. WRITE_EXTERNAL_STORAGE ứng dụng có thể dùng để lưu dữ liệu userdata của game hay ứng dụng vào bộ nhớ. Do đó đa số thường không nguy hiểm
            
            Hãy đánh giá và phân loại ứng dụng vào MỘT trong hai nhóm:
            AN TOÀN: Nếu tên ứng dụng có vẻ chính thống và các quyền phù hợp với chức năng
            NGUY HIỂM: Nếu có dấu hiệu đáng ngờ rõ ràng (tên không rõ ràng, quyền không phù hợp)
            
            Định dạng phản hồi của bạn như sau:
            MỨC ĐỘ RỦI RO: [AN TOÀN/NGUY HIỂM]
            PHÂN TÍCH: [giải thích ngắn gọn về lý do đánh giá, đặc biệt là mối liên hệ giữa tên ứng dụng và các quyền]
            CÁC VẤN ĐỀ CHÍNH: [nếu có, liệt kê các quyền không phù hợp với chức năng dự kiến của ứng dụng]
            
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