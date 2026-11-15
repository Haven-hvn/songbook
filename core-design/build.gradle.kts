plugins {
    id("ipfsmedia.android.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.ipfsmedia.core.design"
}

dependencies {
    // Shared components are drawn from the product model (a card takes a LibraryItem, the drag
    // layer carries a DragPayload), so those types are part of this module's API.
    api(project(":core-library"))
    // Cover art is decoded off the composition thread.
    implementation(libs.kotlinx.coroutines)
}
