import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun signingValue(property: String, environment: String): String? =
    keystoreProperties.getProperty(property) ?: System.getenv(environment)

val releaseKeystore = signingValue("storeFile", "LINKPILOT_KEYSTORE")?.let { rootProject.file(it) }

android {
    namespace = "dev.sohan.linkpilot"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.sohan.linkpilot"
        minSdk = 26
        targetSdk = 37
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "1.0.0"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = signingValue("storePassword", "LINKPILOT_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "LINKPILOT_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "LINKPILOT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val compose = "1.13.0-alpha01"

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.navigationevent:navigationevent-compose:1.1.2")

    implementation("androidx.compose.ui:ui:$compose")
    implementation("androidx.compose.foundation:foundation:$compose")
    implementation("androidx.compose.animation:animation:$compose")
    implementation("androidx.compose.ui:ui-tooling-preview:$compose")
    implementation("androidx.compose.material3:material3:1.5.0-alpha28")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("com.google.zxing:core:3.5.3")

    debugImplementation("androidx.compose.ui:ui-tooling:$compose")

    testImplementation("junit:junit:4.13.2")
}
