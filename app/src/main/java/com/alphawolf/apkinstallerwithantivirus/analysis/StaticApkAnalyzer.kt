package com.alphawolf.apkinstallerwithantivirus.analysis

import android.content.Context
import android.content.pm.PackageManager
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import java.io.File

class StaticApkAnalyzer(private val context: Context) {
    
    fun analyze(apkPath: String): StaticAnalysisData {
        val metadata = extractMetadata(apkPath)
        val permissions = extractPermissions(apkPath)
        val apiCalls = extractApiCalls(apkPath)
        val obfuscationSignals = detectObfuscation(apkPath)
        val exportedComponents = extractExportedComponents(apkPath)
        
        return StaticAnalysisData(
            appMetadata = metadata,
            actualBehavior = ActualBehavior(
                permissions = permissions,
                apiCalls = apiCalls,
                obfuscationSignals = obfuscationSignals,
                exportedComponents = exportedComponents
            )
        )
    }
    
    private fun extractMetadata(apkPath: String): AppMetadata {
        val packageInfo = context.packageManager.getPackageArchiveInfo(
            apkPath,
            PackageManager.GET_META_DATA
        )
        
        return AppMetadata(
            appName = packageInfo?.applicationInfo?.loadLabel(context.packageManager)?.toString() ?: "Unknown",
            packageName = packageInfo?.packageName ?: "Unknown",
            description = packageInfo?.applicationInfo?.loadDescription(context.packageManager)?.toString(),
            versionName = packageInfo?.versionName
        )
    }
    
    private fun extractPermissions(apkPath: String): List<String> {
        val packageInfo = context.packageManager.getPackageArchiveInfo(
            apkPath,
            PackageManager.GET_PERMISSIONS
        )
        return packageInfo?.requestedPermissions?.toList() ?: emptyList()
    }
    
    private fun extractApiCalls(apkPath: String): List<String> {
        val apiCalls = mutableSetOf<String>()
        
        try {
            val dexFile = DexFileFactory.loadDexFile(File(apkPath), Opcodes.getDefault())
            
            for (classDef in dexFile.classes) {
                classDef.methods.forEach { method ->
                    method.implementation?.instructions?.forEach { instruction ->
                        val instructionStr = instruction.toString()
                        
                        // Extract API calls
                        API_PATTERNS.forEach { pattern ->
                            if (instructionStr.contains(pattern)) {
                                apiCalls.add(extractApiName(instructionStr, pattern))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Log error but continue
        }
        
        return apiCalls.toList()
    }
    
    private fun detectObfuscation(apkPath: String): List<String> {
        val signals = mutableListOf<String>()
        
        try {
            val dexFile = DexFileFactory.loadDexFile(File(apkPath), Opcodes.getDefault())
            
            for (classDef in dexFile.classes) {
                classDef.methods.forEach { method ->
                    method.implementation?.instructions?.forEach { instruction ->
                        val instructionStr = instruction.toString()
                        
                        OBFUSCATION_PATTERNS.forEach { pattern ->
                            if (instructionStr.contains(pattern)) {
                                signals.add("Detected: $pattern")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Log error but continue
        }
        
        return signals
    }
    
    private fun extractExportedComponents(apkPath: String): List<String> {
        // TODO: Parse AndroidManifest.xml to find exported components
        return emptyList()
    }
    
    private fun extractApiName(instruction: String, pattern: String): String {
        // Extract clean API name from instruction
        return instruction.substringAfter(pattern).substringBefore("(").trim()
    }
    
    companion object {
        private val API_PATTERNS = listOf(
            "Landroid/telephony/SmsManager",
            "Landroid/telephony/TelephonyManager", 
            "Landroid/location/LocationManager",
            "Landroid/media/MediaRecorder",
            "Landroid/hardware/Camera",
            "Ljavax/crypto",
            "Landroid/content/ContentResolver",
            "sendTextMessage",
            "getDeviceId",
            "getLastKnownLocation",
            "startRecording"
        )
        
        private val OBFUSCATION_PATTERNS = listOf(
            "DexClassLoader",
            "Method.invoke",
            "Base64.decode",
            "Cipher",
            "javax.crypto.Cipher",
            "java.lang.reflect.Method"
        )
    }
}