plugins {
    id("ipfsmedia.android.library")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "app.ipfsmedia.core.player"
}

dependencies {
    api(project(":core-library"))
    implementation(project(":core-domain"))
    implementation(project(":core-userdata"))
    implementation(project(":core-wallet"))
    implementation(project(":core-haven-aol"))
    implementation(project(":core-crypto"))
    implementation(project(":core-cache"))
    implementation(project(":core-cache-mirror"))
    implementation(project(":core-collections"))
    implementation(project(":core-security"))
    implementation(libs.foc.cache)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.androidx.core.ktx)
    // The UI holds the controller (a Media3 Player) for video rendering, so these are API.
    api(libs.androidx.media3.common)
    api(libs.androidx.media3.session)
    implementation(libs.androidx.media3)
    implementation(libs.androidx.media3.datasource)
    // FFmpeg software decoders (Vorbis/Opus/FLAC...) for devices without a platform
    // decoder. Wired via NextRenderersFactory in PlaybackService with extension
    // renderers ON, so platform MediaCodec decoders still win when available.
    implementation(libs.nextlib.media3ext)
    implementation(libs.timber)
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
    testImplementation(libs.json)
}
