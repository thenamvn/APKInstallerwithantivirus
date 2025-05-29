package com.alphawolf.apkinstallerwithantivirus.analysis

class MismatchAnalyzer {
    
    fun findMismatches(
        actualBehavior: ActualBehavior,
        expectedBehavior: ExpectedBehavior
    ): List<Mismatch> {
        val mismatches = mutableListOf<Mismatch>()
        
        // 3.1 & 3.2: Find suspicious permissions
        mismatches.addAll(findSuspiciousPermissions(actualBehavior, expectedBehavior))
        
        // Find missing critical permissions
        mismatches.addAll(findMissingPermissions(actualBehavior, expectedBehavior))
        
        // Find suspicious APIs
        mismatches.addAll(findSuspiciousApis(actualBehavior, expectedBehavior))
        
        // Check obfuscation
        mismatches.addAll(analyzeObfuscation(actualBehavior))
        
        return mismatches
    }
    
    private fun findSuspiciousPermissions(
        actual: ActualBehavior,
        expected: ExpectedBehavior
    ): List<Mismatch> {
        val suspicious = actual.permissions.filter { permission ->
            !expected.expectedPermissions.contains(permission) && 
            DANGEROUS_PERMISSIONS.contains(permission)
        }
        
        return suspicious.map { permission ->
            Mismatch(
                type = MismatchType.SUSPICIOUS_PERMISSION,
                description = "$permission là quyền không phù hợp với loại app ${expected.appType}",
                severity = 2
            )
        }
    }
    
    private fun findMissingPermissions(
        actual: ActualBehavior,
        expected: ExpectedBehavior
    ): List<Mismatch> {
        val missing = expected.corePermissions.filter { permission ->
            !actual.permissions.contains(permission)
        }
        
        return missing.map { permission ->
            Mismatch(
                type = MismatchType.MISSING_PERMISSION,
                description = "Thiếu quyền quan trọng: $permission",
                severity = -1
            )
        }
    }
    
    private fun findSuspiciousApis(
        actual: ActualBehavior,
        expected: ExpectedBehavior
    ): List<Mismatch> {
        val suspicious = actual.apiCalls.filter { api ->
            !expected.expectedApis.any { expectedApi -> api.contains(expectedApi, ignoreCase = true) } &&
            DANGEROUS_APIS.any { dangerousApi -> api.contains(dangerousApi, ignoreCase = true) }
        }
        
        return suspicious.map { api ->
            Mismatch(
                type = MismatchType.SUSPICIOUS_API,
                description = "API lệch chức năng: $api",
                severity = 1
            )
        }
    }
    
    private fun analyzeObfuscation(actual: ActualBehavior): List<Mismatch> {
        return actual.obfuscationSignals.map { signal ->
            Mismatch(
                type = MismatchType.OBFUSCATION_DETECTED,
                description = "Dấu hiệu obfuscation: $signal",
                severity = 2
            )
        }
    }
    
    companion object {
        private val DANGEROUS_PERMISSIONS = listOf(
            "SEND_SMS", "CALL_PHONE", "READ_CONTACTS", "READ_SMS", "WRITE_SMS",
            "RECORD_AUDIO", "CAMERA", "ACCESS_FINE_LOCATION", "READ_PHONE_STATE",
            "INSTALL_PACKAGES", "REQUEST_INSTALL_PACKAGES", "GET_ACCOUNTS",
            "BIND_ACCESSIBILITY_SERVICE", "WRITE_SETTINGS", "RECEIVE_BOOT_COMPLETED"
        )
        
        private val DANGEROUS_APIS = listOf(
            "sendTextMessage", "getDeviceId", "getLastKnownLocation",
            "startRecording", "Method.invoke", "DexClassLoader"
        )
    }
}