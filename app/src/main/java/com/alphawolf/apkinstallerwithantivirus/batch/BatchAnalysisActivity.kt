package com.alphawolf.apkinstallerwithantivirus.batch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.alphawolf.apkinstallerwithantivirus.databinding.ActivityBatchAnalysisBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BatchAnalysisActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBatchAnalysisBinding
    private var isAnalyzing = false
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBatchAnalysisBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
    }
    
    private fun setupUI() {
        // Set default paths
        val defaultDatasetPath = File(Environment.getExternalStorageDirectory(), "apk_dataset").absolutePath
        val defaultOutputPath = File(getExternalFilesDir(null), "test_results").absolutePath
        
        binding.edtDatasetPath.setText(defaultDatasetPath)
        binding.edtOutputPath.setText(defaultOutputPath)
        
        binding.btnStartAnalysis.setOnClickListener {
            if (checkPermissions()) {
                startBatchAnalysis()
            } else {
                requestPermissions()
            }
        }
    }
    
    private fun checkPermissions(): Boolean {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestPermissions() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        ActivityCompat.requestPermissions(
            this,
            arrayOf(permission),
            PERMISSION_REQUEST_CODE
        )
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE && 
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startBatchAnalysis()
        } else {
            Toast.makeText(
                this,
                "Storage permission is required to access APK files",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private fun startBatchAnalysis() {
        if (isAnalyzing) return
        
        val datasetPath = binding.edtDatasetPath.text.toString()
        val outputPath = binding.edtOutputPath.text.toString()
        
        if (datasetPath.isBlank() || outputPath.isBlank()) {
            Toast.makeText(this, "Please enter valid paths", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Validate dataset path
        val datasetDir = File(datasetPath)
        if (!datasetDir.exists() || !datasetDir.isDirectory) {
            Toast.makeText(this, "Invalid dataset directory", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Start analysis
        isAnalyzing = true
        binding.progressBar.visibility = View.VISIBLE
        binding.btnStartAnalysis.isEnabled = false
        binding.tvStatus.text = "Starting batch analysis..."
        
        lifecycleScope.launch {
            try {
                val analyzer = BatchApkAnalyzer(applicationContext)
                val result = withContext(Dispatchers.IO) {
                    analyzer.analyzeDatasetAndGenerateReport(
                        datasetRootPath = datasetPath,
                        outputPath = outputPath
                    )
                }
                
                binding.tvStatus.text = "Analysis complete!\n\n$result"
            } catch (e: Exception) {
                binding.tvStatus.text = "Error: ${e.message}"
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnStartAnalysis.isEnabled = true
                isAnalyzing = false
            }
        }
    }
}