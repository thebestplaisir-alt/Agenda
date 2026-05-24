plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.android)
    // Supprimé : google.services pour éviter les crashs sur montres sans Play Services
}

android {
    namespace = "com.inchios.agendapadel.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.inchios.agendapadel" // MODIFIÉ : Identique au téléphone pour le Play Store
        minSdk = 26
        targetSdk = 35
        versionCode = 10099 // MODIFIÉ : Incrémenté (était 10098)
        versionName = "1.5.63"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // Supprimé : Firebase pour compatibilité maximale montres Android classiques

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material.icons.extended)
    implementation("androidx.compose.material:material:1.7.8") // Matérial 2 stable
    implementation(libs.androidx.appcompat)
    
    // Wear OS specific (gardé pour les montres Wear OS, mais l'UI est en Material2 pour compatibilité)
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)
    implementation(libs.play.services.wearable)
    implementation(libs.horologist.compose.layout)

    debugImplementation(libs.androidx.ui.tooling)
}
