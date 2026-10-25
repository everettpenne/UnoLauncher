plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseSigningVariables = listOf(
    "DUO_RELEASE_STORE_FILE",
    "DUO_RELEASE_STORE_PASSWORD",
    "DUO_RELEASE_KEY_ALIAS",
    "DUO_RELEASE_KEY_PASSWORD",
)
val releaseSigningValues = releaseSigningVariables.associateWith { name ->
    System.getenv(name)?.takeIf { it.isNotBlank() }
}
val suppliedReleaseSigningVariables = releaseSigningValues.filterValues { it != null }.keys
check(suppliedReleaseSigningVariables.isEmpty() || suppliedReleaseSigningVariables.size == releaseSigningVariables.size) {
    val missing = releaseSigningVariables.filterNot(suppliedReleaseSigningVariables::contains)
    "Release signing is only configured when all four DUO_RELEASE_* variables are set. Missing: ${missing.joinToString()}"
}

val releaseStoreFile = releaseSigningValues["DUO_RELEASE_STORE_FILE"]?.let { configuredPath ->
    rootProject.file(configuredPath).canonicalFile.also { storeFile ->
        val repositoryRoot = rootProject.projectDir.canonicalFile.toPath()
        check(!storeFile.toPath().startsWith(repositoryRoot)) {
            "DUO_RELEASE_STORE_FILE must point outside the repository."
        }
        check(storeFile.isFile && storeFile.canRead()) {
            "DUO_RELEASE_STORE_FILE does not point to a readable file."
        }
    }
}

android {
    namespace = "com.jake.duolauncher"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.jake.duolauncher"
        minSdk = 31
        targetSdk = 36
        versionCode = 169
        versionName = "0.107.0-uno01"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appLabel"] = "Uno Launcher"
        manifestPlaceholders["shadeLabel"] = "@string/shade_service_label"
        manifestPlaceholders["readerLabel"] = "@string/status_reader_service_label"
    }
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseSigningValues.getValue("DUO_RELEASE_STORE_PASSWORD")
                keyAlias = releaseSigningValues.getValue("DUO_RELEASE_KEY_ALIAS")
                keyPassword = releaseSigningValues.getValue("DUO_RELEASE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        // -PunoDev builds a debug copy with its own id ("Uno Dev"), so it installs beside a release install on a real phone without
        // replacing it (and its layout): the two are signed with different keys, and Android will not update one with the other.
        if (project.hasProperty("unoDev")) getByName("debug") {
            applicationIdSuffix = ".dev"
            manifestPlaceholders["appLabel"] = "Uno Dev"
            manifestPlaceholders["shadeLabel"] = "Uno Dev shade gestures"
            manifestPlaceholders["readerLabel"] = "Uno Dev island alignment"
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.window:window:1.5.1")
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.palette:palette-ktx:1.0.0")
    // Uno Keyboard's autofill chips: the style the platform asks inline suggestions to be drawn in.
    implementation("androidx.autofill:autofill:1.1.0")
    implementation("io.github.kyant0:backdrop:1.0.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
