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

    // Uniquement ARM64 pour stabiliser le test
    iosArm64()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            freeCompilerArgs.addAll(
                "-Xoverride-konan-properties",
                "clangFlags.apple_sdk=-D_DARWIN_C_SOURCE -Wno-error=non-modular-include-in-framework-module"
            )
        }

        compilations.configureEach {
            cinterops.configureEach {
                // Fix ultime pour Xcode 16+ : on désactive les modules pour le cinterop
                compilerOpts("-fno-modules", "-Xcc", "-fno-modules")
            }
        }
        
        binaries.all {
            linkerOpts("-Xlinker", "-no_warn_duplicate_libraries", "-lc++")
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
        
        pod("FirebaseCore") { version = "10.24.0" }
        pod("FirebaseAuth") { version = "10.24.0" }
        pod("FirebaseFirestore") { version = "10.24.0" }
        
        extraSpecAttributes["pod_target_xcconfig"] = "{ 'ENABLE_USER_SCRIPT_SANDBOXING' => 'NO', 'PRODUCT_BUNDLE_IDENTIFIER' => 'com.inchios.agenda.shared', 'CLANG_ALLOW_NON_MODULAR_INCLUDES_IN_FRAMEWORK_MODULES' => 'YES', 'CLANG_ENABLE_MODULES' => 'YES' }"
        extraSpecAttributes["user_target_xcconfig"] = "{ 'CLANG_ALLOW_NON_MODULAR_INCLUDES_IN_FRAMEWORK_MODULES' => 'YES', 'CLANG_ENABLE_MODULES' => 'YES' }"
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
