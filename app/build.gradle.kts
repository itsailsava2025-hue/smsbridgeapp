plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ru.shinomontaj.smsbridge"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.shinomontaj.smsbridge"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}


dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // QR scanner
    implementation("com.journeyapps:zxing-android-embedded:4.3.0") {
        exclude(group = "com.android.support")
    }
    implementation("com.google.zxing:core:3.5.3")

    // HTTP
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}


androidComponents {
    beforeVariants(selector().all()) { variant ->
        variant.enableAndroidTest = false
        variant.enableUnitTest = false
    }
}
