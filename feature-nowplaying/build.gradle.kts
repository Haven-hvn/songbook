plugins {
    id("ipfsmedia.android.feature")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.ipfsmedia.feature.nowplaying"
}

dependencies {
    implementation(project(":feature-video"))
}
