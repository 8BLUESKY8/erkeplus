import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.androidx.baselineprofile)
}

// 签名材料一律不入库：
//   keystore 路径来自环境变量 ERKE_KS_DIR（指向本机密钥目录）
//   口令、别名来自 ~/.gradle/gradle.properties
val keystoreDir: String? = System.getenv("ERKE_KS_DIR")
val releaseKeystoreFile: File? = keystoreDir?.let { File(it, "erkepp-release.jks") }
val releaseStorePassword: String? = providers.gradleProperty("erkepp.storePassword").orNull
val releaseKeyAlias: String? = providers.gradleProperty("erkepp.keyAlias").orNull
val releaseKeyPassword: String? = providers.gradleProperty("erkepp.keyPassword").orNull
val hasReleaseSigning: Boolean = releaseKeystoreFile != null && releaseKeystoreFile.exists() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "gitee.leolin_me.erkepp"
    compileSdk = 36

    defaultConfig {
        applicationId = "gitee.leolin_me.erkepp"
        minSdk = 26
        targetSdk = 36
        versionCode = 20
        versionName = "0.6.5"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // R8 压缩与资源收缩：dex 更小，冷启动要加载和校验的类更少
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.swiperefreshlayout)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.androidx.profileinstaller)

    // Baseline Profile 生成器，只在跑 :app:generateReleaseBaselineProfile 时用到
    baselineProfile(project(":baselineprofile"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)

    testImplementation(libs.junit)
}
