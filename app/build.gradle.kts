import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.roommate.expenses"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.roommate.expenses"
        minSdk = 23
        targetSdk = 35
        versionCode = 8
        versionName = "1.7"
    }

    val releaseSigning = signingConfigs.create("release") {
        val keystoreB64 = System.getenv("RELEASE_KEYSTORE_B64")
        if (!keystoreB64.isNullOrBlank()) {
            val keystoreFile = File(System.getProperty("java.io.tmpdir"), "roommate-release-keystore.jks")
            keystoreFile.parentFile.mkdirs()
            keystoreFile.writeBytes(Base64.getDecoder().decode(keystoreB64))
            storeFile = keystoreFile
            storeType = "PKCS12"
            storePassword = System.getenv("RELEASE_STORE_PASSWORD")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (!System.getenv("RELEASE_KEYSTORE_B64").isNullOrBlank()) {
                signingConfig = releaseSigning
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
