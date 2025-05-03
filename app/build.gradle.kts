plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.alphawolf.apkinstallerwithantivirus"
    compileSdk = 34

    // Bật BuildConfig
    buildFeatures {
        buildConfig = true
        viewBinding = true
        dataBinding = true
    }
    defaultConfig {
        applicationId = "com.alphawolf.apkinstallerwithantivirus"
        minSdk = 25
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        //truy cập API key từ gradle.properties
        buildConfigField("String", "GEMINI_API_KEY", "\"${project.findProperty("GEMINI_API_KEY")}\"")
    }


//bật G8/ProGuard
    buildTypes {
        release {
            // isMinifyEnabled = false
            // Bật R8 để thu gọn (shrink), tối ưu (optimize), và làm rối (obfuscate) code
            isMinifyEnabled = true
            // Bật tính năng loại bỏ tài nguyên không sử dụng (hình ảnh, layout, string...)
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
        dataBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // APK parsing
    implementation("org.smali:dexlib2:2.5.2")
    implementation("org.apache.commons:commons-compress:1.21")

    // UI Components
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}