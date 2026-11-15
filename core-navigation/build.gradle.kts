plugins {
    id("ipfsmedia.android.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "app.ipfsmedia.core.navigation"
}

dependencies {
    // Route keys and the entry-provider type appear in every feature module's signatures.
    api(libs.androidx.navigation3.runtime)
    api(libs.kotlinx.serialization.core)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
