package com.alphawolf.apkinstallerwithantivirus.analysis

class RiskCalculator {
    
    fun calculateRisk(mismatches: List<Mismatch>): RiskResult {
        var score = 0
        val factors = mutableListOf<String>()
        
        mismatches.forEach { mismatch ->
            score += mismatch.severity
            factors.add(mismatch.description)
        }
        
        val level = when {
            score <= 0 -> SemanticMismatchDetector.RiskLevel.SAFE
            score in 1..2 -> SemanticMismatchDetector.RiskLevel.MEDIUM
            else -> SemanticMismatchDetector.RiskLevel.DANGEROUS
        }
        
        return RiskResult(score, level, factors)
    }
}