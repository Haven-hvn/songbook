plugins {
    id("ipfsmedia.android.library")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "app.ipfsmedia.core.library"
}

dependencies {
    // Library items carry the domain record and are built from user-data rows; both appear in
    // this module's public types.
    api(project(":core-domain"))
    api(project(":core-userdata"))
    implementation(project(":core-cache-mirror"))
    implementation(project(":core-wallet"))
    implementation(libs.kotlinx.coroutines)
    implementation(libs.timber)
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
    testImplementation(libs.json)
}
