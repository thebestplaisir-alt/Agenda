import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// --- FORCE L'ARCHITECTURE AVANT TOUT ---
System.setProperty("os.arch", "x86_64")
System.setProperty("skiko.arch", "x64")
System.setProperty("os.detected.arch", "x86_64")
System.setProperty("os.detected.name", "osx")
System.setProperty("os.detected.classifier", "osx-x86_64")
// ----------------------------------------

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    id("com.android.library")
    kotlin("native.cocoapods")
}

kotlin {
    System.setProperty("os.arch", "x86_64")
    // Force l'architecture pour Kotlin Native sur les machines AMD/Intel non reconnues
    project.extra.set("kotlin.native.hostArchitecture", "x86_64")

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    cocoapods {
        summary = "Shared module for Agenda"
        homepage = "https://github.com/inchios/agenda"
        version = "1.0"
        ios.deploymentTarget = "16.0"
        framework {
            baseName = "shared"
            isStatic = true
        }
        pod("FirebaseCore")
        pod("FirebaseAuth")
        pod("FirebaseFirestore")
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()
    
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            
            // Firebase Multiplatform
            implementation(libs.firebase.kotlin.auth)
            implementation(libs.firebase.kotlin.firestore)
            
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation(libs.androidx.ui.tooling.preview)
            implementation(libs.androidx.activity.compose)
        }
        iosMain.dependencies {
        }
    }
}

android {
    namespace = "com.inchios.agenda.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
