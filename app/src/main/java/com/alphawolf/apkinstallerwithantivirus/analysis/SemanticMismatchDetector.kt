package com.alphawolf.apkinstallerwithantivirus.analysis

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Main semantic mismatch detection engine following the 5-stage pipeline
 */
class SemanticMismatchDetector(private val context: Context) {
    
    private val staticAnalyzer = StaticApkAnalyzer(context)
    private val behaviorInferencer = BehaviorInferencer()
    private val mismatchAnalyzer = MismatchAnalyzer()
    private val riskCalculator = RiskCalculator()
    private val llmInterpreter = LLMInterpreter()
    
    data class DetectionResult(
        val riskScore: Int,
        val riskLevel: RiskLevel,
        val riskFactors: List<String>,
        val llmAnalysis: String,
        val detailedReport: DetailedReport
    )
    
    enum class RiskLevel(val label: String) {
        SAFE("AN TOÀN"),
        MEDIUM("TRUNG BÌNH"), 
        DANGEROUS("NGUY HIỂM")
    }
    
    data class DetailedReport(
        val appMetadata: AppMetadata,
        val actualBehavior: ActualBehavior,
        val expectedBehavior: ExpectedBehavior,
        val mismatches: List<Mismatch>
    )
    
    suspend fun analyzeApk(apkPath: String): DetectionResult = withContext(Dispatchers.IO) {
        // Stage 1: Static APK Analysis
        val staticData = staticAnalyzer.analyze(apkPath)
        
        // Stage 2: Infer expected behavior
        val expectedBehavior = behaviorInferencer.inferExpectedBehavior(
            staticData.appMetadata
        )
        
        // Stage 3: Compare actual vs expected
        val mismatches = mismatchAnalyzer.findMismatches(
            staticData.actualBehavior, 
            expectedBehavior
        )
        
        // Stage 4: Calculate risk score
        val riskResult = riskCalculator.calculateRisk(mismatches)
        
        // Stage 5: LLM interpretation
        val llmAnalysis = llmInterpreter.interpret(
            staticData.appMetadata,
            staticData.actualBehavior,
            expectedBehavior,
            mismatches,
            riskResult
        )
        
        DetectionResult(
            riskScore = riskResult.score,
            riskLevel = riskResult.level,
            riskFactors = riskResult.factors,
            llmAnalysis = llmAnalysis,
            detailedReport = DetailedReport(
                staticData.appMetadata,
                staticData.actualBehavior,
                expectedBehavior,
                mismatches
            )
        )
    }
}