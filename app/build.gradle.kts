plugins {
    // AGP 9 compiles Kotlin itself (built-in Kotlin), so no kotlin-android here.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.linkedout.app"
    // 37 because Compose 1.12 compiles against it. targetSdk is 37 since
    // 2.4.0, after reading every Android 17 change for apps targeting it.
    // What applies to LinkedOut: certificate transparency and Encrypted Client
    // Hello turn on for its https connections (stricter, nothing to change),
    // background audio is restricted (players already pause when the app
    // leaves the screen), and large screens ignore orientation and
    // resizability limits (LinkedOut sets none). Widgets, contacts, SMS, Bluetooth,
    // local network, native code loading and reflection on MessageQueue or
    // static final fields are not used.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.linkedout.app"
        minSdk = 31
        targetSdk = 37
        versionCode = 74
        versionName = "0.6.47"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = providers.gradleProperty("linkedout.storeFile").orNull
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = providers.gradleProperty("linkedout.storePassword").orNull
                keyAlias = providers.gradleProperty("linkedout.keyAlias").orNull
                keyPassword = providers.gradleProperty("linkedout.keyPassword").orNull
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Only attached when the signing properties are supplied, so a
            // local build without a keystore still produces an unsigned APK.
            if (providers.gradleProperty("linkedout.storeFile").isPresent) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Both off, and they stay off. R8 broke this app once already.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // No kotlin { jvmTarget } block any more: with built-in Kotlin the JVM
    // target follows targetCompatibility above.

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/INDEX.LIST",
            "/META-INF/DEPENDENCIES"
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.androidx.work.runtime)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    implementation(libs.androidx.webkit)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
