import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // AGP 9 ships Kotlin support built in, so there is no `kotlin.android` plugin here.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The Firebase Gradle plugin hard-fails when google-services.json is missing. Drop the file into
// app/ (Firebase console -> Project settings -> Your apps -> Android) and it wires itself up on the
// next sync; until then the app runs against the in-memory fake backend.
val googleServicesJson = file("google-services.json")
if (googleServicesJson.exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

android {
    namespace = "com.example.jalraksha"
    // Compose BOM 2026.08, core-ktx 1.19 and okhttp 5.5 all require compiling against API 37.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.jalraksha"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Railway hosts the central-dashboard API. Override per machine in local.properties or via
        // -PjalrakshaApiBaseUrl=... so nobody has to edit this file to point at a preview deploy.
        val apiBaseUrl = (project.findProperty("jalrakshaApiBaseUrl") as String?)
            ?: "https://jalraksha-api.up.railway.app/"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("boolean", "HAS_FIREBASE", "${googleServicesJson.exists()}")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Ship only the languages the picker offers. Without this, library resources drag in ~80
        // other locales, and a handset set to Spanish would show half-Spanish Material strings.
        localeFilters += listOf("en", "hi", "mr", "bn", "te", "ta", "gu", "kn")
    }

    bundle {
        language {
            // The app picks its own language, so Play must not strip the ones the device is not
            // set to — splitting by language would leave every choice but one downloading at
            // runtime, or simply missing.
            enableSplit = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// TranslationCompletenessTest reads strings.xml straight off disk rather than through a compiled
// R class, so Gradle cannot see the dependency on its own and would report the task up to date
// after a translation changed — exactly when the guard needs to run.
tasks.withType<Test>().configureEach {
    inputs.dir(layout.projectDirectory.dir("src/main/res"))
        .withPropertyName("stringResources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // Firebase Auth drags in fragment 1.1.0, which predates the ActivityResult APIs MainActivity
    // uses for the notification permission. Pin it forward.
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
