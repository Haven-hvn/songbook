import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("ipfsmedia.android.library")
}

android {
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    // Material 3 Expressive lives in the 1.5 line; the BOM pins 1.4.0 (docs/PLAN.md §8).
    implementation("androidx.compose.material3:material3:1.5.0-rc01")
    // Window size classes (currentWindowAdaptiveInfo). Version from the BOM.
    implementation("androidx.compose.material3.adaptive:adaptive")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.hilt:hilt-lifecycle-viewmodel-compose:1.3.0")
    implementation("com.google.dagger:hilt-android:2.60.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

// The Expressive components and a few layout and input APIs the app is built on are still marked
// experimental. Opting in here, once, keeps the annotation out of every file; the list is the
// complete set the app is allowed to use.
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
