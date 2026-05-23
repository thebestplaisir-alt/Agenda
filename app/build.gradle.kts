import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.inchios.agendapadel"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.inchios.agendapadel"
        minSdk = 26
        targetSdk = 35
        versionCode = 81
        versionName = "1.5.46"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
    }

    signingConfigs {
        create("release") {
            val properties = Properties()
            val propertiesFile = rootProject.file("local.properties")
            if (propertiesFile.exists()) {
                propertiesFile.inputStream().use { properties.load(it) }
            }

            val sFile = properties.getProperty("RELEASE_STORE_FILE") ?: System.getenv("RELEASE_STORE_FILE")
            storeFile = sFile?.let { pathOrVar ->
                // 1. On cherche d'abord si c'est une variable d'environnement directe
                // 2. Sinon on teste les variables connues de Codemagic (CM_KEYSTORE_PATH, etc.)
                // 3. Sinon on utilise la valeur brute comme chemin
                val actualPath = System.getenv(pathOrVar) 
                    ?: System.getenv("CM_KEYSTORE_PATH") 
                    ?: System.getenv("FNC_KEYSTORE_PATH")
                    ?: pathOrVar
                
                val possibleFile = file(actualPath)
                if (possibleFile.exists()) {
                    possibleFile 
                } else {
                    val rootFile = rootProject.file(actualPath)
                    if (rootFile.exists()) rootFile else null
                }
            }
            storePassword = properties.getProperty("RELEASE_STORE_PASSWORD") ?: System.getenv("RELEASE_STORE_PASSWORD")
            keyAlias = properties.getProperty("RELEASE_KEY_ALIAS") ?: System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = properties.getProperty("RELEASE_KEY_PASSWORD") ?: System.getenv("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            
            // On n'applique la config de signature que si les infos sont présentes
            val isSigningConfigured = signingConfigs.getByName("release").storeFile != null
            if (isSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            
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
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.material)
    
    implementation(libs.androidx.core.splashscreen)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.core.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.storage)
    implementation(libs.play.services.auth)

    // Paiement Google Play
    implementation(libs.billing.ktx)
    implementation(libs.kotlinx.datetime)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    implementation(libs.coil.compose)
    implementation(libs.androidx.concurrent.futures)
    implementation(libs.gson)
    implementation(libs.play.services.wearable)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
