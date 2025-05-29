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
    }    /**
     * Analyze multiple APKs in a single batch request to reduce API calls
     */
    suspend fun analyzeBatchWithGemini(
        apiKey: String,
        apkInfoList: List<ApkBatchInfo>
    ): List<String> = withContext(Dispatchers.IO) {
        if (apkInfoList.isEmpty()) return@withContext emptyList()
        
        val startTime = System.currentTimeMillis()
        println("⚡ Starting batch analysis of ${apkInfoList.size} APKs...")
        
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-04-17:generateContent?key=$apiKey")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        // Create batch prompt for multiple APKs
        val batchPrompt = createBatchPrompt(apkInfoList)

        val requestBody = JSONObject(
            mapOf(
                "contents" to listOf(
                    mapOf("parts" to listOf(mapOf("text" to batchPrompt)))
                ),                "generationConfig" to mapOf(
                    "temperature" to 0.1, // Lower temperature for more consistent batch results
                    "maxOutputTokens" to 16384 // Increased token limit for larger batches
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
                  // Parse batch response into individual results
                val results = parseBatchResponse(content, apkInfoList.size)
                val endTime = System.currentTimeMillis()
                val duration = (endTime - startTime) / 1000.0
                println("✅ Batch analysis completed in ${duration}s for ${apkInfoList.size} APKs (${String.format("%.2f", duration.toDouble() / apkInfoList.size)}s per APK)")
                results
            } else {
                val errorStream = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                // Return error for each APK in batch
                List(apkInfoList.size) { "Phân tích batch thất bại (mã lỗi $responseCode): $errorStream" }
            }
        } catch (e: Exception) {
            // Return error for each APK in batch
            List(apkInfoList.size) { "Lỗi phân tích batch: ${e.message}" }
        } finally {
            connection.disconnect()
        }
    }

    private fun createBatchPrompt(apkInfoList: List<ApkBatchInfo>): String {
        val apkDescriptions = apkInfoList.mapIndexed { index, apkInfo ->
            """
            APK ${index + 1}:
            - TÊN: ${apkInfo.appName}
            - PACKAGE: ${apkInfo.packageName}
            - QUYỀN: ${apkInfo.permissions.joinToString(", ")}
            ${if (!apkInfo.description.isNullOrBlank()) "- MÔ TẢ: ${apkInfo.description}" else ""}
            """.trimIndent()
        }.joinToString("\n\n" + "=".repeat(50) + "\n\n")

        return """
        Bạn là chuyên gia phân tích an ninh ứng dụng Android. Hãy phân tích TỪNG ứng dụng dưới đây:

        $apkDescriptions

        =====================
        YÊU CẦU PHÂN TÍCH BATCH
        =====================
        
        Với mỗi APK, hãy:
        1. Phân loại loại ứng dụng (game, camera, công cụ, xã hội...)
        2. Đánh giá tên và package có khớp với chức năng không
        3. Đánh giá quyền có phù hợp với chức năng dự kiến không
        4. Xác định quyền KHÔNG cần thiết

        =====================
        FORMAT PHẢN HỒI CHO TỪNG APK
        =====================
        
        APK_1_RESULT:
        MỨC ĐỘ RỦI RO: [AN TOÀN/NGUY HIỂM]
        PHÂN LOẠI: [loại ứng dụng]
        PHÂN TÍCH: [đánh giá ngắn gọn]
        VẤN ĐỀ: [liệt kê quyền bất thường nếu có]

        APK_2_RESULT:
        MỨC ĐỘ RỦI RO: [AN TOÀN/NGUY HIỂM]
        PHÂN LOẠI: [loại ứng dụng]
        PHÂN TÍCH: [đánh giá ngắn gọn]
        VẤN ĐỀ: [liệt kê quyền bất thường nếu có]

        [tiếp tục cho tất cả APK...]

        Hãy giữ phân tích ngắn gọn và tập trung vào các quyền bất thường.
        Trả lời hoàn toàn bằng tiếng Việt.
        """.trimIndent()
    }

    private fun parseBatchResponse(response: String, expectedCount: Int): List<String> {
        val results = mutableListOf<String>()
        
        // Split response by APK_X_RESULT pattern
        val apkResults = response.split(Regex("APK_\\d+_RESULT:"))
            .drop(1) // Remove the first empty element
            .map { it.trim() }

        // If we got the expected number of results, use them
        if (apkResults.size >= expectedCount) {
            results.addAll(apkResults.take(expectedCount))
        } else {
            // Fallback: try to split by common patterns
            val fallbackResults = response.split(Regex("(?=MỨC ĐỘ RỦI RO:)"))
                .filter { it.contains("MỨC ĐỘ RỦI RO:") }
                .map { it.trim() }

            if (fallbackResults.size >= expectedCount) {
                results.addAll(fallbackResults.take(expectedCount))
            } else {
                // Last resort: repeat the full response or create error messages
                repeat(expectedCount) { index ->
                    if (index < fallbackResults.size) {
                        results.add(fallbackResults[index])
                    } else {
                        results.add("MỨC ĐỘ RỦI RO: NGUY HIỂM\nPHÂN TÍCH: Không thể phân tích do lỗi parse response")
                    }
                }
            }
        }

        return results
    }

    data class ApkBatchInfo(
        val appName: String,
        val packageName: String,
        val permissions: List<String>,
        val description: String?
    )
}