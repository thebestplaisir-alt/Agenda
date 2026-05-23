plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services) // AJOUTÉ : Plugin Google Services
}

android {
    namespace = "com.inchios.agendapadel.wear"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.inchios.agendapadel" // MODIFIÉ : Identique au téléphone pour le Play Store
        minSdk = 30
        targetSdk = 34
        versionCode = 10088 // MODIFIÉ : Doit être plus élevé que la version mobile (88)
        versionName = "1.5.53"
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
    // Importation du BOM Firebase pour gérer les versions
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.firestore)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material.icons.extended)
    
    // Wear OS specific
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)
    implementation(libs.play.services.wearable)
    implementation(libs.horologist.compose.layout)
    implementation(libs.core.ktx)

    debugImplementation(libs.androidx.ui.tooling)
}
