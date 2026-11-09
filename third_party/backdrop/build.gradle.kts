plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.kyant.backdrop"
    compileSdk = 36
    defaultConfig { minSdk = 31 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // The versions the published 1.0.0 was built on. They also set the app's own Compose, as the published library did.
    implementation("androidx.compose.foundation:foundation:1.9.4")
    implementation("androidx.compose.ui:ui:1.9.4")
    implementation("androidx.compose.ui:ui-graphics:1.9.4")
}
