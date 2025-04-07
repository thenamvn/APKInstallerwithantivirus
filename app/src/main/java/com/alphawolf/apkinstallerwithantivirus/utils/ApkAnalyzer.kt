package com.alphawolf.apkinstallerwithantivirus.utils

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.iface.ClassDef
import java.io.File

class ApkAnalyzer(private val context: Context) {

    companion object {
        private val SUSPICIOUS_PERMISSIONS = listOf(
            "android.permission.SEND_SMS",
            "android.permission.CALL_PHONE",
            "android.permission.READ_CONTACTS",
            "android.permission.READ_SMS",
            "android.permission.WRITE_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.RECORD_AUDIO",
            "android.permission.CAMERA",
            "android.permission.READ_PHONE_STATE",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION"
        )

        private val SUSPICIOUS_APIS = listOf(
            "Landroid/telephony/SmsManager",
            "Landroid/telephony/TelephonyManager",
            "Landroid/location/LocationManager",
            "Landroid/media/MediaRecorder",
            "Landroid/hardware/Camera",
            "Ljavax/crypto",
            "Landroid/content/ContentResolver"
        )
    }

    fun analyzeApk(uri: Uri): List<String> {
        val results = mutableListOf<String>()
        
        try {
            // Create temporary file to analyze
            val tempFile = createTempFileFromUri(uri)
            
            // Analyze APK permissions
            results.addAll(analyzePermissions(tempFile.absolutePath))
            
            // Analyze DEX files for suspicious API usage
            results.addAll(analyzeDexFiles(tempFile))
            
            // Clean up
            tempFile.delete()
            
        } catch (e: Exception) {
            results.add("Error analyzing APK: ${e.message}")
        }
        
        return results
    }

    private fun createTempFileFromUri(uri: Uri): File {
        val tempFile = File.createTempFile("analysis_", ".apk", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    private fun analyzePermissions(apkPath: String): List<String> {
        val results = mutableListOf<String>()
        val packageInfo = context.packageManager.getPackageArchiveInfo(
            apkPath,
            PackageManager.GET_PERMISSIONS
        )

        val permissions = packageInfo?.requestedPermissions ?: emptyArray()
        val suspiciousPermissions = permissions.filter { permission ->
            SUSPICIOUS_PERMISSIONS.any { it == permission }
        }

        if (suspiciousPermissions.isNotEmpty()) {
            results.add("SUSPICIOUS: Found potentially dangerous permissions:")
            suspiciousPermissions.forEach { permission ->
                results.add("- $permission")
            }
        } else {
            results.add("No suspicious permissions found")
        }

        return results
    }

    private fun analyzeDexFiles(apkFile: File): List<String> {
        val results = mutableListOf<String>()
        var suspiciousApisFound = false

        try {
            val dexFile = DexFileFactory.loadDexFile(apkFile, Opcodes.getDefault())
            
            for (classDef in dexFile.classes) {
                if (containsSuspiciousAPIs(classDef)) {
                    suspiciousApisFound = true
                    results.add("SUSPICIOUS: Found potentially dangerous API usage in ${classDef.type}")
                }
            }

            if (!suspiciousApisFound) {
                results.add("No suspicious API usage found")
            }

        } catch (e: Exception) {
            results.add("Error analyzing DEX files: ${e.message}")
        }

        return results
    }

    private fun containsSuspiciousAPIs(classDef: ClassDef): Boolean {
        // Check class name
        if (SUSPICIOUS_APIS.any { classDef.type.startsWith(it) }) {
            return true
        }

        // Check method calls
        classDef.methods.forEach { method ->
            method.implementation?.instructions?.forEach { instruction ->
                val instructionString = instruction.toString()
                if (SUSPICIOUS_APIS.any { instructionString.contains(it) }) {
                    return true
                }
            }
        }

        return false
    }
} 