plugins {
    id("ipfsmedia.android.feature")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.ipfsmedia.feature.onboarding"
}

dependencies {
    implementation(project(":core-wallet"))
}
