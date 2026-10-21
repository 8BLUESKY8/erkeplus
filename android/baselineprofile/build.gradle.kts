import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    // 这个插件会给被测模块的变体打上 baselineProfile 专用的属性
    alias(libs.plugins.androidx.baselineprofile)
}

// 用发布签名：测试 APK 与被测 APK 必须同签名，而且同签名可以原地替换已安装的发布包，
// 这样生成 profile 时用的是真实登录态，覆盖到发现页而不是登录页。
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
    namespace = "gitee.leolin_me.erkepp.baselineprofile"
    compileSdk = 36

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

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 这个模块没有自己的业务代码，只是驱动被测应用跑一遍
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
