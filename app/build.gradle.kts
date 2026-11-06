// The roborazzi plugin, testOptions and test dependencies are copied from Language's app/build.gradle.kts
// https://github.com/213YaZ786/Language/blob/main/app/build.gradle.kts
plugins {
    // AGP 9 compiles Kotlin itself (built-in Kotlin), so no kotlin-android here.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // Screens drawn on the JVM and compared, as SMS's app/build.gradle.kts (roborazzi plugin).
    alias(libs.plugins.roborazzi)
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
        versionCode = 127
        versionName = "0.9.27"
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

    // Screens drawn on the JVM by Robolectric and compared by Roborazzi (its README's setup), copied from Language's
    // https://github.com/213YaZ786/Language/blob/main/app/build.gradle.kts#L104-L126 (testOptions).
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"
                it.maxHeapSize = "4096m"
                // Robolectric on JDK 17 and later, as its getting started page says.
                it.jvmArgs(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.net=ALL-UNNAMED",
                    "--add-opens=java.base/java.security=ALL-UNNAMED",
                    "--add-opens=java.base/java.text=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                    "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"
                )
            }
        }
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
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.androidx.work.runtime)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // GIF frames, decoded only inside the isolated DecoderService.
    implementation(libs.glide.gifdecoder)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    implementation(libs.androidx.webkit)

    // Guava comes in transitively at 33.3.1-android (GHSA-xxph-c9ww-hj94): forced to the fixed release
    // with a dependency constraint (https://docs.gradle.org/current/userguide/dependency_constraints.html).
    constraints {
        implementation("com.google.guava:guava:33.7.2-android") {
            because("GHSA-xxph-c9ww-hj94")
        }
    }

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
