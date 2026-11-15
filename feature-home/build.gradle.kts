plugins {
    id("ipfsmedia.android.feature")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.ipfsmedia.feature.home"
}

dependencies {
    implementation(project(":core-social"))
}
