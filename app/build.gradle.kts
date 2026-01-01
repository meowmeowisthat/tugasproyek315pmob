plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
}


android {
    namespace = "com.proyek.tugasproyek"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.proyek.tugasproyek"
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

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    // Chart (Grafik Pola Makan)
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Firebase
    implementation("com.google.firebase:firebase-auth-ktx:22.3.0")
    implementation("com.google.firebase:firebase-database-ktx:20.3.1")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")



    implementation("com.cloudinary:cloudinary-android:1.30.0") {
        exclude(group = "com.linkedin.android.litr", module = "litr")

        implementation("com.itextpdf:kernel:7.2.5")
        implementation("com.itextpdf:layout:7.2.5")

    }

    // Circle Image
    implementation("de.hdodenhof:circleimageview:3.1.0")
    implementation(libs.firebase.ai)
    implementation(libs.firebase.database)
    implementation(libs.androidx.activity)

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")




}
