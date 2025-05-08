package com.alphawolf.apkinstallerwithantivirus.batch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
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
        private const val REQUEST_DATASET_DIR = 2001
        private const val MANAGE_STORAGE_REQUEST_CODE = 1002
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

        setupDefaultFolders(defaultDatasetPath)


        binding.btnStartAnalysis.setOnClickListener {
            if (checkPermissions()) {
                startBatchAnalysis()
            } else {
                requestPermissions()
            }
        }
    }


    private fun setupDefaultFolders(datasetPath: String) {
        try {
            val datasetDir = File(datasetPath)
            datasetDir.mkdirs()
            File(datasetDir, "safe").mkdir()
            File(datasetDir, "malware").mkdir()
        } catch (e: Exception) {
            // Log lỗi nếu có
        }
    }

    // Phương thức để mở dialog chọn thư mục
    private fun openDocumentTree() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, REQUEST_DATASET_DIR) //chưa có UI
    }

    // Xử lý kết quả khi người dùng chọn thư mục
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_DATASET_DIR && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                // Lưu quyền truy cập lâu dài vào thư mục
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                // Lưu URI trong SharedPreferences để sử dụng lại sau này
                getSharedPreferences("batch_analysis", MODE_PRIVATE).edit()
                    .putString("dataset_uri", uri.toString())
                    .apply()

                // Cập nhật UI với URI đã chọn
                binding.edtDatasetPath.setText(uri.toString())

                // Tạo thư mục con trong thư mục đã chọn
                createRequiredSubfolders(uri)
            }
        }else if (requestCode == MANAGE_STORAGE_REQUEST_CODE) {
            // Kiểm tra lại quyền sau khi người dùng tương tác với màn hình cài đặt
            if (checkPermissions()) {
                startBatchAnalysis()
            } else {
                Toast.makeText(
                    this,
                    "Cần quyền truy cập bộ nhớ để đọc các file APK",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Tạo các thư mục con (safe, malware) trong thư mục đã chọn
    private fun createRequiredSubfolders(parentUri: Uri) {
        val safeDirName = "safe"
        val malwareDirName = "malware"

        try {
            val documentTree = DocumentFile.fromTreeUri(this, parentUri)
            if (documentTree == null) {
                Toast.makeText(this, "Không thể truy cập thư mục đã chọn", Toast.LENGTH_SHORT).show()
                return
            }

            // Kiểm tra và theo dõi trạng thái thư mục
            val safeExists = documentTree.findFile(safeDirName) != null
            val malwareExists = documentTree.findFile(malwareDirName) != null

            // Tạo thư mục "safe" nếu chưa tồn tại
            if (!safeExists) {
                val created = documentTree.createDirectory(safeDirName) != null
                if (!created) {
                    Toast.makeText(this, "Không thể tạo thư mục $safeDirName", Toast.LENGTH_SHORT).show()
                    return
                }
            }

            // Tạo thư mục "malware" nếu chưa tồn tại
            if (!malwareExists) {
                val created = documentTree.createDirectory(malwareDirName) != null
                if (!created) {
                    Toast.makeText(this, "Không thể tạo thư mục $malwareDirName", Toast.LENGTH_SHORT).show()
                    return
                }
            }

            // Hiển thị thông báo phù hợp tùy thuộc vào tình trạng thư mục
            val message = when {
                !safeExists && !malwareExists -> "Đã tạo thư mục safe và malware"
                !safeExists -> "Đã tạo thư mục safe, thư mục malware đã tồn tại"
                !malwareExists -> "Đã tạo thư mục malware, thư mục safe đã tồn tại"
                else -> "Các thư mục safe và malware đã tồn tại"
            }

            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ yêu cầu MANAGE_EXTERNAL_STORAGE thông qua Settings
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse("package:${applicationContext.packageName}")
                startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE)
            } catch (e: Exception) {
                // Nếu không mở được trang cài đặt cụ thể, mở trang cài đặt chung
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE)
            }
        } else {
            // Android 10 trở xuống
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                PERMISSION_REQUEST_CODE
            )
        }
    }
    private fun checkPermissions(): Boolean {
        // return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        //     ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
        //     ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
        //     ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        // } else {
        //     ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        // }
        // Kiểm tra quyền truy cập tất cả file (All Files Access)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        } else {
            return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
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

        val datasetPathText = binding.edtDatasetPath.text.toString()
        val outputPath = binding.edtOutputPath.text.toString()

        if (datasetPathText.isBlank() || outputPath.isBlank()) {
            Toast.makeText(this, "Please enter valid paths", Toast.LENGTH_SHORT).show()
            return
        }

        // Kiểm tra xem đường dẫn có phải là URI từ SAF không
        if (datasetPathText.startsWith("content://")) {
            // Xử lý với URI từ SAF
            val uri = Uri.parse(datasetPathText)
            try {
                val documentTree = DocumentFile.fromTreeUri(this, uri)
                if (documentTree == null || !documentTree.exists()) {
                    Toast.makeText(this, "Invalid dataset URI", Toast.LENGTH_SHORT).show()
                    return
                }

                // Kiểm tra cấu trúc thư mục safe và malware
                val safeDir = documentTree.findFile("safe")
                val malwareDir = documentTree.findFile("malware")

                if (safeDir == null || malwareDir == null) {
                    Toast.makeText(this,
                        "Thiếu thư mục safe hoặc malware. Vui lòng chọn lại thư mục.",
                        Toast.LENGTH_LONG).show()
                    return
                }

                // Tìm các file APK
                val safeApks = safeDir.listFiles().filter { it.name?.endsWith(".apk", true) == true }
                val malwareApks = malwareDir.listFiles().filter { it.name?.endsWith(".apk", true) == true }

                if (safeApks.isEmpty() && malwareApks.isEmpty()) {
                    Toast.makeText(this,
                        "Không tìm thấy file APK nào. Vui lòng thêm file APK vào thư mục safe và/hoặc malware.",
                        Toast.LENGTH_LONG).show()
                    return
                }

                // Bắt đầu phân tích với URI
                isAnalyzing = true
                binding.progressBar.visibility = View.VISIBLE
                binding.btnStartAnalysis.isEnabled = false
                binding.tvStatus.text = "Starting batch analysis..."

                Toast.makeText(this,
                    "Xử lý URI từ SAF chưa được hỗ trợ. Vui lòng sử dụng đường dẫn file thông thường.",
                    Toast.LENGTH_LONG).show()

                binding.progressBar.visibility = View.GONE
                binding.btnStartAnalysis.isEnabled = true
                isAnalyzing = false
                // TODO: Triển khai phân tích với URI - cần sửa lớp BatchApkAnalyzer
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnStartAnalysis.isEnabled = true
                isAnalyzing = false
                Toast.makeText(this, "Lỗi truy cập thư mục: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            // Xử lý với đường dẫn file thông thường như hiện tại
            val datasetDir = File(datasetPathText)
            if (!datasetDir.exists()) {
                Toast.makeText(this, "Dataset directory does not exist. Creating it now.", Toast.LENGTH_LONG).show()
                val success = datasetDir.mkdirs()
                if (!success) {
                    Toast.makeText(this, "Failed to create dataset directory", Toast.LENGTH_SHORT).show()
                    return
                }
            } else if (!datasetDir.isDirectory) {
                Toast.makeText(this, "Invalid dataset path - not a directory", Toast.LENGTH_SHORT).show()
                return
            }

            // Kiểm tra cấu trúc thư mục SAFE và MALWARE (không phân biệt hoa/thường)
            val hasValidStructure = checkAndCreateDatasetStructure(datasetDir)
            if (!hasValidStructure) {
                return // Đã hiển thị thông báo trong hàm checkAndCreateDatasetStructure
            }

            // Kiểm tra có APK trong thư mục không
            val safeDir = File(datasetDir, "safe")
            val malwareDir = File(datasetDir, "malware")

            val safeApks = safeDir.listFiles { _, name -> name.endsWith(".apk", true) }?.size ?: 0
            val malwareApks = malwareDir.listFiles { _, name -> name.endsWith(".apk", true) }?.size ?: 0

            if (safeApks == 0 && malwareApks == 0) {
                Toast.makeText(this,
                    "Không tìm thấy file APK nào. Vui lòng thêm file APK vào thư mục safe và/hoặc malware.",
                    Toast.LENGTH_LONG).show()
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
                            datasetRootPath = datasetPathText,
                            outputPath = outputPath
                        )
                    }

                    if (result.startsWith("Phân tích hoàn tất!")) {
                        binding.tvStatus.text = "Analysis complete!\n\n$result"
                    } else {
                        binding.tvStatus.text = result
                    }
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
    /**
     * Kiểm tra và tạo cấu trúc thư mục dataset nếu chưa tồn tại
     * @return true nếu cấu trúc hợp lệ hoặc đã tạo thành công, false nếu có lỗi
     */
    private fun checkAndCreateDatasetStructure(datasetDir: File): Boolean {
        val requiredDirs = listOf("safe", "malware")
        val existingDirs = datasetDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name.lowercase() }
            ?: emptyList()

        // Kiểm tra các thư mục con còn thiếu
        val missingDirs = requiredDirs.filter { requiredDir ->
            !existingDirs.any { it == requiredDir }
        }

        if (missingDirs.isEmpty()) {
            return true // Cấu trúc hợp lệ
        }

        // Hiển thị thông báo và tạo thư mục còn thiếu
        val message = StringBuilder("Dataset cần có cấu trúc thư mục con:\n")
        var creationSuccess = true

        missingDirs.forEach { dirName ->
            val dirToCreate = File(datasetDir, dirName) // Tạo với tên viết hoa
            message.append("- $dirName: ")

            val success = dirToCreate.mkdir()
            if (success) {
                message.append("Đã tạo thành công\n")
            } else {
                message.append("Không thể tạo\n")
                creationSuccess = false
            }
        }

        Toast.makeText(this, message.toString(), Toast.LENGTH_LONG).show()
        return creationSuccess
    }
}