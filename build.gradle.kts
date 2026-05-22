// FORCE LES PROPRIÉTÉS POUR TOUS LES PLUGINS AVANT TOUT LE RESTE
System.setProperty("os.detected.arch", "x86_64")
System.setProperty("os.detected.name", "osx")
System.setProperty("os.detected.classifier", "osx-x86_64")
System.setProperty("os.arch", "x86_64")

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.jetbrains.compose) apply false
}
