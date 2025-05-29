package com.alphawolf.apkinstallerwithantivirus
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.app.PendingIntent
import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.alphawolf.apkinstallerwithantivirus.databinding.ActivityMainBinding
import com.alphawolf.apkinstallerwithantivirus.utils.ApkAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
//
import com.alphawolf.apkinstallerwithantivirus.utils.GeminiApiHelper
import java.io.File

import com.alphawolf.apkinstallerwithantivirus.analysis.SemanticMismatchDetector
import com.alphawolf.apkinstallerwithantivirus.analysis.MismatchType

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var currentUri: Uri? = null
    private var isSuspiciousApk = false
    private var isAnalyzing = false

    companion object {
        const val ACTION_INSTALL_COMPLETE_CLOSE_ACTIVITY = "com.alphawolf.apkinstallerwithantivirus.ACTION_INSTALL_COMPLETE_CLOSE_ACTIVITY"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            currentUri?.let { analyzeApk(it) }
        } else {
            showPermissionDeniedMessage()
        }
    }

    private val closeActivityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_INSTALL_COMPLETE_CLOSE_ACTIVITY) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Reset state for new intent
        resetState()
        handleIntent(intent)
    }

    private fun resetState() {
        currentUri = null
        isSuspiciousApk = false
        isAnalyzing = false
        binding.btnInstall.isEnabled = false
        binding.btnInstall.text = "Install"
        binding.tvAnalysisResult.text = ""
        binding.progressBar.visibility = View.GONE
        binding.statusIcon.setImageResource(android.R.drawable.ic_dialog_info)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    if (uri != currentUri || !isAnalyzing) {
                        currentUri = uri
                        checkAndRequestPermission()
                    }
                }
            }
        }
    }

    private fun setupUI() {
        // Hide the select APK button since we're handling APK files directly
        binding.btnSelectApk.visibility = View.GONE

        binding.btnInstall.setOnClickListener {
            if (isSuspiciousApk) {
                showInstallWarningDialog()
            } else {
                checkInstallPermissionAndInstall()
            }
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }
        
        // Add a batch analysis button
        val batchAnalysisButton = com.google.android.material.button.MaterialButton(this).apply {
            text = "Batch Analysis"
            layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT,
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                startActivity(android.content.Intent(context, 
                    com.alphawolf.apkinstallerwithantivirus.batch.BatchAnalysisActivity::class.java))
            }
        }
        
        // Add the button to your layout - assuming you're using a ConstraintLayout
        val rootLayout = binding.root as? androidx.constraintlayout.widget.ConstraintLayout
        rootLayout?.addView(batchAnalysisButton)
        
        // Position the button - you'll need to adjust these constraints based on your layout
        val params = batchAnalysisButton.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        params.topToTop = binding.btnCancel.id
        params.endToStart = binding.btnCancel.id
        params.bottomToBottom = binding.btnCancel.id
        params.marginEnd = 16
        batchAnalysisButton.layoutParams = params
    }

    private fun showInstallWarningDialog() {
        AlertDialog.Builder(this)
            .setTitle("Warning")
            .setMessage("This APK contains potentially dangerous permissions or code. Are you sure you want to install it?")
            .setPositiveButton("Install Anyway") { _, _ ->
                checkInstallPermissionAndInstall()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkInstallPermissionAndInstall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                // Show dialog to direct user to enable unknown sources
                AlertDialog.Builder(this)
                    .setTitle("Permission Required")
                    .setMessage("To install APKs, you need to allow installation from unknown sources for this app.")
                    .setPositiveButton("Settings") { _, _ ->
                        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:$packageName")
                        })
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return
            }
        }
        currentUri?.let { installApk(it) }
    }

    private fun checkAndRequestPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED -> {
                currentUri?.let { analyzeApk(it) }
            }
            shouldShowRequestPermissionRationale(permission) -> {
                showPermissionRationale()
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun analyzeApk(uri: Uri) {
        isAnalyzing = true
        binding.progressBar.visibility = View.VISIBLE
        binding.tvAnalysisResult.text = "Analyzing APK for semantic mismatches..."
        binding.btnInstall.isEnabled = false

        lifecycleScope.launch {
            try {
                val tempFile = withContext(Dispatchers.IO) {
                    ApkAnalyzer(applicationContext).createTempFileFromUri(uri)
                }
                
                // Use new semantic mismatch detector
                val detector = SemanticMismatchDetector(applicationContext)
                
                binding.tvAnalysisResult.text = "Performing deep semantic analysis..."
                
                val result = withContext(Dispatchers.IO) {
                    detector.analyzeApk(tempFile.absolutePath)
                }
                
                // Display comprehensive results
                binding.tvAnalysisResult.text = """
                    🔍 PHÂN TÍCH SEMANTIC MISMATCH
                    
                    📊 ĐIỂM RỦI RO: ${result.riskScore}
                    🎯 MỨC ĐỘ: ${result.riskLevel.label}
                    
                    📋 CÁC VẤN ĐỀ:
                    ${result.riskFactors.joinToString("\n") { "• $it" }}
                    
                    🤖 PHÂN TÍCH AI:
                    ${result.llmAnalysis}
                    
                    📈 CHI TIẾT:
                    • Loại app: ${result.detailedReport.expectedBehavior.appType}
                    • Quyền đáng ngờ: ${result.detailedReport.mismatches.count { it.type == MismatchType.SUSPICIOUS_PERMISSION }}
                    • API lệch chức năng: ${result.detailedReport.mismatches.count { it.type == MismatchType.SUSPICIOUS_API }}
                    • Dấu hiệu obfuscation: ${result.detailedReport.actualBehavior.obfuscationSignals.size}
                """.trimIndent()
                
                isSuspiciousApk = result.riskLevel != SemanticMismatchDetector.RiskLevel.SAFE
                binding.btnInstall.isEnabled = true

                when (result.riskLevel) {
                    SemanticMismatchDetector.RiskLevel.SAFE -> {
                        binding.btnInstall.text = "Install"
                        binding.statusIcon.setImageResource(android.R.drawable.ic_dialog_info)
                    }
                    SemanticMismatchDetector.RiskLevel.MEDIUM -> {
                        binding.btnInstall.text = "Install with Caution"
                        binding.statusIcon.setImageResource(android.R.drawable.ic_dialog_alert)
                    }
                    SemanticMismatchDetector.RiskLevel.DANGEROUS -> {
                        binding.btnInstall.text = "Install Anyway (High Risk)"
                        binding.statusIcon.setImageResource(android.R.drawable.ic_delete)
                    }
                }

                tempFile.delete()
            } catch (e: Exception) {
                binding.tvAnalysisResult.text = "Error in semantic analysis: ${e.message}"
                binding.btnInstall.isEnabled = false
                binding.statusIcon.setImageResource(android.R.drawable.ic_dialog_alert)
            } finally {
                binding.progressBar.visibility = View.GONE
                isAnalyzing = false
            }
        }
    }

    private fun installApk(uri: Uri) {
        try {
            val contentUri = if (uri.scheme == "content") {
                uri
            } else {
                val tempFile = File(cacheDir, "temp.apk")
                contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                FileProvider.getUriForFile(
                    this,
                    "${packageName}.provider",
                    tempFile
                )
            }

            // Tạo session cài đặt
            val packageInstaller = packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            // Copy APK vào session
            contentResolver.openInputStream(contentUri)?.use { input ->
                session.openWrite("package", 0, -1).use { output ->
                    input.copyTo(output)
                }
            }

            // Tạo broadcast receiver để nhận kết quả cài đặt
            val intent = Intent(this, InstallResultReceiver::class.java)
            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.getBroadcast(
                    this,
                    sessionId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            } else {
                PendingIntent.getBroadcast(
                    this,
                    sessionId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

            // Hiển thị dialog progress
            binding.progressBar.visibility = View.VISIBLE
            binding.tvAnalysisResult.text = "Installing APK..."
            binding.btnInstall.isEnabled = false
            binding.btnCancel.isEnabled = false

            // Commit session để cài đặt
            session.commit(pendingIntent.intentSender)
            session.close()
        } catch (e: Exception) {
            binding.progressBar.visibility = View.GONE
            binding.btnInstall.isEnabled = true
            binding.btnCancel.isEnabled = true
            Toast.makeText(this, "Error installing APK: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showPermissionDeniedMessage() {
        Toast.makeText(
            this,
            "Storage permission is required to analyze APK files",
            Toast.LENGTH_LONG
        ).show()
        finish()
    }

    private fun showPermissionRationale() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        val intentFilter = IntentFilter(ACTION_INSTALL_COMPLETE_CLOSE_ACTIVITY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeActivityReceiver, intentFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(closeActivityReceiver, intentFilter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(closeActivityReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        resetState()
    }
}

// Thêm class InstallResultReceiver để xử lý kết quả cài đặt
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Người dùng cần xác nhận cài đặt
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                }
                if (confirmIntent != null) {
                    context.startActivity(confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(context, "Installation successful", Toast.LENGTH_SHORT).show()
                // Gửi broadcast để đóng MainActivity
                val closeIntent = Intent(MainActivity.ACTION_INSTALL_COMPLETE_CLOSE_ACTIVITY)
                context.sendBroadcast(closeIntent)
            }
            else -> {
                Toast.makeText(
                    context,
                    "Installation failed: $message",
                    Toast.LENGTH_LONG
                ).show()
                 // Optional: Gửi broadcast để thông báo lỗi và có thể enable lại nút bấm trong MainActivity
            }
        }
    }
}