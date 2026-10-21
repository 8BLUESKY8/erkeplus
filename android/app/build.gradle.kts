import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
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
        versionCode = 1
        versionName = "0.1.0"
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
            isMinifyEnabled = false
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

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)

    testImplementation(libs.junit)
}
