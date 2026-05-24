import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    id("com.android.library")
    kotlin("native.cocoapods")
}

kotlin {
    jvmToolchain(17)
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // Configuration des cibles iOS
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        summary = "Shared module for Agenda"
        homepage = "https://github.com/inchios/agenda"
        version = "1.0"
        ios.deploymentTarget = "16.0"
        framework {
            baseName = "shared"
            isStatic = true
        }
        pod("FirebaseCore") { version = "11.3.0" }
        pod("FirebaseAuth") { version = "11.3.0" }
        pod("FirebaseFirestore") { version = "11.3.0" }
        
        // CORRECTIFS XCODE 15/16 : Sandboxing et compatibilité
        extraSpecAttributes["pod_target_xcconfig"] = "{ 'ENABLE_USER_SCRIPT_SANDBOXING' => 'NO', 'PRODUCT_BUNDLE_IDENTIFIER' => 'com.inchios.agenda.shared' }"
        extraSpecAttributes["user_target_xcconfig"] = "{ 'ENABLE_USER_SCRIPT_SANDBOXING' => 'NO' }"
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        binaries.all {
            freeCompilerArgs += listOf("-Xdisable-phases=RemoveRedundantSafepoints", "-Xallocator=mimalloc", "-Xpms=false", "-Xdisable-checkers")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.firebase.kotlin.auth)
            implementation(libs.firebase.kotlin.firestore)
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation("androidx.compose.ui:ui-tooling-preview:1.7.8")
            implementation(libs.androidx.activity.compose)
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
