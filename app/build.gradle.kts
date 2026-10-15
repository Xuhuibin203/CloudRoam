import java.util.Properties

// 从 local.properties（不进版本库）或环境变量读取高德 Key，缺省为占位符（地图显示提示）
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val amapKey = localProps.getProperty("AMAP_KEY")
    ?: System.getenv("AMAP_KEY")
    ?: "PLACEHOLDER"

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.cloudroam.app"
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.cloudroam.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-M1"
        manifestPlaceholders["AMAP_KEY"] = amapKey
        buildConfigField("String", "AMAP_KEY", "\"$amapKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // LSPosed 模块 API：编译期可见，运行期由框架提供（compileOnly）
    compileOnly("io.github.libxposed:api:101.0.0")
    // App 侧连接 LSPosed 服务（远程偏好设置 / 作用域申请）
    implementation("io.github.libxposed:service:101.0.0")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // 高德 3D 地图（选点引擎）
    implementation("com.amap.api:3dmap:10.0.600")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
