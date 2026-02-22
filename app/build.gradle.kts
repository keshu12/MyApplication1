plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.myapplication1"
    compileSdk = 35

    // --- 1. 定义路径逻辑变量 ---
    val userHome = System.getProperty("user.home")
    val localJksFile = file("$userHome/android.jks")
    val hasLocalKeystore = localJksFile.exists()

    signingConfigs {
        // 如果本地存在证书，则配置 debug 和 release
        if (hasLocalKeystore) {
            getByName("debug") {
                storeFile = localJksFile
                storePassword = "123456Aa@"
                keyPassword = "123456Aa@"
                keyAlias = "key0"
            }
            create("release") {
                storeFile = localJksFile
                storePassword = "123456Aa@"
                keyPassword = "123456Aa@"
                keyAlias = "key0"
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.myapplication1"
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // --- 2. 动态关联签名 ---
            signingConfig = if (hasLocalKeystore) signingConfigs.getByName("release") else null
            
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // --- 2. 动态关联签名 ---
            signingConfig = if (hasLocalKeystore) signingConfigs.getByName("debug") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("androidx.compose.material:material-icons-extended:1.7.0")
}