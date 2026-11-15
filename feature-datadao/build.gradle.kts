plugins {
    id("ipfsmedia.android.feature")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.ipfsmedia.feature.datadao"
}

dependencies {
    implementation(project(":core-social"))
    implementation(project(":feature-discussion"))
}
