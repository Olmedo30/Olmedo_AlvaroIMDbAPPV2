plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "edu.pmdm.olmedo_lvaroimdbapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "edu.pmdm.olmedo_lvaroimdbapp"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation ("com.github.bumptech.glide:glide:4.15.1")
    implementation(libs.firebase.firestore)
    annotationProcessor ("com.github.bumptech.glide:compiler:4.15.1")
    implementation ("com.google.android.gms:play-services-auth:20.7.0")
    implementation ("com.squareup.retrofit2:retrofit:2.9.0")
    implementation ("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation ("com.google.code.gson:gson:2.8.9")
    implementation ("com.facebook.android:facebook-login:15.0.1")
    implementation ("com.facebook.android:facebook-android-sdk:12.3.0")
    implementation ("com.googlecode.libphonenumber:libphonenumber:8.12.45")
    implementation ("com.hbb20:ccp:2.5.3")
    implementation ("com.google.android.gms:play-services-places:17.0.0")
    implementation ("com.google.android.libraries.places:places:3.1.0")
    implementation ("com.google.android.gms:play-services-maps:18.2.0")
    implementation ("com.hbb20:ccp:2.5.1")
    implementation ("com.googlecode.libphonenumber:libphonenumber:8.12.37")

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.firebase.auth)
    implementation(libs.activity)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}