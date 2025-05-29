package com.alphawolf.apkinstallerwithantivirus.analysis

data class StaticAnalysisData(
    val appMetadata: AppMetadata,
    val actualBehavior: ActualBehavior
)

data class AppMetadata(
    val appName: String,
    val packageName: String,
    val description: String?,
    val versionName: String?
)

data class ActualBehavior(
    val permissions: List<String>,
    val apiCalls: List<String>,
    val obfuscationSignals: List<String>,
    val exportedComponents: List<String>
)

data class ExpectedBehavior(
    val appType: String,
    val expectedPermissions: List<String>,
    val expectedApis: List<String>,
    val corePermissions: List<String> // Critical permissions for this app type
)

data class Mismatch(
    val type: MismatchType,
    val description: String,
    val severity: Int // Weight for risk calculation
)

enum class MismatchType {
    SUSPICIOUS_PERMISSION,
    MISSING_PERMISSION, 
    SUSPICIOUS_API,
    OBFUSCATION_DETECTED,
    PACKAGE_NAME_SUSPICIOUS
}

data class RiskResult(
    val score: Int,
    val level: SemanticMismatchDetector.RiskLevel,
    val factors: List<String>
)