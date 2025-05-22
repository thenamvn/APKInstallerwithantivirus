package com.alphawolf.apkinstallerwithantivirus.batch

import android.content.Context
import android.net.Uri
import com.alphawolf.apkinstallerwithantivirus.BuildConfig
import com.alphawolf.apkinstallerwithantivirus.utils.ApkAnalyzer
import com.alphawolf.apkinstallerwithantivirus.utils.GeminiApiHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Class to analyze multiple APK files in batch and export results to CSV
 */
class BatchApkAnalyzer(private val context: Context) {

    // Risk levels enum for clarity
    enum class RiskLevel(val label: String) {
        SAFE("SAFE"),
        DANGEROUS("DANGEROUS"),
        UNKNOWN("UNKNOWN")
    }

    companion object {
        // Map Vietnamese risk labels to enum
        private val RISK_LEVEL_MAPPING = mapOf(
            "AN TOÀN" to RiskLevel.SAFE,
            "NGUY HIỂM" to RiskLevel.DANGEROUS
        )
    }

    /**
     * Batch analyzes APK files in a directory based on categories
     * and generates CSV reports with results
     */
    suspend fun analyzeDatasetAndGenerateReport(
        datasetRootPath: String,
        outputPath: String
    ): String = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outputDir = File(outputPath).apply { mkdirs() }

        try {
            // Quét thư mục và kiểm tra cấu trúc TRƯỚC KHI tạo tệp
            val datasetEntries = scanDatasetFolder(datasetRootPath)

            if (datasetEntries.isEmpty()) {
                return@withContext "Không tìm thấy APK trong thư mục dataset. " +
                        "Vui lòng kiểm tra cấu trúc thư mục (yêu cầu thư mục safe và malware) " +
                        "và đảm bảo đã đặt các file APK vào thư mục tương ứng."
            }

            // Phân tích các APK và chuẩn bị dữ liệu kết quả (TRƯỚC KHI tạo file)
            val analysisResults = analyzeBatch(datasetEntries)

            if (analysisResults.isEmpty()) {
                return@withContext "Không thể phân tích APK. Vui lòng kiểm tra kết nối mạng và API key Gemini."
            }

            // Sau khi phân tích thành công, tạo các tệp CSV
            val datasetFile = File(outputDir, "dataset_info_$timestamp.csv")
            createDatasetCSV(datasetFile, datasetEntries)

            val resultsFile = File(outputDir, "analysis_results_$timestamp.csv")
            createResultsCSV(resultsFile, datasetEntries, analysisResults)

            // Tạo Python script chỉ khi có dữ liệu phân tích
            val pythonScript = generatePythonScript(datasetFile.absolutePath, resultsFile.absolutePath, outputDir.absolutePath)
            File(outputDir, "calculate_metrics_$timestamp.py").writeText(pythonScript)

            return@withContext "Phân tích hoàn tất!\n" +
                    "Đã phân tích: ${datasetEntries.size} APK\n" +
                    "Dataset: ${datasetFile.absolutePath}\n" +
                    "Kết quả: ${resultsFile.absolutePath}"
        } catch (e: Exception) {
            return@withContext "Lỗi: ${e.message}"
        }
    }

    // Phương thức mới để tách phần phân tích APK từ việc ghi file
    private suspend fun analyzeBatch(
        entries: List<DatasetEntry>
    ): Map<String, AnalysisResult> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, AnalysisResult>()

        // Xử lý theo lô để tránh quá tải hệ thống
        val batchSize = 3
        val total = entries.size

        for (i in entries.indices step batchSize) {
            val endIndex = minOf(i + batchSize, entries.size)
            val batch = entries.subList(i, endIndex)

            // Xử lý đồng thời trong lô
            val batchResults = batch.map { entry ->
                async(Dispatchers.IO) {
                    try {
                        val result = analyzeApk(entry.apkPath)
                        println("Đã xử lý ${entries.indexOf(entry) + 1}/$total: ${entry.fileName}")
                        entry.apkPath to result
                    } catch (e: Exception) {
                        println("Lỗi phân tích ${entry.fileName}: ${e.message}")
                        null
                    }
                }
            }.awaitAll().filterNotNull().toMap()

            results.putAll(batchResults)
        }

        return@withContext results
    }

    // Phương thức mới để tạo file CSV kết quả sau khi đã có phân tích thành công
    private fun createResultsCSV(
        file: File,
        entries: List<DatasetEntry>,
        results: Map<String, AnalysisResult>
    ) {
        FileWriter(file).use { writer ->
            writer.append("APK_PATH,FILENAME,GROUND_TRUTH_LABEL,PREDICTED_LABEL,AI_RISK_LEVEL,")
                .append("DANGEROUS_PERMISSIONS,ANALYSIS_SUMMARY\n")

            entries.forEach { entry ->
                val result = results[entry.apkPath] ?: return@forEach
                val csvLine = "${entry.apkPath},${entry.fileName},${entry.groundTruthLabel},"
                    .plus("${result.predictedLabel},${result.riskLevel.label},")
                    .plus("\"${result.dangerousPermissions.joinToString(";")}\",")
                    .plus("\"${result.summary.replace("\"", "'").replace("\n", " ")}\"")

                writer.append(csvLine).append('\n')
            }
        }
    }
    /**
     * Scans dataset directory and returns all APK files with their ground truth labels
     */
    private fun scanDatasetFolder(rootPath: String): List<DatasetEntry> {
        val root = File(rootPath)
        if (!root.exists() || !root.isDirectory) {
            throw IllegalArgumentException("Invalid dataset directory: $rootPath")
        }

        val entries = mutableListOf<DatasetEntry>()

        // Each subdirectory is a category (safe, malware, etc)
        root.listFiles { file -> file.isDirectory }?.forEach { categoryDir ->
            val groundTruthLabel = categoryDir.name.uppercase()

            // Get all APK files in this category
            categoryDir.listFiles { file -> file.name.endsWith(".apk", ignoreCase = true) }?.forEach { apkFile ->
                entries.add(DatasetEntry(
                    apkPath = apkFile.absolutePath,
                    fileName = apkFile.name,
                    groundTruthLabel = groundTruthLabel,
                    fileSizeKB = apkFile.length() / 1024
                ))
            }
        }

        return entries
    }

    /**
     * Creates CSV file with dataset information
     */
    private fun createDatasetCSV(file: File, entries: List<DatasetEntry>) {
        FileWriter(file).use { writer ->
            writer.append("APK_PATH,FILENAME,GROUND_TRUTH_LABEL,FILE_SIZE_KB\n")

            entries.forEach { entry ->
                writer.append("${entry.apkPath},${entry.fileName},${entry.groundTruthLabel},${entry.fileSizeKB}\n")
            }
        }
    }

    /**
     * Analyzes a single APK file and returns the analysis result
     */
    private suspend fun analyzeApk(apkPath: String): AnalysisResult = withContext(Dispatchers.IO) {
        val analyzer = ApkAnalyzer(context)
        val apkFile = File(apkPath)
        val uri = Uri.fromFile(apkFile)

        // Create temporary file for analysis
        val tempFile = analyzer.createTempFileFromUri(uri)

        try {
            // Extract app info (name, permissions)
            val appInfo = analyzer.extractAppInfo(tempFile.absolutePath)
            val appName = appInfo.appName 
            val packageName = appInfo.packageName
            val permissions = appInfo.permissions
            val description = appInfo.description

            // Identify dangerous permissions
            val dangerousPermissions = permissions.filter { permission ->
                ApkAnalyzer.SUSPICIOUS_PERMISSIONS.any {
                    permission.contains(it.replace("android.permission.", ""), ignoreCase = true)
                }
            }

            // Analyze with Gemini AI
            val aiAnalysisResult = GeminiApiHelper.analyzeWithGemini(
                apiKey = BuildConfig.GEMINI_API_KEY,
                appName = appName,
                packageName = packageName,
                permissions = permissions,
                description = description
            )

            // Extract risk level from AI analysis
            val riskLevel = extractRiskLevel(aiAnalysisResult)

            // Map risk level to predicted label
            val predictedLabel = when(riskLevel) {
                RiskLevel.DANGEROUS, RiskLevel.UNKNOWN -> "MALWARE"
                RiskLevel.SAFE -> "SAFE"
            }

            AnalysisResult(
                predictedLabel = predictedLabel,
                riskLevel = riskLevel,
                dangerousPermissions = dangerousPermissions,
                summary = aiAnalysisResult
            )
        } finally {
            // Clean up temporary file
            tempFile.delete()
        }
    }

    /**
    * Extracts risk level from AI analysis text
    */
    private fun extractRiskLevel(analysisText: String): RiskLevel {
        // Tìm kiếm theo định dạng chuẩn 
        val riskPattern = """MỨC\s+ĐỘ\s+RỦI\s+RO:\s*(AN\s+TOÀN|NGUY\s+HIỂM)""".toRegex(RegexOption.IGNORE_CASE)
        val matchResult = riskPattern.find(analysisText)
        
        if (matchResult != null) {
            val riskText = matchResult.groupValues[1].replace("\\s+".toRegex(), " ").trim()
            return when {
                riskText.equals("AN TOÀN", ignoreCase = true) -> RiskLevel.SAFE
                else -> RiskLevel.DANGEROUS // Changed from returning UNKNOWN to DANGEROUS
            }
        }
        
        // Phương pháp dự phòng: kiểm tra từng phần trong phản hồi
        val lines = analysisText.split("\n")
        for (line in lines) {
            if (line.contains("MỨC ĐỘ RỦI RO:", ignoreCase = true) || 
                line.contains("ĐÁNH GIÁ:", ignoreCase = true)) {
                return when {
                    line.contains("AN TOÀN", ignoreCase = true) -> RiskLevel.SAFE
                    else -> RiskLevel.DANGEROUS 
                }
            }
        }
        
        // Nếu không tìm thấy định dạng chuẩn, kiểm tra phần còn lại của văn bản
        return when {
            analysisText.contains("AN TOÀN", ignoreCase = true) &&
            !analysisText.contains("KHÔNG AN TOÀN", ignoreCase = true) &&
            !analysisText.contains("NGUY HIỂM", ignoreCase = true) -> RiskLevel.SAFE
            
            else -> RiskLevel.DANGEROUS // Changed from checking for "NGUY HIỂM" to default to DANGEROUS
        }
    }
    /**
     * Generates Python script for metrics calculation
     */
    private fun generatePythonScript(
        datasetPath: String,
        resultsPath: String,
        outputDir: String
    ): String {
        return """
            import pandas as pd
            import numpy as np
            from sklearn.metrics import classification_report, confusion_matrix, accuracy_score, precision_score, recall_score, f1_score
            import matplotlib.pyplot as plt
            import seaborn as sns
            
            # Load data
            dataset_df = pd.read_csv("$datasetPath")
            results_df = pd.read_csv("$resultsPath")
            
            # Calculate metrics
            y_true = results_df['GROUND_TRUTH_LABEL']
            y_pred = results_df['PREDICTED_LABEL']
            
            # Basic metrics
            accuracy = accuracy_score(y_true, y_pred)
            
            # Chỉ xử lý binary classification (SAFE vs MALWARE)
            precision = precision_score(y_true, y_pred, pos_label='MALWARE')
            recall = recall_score(y_true, y_pred, pos_label='MALWARE')
            f1 = f1_score(y_true, y_pred, pos_label='MALWARE')
            
            # Generate report
            print(f"Accuracy: {accuracy:.4f}")
            print(f"Precision: {precision:.4f}")
            print(f"Recall: {recall:.4f}")
            print(f"F1 Score: {f1:.4f}")
            
            print("\nClassification Report:")
            print(classification_report(y_true, y_pred))
            
            # Create confusion matrix
            cm = confusion_matrix(y_true, y_pred)
            plt.figure(figsize=(10, 8))
            sns.heatmap(cm, annot=True, fmt='d', cmap='Blues',
                        xticklabels=['SAFE', 'MALWARE'],
                        yticklabels=['SAFE', 'MALWARE'])
            plt.title('Confusion Matrix')
            plt.xlabel('Predicted')
            plt.ylabel('Actual')
            plt.tight_layout()
            plt.savefig("$outputDir/confusion_matrix.png")
            
            # Export misclassified samples
            errors_df = results_df[results_df['GROUND_TRUTH_LABEL'] != results_df['PREDICTED_LABEL']]
            errors_df.to_csv("$outputDir/misclassified_apks.csv", index=False)
            
            print(f"\nMisclassified samples: {len(errors_df)}/{len(results_df)} ({len(errors_df)/len(results_df)*100:.2f}%)")
            
            # Summary file
            with open("$outputDir/metrics_summary.txt", "w") as f:
                f.write(f"APK Malware Detection Evaluation\n")
                f.write(f"============================\n\n")
                f.write(f"Dataset: {len(results_df)} APK files\n")
                f.write(f"Distribution: {dict(y_true.value_counts())}\n\n")
                f.write(f"Accuracy: {accuracy:.4f}\n")
                f.write(f"Precision: {precision:.4f}\n")
                f.write(f"Recall: {recall:.4f}\n")
                f.write(f"F1 Score: {f1:.4f}\n\n")
                f.write("Classification Report:\n")
                f.write(classification_report(y_true, y_pred))
            
            print(f"\nResults saved to: {outputDir}")
        """.trimIndent()
    }

    // Data classes to represent dataset entries and analysis results
    data class DatasetEntry(
        val apkPath: String,
        val fileName: String,
        val groundTruthLabel: String,
        val fileSizeKB: Long
    )

    data class AnalysisResult(
        val predictedLabel: String,
        val riskLevel: RiskLevel,
        val dangerousPermissions: List<String>,
        val summary: String
    )
}