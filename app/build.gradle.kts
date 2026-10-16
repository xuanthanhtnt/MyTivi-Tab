import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Số phiên bản = số thứ tự lượt build trên GitHub Actions (tự tăng mỗi lần push).
// Build trên máy (Android Studio) thì dùng 1.
val ciRun = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
// Repo chứa bản phát hành để app tự kiểm tra cập nhật (dạng chủ-repo/tên-repo)
val updateRepo = System.getenv("GITHUB_REPOSITORY") ?: "xuanthanhtnt/MyTivi-Tab"
// Khóa ký cố định (lấy từ GitHub Secrets). Mọi bản phải ký cùng 1 khóa thì mới cài đè được.
val keystoreFile = System.getenv("KEYSTORE_FILE")
// Thông tin bản dựng hiện ở màn hình Giới thiệu
val buildTime: String = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
val gitSha: String = (System.getenv("GITHUB_SHA") ?: "local").take(7)

android {
    // Giữ namespace như bản TV để dùng chung mã nguồn (lớp R, BuildConfig nằm ở com.mytivi.app)
    namespace = "com.mytivi.app"
    compileSdk = 34

    defaultConfig {
        // Mã ứng dụng riêng cho bản máy tính bảng: cài song song được với bản TV,
        // và trình tự cập nhật không bao giờ cài nhầm APK của bản TV.
        applicationId = "com.mytivi.tab"
        minSdk = 23            // Android 6.0+ (Galaxy Tab A đời cũ trở lên)
        targetSdk = 34
        versionCode = ciRun
        versionName = "1.0.$ciRun"
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
    }

    signingConfigs {
        create("release") {
            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = "mytivi"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystoreFile != null) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }
    lint {
        // Không chặn bản release vì các cảnh báo dành cho Google Play (app này cài tay)
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // Trình phát: ExoPlayer (Media3) hỗ trợ HLS .m3u8, DASH, TS, MP4...
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")   // tải playlist / EPG
    implementation("io.coil-kt:coil-compose:2.7.0")        // logo kênh
}
