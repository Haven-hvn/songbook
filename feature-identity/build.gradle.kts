plugins {
    id("ipfsmedia.android.feature")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.ipfsmedia.feature.identity"
}

dependencies {
    implementation(project(":core-wallet"))
    implementation(project(":core-security"))
}
