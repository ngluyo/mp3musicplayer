plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mp3.musicplayer" // Pastikan ini sama dengan package name Anda
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mp3.musicplayer" // Pastikan ini sama dengan package name Anda
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Pustaka Inti AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Pustaka untuk UI (User Interface)
    implementation("com.google.android.material:material:1.11.0") // Komponen Material Design 3
    implementation("androidx.constraintlayout:constraintlayout:2.1.4") // Untuk layout yang kompleks
    implementation("androidx.cardview:cardview:1.0.0") // Kartu dengan bayangan
    implementation("androidx.recyclerview:recyclerview:1.3.2") // Untuk menampilkan daftar putar

    // Pustaka untuk Siklus Hidup & Coroutines (untuk tugas background)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Pustaka untuk Media (khusus untuk notifikasi media)
    implementation("androidx.media:media:1.7.0")
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Pustaka untuk Pengujian (opsional, tapi praktik yang baik)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Untuk Navigasi Antar Fragment
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
}