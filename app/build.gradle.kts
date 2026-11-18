import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("ipfsmedia.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

// `-Pabi=x86_64` (or a comma-separated list, e.g. `-Pabi=arm64-v8a,x86_64`) builds one APK
// per ABI, named app-<abi>-debug.apk. Without it the APK stays universal, which is what the
// `app-debug` artifact and the x86_64 emulator job already use.
val requestedAbis = providers.gradleProperty("abi").orNull
    ?.split(',')
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.toSet()
    ?.takeIf { it.isNotEmpty() }

android {
    namespace = "app.ipfsmedia.player"

    defaultConfig {
        applicationId = "app.ipfsmedia.player"
        // Googlebooks run Android 17 (API 37). minSdk stays at 29 so the app still installs on
        // other Android devices, with plain fallbacks there (docs/PLAN.md §8).
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Only when `-Pabi` is given: split the APK by ABI instead of shipping every
    // ABI in one file. Disabled by default so the release artifact is unchanged.
    splits {
        abi {
            isEnable = requestedAbis != null
            isUniversalApk = false
            reset()
            requestedAbis?.let { include(*it.toTypedArray()) }
        }
    }

    // Required by io.github.haven-hvn:foc-cache (it declares core library desugaring in its
    // AAR metadata). Source and target compatibility come from the convention plugin.
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
            it.failOnNoDiscoveredTests = false
        }
    }
}

// Same opt-ins as the Compose convention plugin (build-logic); :app uses the application one.
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "androidx.compose.animation.ExperimentalSharedTransitionApi",
            "androidx.compose.ui.ExperimentalComposeUiApi",
        )
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // The app hosts the Hilt component, so it aggregates every @Module on the classpath and its
    // generated code names the bound types. Every module therefore has to be on this module's
    // compile classpath, including ones the app never touches itself.
    implementation(project(":core-domain"))
    implementation(project(":core-design"))
    implementation(project(":core-navigation"))
    implementation(project(":core-library"))
    implementation(project(":core-userdata"))
    implementation(project(":core-social"))
    implementation(project(":core-player"))
    implementation(project(":core-wallet"))
    implementation(project(":core-haven-aol"))
    implementation(project(":core-arkiv"))
    implementation(project(":core-collections"))
    implementation(project(":core-crypto"))
    implementation(project(":core-attestation"))
    implementation(project(":core-cache"))
    implementation(project(":core-cache-mirror"))
    implementation(project(":core-security"))

    implementation(project(":feature-onboarding"))
    implementation(project(":feature-identity"))
    implementation(project(":feature-home"))
    implementation(project(":feature-collection"))
    implementation(project(":feature-album"))
    implementation(project(":feature-video"))
    implementation(project(":feature-datadao"))
    implementation(project(":feature-discussion"))
    implementation(project(":feature-uploader"))
    implementation(project(":feature-search"))
    implementation(project(":feature-playlists"))
    implementation(project(":feature-history"))
    implementation(project(":feature-nowplaying"))
    implementation(project(":feature-settings"))

    // The activity registers itself with AppKit for the wallet's return link.
    implementation(platform(libs.reown.bom))
    implementation(libs.reown.core)
    implementation(libs.reown.appkit)

    implementation(libs.foc.cache)
    implementation(libs.timber)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.session)
    // core-security cancels per-wallet work on disconnect.
    implementation(libs.androidx.work.runtime)
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
    testImplementation(libs.kotlinx.coroutines.test)
}
