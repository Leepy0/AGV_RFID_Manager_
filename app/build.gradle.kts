plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val appVersionName = "2.5.1"

// versionCode = git 커밋 수 (PC·CI 빌드 모두 같은 값, 커밋마다 자동 증가 → 앱 내 업데이트 비교용)
// CI는 checkout fetch-depth: 0 필요. git이 없으면 1
val gitCommitCount: Int = try {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
        .standardOutput.asText.get().trim().toInt()
} catch (_: Exception) {
    1
}

android {
    namespace = "com.example.agv_rfid_manager"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.agv_rfid_manager"
        minSdk = 26
        targetSdk = 36
        // 스냅샷 테스트용: -PversionCodeOverride=1 로 낮은 버전을 만들어 업데이트 흐름 확인
        versionCode = providers.gradleProperty("versionCodeOverride").orNull?.toIntOrNull() ?: gitCommitCount
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // debug 서명 키 고정 (PC·CI 빌드 모두 같은 키 → 삭제 없이 업데이트 설치 가능)
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // debug와 같은 키로 서명 → 기존 설치 위에 바로 업데이트 가능
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val outputImpl = output as? com.android.build.api.variant.impl.VariantOutputImpl
            outputImpl?.outputFileName = "AGV_RFID_Manager_v${appVersionName}.apk"
        }
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
    implementation("androidx.compose.material:material-icons-extended")
    // DataStore (설정·기록 영구 저장)
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    // Glass 배경 blur (Haze 1.x)
    implementation("dev.chrisbanes.haze:haze:1.6.10")  // 1.7부터 compileSdk 37 필요
}